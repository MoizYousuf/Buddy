// Streams the Mac's sound (everything apps play) as raw PCM 16-bit little-endian on stdout.
// usage: macaudio <sample rate> <channels>     e.g. 48000 2 at home, 24000 1 on mobile data
import Foundation
import ScreenCaptureKit
import CoreMedia

let args = CommandLine.arguments
let rate = args.count > 1 ? Int(args[1]) ?? 48000 : 48000
let channels = args.count > 2 ? Int(args[2]) ?? 2 : 2
let out = FileHandle.standardOutput
let debug = ProcessInfo.processInfo.environment["MACAUDIO_DEBUG"] != nil

final class Sink: NSObject, SCStreamOutput, SCStreamDelegate {
    func stream(_ s: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .audio else { return }
        if debug { FileHandle.standardError.write("audio \(CMSampleBufferGetNumSamples(sb))\n".data(using: .utf8)!) }
        var block: CMBlockBuffer?
        var need = 0
        CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(sb, bufferListSizeNeededOut: &need, bufferListOut: nil,
            bufferListSize: 0, blockBufferAllocator: nil, blockBufferMemoryAllocator: nil, flags: 0, blockBufferOut: nil)
        let raw = UnsafeMutableRawPointer.allocate(byteCount: need, alignment: 16); defer { raw.deallocate() }
        let list = raw.bindMemory(to: AudioBufferList.self, capacity: 1)
        guard CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(sb, bufferListSizeNeededOut: nil, bufferListOut: list,
              bufferListSize: need, blockBufferAllocator: nil, blockBufferMemoryAllocator: nil,
              flags: kCMSampleBufferFlag_AudioBufferList_Assure16ByteAlignment, blockBufferOut: &block) == noErr else {
            if debug { FileHandle.standardError.write("bufferlist failed\n".data(using: .utf8)!) }; return }
        let bufs = UnsafeMutableAudioBufferListPointer(list)
        let frames = Int(CMSampleBufferGetNumSamples(sb))
        var pcm = Data(count: frames * channels * 2)
        pcm.withUnsafeMutableBytes { (dst: UnsafeMutableRawBufferPointer) in
            let o = dst.bindMemory(to: Int16.self)
            for c in 0..<channels {
                // SCK gives float32, one buffer per channel (non-interleaved)
                let b = bufs[min(c, bufs.count - 1)]
                guard let src = b.mData?.assumingMemoryBound(to: Float.self) else { continue }
                for i in 0..<frames { o[i * channels + c] = Int16(max(-1, min(1, src[i])) * 32767) }
            }
        }
        out.write(pcm)
    }
    func stream(_ s: SCStream, didStopWithError e: Error) { exit(2) }
}

let sink = Sink()
var keep: SCStream?
Task {
    do {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
        guard let d = content.displays.first else { exit(3) }
        let cfg = SCStreamConfiguration()
        cfg.capturesAudio = true; cfg.excludesCurrentProcessAudio = true
        cfg.sampleRate = rate; cfg.channelCount = channels
        cfg.width = 2; cfg.height = 2; cfg.minimumFrameInterval = CMTime(value: 1, timescale: 1)   // no picture needed
        let st = SCStream(filter: SCContentFilter(display: d, excludingWindows: []), configuration: cfg, delegate: sink)
        try st.addStreamOutput(sink, type: .audio, sampleHandlerQueue: DispatchQueue(label: "a"))
        try await st.startCapture(); keep = st
    } catch { FileHandle.standardError.write("\(error)\n".data(using: .utf8)!); exit(1) }
}
signal(SIGPIPE) { _ in exit(0) }
RunLoop.main.run()
