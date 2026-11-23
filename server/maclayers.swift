// maclayers <design.png> <outdir> [width height]
// Splits one design into Canva-ready layers with Apple's on-device Vision (free, offline):
//   print.png        whole design, background removed, on a transparent print canvas (default 4500x5400, 300 dpi)
//   background.png   only the background (what was behind the artwork)
//   art_no_text.png  the artwork with the text painted out (put your own text on top in Canva)
//   element_N.png    each separate object (eagle, flag, frame...) on its own
//   person.png       people / photo, if any
//   text_N.png       each line of text on its own, text.json = the words, position and colour
// Every layer has the SAME canvas size and position, so stacking them in Canva gives back the exact design.
import Foundation
import Vision
import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UniformTypeIdentifiers

var args = CommandLine.arguments
let cutoutOnly = args.count > 1 && args[1] == "--cutout"
if cutoutOnly { args.remove(at: 1) }
guard args.count >= 3, let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
      let img = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
    FileHandle.standardError.write("usage: maclayers design.png outdir [w h]\n".data(using: .utf8)!); exit(1)
}
let out = cutoutOnly ? URL(fileURLWithPath: args[2]).deletingLastPathComponent() : URL(fileURLWithPath: args[2])
try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
let PW = args.count > 4 ? Int(args[3])! : 4500, PH = args.count > 4 ? Int(args[4])! : 5400
let W = img.width, H = img.height
let ctx = CIContext()
let srgb = CGColorSpace(name: CGColorSpace.sRGB)!

// ---------- pixels ----------
func rgba(_ cg: CGImage) -> [UInt8] {
    var buf = [UInt8](repeating: 0, count: W * H * 4)
    let c = CGContext(data: &buf, width: W, height: H, bitsPerComponent: 8, bytesPerRow: W * 4, space: srgb,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.draw(cg, in: CGRect(x: 0, y: 0, width: W, height: H)); return buf
}
func image(_ buf: [UInt8]) -> CGImage {
    var b = buf
    let c = CGContext(data: &b, width: W, height: H, bitsPerComponent: 8, bytesPerRow: W * 4, space: srgb,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    return c.makeImage()!
}
func maskBuf(_ pb: CVPixelBuffer) -> [UInt8] {   // Vision masks are float 0..1 at image size
    let ci = CIImage(cvPixelBuffer: pb)
    let sx = CGFloat(W) / ci.extent.width, sy = CGFloat(H) / ci.extent.height
    let m = ctx.createCGImage(ci.transformed(by: .init(scaleX: sx, y: sy)), from: CGRect(x: 0, y: 0, width: W, height: H))!
    var buf = [UInt8](repeating: 0, count: W * H)
    let c = CGContext(data: &buf, width: W, height: H, bitsPerComponent: 8, bytesPerRow: W, space: CGColorSpaceCreateDeviceGray(),
                      bitmapInfo: CGImageAlphaInfo.none.rawValue)!
    c.draw(m, in: CGRect(x: 0, y: 0, width: W, height: H)); return buf
}
let base = rgba(img)
func layer(_ alpha: [UInt8], from px: [UInt8] = base) -> [UInt8] {   // keep pixels where alpha, premultiplied
    var o = [UInt8](repeating: 0, count: W * H * 4)
    for i in 0..<(W * H) where alpha[i] > 0 {
        let a = Int(alpha[i]) * Int(px[i * 4 + 3]) / 255
        o[i * 4] = UInt8(Int(px[i * 4]) * a / 255); o[i * 4 + 1] = UInt8(Int(px[i * 4 + 1]) * a / 255)
        o[i * 4 + 2] = UInt8(Int(px[i * 4 + 2]) * a / 255); o[i * 4 + 3] = UInt8(a)
    }
    return o
}

// ---------- save on the print canvas (same placement for every layer) ----------
let scale = min(Double(PW) * 0.94 / Double(W), Double(PH) * 0.94 / Double(H))
let dw = Int(Double(W) * scale), dh = Int(Double(H) * scale)
let ox = (PW - dw) / 2, oy = (PH - dh) / 2
func save(_ cg: CGImage, _ name: String) {
    // upscale for print: Lanczos, then a light sharpen so lines and text stay crisp at 300 dpi
    var up = CIImage(cgImage: cg).applyingFilter("CILanczosScaleTransform", parameters: ["inputScale": scale, "inputAspectRatio": 1.0])
    if scale > 1.2 {
        up = up.applyingFilter("CIUnsharpMask", parameters: ["inputRadius": 1.2 * scale, "inputIntensity": 0.45])
    }
    let big = ctx.createCGImage(up, from: CGRect(x: 0, y: 0, width: dw, height: dh), format: .RGBA8, colorSpace: srgb)!
    let c = CGContext(data: nil, width: PW, height: PH, bitsPerComponent: 8, bytesPerRow: PW * 4, space: srgb,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.draw(big, in: CGRect(x: ox, y: PH - oy - dh, width: dw, height: dh))
    let dst = CGImageDestinationCreateWithURL(out.appendingPathComponent(name) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dst, c.makeImage()!, [kCGImagePropertyDPIWidth: 300, kCGImagePropertyDPIHeight: 300] as CFDictionary)
    CGImageDestinationFinalize(dst)
}

let handler = VNImageRequestHandler(cgImage: img)
var info: [String: Any] = ["source": [W, H], "print": [PW, PH], "dpi": 300]

// ---------- subject and each element ----------
let fg = VNGenerateForegroundInstanceMaskRequest()
try? handler.perform([fg])
var subject = [UInt8](repeating: 255, count: W * H)
var elements = 0
if let r = fg.results?.first {
    if let all = try? r.generateScaledMaskForImage(forInstances: r.allInstances, from: handler) { subject = maskBuf(all) }
    for inst in r.allInstances.sorted() {
        guard let m = try? r.generateScaledMaskForImage(forInstances: [inst], from: handler) else { continue }
        let mb = maskBuf(m)
        if mb.reduce(0, { $0 + ($1 > 127 ? 1 : 0) }) < W * H / 400 { continue }   // skip specks
        elements += 1; save(image(layer(mb)), "element_\(elements).png")
    }
}
// flat art on a plain light background: the background is the near-white area connected to the edges (exact, keeps
// white parts inside the design). Vision's subject mask can drop parts of a big emblem, so this wins when it applies.
do {
    func light(_ i: Int) -> Bool { base[i*4] > 232 && base[i*4+1] > 232 && base[i*4+2] > 232 }
    var edge = 0, lit = 0
    for x in 0..<W { for y in [0, H - 1] { edge += 1; if light(y * W + x) { lit += 1 } } }
    for y in 0..<H { for x in [0, W - 1] { edge += 1; if light(y * W + x) { lit += 1 } } }
    if Double(lit) / Double(edge) > 0.9 {
        var bg = [Bool](repeating: false, count: W * H); var st: [Int] = []
        for x in 0..<W { for y in [0, H - 1] { let i = y * W + x; if light(i) && !bg[i] { bg[i] = true; st.append(i) } } }
        for y in 0..<H { for x in [0, W - 1] { let i = y * W + x; if light(i) && !bg[i] { bg[i] = true; st.append(i) } } }
        while let i = st.popLast() {
            let x = i % W, y = i / W
            for (dx, dy) in [(1,0),(-1,0),(0,1),(0,-1)] {
                let nx = x + dx, ny = y + dy
                if nx >= 0, ny >= 0, nx < W, ny < H { let j = ny * W + nx; if !bg[j] && light(j) { bg[j] = true; st.append(j) } }
            }
        }
        subject = bg.map { $0 ? 0 : 255 }
        info["cutout"] = "white-key"
    }
}
let cut = layer(subject)
if cutoutOnly {   // trim to the object and save at its own size
    var minX = W, minY = H, maxX = 0, maxY = 0
    for y in 0..<H { for x in 0..<W where subject[y * W + x] > 20 { minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y) } }
    let full = image(cut)
    let crop = maxX > minX ? full.cropping(to: CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1))! : full
    let dst = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dst, crop, [kCGImagePropertyDPIWidth: 300, kCGImagePropertyDPIHeight: 300] as CFDictionary)
    CGImageDestinationFinalize(dst); exit(0)
}
save(image(cut), "print.png")
save(image(layer(subject.map { 255 - $0 })), "background.png")

// ---------- people ----------
let ppl = VNDetectHumanRectanglesRequest()
try? handler.perform([ppl])
if let n = ppl.results?.count, n > 0 {
    let seg = VNGeneratePersonSegmentationRequest(); seg.qualityLevel = .accurate
    try? handler.perform([seg])
    if let pb = seg.results?.first?.pixelBuffer { save(image(layer(maskBuf(pb))), "person.png"); info["people"] = n }
}

// ---------- text: each line alone, and the artwork with text painted out ----------
let tr = VNRecognizeTextRequest(); tr.recognitionLevel = .accurate; tr.usesLanguageCorrection = false
try? handler.perform([tr])
var noText = cut
var texts: [[String: Any]] = []
for (k, o) in (tr.results ?? []).enumerated() {
    guard let cand = o.topCandidates(1).first, cand.confidence > 0.3 else { continue }
    let b = o.boundingBox   // normalised, origin bottom-left
    let pad = 0.04
    let x0 = max(0, Int((b.minX - b.width * pad / 4) * Double(W))), x1 = min(W - 1, Int((b.maxX + b.width * pad / 4) * Double(W)))
    let y0 = max(0, Int((1 - b.maxY - b.height * pad) * Double(H))), y1 = min(H - 1, Int((1 - b.minY + b.height * pad) * Double(H)))
    if x1 <= x0 || y1 <= y0 { continue }
    // fill colour = most common colour on the box border; text = pixels far from it
    var counts: [Int: Int] = [:]
    func key(_ i: Int) -> Int { (Int(base[i * 4]) / 16) << 8 | (Int(base[i * 4 + 1]) / 16) << 4 | Int(base[i * 4 + 2]) / 16 }
    for x in x0...x1 { for y in [y0, y1] { counts[key(y * W + x), default: 0] += 1 } }
    for y in y0...y1 { for x in [x0, x1] { counts[key(y * W + x), default: 0] += 1 } }
    let bk = counts.max { $0.value < $1.value }!.key
    var fr = 0, fgc = 0, fb = 0, nf = 0, tr_ = 0, tg = 0, tb = 0, nt = 0
    for y in y0...y1 { for x in x0...x1 { let i = y * W + x; if key(i) == bk { fr += Int(base[i*4]); fgc += Int(base[i*4+1]); fb += Int(base[i*4+2]); nf += 1 } } }
    nf = max(nf, 1)
    let fill = (fr / nf, fgc / nf, fb / nf)
    var tmask = [UInt8](repeating: 0, count: W * H)
    for y in y0...y1 { for x in x0...x1 {
        let i = y * W + x
        let d = abs(Int(base[i*4]) - fill.0) + abs(Int(base[i*4+1]) - fill.1) + abs(Int(base[i*4+2]) - fill.2)
        if d > 90 {
            tmask[i] = 255; tr_ += Int(base[i*4]); tg += Int(base[i*4+1]); tb += Int(base[i*4+2]); nt += 1
            noText[i*4] = UInt8(fill.0 * Int(noText[i*4+3]) / 255); noText[i*4+1] = UInt8(fill.1 * Int(noText[i*4+3]) / 255)
            noText[i*4+2] = UInt8(fill.2 * Int(noText[i*4+3]) / 255)
        }
    } }
    if nt == 0 { continue }
    save(image(layer(tmask)), "text_\(texts.count + 1).png")
    let hex = String(format: "#%02X%02X%02X", tr_ / nt, tg / nt, tb / nt)
    texts.append(["file": "text_\(texts.count + 1).png", "text": cand.string, "color": hex,
                  "x": ox + Int(Double(x0) * scale), "y": oy + Int(Double(y0) * scale),
                  "w": Int(Double(x1 - x0) * scale), "h": Int(Double(y1 - y0) * scale), "n": k])
}
save(image(noText), "art_no_text.png")

// ---------- separate pieces (parts that don't touch) as extra elements ----------
if elements <= 1 {
    let g = 4, gw = W / g, gh = H / g
    var occ = [Bool](repeating: false, count: gw * gh)
    for y in 0..<gh { for x in 0..<gw { occ[y * gw + x] = subject[(y * g) * W + x * g] > 127 } }
    var lab = [Int](repeating: 0, count: gw * gh); var n = 0; var sizes: [Int] = [0]
    for s0 in 0..<(gw * gh) where occ[s0] && lab[s0] == 0 {
        n += 1; var st = [s0]; lab[s0] = n; var c = 0
        while let p = st.popLast() {
            c += 1; let px = p % gw, py = p / gw
            for (dx, dy) in [(1,0),(-1,0),(0,1),(0,-1)] {
                let nx = px + dx, ny = py + dy
                if nx >= 0, ny >= 0, nx < gw, ny < gh { let q = ny * gw + nx; if occ[q] && lab[q] == 0 { lab[q] = n; st.append(q) } }
            }
        }
        sizes.append(c)
    }
    let big = (1...max(n, 1)).filter { n > 0 && sizes[$0] >= gw * gh / 300 }
    if big.count > 1 {
        for l in big {
            var mb = [UInt8](repeating: 0, count: W * H)
            for y in 0..<H { for x in 0..<W { let gx = min(x / g, gw - 1), gy = min(y / g, gh - 1)
                if lab[gy * gw + gx] == l { mb[y * W + x] = subject[y * W + x] } } }
            var piece = [UInt8](repeating: 0, count: W * H * 4)
            for i in 0..<(W * H) where mb[i] > 0 { for c in 0..<4 { piece[i * 4 + c] = noText[i * 4 + c] } }
            elements += 1; save(image(piece), "element_\(elements).png")
        }
        info["elements"] = elements
    }
}
info["texts"] = texts
let data = try! JSONSerialization.data(withJSONObject: info, options: [.prettyPrinted])
try! data.write(to: out.appendingPathComponent("layers.json"))
print(String(data: data, encoding: .utf8)!)
