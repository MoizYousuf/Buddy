// macsvg <in.png> <out.svg> [colours=6]
// Colour vector (SVG) of a design for Cricut / SVG buyers: k-means to a few flat colours, each colour traced with
// potrace (free, local) and stacked largest-first. Transparent pixels stay empty.
import Foundation
import CoreGraphics
import ImageIO

let a = CommandLine.arguments
guard a.count >= 3, let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: a[1]) as CFURL, nil),
      let img0 = CGImageSourceCreateImageAtIndex(src, 0, nil) else { print("usage: macsvg in.png out.svg [k]"); exit(1) }
let K = a.count > 3 ? Int(a[3])! : 10
// work at max 1600 px (traces are smooth, file stays small)
let sc = min(1.0, 1600.0 / Double(max(img0.width, img0.height)))
let W = Int(Double(img0.width) * sc), H = Int(Double(img0.height) * sc)
var px = [UInt8](repeating: 0, count: W * H * 4)
let ctx = CGContext(data: &px, width: W, height: H, bitsPerComponent: 8, bytesPerRow: W * 4,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.interpolationQuality = .high
ctx.draw(img0, in: CGRect(x: 0, y: 0, width: W, height: H))

// opaque pixels as colours
var idx: [Int] = []
for i in 0..<(W * H) where px[i * 4 + 3] > 128 { idx.append(i) }
if idx.isEmpty { print("empty image"); exit(1) }
func col(_ i: Int) -> (Double, Double, Double) {
    let al = Double(px[i * 4 + 3]) / 255
    return (Double(px[i * 4]) / al, Double(px[i * 4 + 1]) / al, Double(px[i * 4 + 2]) / al)
}
// k-means on a sample
let sample = stride(from: 0, to: idx.count, by: max(1, idx.count / 40000)).map { idx[$0] }
// farthest-point start: every distinct colour (red, blue, gold...) gets its own centre
var cent: [(Double, Double, Double)] = [col(sample[0])]
while cent.count < K {
    var best = sample[0], bd = -1.0
    for i in sample { let c = col(i); let d = cent.map { pow(c.0 - $0.0, 2) + pow(c.1 - $0.1, 2) + pow(c.2 - $0.2, 2) }.min()!; if d > bd { bd = d; best = i } }
    cent.append(col(best))
}
func near(_ c: (Double, Double, Double)) -> Int {
    var b = 0, bd = Double.infinity
    for (k, m) in cent.enumerated() { let d = pow(c.0 - m.0, 2) + pow(c.1 - m.1, 2) + pow(c.2 - m.2, 2); if d < bd { bd = d; b = k } }
    return b
}
for _ in 0..<12 {
    var sum = [(Double, Double, Double, Double)](repeating: (0, 0, 0, 0), count: K)
    for i in sample { let c = col(i); let k = near(c); sum[k].0 += c.0; sum[k].1 += c.1; sum[k].2 += c.2; sum[k].3 += 1 }
    for k in 0..<K where sum[k].3 > 0 { cent[k] = (sum[k].0 / sum[k].3, sum[k].1 / sum[k].3, sum[k].2 / sum[k].3) }
}
var label = [Int](repeating: -1, count: W * H)
var count = [Int](repeating: 0, count: K)
for i in idx { let k = near(col(i)); label[i] = k; count[k] += 1 }

let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("macsvg-\(getpid())")
try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
var layers: [String] = []
for k in (0..<K).sorted(by: { count[$0] > count[$1] }) where count[k] > W * H / 4000 {
    // PBM: 1 = this colour. Bigger colours first, so smaller details sit on top.
    var pbm = "P1\n\(W) \(H)\n"
    pbm.reserveCapacity(W * H * 2)
    for y in 0..<H { var row = ""; row.reserveCapacity(W); for x in 0..<W { row.append(label[y * W + x] == k ? "1" : "0") }; pbm += row + "\n" }
    let pin = tmp.appendingPathComponent("c\(k).pbm"), pout = tmp.appendingPathComponent("c\(k).svg")
    try! pbm.write(to: pin, atomically: true, encoding: .ascii)
    let hex = String(format: "#%02X%02X%02X", Int(min(255, cent[k].0)), Int(min(255, cent[k].1)), Int(min(255, cent[k].2)))
    let p = Process()
    p.executableURL = URL(fileURLWithPath: "/opt/homebrew/bin/potrace")
    p.arguments = ["-s", "--color", hex, "-t", "4", "-O", "0.4", "-o", pout.path, pin.path]
    try! p.run(); p.waitUntilExit()
    guard let svg = try? String(contentsOf: pout, encoding: .utf8),
          let g0 = svg.range(of: "<g "), let g1 = svg.range(of: "</svg>", options: .backwards) else { continue }
    layers.append("<!-- colour \(hex) -->\n" + String(svg[g0.lowerBound..<g1.lowerBound]))
}
try? FileManager.default.removeItem(at: tmp)
// potrace works in points (72/inch) with the same pixel grid
let out = "<?xml version=\"1.0\" standalone=\"no\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"\(W)\" height=\"\(H)\" viewBox=\"0 0 \(W) \(H)\">\n" +
    layers.joined(separator: "\n") + "\n</svg>\n"
try! out.write(toFile: a[2], atomically: true, encoding: .utf8)
print("{\"colours\": \(layers.count), \"size\": [\(W), \(H)]}")
