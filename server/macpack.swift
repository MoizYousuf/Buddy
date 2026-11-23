// macpack <spec.json>
// Makes Etsy listing photos (2700x2025 JPEG) and a buyer "download instructions" PDF from a design's files.
// spec: {"out": dir, "store": "", "title": "", "subtitle": "", "type": "", "mockup": path, "print": path,
//        "layers": [paths], "includes": [""], "formats": [""], "steps": [""], "colors": ["#hex" or names],
//        "accent": "#hex", "link": "canva template link or empty", "pdf_steps": [""]}
import Foundation
import CoreGraphics
import CoreText
import ImageIO
import UniformTypeIdentifiers
import AppKit

let spec = try! JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [String: Any]
func s(_ k: String) -> String { spec[k] as? String ?? "" }
func a(_ k: String) -> [String] { spec[k] as? [String] ?? [] }
let out = URL(fileURLWithPath: s("out")); try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
let W: CGFloat = 2700, H: CGFloat = 2025

func hex(_ h: String, _ d: NSColor) -> NSColor {
    var t = h.trimmingCharacters(in: .whitespaces); if t.hasPrefix("#") { t.removeFirst() }
    guard t.count == 6, let v = UInt32(t, radix: 16) else { return d }
    return NSColor(srgbRed: CGFloat(v >> 16 & 255) / 255, green: CGFloat(v >> 8 & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
}
let accent = hex(s("accent"), NSColor(srgbRed: 0.42, green: 0.30, blue: 1, alpha: 1))
let ink = NSColor(srgbRed: 0.10, green: 0.08, blue: 0.19, alpha: 1)
let glow = NSColor(srgbRed: 1, green: 0.37, blue: 0.64, alpha: 1)
let paper = NSColor(srgbRed: 0.98, green: 0.97, blue: 0.95, alpha: 1)

func load(_ p: String) -> CGImage? {
    guard !p.isEmpty, let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: p) as CFURL, nil) else { return nil }
    return CGImageSourceCreateImageAtIndex(src, 0, nil)
}
func canvas() -> CGContext {
    let c = CGContext(data: nil, width: Int(W), height: Int(H), bitsPerComponent: 8, bytesPerRow: 0,
                      space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.setFillColor(paper.cgColor); c.fill(CGRect(x: 0, y: 0, width: W, height: H)); return c
}
func save(_ c: CGContext, _ name: String) {
    let d = CGImageDestinationCreateWithURL(out.appendingPathComponent(name) as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(d, c.makeImage()!, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
    CGImageDestinationFinalize(d)
}
// text in a box, top-left origin (y measured from the top)
@discardableResult
func text(_ c: CGContext, _ t: String, x: CGFloat, y: CGFloat, w: CGFloat, size: CGFloat, color: NSColor = ink,
          bold: Bool = true, center: Bool = false, maxH: CGFloat = 900) -> CGFloat {
    let font = NSFont.systemFont(ofSize: size, weight: bold ? .heavy : .regular)
    let para = NSMutableParagraphStyle(); para.alignment = center ? .center : .left; para.lineSpacing = size * 0.18
    let att = NSAttributedString(string: t, attributes: [.font: font, .foregroundColor: color, .paragraphStyle: para])
    let fs = CTFramesetterCreateWithAttributedString(att)
    let fit = CTFramesetterSuggestFrameSizeWithConstraints(fs, CFRange(), nil, CGSize(width: w, height: maxH), nil)
    let path = CGPath(rect: CGRect(x: x, y: H - y - fit.height, width: w, height: fit.height + 2), transform: nil)
    CTFrameDraw(CTFramesetterCreateFrame(fs, CFRange(), path, nil), c)
    return fit.height
}
func round(_ c: CGContext, _ r: CGRect, _ rad: CGFloat, _ col: NSColor) {   // r in top-left coords
    let rr = CGRect(x: r.minX, y: H - r.maxY, width: r.width, height: r.height)
    c.setFillColor(col.cgColor); c.addPath(CGPath(roundedRect: rr, cornerWidth: rad, cornerHeight: rad, transform: nil)); c.fillPath()
}
func draw(_ c: CGContext, _ img: CGImage, in r: CGRect, fill: Bool = false) {   // r top-left coords, aspect fit/fill
    let iw = CGFloat(img.width), ih = CGFloat(img.height)
    let sc = fill ? max(r.width / iw, r.height / ih) : min(r.width / iw, r.height / ih)
    let dw = iw * sc, dh = ih * sc
    c.saveGState(); c.clip(to: CGRect(x: r.minX, y: H - r.maxY, width: r.width, height: r.height))
    c.draw(img, in: CGRect(x: r.midX - dw / 2, y: H - r.midY - dh / 2, width: dw, height: dh)); c.restoreGState()
}
func badge(_ c: CGContext, _ t: String, x: CGFloat, y: CGFloat) {
    round(c, CGRect(x: x, y: y, width: CGFloat(t.count) * 34 + 90, height: 110), 55, accent)
    text(c, t, x: x + 45, y: y + 24, w: CGFloat(t.count) * 34 + 40, size: 52, color: .white)
}

let mock = load(s("mockup")), art = load(s("print"))
let layers = a("layers").compactMap(load)
var made: [String] = []

// 1. cover: mockup big, title strip
do {
    let c = canvas()
    if let m = mock ?? art { draw(c, m, in: CGRect(x: 0, y: 0, width: W, height: H), fill: true) }
    let th = text(canvas(), s("title"), x: 150, y: 0, w: W - 300, size: 92, maxH: 420)
    let boxH = th + 230
    round(c, CGRect(x: 80, y: H - 80 - boxH, width: W - 160, height: boxH), 60, NSColor.white.withAlphaComponent(0.94))
    text(c, s("title"), x: 150, y: H - 80 - boxH + 60, w: W - 300, size: 92, maxH: 420)
    text(c, s("subtitle"), x: 150, y: H - 80 - 120, w: W - 300, size: 54, color: accent, bold: true, maxH: 90)
    badge(c, "EDITABLE · INSTANT DOWNLOAD", x: 80, y: 80)
    save(c, "listing_1_cover.jpg"); made.append("listing_1_cover.jpg")
}
// 2. what's included
do {
    let c = canvas()
    text(c, "What's included", x: 150, y: 120, w: W - 300, size: 130)
    if let m = art ?? mock { round(c, CGRect(x: 150, y: 400, width: 1150, height: 1450), 50, .white); draw(c, m, in: CGRect(x: 190, y: 440, width: 1070, height: 1370)) }
    var y: CGFloat = 430
    for t in a("includes").prefix(7) {
        round(c, CGRect(x: 1420, y: y, width: 1130, height: 170), 40, .white)
        text(c, "✓  " + t, x: 1470, y: y + 45, w: 1040, size: 58, maxH: 120); y += 205
    }
    save(c, "listing_2_included.jpg"); made.append("listing_2_included.jpg")
}
// 3. layers: everything separate
if !layers.isEmpty {
    let c = canvas()
    text(c, "Every part is a separate layer", x: 150, y: 120, w: W - 300, size: 120)
    text(c, "Background, artwork, each element and each text line: move, recolour or remove anything.", x: 150, y: 290, w: W - 300, size: 60, color: accent, bold: false)
    let n = min(layers.count, 8), cols = n > 4 ? 4 : n, rows = n > 4 ? 2 : 1
    let cw = (W - 300 - CGFloat(cols - 1) * 40) / CGFloat(cols), ch = (H - 620 - CGFloat(rows - 1) * 40) / CGFloat(rows)
    for i in 0..<n {
        let r = CGRect(x: 150 + CGFloat(i % cols) * (cw + 40), y: 500 + CGFloat(i / cols) * (ch + 40), width: cw, height: ch)
        round(c, r, 40, NSColor(srgbRed: 0.93, green: 0.92, blue: 0.97, alpha: 1)); draw(c, layers[i], in: r.insetBy(dx: 40, dy: 40))
    }
    save(c, "listing_3_layers.jpg"); made.append("listing_3_layers.jpg")
}
// 4. edit in Canva steps
do {
    let c = canvas()
    round(c, CGRect(x: 0, y: 0, width: W, height: H), 0, ink)
    text(c, "Edit it in Canva in minutes", x: 150, y: 130, w: W - 300, size: 130, color: .white)
    var y: CGFloat = 470
    for (i, t) in a("steps").prefix(4).enumerated() {
        round(c, CGRect(x: 150, y: y, width: 180, height: 180), 90, glow)
        text(c, "\(i + 1)", x: 150, y: y + 35, w: 180, size: 96, color: .white, center: true)
        text(c, t, x: 400, y: y + 40, w: W - 560, size: 68, color: .white, bold: false, maxH: 200); y += 340
    }
    text(c, "No software to install · works with a free Canva account", x: 150, y: H - 170, w: W - 300, size: 54, color: glow)
    save(c, "listing_4_canva.jpg"); made.append("listing_4_canva.jpg")
}
// 5. formats / colours
do {
    let c = canvas()
    text(c, "Format & details", x: 150, y: 120, w: W - 300, size: 130)
    var y: CGFloat = 420
    for t in a("formats").prefix(6) {
        round(c, CGRect(x: 150, y: y, width: 1500, height: 170), 40, .white)
        text(c, t, x: 210, y: y + 45, w: 1400, size: 60, maxH: 120); y += 205
    }
    let cols = a("colors").prefix(6)
    if !cols.isEmpty {
        text(c, "Colours", x: 1820, y: 420, w: 700, size: 80)
        for (i, col) in cols.enumerated() {
            let cc = hex(col, accent)
            round(c, CGRect(x: 1820, y: 580 + CGFloat(i) * 210, width: 170, height: 170), 85, cc)
            text(c, col, x: 2030, y: 625 + CGFloat(i) * 210, w: 560, size: 52, bold: false, maxH: 90)
        }
    }
    text(c, s("store"), x: 150, y: H - 170, w: W - 300, size: 60, color: accent)
    save(c, "listing_5_details.jpg"); made.append("listing_5_details.jpg")
}

// buyer PDF (US Letter)
do {
    let url = out.appendingPathComponent("Download_Instructions.pdf")
    var box = CGRect(x: 0, y: 0, width: 612, height: 792)
    let pdf = CGContext(url as CFURL, mediaBox: &box, nil)!
    pdf.beginPDFPage(nil)
    let ph: CGFloat = 792
    func ptext(_ t: String, _ y: CGFloat, _ size: CGFloat, _ col: NSColor = ink, bold: Bool = true, link: String? = nil) -> CGFloat {
        let font = NSFont.systemFont(ofSize: size, weight: bold ? .bold : .regular)
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: col]
        if link != nil { attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue }
        let att = NSAttributedString(string: t, attributes: attrs)
        let fs = CTFramesetterCreateWithAttributedString(att)
        let fit = CTFramesetterSuggestFrameSizeWithConstraints(fs, CFRange(), nil, CGSize(width: 492, height: 400), nil)
        let r = CGRect(x: 60, y: ph - y - fit.height, width: 492, height: fit.height + 2)
        CTFrameDraw(CTFramesetterCreateFrame(fs, CFRange(), CGPath(rect: r, transform: nil), nil), pdf)
        if let l = link, let u = URL(string: l) { pdf.setURL(u as CFURL, for: r) }
        return fit.height
    }
    pdf.setFillColor(accent.cgColor); pdf.fill(CGRect(x: 0, y: ph - 110, width: 612, height: 110))
    _ = ptext(s("store").isEmpty ? "Thank you for your order" : s("store"), 38, 26, .white)
    var y: CGFloat = 140
    y += ptext("Thank you for your purchase!", y, 22) + 10
    y += ptext(s("title"), y, 14, accent) + 24
    y += ptext("Open your template", y, 18) + 8
    if s("link").isEmpty {
        y += ptext("[Your Canva template link will be here]", y, 13, .systemRed, bold: false) + 8
    } else {
        y += ptext("👉 CLICK HERE TO OPEN IN CANVA", y, 16, accent, link: s("link")) + 6
        y += ptext(s("link"), y, 9, .gray, bold: false, link: s("link")) + 8
    }
    y += 16
    y += ptext("How to edit", y, 18) + 8
    for (i, t) in (a("pdf_steps").isEmpty ? a("steps") : a("pdf_steps")).enumerated() { y += ptext("\(i + 1). " + t, y, 12, bold: false) + 8 }
    y += 16
    y += ptext("Good to know", y, 18) + 8
    for t in ["You need a free Canva account (canva.com).", "Click 'Use template': it makes your own copy, so you can edit it as much as you like.",
              "Print at home or at any print shop. Choose 'PDF Print' when you download.",
              "This is a digital product: no physical item will be shipped.",
              "For personal use only. Please don't share or resell the template."] { y += ptext("• " + t, y, 11, bold: false) + 6 }
    y += 16
    _ = ptext("Questions? Message us on Etsy — we're happy to help.", y, 12, accent)
    pdf.endPDFPage(); pdf.closePDF()
    made.append("Download_Instructions.pdf")
}
print(String(data: try! JSONSerialization.data(withJSONObject: ["files": made]), encoding: .utf8)!)
