"""Naat / nasheed / song projects: the owner explains an idea (often by voice, in Urdu) → Claude writes the lyrics,
a Suno prompt and a visual plan → the owner makes the song in Suno and imports it → Claude makes a premium,
fully animated video in Google Flow with a credit budget the owner picks. Projects live in the config songs.project_dir folder, one per song."""
import json
import re
import time
from pathlib import Path

from config import SONGS as ROOT, KFS, chrome_desc
STYLES = {
    "vocals": "Vocals only, no instruments (traditional naat/nasheed)",
    "daf": "Vocals with soft daf only",
    "music": "Vocals with soft, respectful music",
}
VOICES = {
    "kids": "children's voices (a cute kids' choir with a lead child singer, about 6-10 years old)",
    "girl": "one sweet young girl's voice (about 7 years old)",
    "boy": "one sweet young boy's voice (about 7 years old)",
    "male": "a soft adult male voice",
    "female": "a soft adult female voice",
}
KIDS_STYLE = ("FOR KIDS (ages 3-8): very simple, short Urdu words a small child knows; short lines; lots of repetition and a "
              "catchy sing-along chorus; happy or gentle, warm mood; call-and-response or counting where it fits; teach one "
              "small good thing (e.g. saying Bismillah, Alhamdulillah, thanking Allah, loving parents, sharing, salah, wudu); "
              "no scary or sad parts; 90-150 seconds.")
RULES = ("Islamic rules (always): never show Allah in any form, the Prophet ﷺ, other prophets, companions (Sahaba) or "
         "angels, and no faces of revered figures; no women's faces close-up; true and respectful words only (no made-up "
         "hadith or claims). Good visuals: the Kaaba and Masjid an-Nabawi from a distance, mosques, minarets, lanterns, "
         "Arabic/Urdu calligraphy, Quran pages (closed or from afar), deserts, stars, moonlit sky, rain, light rays, "
         "nature, hands raised in dua seen from behind, silhouettes walking to a mosque.")


def _dir(pid):
    if not re.match(r"^[a-z0-9-]{3,80}$", pid or ""):
        raise ValueError("bad id")
    d = ROOT / pid
    if not d.is_dir():
        raise ValueError("not found")
    return d


def _status(d):
    try:
        st = json.load(open(d / "status.json"))
    except (OSError, ValueError):
        return {}
    if st.get("stage") in ("writing", "making video") and st.get("job"):
        import assistant
        j = assistant.job(st["job"])
        if not j or j.get("status") != "running":    # finished (or forgotten after a server restart): move the stage on
            if st["stage"] == "writing":
                st["stage"] = "lyrics ready" if (d / "brief.json").exists() else "writing failed"
            else:
                st["stage"] = "video ready" if list(d.rglob("final*.mp4")) else "video stopped"
            json.dump(st, open(d / "status.json", "w"), indent=1)
    return st


def _set_status(d, **kw):
    st = _status(d); st.update(kw)
    json.dump(st, open(d / "status.json", "w"), indent=1)


def _song(d):
    return next((p for p in sorted(d.glob("song.*")) if p.suffix.lower() in (".mp3", ".wav", ".m4a")), None)


def listing():
    out = []
    if ROOT.exists():
        for d in sorted(ROOT.iterdir(), key=lambda p: -p.stat().st_mtime):
            if not d.is_dir():
                continue
            try:
                b = json.load(open(d / "brief.json"))
            except (OSError, ValueError):
                b = {}
            st = _status(d)
            out.append({"id": d.name, "title": b.get("title") or st.get("idea", "")[:50], "ready": bool(b),
                        "song": bool(_song(d)), "stage": st.get("stage", "writing"), "job": st.get("job", ""),
                        "created": st.get("created", "")})
    return {"items": out[:50], "styles": STYLES, "voices": VOICES}


def get(pid):
    d = _dir(pid)
    try:
        b = json.load(open(d / "brief.json"))
    except (OSError, ValueError):
        b = {}
    s = _song(d)
    finals = sorted(str(p) for p in d.rglob("final*.mp4"))
    return {"id": pid, "brief": b, "status": _status(d), "song": s.name if s else "", "videos": finals}


def _brief_prompt(d, idea, style, voice="kids", kids=True):
    return (f"You write Islamic naats/nasheeds/songs for the owner's YouTube channel. The owner explained this idea (maybe in "
            f"Urdu, Roman Urdu or Hindi, maybe by voice with mistakes): \"{idea}\"\nMusic style chosen: {STYLES.get(style, style)}.\n"
            f"Voice: {VOICES.get(voice, voice)} — say this clearly in suno_style (e.g. 'children's choir, kids vocals').\n"
            f"{KIDS_STYLE if kids else ''}\n{RULES}\nWrite the result to {d}/brief.json (UTF-8) with exactly these keys:\n"
            "{\"title\": short YouTube title (Urdu + English), \"theme\": one line, "
            "\"lyrics_urdu\": the full lyrics in Urdu script with [Intro]/[Verse 1]/[Chorus]/[Verse 2]/[Bridge]/[Outro] tags, "
            "\"lyrics_roman\": the same in Roman Urdu, \"meaning_en\": a short English meaning, "
            "\"suno_style\": Suno 'Style of Music' text under 200 characters (language, voice, mood, tempo, instruments per the "
            "chosen style; if vocals only say 'a cappella, no instruments'), \"suno_lyrics\": the lyrics exactly as to paste into "
            "Suno's Lyrics box (Roman Urdu or Urdu, with the section tags), \"length\": target seconds (150-240), "
            "\"scenes\": [{\"section\": tag, \"visual\": what we see, all animated, following the rules}], "
            "\"checks\": list of the rules you checked}.\n"
            "Use correct, respectful wording; no invented hadith. Reply with the title only.")


def create(idea, style, start_job, voice="kids", kids=True):
    idea = (idea or "").strip()
    if len(idea) < 5:
        raise ValueError("Tell me a little more about the idea")
    slug = re.sub(r"[^a-z0-9]+", "-", idea.lower())[:30].strip("-") or "naat"
    pid = time.strftime("%Y%m%d-%H%M") + "-" + slug
    d = ROOT / pid
    d.mkdir(parents=True, exist_ok=True)
    _set_status(d, idea=idea, style=style, voice=voice, kids=bool(kids), stage="writing", created=time.strftime("%Y-%m-%d %H:%M"))
    jid = start_job(_brief_prompt(d, idea, style, voice, kids), d)
    _set_status(d, job=jid)
    return {"id": pid, "job_id": jid}


def revise(pid, note, start_job):
    d = _dir(pid)
    _set_status(d, stage="writing")
    jid = start_job(f"Update {d}/brief.json (keep the same keys) with this change from the owner: \"{note}\". {RULES} "
                    "Reply with what you changed in one line.", d)
    _set_status(d, job=jid)
    return {"job_id": jid}


def save_song(pid, name, data):
    d = _dir(pid)
    ext = Path(name or "song.mp3").suffix.lower()
    if ext not in (".mp3", ".wav", ".m4a"):
        raise ValueError("Import an MP3, WAV or M4A from Suno")
    for old in d.glob("song.*"):
        old.unlink()
    (d / f"song{ext}").write_bytes(data)
    _set_status(d, stage="song ready", job="")
    return {"ok": True, "bytes": len(data)}


def video_prompt(pid, credits, shorts):
    d = _dir(pid)
    s = _song(d)
    if not s:
        raise ValueError("Import the song from Suno first")
    from autopilot import BROWSER
    return (BROWSER + f"Make a premium music video for the owner's Islamic naat/song in {d} (song: {s.name}, plan: brief.json).\n"
            "CONTINUE if work already exists in the folder (whisper json, clip plan, kf/, clips/, progress.json): reuse it, "
            "never remake a keyframe or clip that is already there and good, and never spend credits twice.\n"
            f"Credit budget: up to {int(credits)} Google Flow credits — use them for quality: every scene animated (Veo clips, "
            "first and last frame keyframes where it helps), no still images. Use Chrome with Claude in Chrome on " + chrome_desc() +
            " for Google Flow; click Approve only, never 'Always approve'; stop before going over the budget.\n"
            f"{RULES}\n"
            + ("KIDS VIDEO: this is for children (3-8). Bright, colourful, cute 3D cartoon look (soft Pixar-like style, like the "
               "kids videos in " + KFS + " and its PROMPT_BIBLE.md for character consistency): animated kids in modest "
               "clothes (caps, scarves), at most 2 characters per clip, playing, making wudu, saying dua with raised hands, "
               "walking to a cute mosque, stars, moon, flowers, animals; big friendly on-screen words for the key Urdu word of each "
               "line; happy, gentle movement, nothing scary.\n" if _status(d).get("kids", True) else "") +
            "Steps: 1) Whisper the song for exact line times (the video project's Python, mlx_whisper; Urdu). 2) Plan one clip per "
            "lyric line or section from brief.json scenes, matching the mood and the beat. 3) Make keyframes and Veo clips in "
            "Flow; check each at 4 fps, redo only real failures. 4) Edit with " + KFS + "/song_video.py ideas (cut to Whisper "
            "line times, soft crossfades, slow motion where calm), then lyrics on screen: Urdu script line + Roman Urdu under it "
            "(check the Urdu renders correctly right-to-left; if the font can't, use Roman Urdu only). 5) 16:9 video "
            + ("and a 9:16 Short from the chorus " if shorts else "") +
            f"as {d}/final.mp4 (and final_short.mp4), plus a thumbnail and YouTube SEO in meta.json (Urdu + English). "
            "6) Post it to Discord for the owner's approval with discord_send.send_for_approval so it shows in the app's "
            f"Videos screen. 7) Write the credits used to {d}/status.json key credits_used. "
            f"PROGRESS: after every step, write {d}/progress.json as {{\"phase_no\": 1-9, \"clips_done\": N, \"clips_total\": N, "
            "\"credits_used\": N, \"note\": \"what you are doing now, one short line\"}} (phases: 1 plan, 2 whisper, 3 clip plan, "
            "4 keyframes, 5 Veo clips, 6 checking, 7 edit + lyrics, 8 short/thumbnail/SEO, 9 sent for OK). Read " + KFS + "/FLOW_RULES.md "
            "first for how Flow works on this Mac.")


def mark_making(pid, jid, credits):
    _set_status(_dir(pid), stage="making video", job=jid, budget=int(credits))


PHASES = ["Reading the plan", "Timing the song (Whisper)", "Planning the clips", "Keyframes in Flow", "Veo clips in Flow",
          "Checking every clip", "Editing + lyrics on screen", "Short, thumbnail and SEO", "Sent for your OK"]


def _count(d, sub, exts):
    p = d / sub
    return sum(1 for f in p.iterdir() if f.suffix.lower() in exts) if p.is_dir() else 0


def progress(pid, job):
    """How far the video is: phase list, clips, credits, time so far and time left (from the files Claude makes,
    or from progress.json when Claude writes it)."""
    d = _dir(pid)
    st = _status(d)
    j = job(st.get("job", "")) if st.get("job") else {}
    started = j.get("started") or (d / "status.json").stat().st_mtime
    elapsed = (time.time() - started) / 60
    try:
        pj = json.load(open(d / "progress.json"))
    except (OSError, ValueError):
        pj = {}
    plan = None
    for name in ("plan.json", "prompts.json", "clips.json", "clip_plan.json"):
        try:
            plan = json.load(open(d / name)); break
        except (OSError, ValueError):
            continue
    if isinstance(plan, dict):
        plan = plan.get("clips") or plan.get("shots") or plan.get("prompts") or []
    b = {}
    try:
        b = json.load(open(d / "brief.json"))
    except (OSError, ValueError):
        pass
    clips_total = int(pj.get("clips_total") or (len(plan) if plan else 0) or max(len(b.get("scenes", [])), 6))
    kf = _count(d, "kf", {".png", ".jpg", ".jpeg", ".webp"})
    clips = int(pj.get("clips_done") or _count(d, "clips", {".mp4", ".mov"}))
    qc = _count(d, "qc", {".jpg", ".png"})
    whisper = any(d.glob("whisper*.json"))
    final = (d / "final.mp4").exists() or any(d.glob("final*.mp4"))
    extras = (d / "meta.json").exists()
    done_stage = st.get("stage") in ("video ready", "sent for OK", "sent for ok", "done")
    flags = [True, whisper or kf > 0 or clips > 0, bool(plan) or kf > 0 or clips > 0, kf >= clips_total or clips > 0,
             clips >= clips_total, qc >= clips_total or final, final, extras or done_stage, done_stage]
    if pj.get("phase_no"):
        n = int(pj["phase_no"]); flags = [i < n - 1 for i in range(len(PHASES))]
    cur = next((i for i, f in enumerate(flags) if not f), len(PHASES))
    weights = [2, 5, 5, 18, 45, 5, 12, 5, 3]
    pct = sum(w for w, f in zip(weights, flags) if f)
    if cur == 3: pct += weights[3] * min(1, kf / max(clips_total, 1))
    if cur == 4: pct += weights[4] * min(1, clips / max(clips_total, 1))
    if cur == 5: pct += weights[5] * min(1, qc / max(clips_total, 1))
    pct = 100 if done_stage else min(99, round(pct))
    typical = 30 + clips_total * 7               # ~7 min per Veo clip incl. keyframe and check, + edit
    left = max(0, round(elapsed / pct * (100 - pct))) if pct >= 15 else max(5, round(typical - elapsed))
    steps = [l[6:] for l in j.get("log", [])]
    words = [s for s in steps if not s.startswith("▸")]
    return {"pct": pct, "phase_no": cur + 1, "phases": PHASES, "phase": PHASES[min(cur, len(PHASES) - 1)],
            "clips": clips, "clips_total": clips_total, "keyframes": kf, "checked": qc,
            "credits_used": int(pj.get("credits_used") or clips * 10), "budget": st.get("budget", 0),
            "elapsed_min": round(elapsed), "left_min": 0 if done_stage else left,
            "now": pj.get("note") or (words[-1] if words else steps[-1] if steps else "Starting…"),
            "running": j.get("status") == "running", "status": j.get("status", ""), "result": j.get("result", "")[:1500],
            "limit": "hit your" in (j.get("result") or "") and "limit" in (j.get("result") or ""),
            "log": [l for l in j.get("log", [])][-150:]}
