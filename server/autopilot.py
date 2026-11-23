"""Autopilot day: every day at the owner's time, make sure today's YouTube video gets made even if the Claude app is
closed. Research (7:00, launchd) -> Flow video (same SKILL.md as the Claude app's 08:30 task) -> Discord/app approval ->
upload by the approvals flow. Never runs twice in a day: it skips when today's video is already started or done."""
import datetime
import json
import subprocess
import threading
import time
from pathlib import Path

from config import CFG, chrome_desc
import config

KF = config.path(CFG["youtube"]["factory_dir"])   # your video project (see docs/SETUP.md, YouTube autopilot)
DATA = KF / "studio" / "data"
SKILL = config.path(CFG["youtube"]["skill_file"])
CHANNEL = CFG["youtube"]["channel_name"] or "YouTube channel"
CFG_FILE = Path(__file__).parent / "autopilot.json"
STATE = Path(__file__).parent / "autopilot_state.json"


def settings():
    try:
        return {"on": False, "time": "08:45", "credits": 60, **json.load(open(CFG_FILE))}
    except (OSError, ValueError):
        return {"on": False, "time": "08:45", "credits": 60}


def save(on, t, credits):
    if len(t) != 5 or t[2] != ":":
        raise ValueError("time must be HH:MM")
    json.dump({"on": bool(on), "time": t, "credits": max(10, min(400, int(credits)))}, open(CFG_FILE, "w"))
    return status()


def _state():
    try:
        return json.load(open(STATE))
    except (OSError, ValueError):
        return {}


def _queue():
    try:
        return json.load(open(DATA / "flow_queue.json"))
    except (OSError, ValueError):
        return []


def _today():
    return datetime.date.today().isoformat()


def today_job():
    """Today's video in the Flow queue: started today, or queued for today."""
    t = _today()
    q = _queue()
    return next((j for j in reversed(q) if (j.get("started") or "").startswith(t)), None) or \
        next((j for j in reversed(q) if j.get("date") == t and j.get("status") in ("queued", "in_progress")), None)


def status():
    t = _today()
    s = settings()
    st = _state()
    job = today_job()
    try:
        pending = json.load(open(KF / "uploads" / "pending.json"))
    except (OSError, ValueError):
        pending = []
    waiting = [p["title"] for p in pending if p.get("status") == "pending" and (p.get("posted") or "").startswith(t)]
    uploaded = [{"title": p["title"], "privacy": p.get("privacy", "")} for p in pending
                if p.get("status") == "uploaded" and (p.get("uploaded") or "").startswith(t)]
    research = (DATA / f"{t}.json").exists()
    video = "not started"
    if job:
        video = {"queued": "queued", "in_progress": "making now", "done": "made", "failed": "failed", "skipped": "skipped"}.get(job["status"], job["status"])
    steps = [{"name": "Research picks today's video", "done": research, "now": _research_running()},
             {"name": "Video made in Flow", "done": bool(job and job["status"] == "done"), "now": video == "making now"},
             {"name": "Waiting for your OK", "done": bool(uploaded), "now": bool(waiting)},
             {"name": "Uploaded / scheduled on YouTube", "done": bool(uploaded)}]
    return {"settings": s, "today": t, "research": research, "video": video, "title": job.get("title", "") if job else "",
            "note": job.get("note", "") if job else "", "waiting": waiting, "uploaded": uploaded, "steps": steps,
            "last_run": st.get("last_run", ""), "last_result": st.get("last_result", "")}


def _research_running():
    """True while Studio's research (07:00 or at login) is still working: its lock file holds a live process id."""
    import os
    try:
        pid = int((DATA / ".research.lock").read_text().strip())
        os.kill(pid, 0)
        return True
    except (OSError, ValueError):
        return False


from videorules import NOTE as _RULES
BROWSER = _RULES + "BROWSER CHOICE (the owner decided this in advance, so do not stop to ask): if more than one browser is connected to Claude in Chrome, call list_connected_browsers and select_browser the one on THIS Mac (onThisComputer / isLocal) running " + chrome_desc() + " (signed in to Google Flow); if several are local, use the one that has a Google Flow tab or is in use. "


def _prompt(credits):
    return (f"Follow {SKILL} exactly, start to finish, for today's {CHANNEL} video. " + BROWSER +
            "In STEP 0.5 run  python3 " + str(KF) + "/studio/agent.py --next-flow-job --fallback  (with --fallback): if nothing "
            "is queued it picks today's best fresh plan, because the owner wants one video every day. "
            f"Today's Flow credit limit is {credits} credits (this replaces the 60-credit limit written in that file; still "
            "stop if Flow shows under 50 credits left). The owner approves in Discord or in their phone app before upload; never "
            "upload yourself. Report progress with agent.py --flow-progress as the file says.")


def _go(start_job, notify):
    """Wait for the research, then start today's video (also resumed after a server restart)."""
    # 1) a research already running (e.g. it started at login): wait for it, up to 2 hours
    waited = 0
    while _research_running() and waited < 120:
        time.sleep(60); waited += 1
    # 2) no research today at all (Mac was off at 07:00): run it now
    if not (DATA / f"{_today()}.json").exists():
        subprocess.run(["/opt/homebrew/bin/python3", str(KF / "studio" / "agent.py"), "--if-missing"], timeout=7200)
    j = today_job()
    if j and j["status"] in ("in_progress", "done"):
        return
    jid = start_job(_prompt(settings()["credits"]))
    json.dump({**_state(), "last_run": time.strftime("%Y-%m-%d %H:%M"), "last_result": "started", "job": jid}, open(STATE, "w"))
    notify.push("video", "🤖 Autopilot started today's video", "Research → Flow → edit. It will wait for your OK.", "Studio")


def run(start_job, notify, force=False):
    """Start today's video now unless it is already started/done today."""
    job = today_job()
    st = _state()
    if st.get("last_result", "").startswith("waiting") and st.get("last_run", "").startswith(_today()) and not force:
        return {"ok": False, "skipped": "Autopilot is already waiting for today's research, then it makes the video"}
    if job and job["status"] in ("in_progress", "done") and not force:
        msg = f"Today's video is already {('being made' if job['status'] == 'in_progress' else 'made')}: {job.get('title', '')}"
        json.dump({**_state(), "last_run": time.strftime("%Y-%m-%d %H:%M"), "last_result": msg}, open(STATE, "w"))
        return {"ok": False, "skipped": msg}
    import credits
    import assistant
    ok, why, _ = credits.check(settings()["credits"], assistant.job)
    if not ok:
        json.dump({**_state(), "last_run": time.strftime("%Y-%m-%d %H:%M"), "last_result": why}, open(STATE, "w"))
        notify.push("system", "🤖 Autopilot skipped today", why, "Studio")
        return {"ok": False, "skipped": why}
    waiting = _research_running()
    json.dump({**_state(), "last_run": time.strftime("%Y-%m-%d %H:%M"),
               "last_result": "waiting for today's research to finish" if waiting else "starting"}, open(STATE, "w"))
    threading.Thread(target=_go, args=(start_job, notify), daemon=True).start()
    return {"ok": True, "waiting_for_research": waiting}


def start(start_job, notify):
    if not STATE.exists():   # first install: never start a video by itself on the same day
        json.dump({"day": _today()}, open(STATE, "w"))
    st = _state()
    if st.get("last_result", "").startswith("waiting") and st.get("last_run", "").startswith(_today()):
        print("Autopilot: resuming the wait for today's research", flush=True)
        threading.Thread(target=_go, args=(start_job, notify), daemon=True).start()   # server restarted mid-wait

    def loop():
        while True:
            s = settings()
            now = datetime.datetime.now()
            st = _state()
            if s["on"] and now.strftime("%H:%M") >= s["time"] and now.hour < 20 and st.get("day") != _today():
                json.dump({**st, "day": _today()}, open(STATE, "w"))
                try:
                    run(start_job, notify)
                except Exception as e:
                    notify.push("video", "Autopilot couldn't start", str(e)[:200], "Studio")
            time.sleep(60)
    threading.Thread(target=loop, daemon=True).start()
