"""YouTube analytics for the phone: channel totals, growth from daily snapshots (and real per-day numbers + watch time
when the YouTube Analytics API is on), best and newest videos, Shorts vs long, and one AI tip a day."""
import datetime
import json
import subprocess
import time
from pathlib import Path

SNAP = Path(__file__).parent / "analytics"
from config import VENV
_cache = {"t": 0, "data": None}


def _fetch():
    r = subprocess.run([str(VENV), str(Path(__file__).parent / "ytchannel.py")], capture_output=True, text=True, timeout=180)
    return json.loads(r.stdout)


def _snapshots():
    SNAP.mkdir(exist_ok=True)
    out = {}
    for f in sorted(SNAP.glob("snap-*.json")):
        try:
            out[f.stem[5:]] = json.load(open(f))
        except (OSError, ValueError):
            pass
    return out


def data(force=False):
    if force or not _cache["data"] or time.time() - _cache["t"] > 1800:
        d = _fetch()
        SNAP.mkdir(exist_ok=True)
        today = datetime.date.today().isoformat()
        json.dump({"channel": d["channel"], "views": {v["id"]: v["views"] for v in d["videos"]}}, open(SNAP / f"snap-{today}.json", "w"))
        _cache.update(t=time.time(), data=d)
    return _cache["data"]


def _age_days(v):
    t = datetime.datetime.fromisoformat(v["published"].replace("Z", "+00:00"))
    return max(1, (datetime.datetime.now(datetime.timezone.utc) - t).total_seconds() / 86400)


def summary(ask=None, force=False):
    d = data(force)
    ch, vids = d["channel"], [v for v in d["videos"] if v["privacy"] == "public"]
    snaps = _snapshots()
    days = sorted(snaps)
    today = datetime.date.today()

    def delta(n):   # channel views / subs gained in the last n days, from our snapshots
        old = next((snaps[k] for k in days if k >= (today - datetime.timedelta(days=n)).isoformat()), None)
        if not old or old is snaps.get(today.isoformat()):
            return None
        return {"views": ch["views"] - old["channel"]["views"], "subs": ch["subs"] - old["channel"]["subs"], "since": next(
            k for k in days if snaps[k] is old)}
    for v in vids:
        v["per_day"] = round(v["views"] / _age_days(v), 1)
    shorts = [v for v in vids if v["short"]]; longs = [v for v in vids if not v["short"]]
    avg = lambda xs: round(sum(x["views"] for x in xs) / len(xs)) if xs else 0
    newest = sorted(vids, key=lambda v: v["published"], reverse=True)[:5]
    top = sorted(vids, key=lambda v: -v["views"])[:5]
    scheduled = [v for v in d["videos"] if v["privacy"] == "private" and v.get("publish_at")]
    daily = d.get("daily") or []
    last7 = daily[-7:]
    out = {"channel": ch, "updated": time.strftime("%Y-%m-%d %H:%M", time.localtime(_cache["t"])),
           "growth_1d": delta(1), "growth_7d": delta(7), "snapshot_days": len(days),
           "analytics": "on" if daily else d.get("analytics_error", "off"),
           "views_7d": sum(r["views"] for r in last7) if last7 else None,
           "minutes_7d": sum(r["minutes"] for r in last7) if last7 else None,
           "subs_7d": sum(r["subs_gained"] - r["subs_lost"] for r in last7) if last7 else None,
           "avg_view_secs": round(sum(r["avg_secs"] for r in last7) / len(last7)) if last7 else None,
           "daily": [{"day": r["day"], "views": r["views"]} for r in daily],
           "shorts_avg": avg(shorts), "longs_avg": avg(longs), "shorts": len(shorts), "longs": len(longs),
           "top": [{k: v[k] for k in ("id", "title", "views", "likes", "per_day", "short")} for v in top],
           "newest": [{k: v[k] for k in ("id", "title", "views", "likes", "per_day", "short", "published")} for v in newest],
           "scheduled": len(scheduled)}
    out["tip"] = tip(out, ask)
    return out


def tip(s, ask):
    """One short, practical tip a day (local AI model; a simple rule if it isn't available)."""
    f = SNAP / f"tip-{datetime.date.today()}.json"
    if f.exists():
        return json.load(open(f)).get("tip", "")
    facts = {"subs": s["channel"]["subs"], "total_views": s["channel"]["views"], "videos": s["channel"]["videos"],
             "shorts_avg_views": s["shorts_avg"], "long_avg_views": s["longs_avg"],
             "top": [(t["title"][:60], t["views"], "Short" if t["short"] else "video") for t in s["top"][:5]],
             "newest": [(n["title"][:60], n["views"], n["published"][:10]) for n in s["newest"][:4]]}
    text = ""
    try:   # Claude, once a day: much better reasoning than the small local model
        from config import CFG
        about = CFG["youtube"].get("about") or "Shorts and longer videos made with AI video"
        prompt = (f"You coach a YouTube channel ({CFG['youtube']['channel_name'] or 'the owner\'s channel'}: {about}). From ONLY these numbers, give ONE concrete tip in 1-2 short sentences: what to make next "
                  "or change, pointing at what clearly works here (e.g. an outlier video and why). No generic advice, no intro. "
                  "Numbers: " + json.dumps(facts, ensure_ascii=False))
        r = subprocess.run([str(Path.home() / ".local/bin/claude"), "-p", prompt], capture_output=True, text=True, timeout=120)
        if r.returncode == 0 and "limit" not in r.stdout.lower()[:80]:
            text = r.stdout.strip()[:400]
    except Exception:
        text = ""
    if not text and ask:
        try:
            text = (ask([{"role": "system", "content": "You coach a kids YouTube channel. Reply JSON {\"tip\": \"...\"} with one "
                          "concrete tip from these numbers."}, {"role": "user", "content": json.dumps(facts, ensure_ascii=False)}]) or {}).get("tip", "")
        except Exception:
            text = ""
    if not text:
        best = s["top"][0] if s["top"] else None
        text = (f"\"{best['title'][:40]}\" has {best['views']} views — far more than the rest. Make more videos like it "
                "(same characters, song format, colours) and reuse its style in the next Shorts.") if best else ""
    json.dump({"tip": text}, open(f, "w"))
    return text
