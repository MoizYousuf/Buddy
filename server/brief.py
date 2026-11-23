"""The morning brief: one look at the day — today's video, YouTube, credits, Mac health and overnight Claude jobs.
A notification goes to the phone every morning at the time in brief.json (default 09:00)."""
import datetime
import json
import shutil
import threading
import time
from pathlib import Path

import reviews
import studio

CFG = Path(__file__).parent / "brief.json"
PKT = datetime.datetime.now().astimezone().tzinfo  # the Mac's own time zone (name kept from the original code)


def settings():
    try:
        return {"time": "09:00", "on": True, **json.load(open(CFG))}
    except (OSError, ValueError):
        return {"time": "09:00", "on": True}


def save(t, on):
    if len(t) != 5 or t[2] != ":":
        raise ValueError("time must be HH:MM")
    json.dump({"time": t, "on": bool(on)}, open(CFG, "w"))
    return settings()


def build(jobs=()):
    now = datetime.datetime.now(PKT)
    today = now.strftime("%Y-%m-%d")
    out = {"date": now.strftime("%A, %d %B"), "time": now.strftime("%H:%M")}
    # today's video from the weekly calendar
    try:
        cal = json.load(open(studio.DATA / "calendar.json"))
        day = next((d for d in cal.get("days", []) if d.get("date") == today), None)
        out["video"] = {"title": day["title"], "type": day.get("type", ""), "mode": day.get("mode", ""), "why": day.get("why", ""),
                        "time": day.get("upload_long_pkt" if day.get("type") == "long" else "upload_short_pkt", "")} if day else None
    except (OSError, ValueError):
        out["video"] = None
    # YouTube: what goes public today, what waits for OK, top views
    going = []
    for it in reviews._queue():
        p = it.get("privacy") or ""
        if p.startswith("scheduled "):
            t = datetime.datetime.strptime(p[10:29], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=datetime.timezone.utc).astimezone(PKT)
            if t.strftime("%Y-%m-%d") == today:
                going.append({"title": it.get("title", ""), "time": t.strftime("%H:%M")})
    waiting = sum(1 for i in reviews._queue() if i.get("status") == "pending")
    try:
        reviews.uploaded()            # fills the 5-minute YouTube stats cache if it's empty or old
    except Exception:
        pass
    stats = reviews._stats["data"]
    views = sum(v.get("views", 0) for v in stats.values())
    top = max(((i.get("title", ""), stats.get(i.get("youtube_id", ""), {}).get("views", 0)) for i in reviews._queue()
               if i.get("youtube_id")), key=lambda x: x[1], default=("", 0))
    out["youtube"] = {"going_public": going, "waiting": waiting, "total_views": views, "top": {"title": top[0], "views": top[1]}}
    out["credits"] = studio.credits()
    try:
        out["next_slot"] = reviews.next_free_slot()
    except ValueError:
        out["next_slot"] = None
    du = shutil.disk_usage("/System/Volumes/Data")
    out["mac"] = {"free_gb": round(du.free / 1e9), "used_pct": round(100 * (du.total - du.free) / du.total)}
    day_ago = time.time() - 86400
    recent = [j for j in jobs if j.get("started", 0) > day_ago]
    out["claude"] = {"done": sum(1 for j in recent if j.get("status") == "done"),
                     "failed": sum(1 for j in recent if j.get("status") == "error"),
                     "running": sum(1 for j in recent if j.get("status") == "running")}
    try:
        import money
        out["money"] = money.today_summary()
    except Exception:  # noqa: BLE001
        out["money"] = None
    try:
        import etsy
        r = etsy._report() if hasattr(etsy, "_report") else {}
        order = {"high": 0, "medium": 1, "low": 2}
        ts = sorted(r.get("all_tasks", []), key=lambda t: order.get(t.get("priority"), 3))[:3]
        out["etsy"] = [{"store": t.get("store"), "title": t.get("title"), "kind": t.get("kind"), "priority": t.get("priority")} for t in ts]
    except Exception:  # noqa: BLE001
        out["etsy"] = []
    out["speech"] = speech(out)
    return out


def _say_time(t):
    h, m = map(int, t.split(":"))
    return f"{h % 12 or 12}{':%02d' % m if m else ''} {'AM' if h < 12 else 'PM'}"


def speech(b):
    """Short text the phone reads aloud (no emojis, times as '7 PM')."""
    import re as _re
    clean = lambda t: _re.sub(r"[^\w\s,.!?'&:-]", "", t).strip()
    parts = [f"Good morning! It's {b['date']}."]
    if b["youtube"]["total_views"]:
        parts.append(f"Your channel has {b['youtube']['total_views']} views in total.")
    if b["video"]:
        parts.append(f"Today's video is {clean(b['video']['title'].split('|')[0])}.")
    g = b["youtube"]["going_public"]
    if g:
        parts.append(f"{len(g)} video{'s go' if len(g) > 1 else ' goes'} public today, first at {_say_time(g[0]['time'])}.")
    if b["youtube"]["waiting"]:
        parts.append(f"{b['youtube']['waiting']} videos are waiting for your OK.")
    if b["credits"].get("left") is not None:
        parts.append(f"You have {b['credits']['left']} Flow credits left.")
    parts.append(f"The Mac has {b['mac']['free_gb']} gigabytes free.")
    mo = b.get("money") or {}
    if mo.get("home_owes", 0) > 0:
        parts.append(f"Home owes you {mo['home_owes']:.0f} rupees.")
    if mo.get("bills_due"):
        parts.append("Bills due: " + ", ".join(mo["bills_due"]) + ".")
    if b.get("etsy"):
        parts.append(f"Top Etsy idea: {clean(b['etsy'][0]['title'] or '')} for {b['etsy'][0]['store']}.")
    c = b["claude"]
    if c["done"] or c["failed"]:
        parts.append(f"Claude finished {c['done']} job{'s' if c['done'] != 1 else ''}" + (f" and {c['failed']} failed." if c["failed"] else "."))
    return " ".join(parts)


def start(notify, jobs):
    """Sends the brief as a notification once a day at the chosen time."""
    def loop():
        sent = None
        while True:
            s = settings()
            now = datetime.datetime.now(PKT)
            if s["on"] and now.strftime("%H:%M") >= s["time"] and sent != now.date() and now.strftime("%H:%M") < "23:00":
                try:
                    b = build(jobs())
                    lines = []
                    if b["video"]:
                        lines.append(f"🎬 {b['video']['title'][:60]}")
                    if b["youtube"]["going_public"]:
                        lines.append(f"📺 {len(b['youtube']['going_public'])} going public today")
                    if b["youtube"]["waiting"]:
                        lines.append(f"✅ {b['youtube']['waiting']} waiting for your OK")
                    if b["credits"].get("left") is not None:
                        lines.append(f"💳 {b['credits']['left']} Flow credits")
                    lines.append(f"💾 {b['mac']['free_gb']} GB free")
                    notify.push("system", "☀️ Good morning: your day", "\n".join(lines), "Brief")
                except Exception as e:
                    notify.push("system", "Morning brief failed", str(e)[:200], "")
                sent = now.date()
            time.sleep(60)
    threading.Thread(target=loop, daemon=True).start()
