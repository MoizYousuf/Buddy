import CoreGraphics
import Foundation
// maclocked        -> prints "locked" or "unlocked"
// maclocked lock   -> locks the screen now (same as the menu's Lock Screen; needs no Accessibility permission)
if CommandLine.arguments.dropFirst().first == "lock" {
    guard let h = dlopen("/System/Library/PrivateFrameworks/login.framework/Versions/Current/login", RTLD_NOW),
          let f = dlsym(h, "SACLockScreenImmediate") else { print("unavailable"); exit(1) }
    typealias Lock = @convention(c) () -> Int32
    _ = unsafeBitCast(f, to: Lock.self)()
    print("ok"); exit(0)
}
// maclocked type  -> reads text from stdin and presses each key by its US key code, then Return
// (key codes, not text events, so the lock screen's password field accepts them)
if CommandLine.arguments.dropFirst().first == "type" {
    let codes: [Character: (CGKeyCode, Bool)] = {
        var m: [Character: (CGKeyCode, Bool)] = [:]
        let lower: [(Character, CGKeyCode)] = [("a",0),("s",1),("d",2),("f",3),("h",4),("g",5),("z",6),("x",7),("c",8),("v",9),("b",11),
            ("q",12),("w",13),("e",14),("r",15),("y",16),("t",17),("1",18),("2",19),("3",20),("4",21),("6",22),("5",23),("=",24),("9",25),
            ("7",26),("-",27),("8",28),("0",29),("]",30),("o",31),("u",32),("[",33),("i",34),("p",35),("l",37),("j",38),("'",39),("k",40),
            (";",41),("\\",42),(",",43),("/",44),("n",45),("m",46),(".",47),("`",50),(" ",49)]
        for (c, k) in lower { m[c] = (k, false) }
        for (c, k) in lower where c.isLetter { m[Character(c.uppercased())] = (k, true) }
        let shifted: [(Character, Character)] = [("!","1"),("@","2"),("#","3"),("$","4"),("%","5"),("^","6"),("&","7"),("*","8"),("(","9"),
            (")","0"),("_","-"),("+","="),("{","["),("}","]"),("|","\\"),(":",";"),("\"","'"),("<",","),(">","."),("?","/"),("~","`")]
        for (s, b) in shifted { if let k = m[b]?.0 { m[s] = (k, true) } }
        return m
    }()
    let text = String(data: FileHandle.standardInput.readDataToEndOfFile(), encoding: .utf8)?.trimmingCharacters(in: .newlines) ?? ""
    let src = CGEventSource(stateID: .hidSystemState)
    func press(_ k: CGKeyCode, shift: Bool = false) {
        if shift { CGEvent(keyboardEventSource: src, virtualKey: 56, keyDown: true)?.post(tap: .cghidEventTap) }
        let dn = CGEvent(keyboardEventSource: src, virtualKey: k, keyDown: true); if shift { dn?.flags = .maskShift }
        dn?.post(tap: .cghidEventTap); usleep(30_000)
        let up = CGEvent(keyboardEventSource: src, virtualKey: k, keyDown: false); if shift { up?.flags = .maskShift }
        up?.post(tap: .cghidEventTap)
        if shift { CGEvent(keyboardEventSource: src, virtualKey: 56, keyDown: false)?.post(tap: .cghidEventTap) }
        usleep(70_000)
    }
    if text.isEmpty { press(56); print("ok"); exit(0) }   // just a Shift tap: wakes the password field
    for _ in 0..<20 { press(51) }          // backspace: empty the field
    for ch in text { guard let (k, sh) = codes[ch] else { print("unsupported"); exit(2) }; press(k, shift: sh) }
    press(36)                              // Return
    print("ok"); exit(0)
}
let d = CGSessionCopyCurrentDictionary() as? [String: Any]
print((d?["CGSSessionScreenIsLocked"] as? Bool ?? false) ? "locked" : "unlocked")
