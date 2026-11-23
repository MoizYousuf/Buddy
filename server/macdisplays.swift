// Prints the Mac's displays as JSON: [{"id":..,"x":..,"y":..,"w":..,"h":..,"main":true}], in global
// top-left points (the same space cliclick uses). The coach server uses it for multi-display control.
import CoreGraphics
import Foundation

var ids = [CGDirectDisplayID](repeating: 0, count: 16)
var count: UInt32 = 0
CGGetActiveDisplayList(16, &ids, &count)
let list = ids.prefix(Int(count)).map { id -> [String: Any] in
    let b = CGDisplayBounds(id)
    return ["id": Int(id), "x": Int(b.origin.x), "y": Int(b.origin.y), "w": Int(b.width), "h": Int(b.height), "main": CGDisplayIsMain(id) != 0]
}
print(String(data: try! JSONSerialization.data(withJSONObject: list), encoding: .utf8)!)
