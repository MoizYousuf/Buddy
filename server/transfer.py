"""Clipboard and files between the phone and the Mac.
Phone -> Mac files land in ~/Downloads/From Phone. Mac -> phone: only files under the home folder, never hidden
folders, Library, keychains or this app's secrets."""
import os
import re
import subprocess
import time
from pathlib import Path

HOME = Path.home()
INBOX = HOME / "Downloads" / "From Phone"
PLACES = {"From Phone": INBOX, "Downloads": HOME / "Downloads", "Desktop": HOME / "Desktop", "Documents": HOME / "Documents"}
MAX = 1024 * 1024 * 1024  # 1 GB per file


def get_clip():
    return {"text": subprocess.run(["pbpaste"], capture_output=True, text=True).stdout[:100_000]}


def set_clip(text):
    subprocess.run(["pbcopy"], input=text[:1_000_000], text=True)
    return {"ok": True}


def save(name, data):
    """Stores a file from the phone; never overwrites (adds ' 2', ' 3' …)."""
    name = re.sub(r"[/\\:\x00]", "_", os.path.basename(name or "")).strip(". ") or f"phone-{int(time.time())}"
    INBOX.mkdir(parents=True, exist_ok=True)
    stem, ext = os.path.splitext(name)
    p, n = INBOX / name, 2
    while p.exists():
        p, n = INBOX / f"{stem} {n}{ext}", n + 1
    p.write_bytes(data)
    return {"ok": True, "name": p.name, "path": str(p), "bytes": len(data)}


def _safe(path):
    real = os.path.realpath(path)
    parts = Path(real).relative_to(HOME).parts if real.startswith(str(HOME) + os.sep) else None
    if not parts or parts[0] == "Library" or any(x.startswith(".") for x in parts) or "english-coach" in parts or Path(__file__).resolve().parent.parent.name in parts:
        return None
    return real


def listing(place, sub=""):
    base = PLACES.get(place)
    if not base:
        raise ValueError("unknown place")
    d = _safe(base / sub) if sub else str(base)
    if not d or not os.path.isdir(d):
        return {"items": [], "place": place, "sub": sub}
    out = []
    for e in os.scandir(d):
        if e.name.startswith("."):
            continue
        st = e.stat()
        out.append({"name": e.name, "dir": e.is_dir(), "bytes": 0 if e.is_dir() else st.st_size, "t": st.st_mtime})
    out.sort(key=lambda x: -x["t"])
    return {"items": out[:300], "place": place, "sub": sub}


def file_path(place, sub, name):
    base = PLACES.get(place)
    p = _safe(base / sub / name) if base else None
    if not p or not os.path.isfile(p) or os.path.getsize(p) > MAX:
        return None
    return p


def reveal(place, sub, name):
    p = _safe(PLACES[place] / sub / name) if place in PLACES else None
    if not p:
        raise ValueError("not found")
    subprocess.run(["open", "-R", p])
    return {"ok": True}
