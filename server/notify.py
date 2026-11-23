"""Alerts for the phone. The app keeps one long-poll open (GET /events?after=N) and shows each event as an
Android notification. Nothing leaves the Mac except to the paired phone."""
import json
import threading
import time
from pathlib import Path

FILE = Path(__file__).parent / "events.json"
_cv = threading.Condition()
_events = []
try:
    _events = json.load(open(FILE))[-200:]
except (OSError, ValueError):
    pass


def push(kind, title, body="", screen="", action=None):
    """kind: video | agent | permission | disk | system. screen: which app screen a tap opens.
    action: optional {"label", "path", "body"} the phone sends (signed) when its button is tapped."""
    with _cv:
        eid = (_events[-1]["id"] + 1) if _events else 1
        _events.append({"id": eid, "t": time.time(), "kind": kind, "title": title[:120], "body": body[:400],
                        "screen": screen, "action": action})
        del _events[:-200]
        try:
            json.dump(_events, open(FILE, "w"))
        except OSError:
            pass
        _cv.notify_all()
    return eid


def since(after, wait=25):
    """Events newer than `after`; waits up to `wait` s for one so the phone gets it at once."""
    end = time.time() + wait
    with _cv:
        while True:
            new = [e for e in _events if e["id"] > after]
            left = end - time.time()
            if new or left <= 0:
                return {"events": new, "last": _events[-1]["id"] if _events else 0}
            _cv.wait(left)


def clear():
    """Empties the stored list (ids keep counting up so the phone never re-shows old ones)."""
    with _cv:
        last = _events[-1]["id"] if _events else 0
        _events[:] = [{"id": last, "t": time.time(), "kind": "cleared", "title": "", "body": "", "screen": "", "action": None}]
        json.dump(_events, open(FILE, "w"))
    return {"ok": True}


def _watch_disk():
    import shutil
    warned = False
    while True:
        free = shutil.disk_usage("/System/Volumes/Data").free / 1e9
        if free < 25 and not warned:
            push("disk", "Mac storage is low", f"Only {free:.0f} GB free. Open the Storage cleaner.", "Cleaner")
        warned = free < 25
        time.sleep(1800)


def start():
    threading.Thread(target=_watch_disk, daemon=True).start()
