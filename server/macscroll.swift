// Smooth scroll on the Mac: `macscroll <dy> [dx]` (pixels; positive dy scrolls down).
// cliclick has no wheel support, so the coach server uses this for the remote screen.
import CoreGraphics
import Foundation

let args = CommandLine.arguments.dropFirst().compactMap { Int32($0) }
let dy = args.first ?? 0
let dx = args.dropFirst().first ?? 0
if let e = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: -dy, wheel2: -dx, wheel3: 0) {
    e.post(tap: .cghidEventTap)
}
