"""Find what's taking space on the Mac, and move chosen items to the Trash (never a permanent delete).

A scan runs in the background (it walks the home folder) and lists:
  apps not opened for a while, node_modules folders, big caches, large old Downloads, and the Trash itself.
Only paths that appeared in the latest scan may be moved to the Trash.
"""
import json
import os
import subprocess
import threading
import time
from datetime import datetime
from pathlib import Path

HOME = Path.home()
DAY = 86400
_lock = threading.Lock()
_state = {"status": "idle", "started": 0, "finished": 0, "items": []}

CACHE_SPOTS = [
    ("Xcode build files", HOME / "Library/Developer/Xcode/DerivedData", "Rebuilt automatically by Xcode."),
    ("Old iOS device support", HOME / "Library/Developer/Xcode/iOS DeviceSupport", "Re-downloaded when a device connects."),
    ("iOS simulator caches", HOME / "Library/Developer/CoreSimulator/Caches", "Safe cache."),
    ("Gradle caches", HOME / ".gradle/caches", "Android builds download these again."),
    ("npm cache", HOME / ".npm/_cacache", "npm downloads these again."),
    ("Yarn cache", HOME / "Library/Caches/Yarn", "Yarn downloads these again."),
    ("CocoaPods cache", HOME / "Library/Caches/CocoaPods", "Pods download again."),
    ("pip cache", HOME / "Library/Caches/pip", "pip downloads again."),
    ("Homebrew downloads", HOME / "Library/Caches/Homebrew", "Old installers."),
    ("Chrome cache", HOME / "Library/Caches/Google", "Browser rebuilds it."),
]
# Build-tool caches you rebuild from: if one was used in the last 2 weeks, removing it only means a long re-download.
IN_USE_CACHES = {"Gradle caches", "Xcode build files", "npm cache", "Yarn cache", "CocoaPods cache", "pip cache"}


def _recently_used(path, days=14):
    try:
        newest = max((c.stat().st_mtime for c in Path(path).iterdir()), default=0)
    except OSError:
        return False
    return time.time() - newest < days * DAY


SKIP_DIRS = {"Library", ".Trash", "Applications", ".cache", ".npm", ".gradle", ".ollama"}


def _du(path):
    r = subprocess.run(["du", "-sk", str(path)], capture_output=True, text=True, timeout=300)
    try:
        return int(r.stdout.split()[0]) * 1024
    except (IndexError, ValueError):
        return 0


def _last_used(app):
    r = subprocess.run(["mdls", "-raw", "-name", "kMDItemLastUsedDate", str(app)], capture_output=True, text=True)
    try:
        return datetime.strptime(r.stdout.strip(), "%Y-%m-%d %H:%M:%S %z").timestamp()
    except ValueError:
        return None


def _item(kind, path, size, last, note):
    return {"kind": kind, "path": str(path), "name": Path(path).name, "bytes": size,
            "last_used": int(last) if last else None, "note": note}


def _scan():
    items = []
    now = time.time()

    # apps in /Applications not opened for 60+ days (Apple's own apps are left alone)
    for app in sorted(Path("/Applications").glob("*.app")):
        owner = subprocess.run(["stat", "-f", "%Su", str(app)], capture_output=True, text=True).stdout.strip()
        if owner == "root":
            continue
        last = _last_used(app)
        if last is None or now - last > 60 * DAY:
            items.append(_item("app", app, _du(app), last, "Not opened in 60+ days" if last else "Never opened"))

    # node_modules folders anywhere under home (but not inside other node_modules)
    for root, dirs, _ in os.walk(HOME):
        rel = Path(root).relative_to(HOME)
        if rel.parts and rel.parts[0] in SKIP_DIRS:
            dirs[:] = []
            continue
        if "node_modules" in dirs:
            nm = Path(root) / "node_modules"
            pkg = Path(root) / "package.json"
            last = pkg.stat().st_mtime if pkg.exists() else nm.stat().st_mtime
            items.append(_item("node_modules", nm, _du(nm), last, f"Project: {Path(root).name} — reinstall with npm install"))
            dirs.remove("node_modules")
        dirs[:] = [d for d in dirs if not d.startswith(".") or d in (".config",)]

    # caches
    for label, path, note in CACHE_SPOTS:
        if path.exists():
            size = _du(path)
            if size > 50 * 1024 * 1024:
                it = _item("cache", path, size, path.stat().st_mtime, note)
                it["name"] = label
                items.append(it)

    # big, old downloads
    dl = HOME / "Downloads"
    if dl.exists():
        for f in dl.iterdir():
            try:
                st = f.stat()
                size = _du(f) if f.is_dir() else st.st_size
                if size > 100 * 1024 * 1024 and now - st.st_mtime > 30 * DAY:
                    items.append(_item("download", f, size, st.st_mtime, "Big and older than 30 days"))
            except OSError:
                pass

    trash = HOME / ".Trash"
    if trash.exists():
        size = _du(trash)
        if size:
            it = _item("trash", trash, size, None, "Empty the Trash on the Mac to free this.")
            it["name"] = "Trash"
            items.append(it)

    items.sort(key=lambda i: -i["bytes"])
    return items


def start_scan():
    with _lock:
        if _state["status"] == "running":
            return dict(_state, items=[])
        _state.update(status="running", started=time.time())

    def work():
        try:
            items = [classify(i) for i in _scan()]
            _remember_sizes(items)
            with _lock:
                _state.update(status="done", finished=time.time(), items=items)
            _save_memory()
        except Exception as e:
            with _lock:
                _state.update(status="error", error=str(e))
    threading.Thread(target=work, daemon=True).start()
    return {"status": "running"}


def result():
    with _lock:
        return dict(_state)


def move_to_trash(paths):
    """Move chosen paths to the Trash via Finder (recoverable). Only paths from the last scan are accepted."""
    with _lock:
        allowed = {i["path"] for i in _state["items"] if i["kind"] != "trash"}
    moved, refused = [], []
    for p in paths:
        if p not in allowed or not Path(p).exists():
            refused.append(p)
            continue
        safe = p.replace('"', '\\"')
        r = subprocess.run(["osascript", "-e", f'tell application "Finder" to delete (POSIX file "{safe}" as alias)'],
                           capture_output=True, text=True, timeout=600)
        (moved if r.returncode == 0 else refused).append(p)
    with _lock:
        _state["items"] = [i for i in _state["items"] if i["path"] not in moved]
    return {"moved": moved, "refused": refused}


# ---------- smart monitor: remembers sizes and your decisions, scans by itself every day ----------

MEMORY = Path(__file__).parent / "cleanup_memory.json"
_memory = {"decisions": {}, "sizes": {}, "last_scan": 0}  # decisions: path -> "keep"; sizes: path -> [[day, bytes], ...]
if MEMORY.exists():
    try:
        _memory.update(json.loads(MEMORY.read_text()))
    except ValueError:
        pass


def _save_memory():
    with _lock:
        _memory["last_scan"] = time.time()
        MEMORY.write_text(json.dumps(_memory))


def _remember_sizes(items):
    today = int(time.time() // DAY)
    with _lock:
        for i in items:
            hist = _memory["sizes"].setdefault(i["path"], [])
            if not hist or hist[-1][0] != today:
                hist.append([today, i["bytes"]])
            del hist[:-30]  # a month of daily sizes is enough


def _growth(path, now_bytes):
    """Bytes added over the last 7 days, from the daily size history."""
    hist = _memory["sizes"].get(path, [])
    week_ago = [b for d, b in hist if d <= int(time.time() // DAY) - 7]
    return now_bytes - week_ago[-1] if week_ago else 0


def classify(i):
    """Adds verdict (safe | suggest | ask | keep | info), a plain reason, and weekly growth to one scan item."""
    days = int((time.time() - i["last_used"]) / DAY) if i.get("last_used") else None
    kind = i["kind"]
    if kind == "trash":
        verdict, reason = "info", "Already in the Trash — empty it on the Mac to free this space."
    elif kind == "cache" and i.get("name") in IN_USE_CACHES and _recently_used(i["path"]):
        verdict, reason = "keep", "You use this for your builds (used in the last 2 weeks). Removing it only makes the next build re-download everything."
    elif kind == "cache":
        verdict, reason = "safe", f"{i['note']} Nothing is lost if you remove it."
    elif kind == "node_modules":
        project = i["note"].split("Project: ")[-1].split(" —")[0]
        if days is not None and days > 30:
            verdict, reason = "safe", f"'{project}' hasn't been touched for {days} days. `npm install` brings this back when you need it."
        else:
            verdict, reason = "keep", f"You worked on '{project}' recently."
    elif kind == "app":
        running = subprocess.run(["pgrep", "-f", i["path"] + "/Contents/MacOS/"], capture_output=True).returncode == 0
        if running:
            verdict, reason = "keep", "This app is open right now."
        elif days is None:
            verdict, reason = "ask", "macOS has no record of when this was last opened, so I can't tell if you use it."
        elif days > 180:
            verdict, reason = "suggest", f"Not opened for {days} days (over 6 months)."
        else:
            verdict, reason = "ask", f"Last opened {days} days ago."
    elif kind == "download":
        verdict, reason = "ask", f"A big download from {days} days ago — only you know if it's still needed."
    else:
        verdict, reason = "ask", i["note"]
    if _memory["decisions"].get(i["path"]) == "keep" and verdict != "info":
        verdict, reason = "keep", "You chose to keep this."
    return {**i, "verdict": verdict, "reason": reason, "days": days, "growth": _growth(i["path"], i["bytes"])}


def review():
    """Items waiting for your decision, biggest first."""
    with _lock:
        items = [i for i in _state["items"] if i.get("verdict") in ("safe", "suggest", "ask")]
        return {"status": _state["status"], "last_scan": _memory["last_scan"], "items": items}


def decide(path, decision):
    """keep = never ask again · remove = move to the Trash · later = ask next time."""
    if decision == "keep":
        with _lock:
            _memory["decisions"][path] = "keep"
            for i in _state["items"]:
                if i["path"] == path:
                    i.update(verdict="keep", reason="You chose to keep this.")
        _save_memory()
        return {"ok": True}
    if decision == "remove":
        return move_to_trash([path])
    return {"ok": True}


def empty_trash():
    """Empties the Mac's Trash through Finder. Permanent: the app asks twice before calling this."""
    trash = HOME / ".Trash"
    before = _du(trash) if trash.exists() else 0
    r = subprocess.run(["osascript", "-e", 'tell application "Finder" to empty trash'], capture_output=True, text=True, timeout=600)
    with _lock:
        _state["items"] = [i for i in _state["items"] if i["kind"] != "trash"]
    return {"ok": r.returncode == 0, "freed": before, "error": r.stderr.strip() or None}


def _daily():
    """Scan once a day by itself (an hour after start, then every 24 h), so the list is always fresh."""
    time.sleep(3600 if _memory["last_scan"] else 120)
    while True:
        if time.time() - _memory["last_scan"] > DAY:
            start_scan()
        time.sleep(3600)


threading.Thread(target=_daily, daemon=True).start()
