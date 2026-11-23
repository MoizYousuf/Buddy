"""One-tap routines: a named list of simple Mac steps the owner edits in the app.
Each step is {"type": ..., "arg": ...}; only the types below exist, so a routine can't run anything else."""
import json
import re
import subprocess
import threading
import time
from pathlib import Path

FILE = Path(__file__).parent / "routines.json"

from config import CFG  # noqa: E402
_PROFILE = CFG["chrome"]["profile_name"] or CFG["chrome"]["profile_dir"]
_STUDIO = CFG["youtube"]["studio_url"]

STEPS = {  # type -> what the arg means (shown in the app)
    "open_app": "App name, e.g. Visual Studio Code",
    "quit_app": "App name to close",
    "open_url": "Website, e.g. youtube.com",
    "chrome": "Chrome profile name, e.g. Personal (opens Google Flow)",
    "volume": "Volume 0-100",
    "mute": "on or off",
    "say": "Text the Mac says out loud",
    "display_off": "(nothing)",
    "lock": "(nothing)",
    "wait": "Seconds to wait",
    "agent": "A task for the Mac agent (Claude)",
}

DEFAULTS = [
    {"id": "work", "name": "Work", "emoji": "💼", "steps": [
        {"type": "chrome", "arg": _PROFILE}, {"type": "open_app", "arg": "Visual Studio Code"},
        {"type": "open_url", "arg": _STUDIO}, {"type": "volume", "arg": "30"}]},
    {"id": "studio", "name": "Studio", "emoji": "🎬", "steps": [
        {"type": "chrome", "arg": _PROFILE}, {"type": "open_url", "arg": _STUDIO},
        {"type": "say", "arg": "Studio is ready"}]},
    {"id": "night", "name": "Night", "emoji": "🌙", "steps": [
        {"type": "quit_app", "arg": "Visual Studio Code"}, {"type": "mute", "arg": "on"},
        {"type": "wait", "arg": "2"}, {"type": "lock", "arg": ""}]},
]


def load():
    try:
        return json.load(open(FILE))
    except (OSError, ValueError):
        return DEFAULTS


def listing():
    return {"routines": load(), "types": STEPS}


def save(routines):
    clean = []
    for r in routines[:20]:
        steps = [{"type": s["type"], "arg": str(s.get("arg", ""))[:500]} for s in r.get("steps", [])[:30] if s.get("type") in STEPS]
        name = str(r.get("name", "")).strip()[:30]
        if name:
            rid = re.sub(r"[^a-z0-9]+", "-", str(r.get("id") or name).lower()).strip("-") or str(int(time.time()))
            clean.append({"id": rid, "name": name, "emoji": str(r.get("emoji", "⚡"))[:4], "steps": steps})
    json.dump(clean, open(FILE, "w"), indent=1, ensure_ascii=False)
    return listing()


def _chrome(name):
    import studio
    prof = next((p["dir"] for p in studio.chrome_profiles()["items"] if p["name"] == name), None) or \
        next((p["dir"] for p in studio.chrome_profiles()["items"] if p["name"].lower() == name.lower()), None)
    if not prof:
        raise ValueError(f"No Chrome profile '{name}'")
    studio.chrome_open(prof, True)


def _step(s, assistant):
    t, a = s["type"], s.get("arg", "")
    run = lambda *c: subprocess.run(c, capture_output=True, text=True, timeout=30)
    if t == "open_app":
        r = run("open", "-a", a)
        if r.returncode:
            raise RuntimeError(r.stderr.strip() or f"Can't open {a}")
    elif t == "quit_app":
        run("osascript", "-e", f'tell application "{a.replace(chr(34), "")}" to quit')
    elif t == "open_url":
        run("open", a if re.match(r"^https?://", a) else "https://" + a)
    elif t == "chrome":
        _chrome(a)
    elif t == "volume":
        run("osascript", "-e", f"set volume output volume {max(0, min(100, int(a or 50)))}")
    elif t == "mute":
        run("osascript", "-e", f"set volume output muted {'true' if a.strip().lower() != 'off' else 'false'}")
    elif t == "say":
        run("say", a[:300])
    elif t == "display_off":
        run("pmset", "displaysleepnow")
    elif t == "lock":
        assistant.lock()
    elif t == "wait":
        time.sleep(max(0, min(60, float(a or 1))))
    elif t == "agent":
        assistant.agent(a, "en")


_running = {}


def run(rid, assistant, notify):
    r = next((x for x in load() if x["id"] == rid), None)
    if not r:
        raise ValueError("unknown routine")
    if _running.get(rid):
        return {"ok": False, "error": "Already running"}

    def work():
        _running[rid] = True
        errors = []
        for i, s in enumerate(r["steps"], 1):
            try:
                _step(s, assistant)
            except Exception as e:
                errors.append(f"step {i} ({s['type']}): {e}")
            time.sleep(0.6)
        _running.pop(rid, None)
        notify.push("system", f"{r['emoji']} {r['name']} " + ("done" if not errors else "finished with problems"),
                    "\n".join(errors) or f"All {len(r['steps'])} steps ran.", "")
    threading.Thread(target=work, daemon=True).start()
    return {"ok": True, "steps": len(r["steps"])}
