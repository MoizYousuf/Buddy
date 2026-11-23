"""Activity log: every action the paired phone takes on the Mac (and every blocked attempt), with the time and the route
(home Wi-Fi or Tailscale). Typed text and file contents are never stored, only that something was typed or sent."""
import json
import config
import threading
import time
from pathlib import Path
from urllib.parse import parse_qs, urlparse

FILE = Path(__file__).parent / "activity.jsonl"
_lock = threading.Lock()
_last = {}   # path -> time, for things logged at most every few minutes (screen watching)


def _title(vid):
    try:
        q = json.load(open(config.KF / "uploads/pending.json"))
        return next((i["title"][:70] for i in q if i["message_id"] == vid), "a video")
    except (OSError, ValueError):
        return "a video"


def _label(method, path, d, q):
    """(icon, text, kind) for an action, or None to skip it."""
    g = lambda k, n=100: str(d.get(k, ""))[:n]
    choice = {"✅": "Approved for YouTube", "📱": "Asked for Shorts of", "❌": "Skipped"}
    table = {
        "/mac/lock": ("🔒", "Locked the Mac", "mac"),
        "/mac/unlock": ("🔓", "Unlocked the Mac", "mac"),
        "/mac/awake": ("☀️", f"Keep Mac awake: {'on' if d.get('on') else 'off'}", "settings") if "on" in d else None,
        "/mac/display_mode": ("🖥", f"Display mode: {g('mode')}", "mac"),
        "/assistant/agent": ("🤖", "Asked the agent: " + g("text", 140) + (" (allowed: " + ", ".join(d.get("allow", [])) + ")" if d.get("allow") else ""), "agent"),
        "/assistant": ("🤖", "Asked the assistant: " + g("text", 140), "agent"),
        "/assistant/confirm": ("🤖", "Confirmed an assistant action", "agent"),
        "/screen/input": ("⌨️", f"Typed on the Mac ({len(str(d.get('text', '')))} characters)", "mac") if d.get("kind") == "type" or d.get("type") == "type" else None,
        "/files/upload": ("📤", "Sent a file to the Mac: " + q.get("name", [""])[0][:80], "files"),
        "/files/get": ("📥", "Downloaded from the Mac: " + q.get("name", [""])[0][:80], "files"),
        "/files/reveal": ("🔍", "Showed a file in Finder", "files"),
        "/clip/set": ("📋", "Sent text to the Mac clipboard", "files"),
        "/clip": ("📋", "Copied the Mac clipboard to the phone", "files"),
        "/review/choose": ("✅", f"{choice.get(d.get('choice'), 'Chose for')} {_title(d.get('id'))}" + (f" (in {d['days']} day(s) at {d.get('time')})" if d.get("days") not in (None, -1) else ""), "video"),
        "/review/reschedule": ("🕒", "Changed publish time of " + _title(d.get("id")), "video"),
        "/review/fix": ("✏️", "Asked for a fix: " + g("note", 120), "video"),
        "/review/schedule": ("📅", f"Upload plan: {len(d.get('slots', []))} a day, auto {'on' if d.get('auto') else 'off'}", "settings"),
        "/review/best_time": ("⭐", "Asked for the best upload time", "video"),
        "/studio/flow_start": ("🎬", "Started making a video" + (" (finish only, no new credits)" if d.get("folder") else ""), "video"),
        "/studio/flow_retry": ("🎬", "Allowed & continued a video", "video"),
        "/studio/chrome_open": ("🌐", "Opened Chrome on the Mac", "mac"),
        "/studio/open_folder": ("📁", "Opened a video folder on the Mac", "mac"),
        "/studio/credits_check": ("💳", "Checked Flow credits", "video"),
        "/naat/create": ("🕌", "New naat idea: " + g("idea", 120), "video"),
        "/naat/revise": ("🕌", "Changed naat lyrics: " + g("note", 120), "video"),
        "/naat/make": ("🕌", f"Started a naat video (up to {d.get('credits', '?')} credits)", "video"),
        "/naat/upload": ("🎵", "Imported a song for a naat", "video"),
        "/routines/run": ("⚡", "Ran routine: " + g("id"), "mac"),
        "/routines/save": ("⚡", "Edited routines", "settings"),
        "/autopilot/run": ("🤖", "Autopilot: Run now", "video"),
        "/autopilot/settings": ("🤖", f"Autopilot {'on' if d.get('on') else 'off'}, {g('time')}, {d.get('credits')} credits", "settings"),
        "/credits/settings": ("💳", "Credit guard settings changed", "settings"),
        "/credits/extra": ("💳", f"Allowed extra credits today: {d.get('amount')}", "settings"),
        "/brief/settings": ("☀️", "Morning brief settings changed", "settings"),
        "/alerts/clear": ("🔔", "Cleared notifications", "settings"),
        "/cleanup/empty_trash": ("🗑", "Emptied the Mac's Trash", "mac"),
        "/cleanup/clean": ("🧹", "Cleaned files on the Mac", "mac"),
        "/resume": ("📄", "Uploaded a CV", "settings") if method == "POST" else None,
        "/resume/delete": ("📄", "Deleted the CV", "settings"),
        "/cv/create": ("📄", "Tailored a CV for a job: " + (g("note", 60) or g("link", 80) or "pasted job"), "settings"),
        "/cv/save": ("📄", "Edited a CV", "settings"),
        "/cv/ai_edit": ("📄", "Asked AI to change a CV: " + g("note", 100), "settings"),
        "/cv/restore": ("📄", "Restored an older CV version", "settings"),
        "/interview/feedback": ("💼", "Finished a CV interview", "practice") if method == "POST" else None,
    }
    if path in ("/screen.stream", "/screen.h264", "/screen.jpg"):
        if time.time() - _last.get("screen", 0) < 600:
            return None
        _last["screen"] = time.time()
        return ("📺", "Watched the Mac screen", "mac")
    if path == "/screen.audio":
        return ("🔊", "Played the Mac's sound on the phone", "mac")
    if path in table:
        return table[path]
    if method == "POST" and path.startswith("/cleanup") and not any(w in path for w in ("scan", "review", "result", "status")):
        return ("🧹", "Storage cleaner: " + path.rsplit("/", 1)[-1].replace("_", " "), "mac")
    return None


def log(method, raw_path, body, ip):
    u = urlparse(raw_path)
    try:
        d = json.loads(body or b"{}") if method == "POST" else {}
        if not isinstance(d, dict):
            d = {}
    except ValueError:
        d = {}
    lab = _label(method, u.path, d, parse_qs(u.query))
    if lab:
        _write({"t": time.time(), "icon": lab[0], "text": lab[1], "kind": lab[2], "via": _via(ip)})


def blocked(raw_path, ip):
    path = urlparse(raw_path).path
    if time.time() - _last.get("blocked:" + ip, 0) < 60:   # at most one line a minute per address
        return
    _last["blocked:" + ip] = time.time()
    _write({"t": time.time(), "icon": "🛡", "text": f"Blocked a request from {ip} to {path} (not this phone, or bad signature)",
            "kind": "security", "via": _via(ip)})


def note(icon, text, kind="security", ip=""):
    _write({"t": time.time(), "icon": icon, "text": text, "kind": kind, "via": _via(ip) if ip else ""})


def _via(ip):
    return "Tailscale" if ip.startswith("100.") else "Home Wi-Fi" if ip.startswith("192.168.") else ip


def _write(e):
    with _lock:
        with open(FILE, "a") as f:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")


def listing(kind="", limit=300):
    try:
        lines = FILE.read_text().splitlines()[-5000:]
    except OSError:
        return {"items": []}
    out = []
    for l in reversed(lines):
        try:
            e = json.loads(l)
        except ValueError:
            continue
        if kind and e.get("kind") != kind:
            continue
        out.append(e)
        if len(out) >= limit:
            break
    return {"items": out}


def trim():
    try:
        lines = FILE.read_text().splitlines()
        if len(lines) > 5000:
            FILE.write_text("\n".join(lines[-5000:]) + "\n")
    except OSError:
        pass
