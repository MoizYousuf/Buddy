"""Phase 4: video score before upload, YouTube stats, and the read-only family page for the Money book."""
import html, json, re, secrets, subprocess, tempfile, time
from config import owner
from pathlib import Path

import money
import reviews

HERE = Path(__file__).parent
AGY = Path.home() / ".local" / "bin" / "agy"
CLAUDE = str(Path.home() / ".local" / "bin" / "claude")
SCORES = HERE / "video_scores.json"


# ---------- video score ----------

def _scores():
    try:
        return json.loads(SCORES.read_text())
    except (OSError, ValueError):
        return {}


def video_score(mid):
    """4 frames of the video → Gemini looks at them like a viewer and a YouTube expert. Returns score 1-10 + fixes."""
    it = next((i for i in reviews._queue() if str(i.get("message_id")) == str(mid)), None)
    if not it or not Path(it.get("video", "")).exists():
        raise ValueError("Video file not found")
    v = it["video"]
    d = Path(tempfile.mkdtemp(prefix="vscore-"))
    dur = subprocess.run(["/opt/homebrew/bin/ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", v],
                         capture_output=True, text=True).stdout.strip()
    dur = float(dur or 30)
    frames = []
    for k, f in enumerate((0.1, 0.35, 0.6, 0.85)):
        out = d / f"f{k}.jpg"
        subprocess.run(["/opt/homebrew/bin/ffmpeg", "-y", "-loglevel", "error", "-ss", str(dur * f), "-i", v, "-frames:v", "1", "-vf", "scale=640:-1", str(out)])
        if out.exists():
            frames.append(str(out))
    if not frames:
        raise ValueError("Couldn't read frames")
    p = (f"Look at these image files, frames from a kids YouTube video: {', '.join(frames)}. Title: \"{it.get('title', '')}\". "
         "Judge it like a parent and a YouTube kids-channel expert. Reply with JSON only: {\"score\": 1-10, \"looks_ai\": 1-10 (10 = very AI looking), "
         "\"good\": [\"...\"], \"fix\": [\"short concrete fixes, max 4\"], \"title_tip\": \"better title or empty\", \"thumbnail_frame\": 1-4}. "
         "Write the lists in simple Roman Urdu. Don't run any commands.")
    out = ""
    if AGY.exists():
        try:
            out = subprocess.run([str(AGY), "-p", p, "--print-timeout", "150s"], capture_output=True, text=True, timeout=180, cwd=str(d)).stdout
        except subprocess.TimeoutExpired:
            pass
    if "{" not in out:
        out = subprocess.run([CLAUDE, "-p", p, "--output-format", "text", "--model", "sonnet", "--allowedTools", "Read"],
                             capture_output=True, text=True, timeout=200, cwd=str(d)).stdout
    try:
        j = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except ValueError:
        raise ValueError("Score nahi ban saka, dobara try karo")
    j["at"] = time.strftime("%Y-%m-%d %H:%M")
    s = _scores(); s[str(mid)] = j; SCORES.write_text(json.dumps(s, ensure_ascii=False, indent=1))
    return j


# ---------- YouTube stats ----------

def yt_stats():
    try:
        reviews.uploaded()
    except Exception:  # noqa: BLE001
        pass
    st = reviews._stats["data"]
    rows = []
    for i in reviews._queue():
        y = i.get("youtube_id")
        if y and y in st:
            rows.append({"title": i.get("title", ""), "views": st[y].get("views", 0), "likes": st[y].get("likes", 0),
                         "date": (i.get("uploaded") or i.get("posted") or "")[:10], "short": "#shorts" in i.get("title", "").lower(), "id": y})
    rows.sort(key=lambda r: -r["views"])
    by_day = {}
    for r in rows:
        if r["date"]:
            by_day[r["date"]] = by_day.get(r["date"], 0) + r["views"]
    scores = _scores()
    return {"total_views": sum(r["views"] for r in rows), "videos": len(rows), "top": rows[:10],
            "shorts_views": sum(r["views"] for r in rows if r["short"]), "long_views": sum(r["views"] for r in rows if not r["short"]),
            "by_upload_day": sorted(by_day.items())[-30:], "scores": scores}


# ---------- family page (read-only, home Wi-Fi only, secret link) ----------

def family_key():
    b = money._load()
    if not b.get("family_key"):
        with money._lock:
            b = money._load(); b["family_key"] = secrets.token_urlsafe(18); money._save(b)
    return b["family_key"]


def family_ok(k, ip):
    private = ip.startswith(("192.168.", "10.", "172.16.", "100.", "127."))
    return private and k and secrets.compare_digest(k, family_key())


def family_html():
    L = money.listing()
    k = family_key()
    pend = [x for x in L["entries"] if x["kind"] == "spend" and x["whose"] == "home"][:40]
    rows = "".join(f"<tr><td>{x['date'][5:]}</td><td>{html.escape(x['note'] or x['category'])}</td><td>{html.escape(x.get('person') or 'Ghar')}</td>"
                   f"<td class=r>{x['amount']:,.0f}</td></tr>" for x in pend)
    ppl = "".join(f"<div class=p><b>{html.escape(p)}</b><span>PKR {v['owes']:,.0f}</span></div>" for p, v in L["people"].items() if v["owes"] > 0)
    return f"""<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Ghar ka hisaab</title><style>body{{font-family:-apple-system,Roboto,sans-serif;margin:0;background:#f3f7f5;color:#1a1530}}
.h{{background:linear-gradient(135deg,#052e2b,#065f46);color:#fff;padding:28px 20px;border-radius:0 0 28px 28px}}
.h b{{font-size:38px;display:block;margin-top:6px}}.c{{background:#fff;margin:16px;border-radius:20px;padding:16px;box-shadow:0 6px 20px #0001}}
.p{{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #eee}}table{{width:100%;border-collapse:collapse;font-size:14px}}
td{{padding:8px 4px;border-bottom:1px solid #eee}}.r{{text-align:right;font-weight:700}}input,button{{font-size:17px;padding:12px;border-radius:12px;border:1px solid #ccc;width:100%;box-sizing:border-box}}
button{{background:#16a34a;color:#fff;border:0;font-weight:800;margin-top:8px}}</style></head><body>
<div class=h>Ghar se dene hain<b>PKR {L['balance']['home_owes']:,.0f}</b><small>Is mahine ghar ka kharcha: PKR {L['month_total']['home']:,.0f}</small></div>
{f'<div class=c>{ppl}</div>' if ppl else ''}
<div class=c><b>💵 Paise de diye?</b><form method=post action="/family/paid?k={k}"><input name=amount type=number placeholder="Kitne diye (PKR)" required>
<input name=who placeholder="Kis ne diye (Ammi, Abu…)" style="margin-top:8px"><button>✅ De diye</button></form>
<small>{html.escape(owner("Owner"))} ko notification jayega, woh confirm karega.</small></div>
<div class=c><b>🧾 Ghar ke kharche</b><table>{rows}</table></div></body></html>"""


def family_paid(amount, who):
    a = money._num(amount)
    if a <= 0:
        raise ValueError("Amount?")
    with money._lock:
        b = money._load()
        b.setdefault("family_paid", []).append({"id": secrets.token_hex(4), "amount": a, "who": str(who or "Ghar")[:30], "at": time.strftime("%Y-%m-%d %H:%M")})
        money._save(b)
    return a


def family_pending():
    return money._load().get("family_paid", [])


def family_confirm(fid, ok):
    with money._lock:
        b = money._load(); items = b.get("family_paid", [])
        it = next((x for x in items if x["id"] == fid), None)
        b["family_paid"] = [x for x in items if x["id"] != fid]
        money._save(b)
    if it and ok:
        return money.add({"kind": "received", "amount": it["amount"], "note": f"{it['who']} ne diye", "person": "Ghar"})
    return money.listing()
