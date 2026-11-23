"""Studio (your YouTube dashboard, <video project>/studio, config youtube.studio_url) from the phone.

The phone never talks to Studio directly: it sends signed requests here and this module forwards only the
actions listed below to Studio on localhost. Studio's own code is not changed.
"""
import json
import re
import os
import subprocess
import time
import urllib.request
from pathlib import Path

from config import CFG, KF, KFS, VENV

STUDIO = CFG["youtube"]["studio_url"].rstrip("/")
DATA = KF / "studio" / "data"
GET_API = {"status", "flowqueue", "flowlive", "schedule", "pipeline"}
POST_API = {"analyze", "regen", "create", "autofix", "edit", "shot", "images", "clips", "qc", "review",
            "schedule", "calendar", "chat", "flowqueue"}


def call(method, name, query="", body=None):
    """Forward one allowed /api/<name> call to Studio and return its JSON."""
    if name not in (GET_API if method == "GET" else POST_API):
        raise ValueError("not allowed")
    url = f"{STUDIO}/api/{name}" + (f"?{query}" if query else "")
    data = json.dumps(body or {}).encode() if method == "POST" else None
    req = urllib.request.Request(url, data, {"Content-Type": "application/json"}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            out = json.load(r)
            return {"items": out} if isinstance(out, list) else out  # the phone expects an object
    except urllib.error.HTTPError as e:
        return json.load(e)
    except OSError:
        raise RuntimeError("Studio is not running on the Mac")


def today():
    """Today's plans, shortened for a phone screen (Studio has no single GET for this)."""
    d = json.load(open(DATA / "latest.json"))
    labels = d.get("mode_labels", {})
    plans = []
    for mode, p in (d.get("plans") or {}).items():
        q = p.get("quality") or {}
        plans.append({
            "mode": mode, "label": labels.get(mode, mode), "title": p.get("title", ""),
            "why": p.get("why_today", ""), "story": p.get("story", ""), "moral": p.get("moral", ""),
            "length": p.get("target_length", ""), "credits": (p.get("credits") or {}).get("estimate"),
            "quality": q.get("quality_score"), "warnings": p.get("warnings", []), "fixes": q.get("fixes", []),
            "shots": [{"n": s.get("n"), "location": s.get("location", ""), "action": s.get("action", ""),
                       "characters": s.get("characters", []),
                       "dialogue": [f"{x.get('who', '')}: {x.get('line', '')}" for x in s.get("dialogue", [])]}
                      for s in p.get("shots", [])],
        })
    cal = d.get("calendar") or {}
    return {"date": d.get("date"), "series": (cal.get("series") or {}).get("name", ""),
            "days": [{k: x.get(k) for k in ("day", "date", "type", "mode", "title")} for x in cal.get("days", [])],
            "plans": plans}


def media(path):
    """A generated image/clip from <video project>/daily, or None."""
    f = os.path.realpath(KF / path)
    if not f.startswith(str(KF / "daily") + os.sep) or not os.path.isfile(f):
        return None
    return open(f, "rb").read(), {"png": "image/png", "jpg": "image/jpeg", "mp4": "video/mp4"}.get(f.rsplit(".", 1)[-1], "application/octet-stream")


def today_title(mode):
    return json.load(open(DATA / "latest.json"))["plans"][mode].get("title", mode)


def flow_prompt(mode):
    """What Claude is asked to do for 'Make the video in Flow' (the owner confirms on the phone first)."""
    d = json.load(open(DATA / "latest.json"))
    title = d["plans"][mode].get("title", mode)
    from autopilot import BROWSER
    return (BROWSER + f"In {KFS}: make today's ({d['date']}) '{mode}' video \"{title}\" in Google Flow, from the Studio "
            "flow queue. Read HANDOFF.md, FLOW_RULES.md and PROMPT_BIBLE.md first and follow them exactly: "
            "at most 2 characters per Veo clip, click Approve only (never Always approve), stay within the credit budget. "
            "If this video's queue item is already in_progress, continue from its note and the files in its folder: "
            "never remake clips that already exist. Finish the whole job (clips, Whisper, edit, SEO, post for review) "
            "and update the flow queue status when done." + CREDITS_NOTE)


# A Flow video takes 40+ minutes in Chrome; Claude may only use Chrome, files and Studio's own scripts.
FLOW_ARGS = ("--chrome", "--allowedTools", "mcp__claude-in-chrome__*", "Read", "Write", "Edit", "Glob", "Grep",
             "Bash(python3:*)", "Bash(ls:*)",
             # editing the finished clips (pf_stories / short_v2 / Whisper) needs these too
             f"Bash({VENV}:*)",
             "Bash(PF_ROOT=*)", "Bash(HF_HUB_OFFLINE=1 *)", "Bash(cd:*)", "Bash(ffmpeg:*)", "Bash(ffprobe:*)",
             "Bash(cp:*)", "Bash(mv:*)", "Bash(mkdir:*)", "Bash(unzip:*)",
             "Bash(/opt/homebrew/bin/ffmpeg:*)", "Bash(/opt/homebrew/bin/ffprobe:*)", "Bash(/usr/bin/python3:*)",
             "Bash(head:*)", "Bash(tail:*)", "Bash(grep:*)", "Bash(cat:*)", "Bash(wc:*)", "Bash(which:*)", "Bash(echo:*)",
             "Bash(file:*)", "Bash(du:*)", "Bash(sort:*)", f"Bash(curl -s {STUDIO}/*)")
FLOW_TIMEOUT = 3 * 3600

CHROME_STATE = Path.home() / "Library/Application Support/Google/Chrome/Local State"
FLOW_URL = "https://labs.google/fx/tools/flow"


def chrome_profiles():
    info = json.load(open(CHROME_STATE))["profile"]["info_cache"]
    return {"items": sorted(({"dir": k, "name": v.get("name", k), "email": v.get("user_name", "")} for k, v in info.items()),
                            key=lambda p: p["name"].lower())}


def chrome_open(profile_dir, flow=True):
    """Opens Chrome on the Mac in one of its own profiles (only names from Local State are accepted)."""
    if profile_dir not in json.load(open(CHROME_STATE))["profile"]["info_cache"]:
        raise ValueError("unknown profile")
    subprocess.run(["open", "-na", "Google Chrome", "--args", f"--profile-directory={profile_dir}"] + ([FLOW_URL] if flow else []),
                   timeout=15)
    return {"ok": True}

FLOW_JOB_FILE = Path(__file__).parent / "flow_job.json"
try:
    FLOW_JOB = json.load(open(FLOW_JOB_FILE))   # the video Claude is making now: {"id", "mode", "title", "folder"}
except (OSError, ValueError):
    FLOW_JOB = {}


def save_flow_job():
    json.dump(FLOW_JOB, open(FLOW_JOB_FILE, "w"))


def flow_progress(job):
    """How far the current Create-video job is: shots with image / clip / QC done, Claude's status and its log."""
    if not FLOW_JOB:
        return {"active": False}
    if FLOW_JOB.get("auto") and not FLOW_JOB.get("folder"):   # autopilot: fill in today's job once it has picked one
        import autopilot
        tj = autopilot.today_job()
        if tj and tj.get("folder"):
            FLOW_JOB.update(mode=tj.get("mode", ""), title="Autopilot: " + tj.get("title", ""), folder=tj["folder"]); save_flow_job()
    j = job(FLOW_JOB["id"])
    shots = []
    try:
        shots = call("GET", "pipeline", f"mode={FLOW_JOB['mode']}").get("shots", [])
    except Exception:
        pass
    n = len(shots) or 1
    img = sum(1 for s in shots if s.get("keyframe")); clip = sum(1 for s in shots if s.get("clip")); qc = sum(1 for s in shots if s.get("qc"))
    made = bool(FLOW_JOB.get("folder")) and any(Path(FLOW_JOB["folder"]).glob("**/final*.mp4"))
    if j.get("status") == "done" and not made:   # Claude ended without a video (blocked, nothing queued...)
        j = {**j, "status": "error"}
    pct = 100 if j.get("status") == "done" else min(99, round(100 * (img + clip * 2 + qc) / (4 * n)))
    log = j.get("log", [])
    # stages and time, like the naat videos
    phases = ["Reading the rules", "Keyframe images", "Veo clips in Flow", "Checking every clip", "Editing (voice, music, subtitles)",
              "Short, thumbnail and SEO", "Sent for your OK"]
    done = j.get("status") == "done"
    final = bool(FLOW_JOB.get("folder")) and any(Path(FLOW_JOB["folder"]).glob("**/final*.mp4"))
    flags = [bool(log), img >= n, clip >= n, qc >= n or final, final, final and done, done]
    cur = next((i for i, f in enumerate(flags) if not f), len(phases))
    elapsed = (time.time() - j.get("started", time.time())) / 60
    typical = 25 + n * 7
    left = 0 if done else (max(0, round(elapsed / pct * (100 - pct))) if pct >= 15 else max(5, round(typical - elapsed)))
    words = [l[6:] for l in log if not l[6:].startswith("▸")]
    limit = "hit your" in (j.get("result") or "") and "limit" in (j.get("result") or "")
    return {"active": True, "title": FLOW_JOB["title"], "mode": FLOW_JOB["mode"], "status": j.get("status", "?"),
            "phases": phases, "phase_no": cur + 1, "phase": phases[min(cur, len(phases) - 1)], "elapsed_min": round(elapsed),
            "left_min": left, "credits_used": clip * 10, "budget": 60, "running": j.get("status") == "running",
            "limit": limit, "now_words": words[-1] if words else "",
            "pct": pct, "shots": len(shots), "images": img, "clips": clip, "qc": qc,
            "now": log[-1][6:] if log else "Starting Claude…", "denied": suggest_rules(j.get("denied", [])),
            "folder": FLOW_JOB.get("folder", ""), "minutes": round((time.time() - j.get("started", time.time())) / 60),
            "result": j.get("result", ""), "log": log[-120:]}


def open_folder(path):
    """Shows a video's folder in Finder on the Mac (only folders inside <video project>/daily)."""
    f = os.path.realpath(path)
    if not f.startswith(str(KF / "daily") + os.sep) or not os.path.isdir(f):
        raise ValueError("folder not found")
    subprocess.run(["open", f], timeout=10)
    return {"ok": True}


# Commands the owner allowed from the phone after a run stopped for permission (kept for later runs).
ALLOW_FILE = Path(__file__).parent / "studio_allow.json"
RULE = re.compile(r"^Bash\([^()`$;&|<>]{1,200}\)$")


def allowed_extra():
    try:
        return json.load(open(ALLOW_FILE))
    except (OSError, ValueError):
        return []


def flow_args():
    """Full control for the video run (no permission stops), with the same blocked list as the Mac agent."""
    from assistant import FULL_CONTROL
    return ("--chrome", *FULL_CONTROL)


def suggest_rules(commands):
    """Turns denied shell commands into 'Bash(<program>:*)' rules the owner can allow."""
    rules = []
    for c in commands:
        for part in re.split(r"&&|\|\||;|\||\n", c or ""):
            words = [w for w in part.strip().split() if not re.match(r"^[A-Z_][A-Z0-9_]*=", w)]
            while words and words[0] in ("then", "else", "do", "!", "{", "time"):
                words = words[1:]
            if words and re.match(r"^[\w./~+-]+$", words[0]) and words[0] not in ("cd", "if", "then", "else", "elif", "fi", "for", "do", "done", "while", "case", "esac", "{", "}", "[", "[[", "!", "true", "false"):
                r = f"Bash({words[0]}:*)"
                if r not in rules and r not in FLOW_ARGS and r not in allowed_extra():
                    rules.append(r)
    return rules


def allow(rules):
    bad = [r for r in rules if not RULE.match(r)]
    if bad:
        raise ValueError(f"not allowed: {bad}")
    keep = allowed_extra()
    keep += [r for r in rules if r not in keep]
    json.dump(keep, open(ALLOW_FILE, "w"), indent=1)
    return keep


def resume_prompt(folder, title):
    from autopilot import BROWSER
    return (BROWSER + f"In {KFS}: finish the video \"{title}\" in {folder}. Its Flow clips are already made: never remake them "
            "or spend Flow credits. Read HANDOFF.md, FLOW_RULES.md and the queue note, then do every remaining step "
            "(copy clips, Whisper, edit, SEO, post to Discord for the owner's approval) and update the flow queue status. "
            "Use one simple command per Bash call (no && chains), with absolute paths." + CREDITS_NOTE)


CREDITS = DATA / "flow_credits.json"  # {"left", "checked", "source"}; written after each Flow run and by "Check now"
CREDITS_NOTE = (" When you finish, write the Flow credits left (from Flow's PRO/credits menu) to "
                + str(CREDITS) + " as {\"left\": N, \"checked\": \"YYYY-MM-DD HH:MM\", \"source\": \"flow\"}.")


def credits():
    """Flow credits left: the saved check, or the newest 'N left' in the flow queue notes."""
    best = None
    try:
        c = json.load(open(CREDITS))
        best = {"left": int(c["left"]), "checked": c.get("checked", ""), "source": c.get("source", "flow")}
    except (OSError, ValueError, KeyError):
        pass
    try:
        q = json.load(open(DATA / "flow_queue.json"))
        for it in sorted(q, key=lambda i: i.get("finished") or ""):
            m = re.search(r"(\d{2,4}) (?:credits )?left", it.get("note") or "")
            when = (it.get("finished") or "").replace("T", " ")
            if m and (best is None or when > best["checked"]):
                best = {"left": int(m.group(1)), "checked": when, "source": "queue note"}
    except (OSError, ValueError):
        pass
    return {"left": best["left"] if best else None, "checked": best["checked"] if best else "", "source": best["source"] if best else "",
            "monthly": 1000, "per_video": 60}


def credits_prompt():
    from autopilot import BROWSER
    return (BROWSER + "Open Google Flow (https://labs.google/fx/tools/flow) in Chrome with Claude in Chrome, find how many AI credits are "
            "left (the PRO / credits menu), without creating anything or spending credits. Then write it to "
            + str(CREDITS) + " as {\"left\": N, \"checked\": \"YYYY-MM-DD HH:MM\", \"source\": \"flow\"} "
            "(local time) and reply with just the number.")
