"""Videos waiting for the owner's OK (<video project>/uploads/pending.json), reviewed from the phone.

A choice made here is written to uploads/app_reactions.json, which approvals.py reads exactly like a Discord reaction:
   ✅ upload to YouTube (+ TikTok inbox)   📱 make Shorts for review   ❌ skip
Then approvals.sh runs straight away instead of waiting for n8n's 2-minute tick.
"""
import json
import os
import secrets
import subprocess
import threading
import time
from pathlib import Path

from config import CFG, tz_label
import config

KF = config.path(CFG["youtube"]["factory_dir"])   # your video project (see docs/SETUP.md)
PENDING = KF / "uploads" / "pending.json"
CHOICES = KF / "uploads" / "app_reactions.json"
BLOCKED_PARTS = {"Library", ".ssh", ".config", "english-coach", Path(__file__).resolve().parent.parent.name}   # never served, whatever the queue says


def _servable(path):
    """A file may be played on the phone when the video queue points at it (written by our own video scripts),
    it is inside the home folder, and it is not in a private place. So a new project folder never breaks playback."""
    real = os.path.realpath(path) if path else ""
    home = str(Path.home()) + os.sep
    if not real.startswith(home) or not os.path.isfile(real):
        return None
    parts = Path(real).relative_to(Path.home()).parts
    if any(p in BLOCKED_PARTS or p.startswith(".") for p in parts):
        return None
    return real


def problem(it):
    """Why a waiting video can't be played, in plain words (or "")."""
    v = it.get("video", "")
    if not v:
        return "No video file in the queue entry"
    if not os.path.exists(v):
        return "The video file is missing on the Mac"
    if not _servable(v):
        return "The video is in a folder the app may not open"
    if os.path.getsize(v) < 10_000:
        return "The video file is empty or broken"
    return ""
_tokens = {}  # short-lived links for the phone's video player: token -> expiry
_lock = threading.Lock()


def _queue():
    try:
        return json.loads(PENDING.read_text())
    except (OSError, ValueError):
        return []


def _choices():
    try:
        return json.loads(CHOICES.read_text())
    except (OSError, ValueError):
        return {}


def _thumb(video):
    meta = Path(video).parent / "meta.json"
    try:
        t = json.loads(meta.read_text()).get("thumb_image")
    except (OSError, ValueError):
        return ""
    if not t:
        return ""
    p = Path(t) if os.path.isabs(t) else Path(video).parent / t
    return str(p) if p.exists() else ""


def suggest(it):
    """A ready upload time (Pakistan time) for this video, with the reason: naat videos go out around Jumu'ah,
    kids' videos follow the owner's upload plan. Never on a slot another video already uses."""
    import datetime
    v = it.get("video", "")
    short = "short" in Path(v).name.lower()
    tz = datetime.timezone(datetime.timedelta(hours=5))
    now = datetime.datetime.now(tz)
    used = _taken()
    if "/naat/" in v:
        wd, hhmm, why = (3, "20:00", "Thursday night (Shab-e-Jumu'ah): Islamic Shorts are watched most, and it warms people up for Friday's full video") if short \
            else (4, "11:00", "Friday before Jumu'ah: the time Islamic content is shared most in Pakistan and the Gulf")
        for week in range(0, 8):
            day = now + datetime.timedelta(days=(wd - now.weekday()) % 7 + 7 * week)
            hh, mm = map(int, hhmm.split(":"))
            t = day.replace(hour=hh, minute=mm, second=0, microsecond=0)
            iso = t.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M")
            if t > now + datetime.timedelta(minutes=30) and iso not in used:
                return {"days": (t.date() - now.date()).days, "time": hhmm, "label": t.strftime("%a %d %b, ") + t.strftime("%I:%M %p").lstrip("0"), "why": why}
    try:
        n = next_free_slot()
    except ValueError:
        return None
    t = datetime.datetime.strptime(n["iso"][:16], "%Y-%m-%dT%H:%M").replace(tzinfo=datetime.timezone.utc).astimezone(tz)
    return {"days": n["day"], "time": n["time"], "label": t.strftime("%a %d %b, ") + t.strftime("%I:%M %p").lstrip("0"),
            "why": "The next free slot in your upload plan"}


def items():
    """Waiting videos, newest first."""
    ch = _choices()
    out = []
    for it in reversed(_queue()):
        if it.get("status") not in ("pending", "uploading"):
            continue
        v = it.get("video", "")
        tags = it.get("tags") or []
        cv = ch.get(it["message_id"], "")
        out.append({"id": it["message_id"], "publish_at": cv.get("publish_at", "") if isinstance(cv, dict) else "", "title": it.get("title", ""), "description": it.get("description", ""),
                    "tags": tags if isinstance(tags, list) else [str(tags)], "posted": it.get("posted", ""),
                    "status": it["status"], "short": "short" in Path(v).name.lower(), "exists": Path(v).exists(),
                    "has_thumb": bool(_thumb(v)), "problem": problem(it), "suggest": suggest(it),
                    "net_retry": any(w in (it.get("error") or "") for w in NET_WORDS), "choice": cv.get("choice", "") if isinstance(cv, dict) else cv, "error": it.get("error", "")})
    try:
        nxt = next_free_slot()
    except ValueError:
        nxt = None
    return {"items": out, "times": default_times(), "next_slot": nxt, "best": best_times()}


_KEY_FILE = Path(__file__).parent / "media_key"


def _key():
    if not _KEY_FILE.exists():
        _KEY_FILE.write_bytes(secrets.token_bytes(32)); os.chmod(_KEY_FILE, 0o600)
    return _KEY_FILE.read_bytes()


def media_token():
    """A signed link key: '<expiry>.<signature>'. It needs no memory, so it keeps working after a server restart."""
    import hmac, hashlib
    exp = str(int(time.time()) + 6 * 3600)
    return {"token": exp + "." + hmac.new(_key(), exp.encode(), hashlib.sha256).hexdigest()[:32]}


def _token_ok(token):
    import hmac, hashlib
    try:
        exp, sig = token.split(".")
        return int(exp) > time.time() and hmac.compare_digest(sig, hmac.new(_key(), exp.encode(), hashlib.sha256).hexdigest()[:32])
    except (ValueError, AttributeError):
        return False


def file_for(token, mid, what):
    """Path of a waiting video (or its thumbnail) if the token is valid, else None."""
    if not _token_ok(token):
        return None
    for it in _queue():
        if it["message_id"] == mid:
            p = it.get("video", "") if what == "video" else _thumb(it.get("video", ""))
            return _servable(p)
    return None


SCHED = KF / "studio" / "data" / "schedule.json"
BEST = KF / "studio" / "data" / "best_times.json"


def _sched():
    try:
        return json.loads(SCHED.read_text())
    except (OSError, ValueError):
        return {}


def default_times():
    c = _sched()
    slots = c.get("slots") or [c.get("long_time", "19:00")]
    return {"long": c.get("long_time", "19:00"), "short": c.get("short_time", "17:00"), "utc_offset": c.get("utc_offset", 5),
            "per_day": int(c.get("per_day", len(slots))), "slots": slots, "auto": c.get("auto", True)}


def save_schedule(per_day, slots, auto):
    """The owner's plan: how many videos a day and at what times (Pakistan time). Keeps Studio's own keys working."""
    slots = sorted({t for t in slots if len(t) == 5 and t[2] == ":"})[: max(1, min(6, int(per_day)))]
    if not slots:
        raise ValueError("Add at least one time")
    c = _sched()
    c.update(per_day=len(slots), slots=slots, auto=bool(auto), long_time=slots[-1], short_time=slots[0], enabled=True)
    SCHED.write_text(json.dumps(c, indent=2))
    return default_times()


def _taken():
    """Publish times already used (UTC ISO, minute precision): scheduled uploads + choices waiting to upload."""
    used = set()
    for it in _queue():
        p = it.get("privacy") or ""
        if p.startswith("scheduled "):
            used.add(p[10:26])
    for c in _choices().values():
        if isinstance(c, dict) and c.get("publish_at") and c["publish_at"] != "now":
            used.add(c["publish_at"][:16])
    return used


def next_free_slot():
    """Next day/time slot with no video yet, from today on (e.g. 1 a day: today 7 PM if free, else tomorrow 7 PM)."""
    import datetime
    d = default_times()
    tz = datetime.timezone(datetime.timedelta(hours=float(d["utc_offset"])))
    now = datetime.datetime.now(tz)
    used = _taken()
    for day in range(0, 60):
        for t in d["slots"]:
            hh, mm = map(int, t.split(":"))
            when = (now + datetime.timedelta(days=day)).replace(hour=hh, minute=mm, second=0, microsecond=0)
            if when < now + datetime.timedelta(minutes=30):
                continue
            iso = when.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
            if iso[:16] not in used:
                return {"iso": iso, "day": day, "time": t}
    raise ValueError("No free slot in the next 60 days")


def reschedule(mid, days, hhmm):
    """Moves an uploaded, still-private video to a new day/time (or "now" with days < 0) on YouTube."""
    it = next((i for i in _queue() if i["message_id"] == mid and i.get("youtube_id")), None)
    if not it:
        raise ValueError("not found")
    when = next_free_slot()["iso"] if days is None else publish_time(int(days), hhmm or "19:00")
    r = subprocess.run([str(config.VENV), str(Path(__file__).parent / "ytsched.py")],
                       input=json.dumps({"id": it["youtube_id"], "publish_at": when}), capture_output=True, text=True, timeout=90)
    try:
        out = json.loads(r.stdout.strip().splitlines()[-1])
    except (ValueError, IndexError):
        raise RuntimeError("YouTube didn't answer: " + (r.stderr or "")[-200:])
    if out.get("ok"):
        with _lock:
            q = _queue()
            for i in q:
                if i["message_id"] == mid:
                    i["privacy"] = "public" if when == "now" else "scheduled " + when
            PENDING.write_text(json.dumps(q, indent=1, ensure_ascii=False))
        _stats["t"] = 0
    return out


def best_times():
    try:
        return json.loads(BEST.read_text())
    except (OSError, ValueError):
        return {}


CHANNEL_ID = CFG["youtube"]["channel_id"]      # your channel id (UC...), from config.json
CHANNEL = CFG["youtube"]["channel_name"] or "the owner's channel"


def best_time_prompt():
    url = f"https://studio.youtube.com/channel/{CHANNEL_ID}/analytics/tab-audience/period-default"
    from autopilot import BROWSER
    c = CFG["chrome"]
    order = [f"'{c['profile_dir']}'" + (f" ({c['profile_name']})" if c["profile_name"] else "")] + \
        [f"'{d}'" for d in c["other_profiles"] if d != c["profile_dir"]]
    if not CHANNEL_ID:
        url = "https://studio.youtube.com/ (open Analytics, then the Audience tab)"
    return (BROWSER + f"Find when the owner's YouTube channel {CHANNEL}'s viewers are online. Use Claude in Chrome. Open "
            f"{url} . It only works in the Chrome profile whose Google account owns the channel: try the profiles in this order "
            "(open Chrome with: open -na 'Google Chrome' --args --profile-directory=<dir> <url>): " + ", ".join(order) + ". "
            f"The right one shows '{CHANNEL}' as the channel in YouTube Studio; never "
            "use another channel's data. Change nothing, sign in nowhere. Read the 'When your viewers are on YouTube' chart "
            f"(times are shown in the page's time zone; convert to {tz_label()}). "
            "Pick the 3 best publish times about "
            "1-2 hours before the biggest peaks, as HH:MM. Write " + str(BEST) + " as "
            "{\"slots\": [\"HH:MM\", ...], \"note\": \"one short sentence: which profile worked and why these times\", "
            "\"source\": \"studio\" or \"best practice\", \"checked\": \"YYYY-MM-DD HH:MM\"}. If no profile owns the "
            "channel, or the chart says there isn't enough data yet, write best-practice times for toddler content with "
            "source 'best practice' and say exactly why in the note. Reply with the times and the source.")


def publish_time(days, hhmm):
    """days from today + local HH:MM (Pakistan time from schedule.json) -> UTC ISO for YouTube. days < 0 = now."""
    import datetime
    if days < 0:
        return "now"
    hh, mm = map(int, hhmm.split(":"))
    tz = datetime.timezone(datetime.timedelta(hours=float(default_times()["utc_offset"])))
    now = datetime.datetime.now(tz)
    t = (now + datetime.timedelta(days=days)).replace(hour=hh, minute=mm, second=0, microsecond=0)
    if t < now + datetime.timedelta(minutes=20):
        raise ValueError("Pick a time at least 20 minutes from now")
    return t.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def choose(mid, emoji, notify, days=None, hhmm=None):
    if emoji not in ("✅", "📱", "❌"):
        raise ValueError("bad choice")
    if emoji == "✅" and days is None and default_times()["auto"]:
        when = next_free_slot()["iso"]          # auto: the next free day/time slot
    else:
        when = publish_time(int(days), hhmm or "19:00") if emoji == "✅" and days is not None else None
    if not any(i["message_id"] == mid for i in _queue()):
        raise ValueError("not found")
    with _lock:
        c = _choices()
        c[mid] = {"choice": emoji, "publish_at": when} if when else emoji
        CHOICES.write_text(json.dumps(c, indent=1, ensure_ascii=False))
    threading.Thread(target=_run_now, args=(notify,), daemon=True).start()
    return {"ok": True}


NET_WORDS = ("Network is unreachable", "nodename nor servname", "timed out", "Temporary failure", "Connection reset",
             "Connection refused", "Max retries", "RemoteDisconnected", "ssl", "SSL")


def _run_now(notify):
    """Runs the approvals script now. If the network dropped (the Mac's DNS goes through Tailscale and sometimes
    hiccups), tries again up to 5 times, 90 s apart, before telling the owner."""
    for attempt in range(6):
        r = subprocess.run([str(KF / "approvals.sh")], capture_output=True, text=True, timeout=3600)
        try:
            out = json.loads((r.stdout or "{}").strip().splitlines()[-1])
        except (ValueError, IndexError):
            out = {}
        if out.get("busy"):                    # another run is uploading right now; it will handle our choice
            time.sleep(90); continue
        acts = out.get("actions", [])
        net = [a for a in acts if a.get("status") == "error" and any(w in a.get("error", "") for w in NET_WORDS)]
        for a in acts:
            st = a.get("status")
            if st == "uploaded":
                p = a.get("privacy", "")
                head = "Scheduled on YouTube 🕒" if p.startswith("scheduled") else "Uploaded to YouTube ✅"
                when = f"\nGoes public {p[10:]} (UTC)" if p.startswith("scheduled") else ""
                notify.push("video", head, f"{a.get('title', '')}\n{a.get('link', '')}{when}", "Videos")
            elif st == "shorts_for_review":
                notify.push("video", "Shorts ready for review 📱", f"{a.get('count')} Short(s) from {a.get('title', '')}", "Videos")
            elif st == "error" and a not in net:
                notify.push("video", "Upload failed", f"{a.get('title', '')}: {a.get('error', '')}", "Videos")
        if not net:
            return
        if attempt == 5:
            notify.push("video", "Upload waiting for the internet", "The Mac couldn't reach YouTube after 6 tries. It tries again "
                        "every 2 minutes by itself.", "Videos")
            return
        time.sleep(90)


def fix_prompt(mid, note):
    it = next((i for i in _queue() if i["message_id"] == mid), None)
    if not it:
        raise ValueError("not found")
    return (f"In {KF}: the owner reviewed the video \"{it['title']}\" ({it['video']}) and wants this change: {note}\n"
            "Read HANDOFF.md, FLOW_RULES.md and PROMPT_BIBLE.md first. Make the change with the least Flow credits "
            "(re-edit before regenerating clips). Then post the new version for approval the usual way "
            "(discord_send.send_for_approval) and mark the old queue item status 'replaced'.")


_stats = {"t": 0, "data": {}}


def uploaded(force=False):
    """Uploaded videos, newest first, with link, views, likes, comments and whether it's public/scheduled."""
    done = [i for i in reversed(_queue()) if i.get("status") == "uploaded" and i.get("youtube_id")]
    ids = [i["youtube_id"] for i in done]
    if ids and (force or time.time() - _stats["t"] > 300 or not set(ids) <= set(_stats["data"])):
        r = subprocess.run([str(config.VENV), str(Path(__file__).parent / "ytstats.py")],
                           input=json.dumps(ids), capture_output=True, text=True, timeout=90)
        try:
            _stats["data"] = json.loads(r.stdout); _stats["t"] = time.time()
        except ValueError:
            pass
    out = []
    for i in done:
        s = _stats["data"].get(i["youtube_id"], {})
        short = "short" in Path(i.get("video", "")).name.lower()
        out.append({"id": i["message_id"], "title": i.get("title", ""), "youtube_id": i["youtube_id"], "can_reschedule": s.get("privacy") == "private",
                    "link": f"https://youtube.com/shorts/{i['youtube_id']}" if short else f"https://youtu.be/{i['youtube_id']}",
                    "short": short, "uploaded": i.get("uploaded", ""), "views": s.get("views", 0), "likes": s.get("likes", 0),
                    "comments": s.get("comments", 0), "privacy": s.get("privacy", ""), "publish_at": s.get("publish_at", ""),
                    "thumb": s.get("thumb", ""), "tiktok": bool(i.get("tiktok"))})
    out.sort(key=lambda x: x["uploaded"], reverse=True)
    return {"items": out, "checked": _stats["t"]}


def watch_problems(notify):
    """Every 10 minutes: if a newly waiting video can't be played on the phone, tell the owner why (once per video)."""
    seen = set()
    def loop():
        first = True
        while True:
            for it in _queue():
                if it.get("status") != "pending" or it["message_id"] in seen:
                    continue
                seen.add(it["message_id"])
                why = problem(it)
                if why and not first:
                    notify.push("video", "A video can't be played", f"{it.get('title', '')[:80]}: {why}", "Videos")
            first = False
            time.sleep(600)
    threading.Thread(target=loop, daemon=True).start()
