// Streams one display as JPEG frames on stdout: [4-byte big-endian length][jpeg] ... (ScreenCaptureKit, ~20 fps).
// usage: macstream <display index> <max width> <quality 0-1> <fps>
import Foundation
import ScreenCaptureKit
import CoreImage
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
let index = args.count > 1 ? Int(args[1]) ?? 0 : 0
let maxW = args.count > 2 ? Int(args[2]) ?? 1280 : 1280
let quality = args.count > 3 ? Double(args[3]) ?? 0.5 : 0.5
let fps = args.count > 4 ? Int(args[4]) ?? 20 : 20
let ctx = CIContext(options: [.useSoftwareRenderer: false])
let out = FileHandle.standardOutput

final class Sink: NSObject, SCStreamOutput, SCStreamDelegate {
    func stream(_ s: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen, let px = sb.imageBuffer else { return }
        let img = CIImage(cvPixelBuffer: px)
        guard let cg = ctx.createCGImage(img, from: img.extent) else { return }
        let data = NSMutableData()
        guard let dst = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return }
        CGImageDestinationAddImage(dst, cg, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        guard CGImageDestinationFinalize(dst) else { return }
        var n = UInt32(data.length).bigEndian
        out.write(Data(bytes: &n, count: 4)); out.write(data as Data)
    }
    func stream(_ s: SCStream, didStopWithError e: Error) { FileHandle.standardError.write("\(e)\n".data(using: .utf8)!); exit(2) }
}

let sink = Sink()
var keep: SCStream?
Task {
    do {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
        let ds = content.displays.sorted { $0.displayID == CGMainDisplayID() ? true : ($1.displayID == CGMainDisplayID() ? false : $0.displayID < $1.displayID) }
        guard index < ds.count else { exit(3) }
        let d = ds[index]
        let cfg = SCStreamConfiguration()
        let scale = min(1.0, Double(maxW) / Double(d.width))
        cfg.width = Int(Double(d.width) * scale); cfg.height = Int(Double(d.height) * scale)
        cfg.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(fps))
        cfg.queueDepth = 3; cfg.showsCursor = true; cfg.pixelFormat = kCVPixelFormatType_32BGRA
        let st = SCStream(filter: SCContentFilter(display: d, excludingWindows: []), configuration: cfg, delegate: sink)
        try st.addStreamOutput(sink, type: .screen, sampleHandlerQueue: DispatchQueue(label: "f"))
        try await st.startCapture(); keep = st
    } catch { FileHandle.standardError.write("\(error)\n".data(using: .utf8)!); exit(1) }
}
signal(SIGPIPE) { _ in exit(0) }
RunLoop.main.run()
