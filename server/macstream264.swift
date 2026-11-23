// Streams one display as H.264 on stdout, ~5-10x less data than JPEG frames (for mobile data / Tailscale).
// usage: macstream264 <display index> <max width> <kbps> <fps>
// Packets: [4-byte big-endian length][1-byte kind][payload]
//   kind 0 = config: [2-byte width][2-byte height] + SPS/PPS as Annex-B (start codes)
//   kind 1 = key frame, kind 2 = other frame: Annex-B NAL units
import Foundation
import ScreenCaptureKit
import VideoToolbox

let args = CommandLine.arguments
let index = args.count > 1 ? Int(args[1]) ?? 0 : 0
let maxW = args.count > 2 ? Int(args[2]) ?? 1280 : 1280
let kbps = args.count > 3 ? Int(args[3]) ?? 1500 : 1500
let fps = args.count > 4 ? Int(args[4]) ?? 24 : 24
let out = FileHandle.standardOutput
let startCode: [UInt8] = [0, 0, 0, 1]
var width = 0, height = 0
var session: VTCompressionSession?
var lastFrame: CVPixelBuffer?
let lock = NSLock()

func emit(_ kind: UInt8, _ body: Data) {
    var n = UInt32(body.count + 1).bigEndian
    var d = Data(bytes: &n, count: 4); d.append(kind); d.append(body)
    lock.lock(); out.write(d); lock.unlock()
}

let encoded: VTCompressionOutputCallback = { _, _, status, _, sb in
    guard status == noErr, let sb = sb, CMSampleBufferDataIsReady(sb) else { return }
    let atts = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: false) as? [[CFString: Any]]
    let key = !(atts?.first?[kCMSampleAttachmentKey_NotSync] as? Bool ?? false)
    if key, let fmt = CMSampleBufferGetFormatDescription(sb) {
        var cfg = Data()
        var w = UInt16(width).bigEndian, h = UInt16(height).bigEndian
        cfg.append(Data(bytes: &w, count: 2)); cfg.append(Data(bytes: &h, count: 2))
        var count = 0
        CMVideoFormatDescriptionGetH264ParameterSetAtIndex(fmt, parameterSetIndex: 0, parameterSetPointerOut: nil,
            parameterSetSizeOut: nil, parameterSetCountOut: &count, nalUnitHeaderLengthOut: nil)
        for i in 0..<count {
            var p: UnsafePointer<UInt8>?; var size = 0
            CMVideoFormatDescriptionGetH264ParameterSetAtIndex(fmt, parameterSetIndex: i, parameterSetPointerOut: &p,
                parameterSetSizeOut: &size, parameterSetCountOut: nil, nalUnitHeaderLengthOut: nil)
            if let p = p { cfg.append(contentsOf: startCode); cfg.append(p, count: size) }
        }
        emit(0, cfg)
    }
    guard let block = CMSampleBufferGetDataBuffer(sb) else { return }
    var total = 0; var ptr: UnsafeMutablePointer<CChar>?
    CMBlockBufferGetDataPointer(block, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &total, dataPointerOut: &ptr)
    guard let base = ptr else { return }
    var body = Data(capacity: total + 16); var off = 0
    while off + 4 <= total {                       // AVCC (4-byte lengths) -> Annex-B (start codes)
        var len: UInt32 = 0
        memcpy(&len, base + off, 4); len = CFSwapInt32BigToHost(len)
        off += 4
        if off + Int(len) > total { break }
        body.append(contentsOf: startCode)
        body.append(Data(bytes: base + off, count: Int(len)))
        off += Int(len)
    }
    emit(key ? 1 : 2, body)
}

func makeSession(_ w: Int, _ h: Int) {
    width = w; height = h
    VTCompressionSessionCreate(allocator: nil, width: Int32(w), height: Int32(h), codecType: kCMVideoCodecType_H264,
        encoderSpecification: nil, imageBufferAttributes: nil, compressedDataAllocator: nil,
        outputCallback: encoded, refcon: nil, compressionSessionOut: &session)
    guard let s = session else { exit(4) }
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_RealTime, value: kCFBooleanTrue)
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_ProfileLevel, value: kVTProfileLevel_H264_Main_AutoLevel)
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_AllowFrameReordering, value: kCFBooleanFalse)
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_AverageBitRate, value: NSNumber(value: kbps * 1000))
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_DataRateLimits, value: [NSNumber(value: kbps * 1000 / 8 * 2), NSNumber(value: 1)] as CFArray)
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_MaxKeyFrameInterval, value: NSNumber(value: fps * 3))
    VTSessionSetProperty(s, key: kVTCompressionPropertyKey_ExpectedFrameRate, value: NSNumber(value: fps))
    VTCompressionSessionPrepareToEncodeFrames(s)
}

var frameNo: Int64 = 0
func encode(_ px: CVPixelBuffer) {
    guard let s = session else { return }
    frameNo += 1
    VTCompressionSessionEncodeFrame(s, imageBuffer: px, presentationTimeStamp: CMTime(value: frameNo, timescale: CMTimeScale(fps)),
        duration: .invalid, frameProperties: nil, sourceFrameRefcon: nil, infoFlagsOut: nil)
}

final class Sink: NSObject, SCStreamOutput, SCStreamDelegate {
    func stream(_ s: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen, let px = sb.imageBuffer else { return }
        lock.lock(); lastFrame = px; lock.unlock()
        encode(px)
    }
    func stream(_ s: SCStream, didStopWithError e: Error) { exit(2) }
}

// A still screen sends no frames; repeat the last one every 2 s so a phone that just connected still gets a picture.
Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { _ in
    lock.lock(); let f = lastFrame; lock.unlock()
    if let f = f { encode(f) }
}

let sink = Sink()
var keep: SCStream?
Task {
    do {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
        let ds = content.displays.sorted { $0.displayID == CGMainDisplayID() ? true : ($1.displayID == CGMainDisplayID() ? false : $0.displayID < $1.displayID) }
        guard index < ds.count else { exit(3) }
        let d = ds[index]
        let scale = min(1.0, Double(maxW) / Double(d.width))
        let w = Int(Double(d.width) * scale) & ~1, h = Int(Double(d.height) * scale) & ~1
        makeSession(w, h)
        let cfg = SCStreamConfiguration()
        cfg.width = w; cfg.height = h
        cfg.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(fps))
        cfg.queueDepth = 4; cfg.showsCursor = true; cfg.pixelFormat = kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
        let st = SCStream(filter: SCContentFilter(display: d, excludingWindows: []), configuration: cfg, delegate: sink)
        try st.addStreamOutput(sink, type: .screen, sampleHandlerQueue: DispatchQueue(label: "f"))
        try await st.startCapture(); keep = st
    } catch { FileHandle.standardError.write("\(error)\n".data(using: .utf8)!); exit(1) }
}
signal(SIGPIPE) { _ in exit(0) }
RunLoop.main.run()
