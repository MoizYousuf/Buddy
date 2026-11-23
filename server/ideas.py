"""Idea notebook: ideas the owner says or types (often offline, sent later by the phone) become proper notes.

Each idea: raw text + when it was said. On arrival Gemini (Antigravity, Claude fallback) turns it into a note:
title, what it is, why it's good, first steps, which project (Etsy / YouTube / App / Money / Other). Then a notification.
Stored in server/ideas.json, nothing leaves the Mac except the AI call.
"""
import json, subprocess, threading, time, uuid
from pathlib import Path

HERE = Path(__file__).parent
DB = HERE / "ideas.json"
AGY = Path.home() / ".local" / "bin" / "agy"
CLAUDE = str(Path.home() / ".local" / "bin" / "claude")
_lock = threading.Lock()


def _load():
    try:
        return json.loads(DB.read_text())
    except (OSError, ValueError):
        return {"ideas": []}


def _save(d):
    tmp = DB.with_suffix(".tmp"); tmp.write_text(json.dumps(d, ensure_ascii=False, indent=1)); tmp.replace(DB)


def _ai(text):
    p = ("The owner had this idea (Roman Urdu / English, maybe spoken): \"" + text + "\". They run Etsy stores for funeral/memorial "
         "Canva templates and shirts, two kids YouTube channels (Pip & Friends, naats), an Android app, and track money. "
         "Turn it into a short useful note. Reply with JSON only: {\"title\": \"max 8 words\", \"project\": \"Etsy|YouTube|App|Money|Personal|Other\", "
         "\"note\": \"2-3 lines, what the idea is\", \"why\": \"one line why it is worth doing\", \"steps\": [\"3-5 concrete first steps\"], "
         "\"effort\": \"chhota|darmiyana|bara\"}. Write in simple Roman Urdu mixed with English words. Don't run any commands.")
    out = ""
    if AGY.exists():
        try:
            out = subprocess.run([str(AGY), "-p", p, "--print-timeout", "90s"], capture_output=True, text=True, timeout=120).stdout
        except subprocess.TimeoutExpired:
            pass
    if "{" not in out:
        out = subprocess.run([CLAUDE, "-p", p, "--output-format", "text", "--model", "sonnet"], capture_output=True, text=True, timeout=150).stdout
    return json.loads(out[out.index("{"):out.rindex("}") + 1])


def add(text, said_at="", push=None, local_id=""):
    text = str(text).strip()[:2000]
    if not text:
        raise ValueError("Idea khaali hai")
    with _lock:
        d = _load()
        if local_id and any(i.get("local_id") == local_id for i in d["ideas"]):   # phone re-sent it
            return listing()
        it = {"id": uuid.uuid4().hex[:10], "local_id": local_id, "text": text, "said_at": said_at or time.strftime("%Y-%m-%d %H:%M"),
              "added": time.strftime("%Y-%m-%d %H:%M"), "status": "thinking", "done": False}
        d["ideas"].insert(0, it); _save(d)

    def work():
        try:
            n = _ai(text); st = "ready"
        except Exception as e:  # noqa: BLE001
            n = {"title": text[:50], "note": text, "steps": [], "error": str(e)[:100]}; st = "raw"
        with _lock:
            d = _load()
            for x in d["ideas"]:
                if x["id"] == it["id"]:
                    x.update(n); x["status"] = st
            _save(d)
        if push:
            push("system", "💡 Aap ka idea note ban gaya", f"{n.get('title', text[:40])} · {it['said_at']} ko socha tha. Is par kaam karna hai!", "Ideas")

    threading.Thread(target=work, daemon=True).start()
    return listing()


def listing():
    return {"ideas": _load()["ideas"]}


def change(iid, done=None, delete=False):
    with _lock:
        d = _load()
        if delete:
            d["ideas"] = [x for x in d["ideas"] if x["id"] != iid]
        else:
            for x in d["ideas"]:
                if x["id"] == iid and done is not None:
                    x["done"] = bool(done)
        _save(d)
    return listing()


def nudge(push):
    """Once a day at 11:00: remind about the oldest open idea that's a few days old."""
    def loop():
        sent = ""
        while True:
            day = time.strftime("%Y-%m-%d")
            if time.strftime("%H") >= "11" and sent != day:
                sent = day
                old = [x for x in _load()["ideas"] if not x.get("done") and x.get("added", "") < time.strftime("%Y-%m-%d", time.localtime(time.time() - 3 * 86400))]
                if old:
                    x = old[-1]
                    push("system", "💡 Purana idea yaad hai?", f"\"{x.get('title') or x['text'][:40]}\" ({x['said_at'][:10]}). Aaj is ka pehla step karein?", "Ideas")
            time.sleep(1800)
    threading.Thread(target=loop, daemon=True).start()
