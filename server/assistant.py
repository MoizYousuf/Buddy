"""Mac Assistant: turns a spoken request into one Mac action, plus remote screen control.

The fast model only picks a tool and fills its arguments; the tools themselves are fixed code,
so the model can never run arbitrary commands. Actions that change things wait for a confirm tap.
"""
import json
import config
import os
import re
import subprocess
import threading
import time
import uuid
from pathlib import Path

CLAUDE = str(Path.home() / ".local/bin/claude")
SERVER_DIR = str(Path(__file__).resolve().parent)
CLICLICK = "/opt/homebrew/bin/cliclick"
SHOT = Path("/tmp/englishcoach-screen.jpg")

TOOLS = {
    "open_app":    "open a Mac app. args: {\"name\": \"Safari\"}",
    "open_url":    "open a website in the browser. args: {\"url\": \"https://...\"}",
    "web_search":  "search Google in the browser. args: {\"query\": \"...\"}",
    "volume":      "set volume 0-100. args: {\"level\": 40}",
    "mute":        "mute or unmute. args: {\"on\": true}",
    "lock":        "turn off the Mac's display. args: {}",
    "reminders":   "read today's reminders. args: {}",
    "add_reminder": "add a reminder. args: {\"title\": \"...\"}",
    "calendar":    "read today's calendar events. args: {}",
    "mac_status":  "disk, memory, CPU. args: {}",
    "ask_claude":  "give a bigger task to Claude (code, research, files, writing). args: {\"prompt\": \"...\"}",
    "type_text":   "type text on the Mac keyboard. args: {\"text\": \"...\"}",
    "chat":        "just answer, no action. args: {}",
}
NEEDS_CONFIRM = {"ask_claude", "type_text", "lock", "add_reminder"}

SYSTEM = """You control the user's Mac mini. Pick exactly ONE tool for their request.
The user may speak English, Urdu or Roman Urdu. Reply language: {lang}.
Tools:
{tools}
Reply with ONLY JSON: {{"tool": "<name>", "args": {{...}}, "reply": "one short sentence saying what you will do (or the answer, for chat)"}}"""

jobs = {}      # id -> {"status", "prompt", "result", "started"}
pending = {}   # id -> {"tool", "args"} waiting for the user's confirm
_lock = threading.Lock()


def _run(*cmd, timeout=20):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def _osa(script):
    r = _run("osascript", "-e", script)
    return (r.stdout or r.stderr).strip()


def plan(chat, text, lang):
    """Ask the fast model which tool fits. `chat` is the server's Ollama helper."""
    system = SYSTEM.format(lang="Roman Urdu" if lang == "roman" else "English",
                           tools="\n".join(f"- {k}: {v}" for k, v in TOOLS.items()))
    out = chat([{"role": "system", "content": system}, {"role": "user", "content": text}])
    tool = out.get("tool") if out.get("tool") in TOOLS else "chat"
    return tool, out.get("args") or {}, out.get("reply", "")


def handle(chat, text, lang):
    tool, args, reply = plan(chat, text, lang)
    if tool in NEEDS_CONFIRM:
        pid = uuid.uuid4().hex[:10]
        with _lock:
            pending[pid] = {"tool": tool, "args": args}
        return {"reply": reply, "tool": tool, "args": args, "confirm_id": pid}
    return {"reply": reply, "tool": tool, "args": args, **execute(tool, args)}


def confirm(pid):
    with _lock:
        p = pending.pop(pid, None)
    if not p:
        return {"error": "expired"}
    return {"tool": p["tool"], **execute(p["tool"], p["args"])}


def execute(tool, args):
    try:
        if tool == "open_app":
            r = _run("open", "-a", str(args.get("name", "")))
            return {"result": "Opened" if r.returncode == 0 else r.stderr.strip()}
        if tool == "open_url":
            url = str(args.get("url", ""))
            if not re.match(r"^https?://", url):
                url = "https://" + url
            _run("open", url)
            return {"result": "Opened " + url}
        if tool == "web_search":
            from urllib.parse import quote_plus
            _run("open", "https://www.google.com/search?q=" + quote_plus(str(args.get("query", ""))))
            return {"result": "Searching"}
        if tool == "volume":
            lvl = max(0, min(100, int(args.get("level", 50))))
            _osa(f"set volume output volume {lvl}")
            return {"result": f"Volume {lvl}%"}
        if tool == "mute":
            _osa(f"set volume output muted {'true' if args.get('on', True) else 'false'}")
            return {"result": "Done"}
        if tool == "lock":
            _run("pmset", "displaysleepnow")
            return {"result": "Display off"}
        if tool == "reminders":
            out = _osa('tell application "Reminders" to get name of (reminders whose completed is false)')
            return {"result": out or "No open reminders"}
        if tool == "add_reminder":
            title = str(args.get("title", "")).replace('"', "'")
            _osa(f'tell application "Reminders" to make new reminder with properties {{name:"{title}"}}')
            return {"result": "Added: " + title}
        if tool == "calendar":
            out = _osa('set d to current date\nset s to d - (time of d)\nset e to s + 86400\n'
                       'tell application "Calendar" to get summary of (every event of every calendar whose start date ≥ s and start date < e)')
            return {"result": out or "Nothing on the calendar today"}
        if tool == "type_text":
            r = _run(CLICLICK, "t:" + str(args.get("text", "")))
            return {"result": "Typed" if r.returncode == 0 else r.stderr.strip()}
        if tool == "ask_claude":
            return {"job_id": start_claude(str(args.get("prompt", "")))}
        return {}
    except Exception as e:
        return {"result": f"Failed: {e}"}


def start_claude(prompt, cwd=None, args=(), timeout=900):
    jid = uuid.uuid4().hex[:10]
    with _lock:
        jobs[jid] = {"status": "running", "prompt": prompt, "result": "", "started": time.time()}

    def work():
        try:
            r = subprocess.run([CLAUDE, "-p", prompt, *args], capture_output=True, text=True, timeout=timeout, cwd=str(cwd or Path.home()))
            res = {"status": "done", "result": (r.stdout or r.stderr).strip()}
        except Exception as e:
            res = {"status": "error", "result": str(e)}
        with _lock:
            jobs[jid].update(res)
    threading.Thread(target=work, daemon=True).start()
    return jid


JOBS_DIR = Path(__file__).parent / "jobs"   # one <id>.json (what) + <id>.jsonl (Claude's output) per long job


def _alive(pid):
    try:
        os.kill(pid, 0); return True
    except OSError:
        return False


def start_claude_live(prompt, cwd, args, timeout, extra=None):
    """Runs Claude as its own process (it keeps going if this server restarts) and keeps a readable log of what it is
    doing (jobs[id]["log"]). Output goes to jobs/<id>.jsonl, which a reader thread follows."""
    JOBS_DIR.mkdir(exist_ok=True)
    jid = uuid.uuid4().hex[:10]
    out = open(JOBS_DIR / f"{jid}.jsonl", "w")
    p = subprocess.Popen([CLAUDE, "-p", prompt, "--output-format", "stream-json", "--verbose", *args],
                         stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, text=True, cwd=str(cwd),
                         start_new_session=True,
                         env={**os.environ, "PATH": "/opt/homebrew/bin:/usr/local/bin:" + os.environ.get("PATH", "/usr/bin:/bin")})
    out.close()
    meta = {"pid": p.pid, "prompt": prompt, "started": time.time(), "timeout": timeout, "extra": extra or {},
            "cwd": str(cwd), "args": [a for a in args if a != "--resume"] if "--resume" not in args else
            list(args[args.index("--resume") + 2:])}
    json.dump(meta, open(JOBS_DIR / f"{jid}.json", "w"))
    threading.Thread(target=lambda: p.wait(), daemon=True).start()   # reap it when it ends
    _follow(jid, meta)
    return jid


def _follow(jid, meta):
    with _lock:
        jobs[jid] = {"status": "running", "prompt": meta["prompt"], "result": "", "started": meta["started"], "log": [],
                     **meta.get("extra", {})}

    def note(line, t=None):
        with _lock:
            jobs[jid]["log"] = (jobs[jid]["log"] + [time.strftime("%H:%M ", time.localtime(t)) + line[:300]])[-300:]

    def work():
        path = JOBS_DIR / f"{jid}.jsonl"
        end = meta["started"] + meta.get("timeout", 3600)
        result, ok, buf = "", None, ""
        with open(path) as f:
            while True:
                chunk = f.readline()
                if chunk:
                    buf += chunk
                    if not buf.endswith("\n"):
                        continue
                    raw, buf = buf, ""
                    try:
                        ev = json.loads(raw)
                    except ValueError:
                        continue
                    if ev.get("type") == "system" and ev.get("session_id"):
                        with _lock:
                            jobs[jid]["session"] = ev["session_id"]
                        if meta.get("session") != ev["session_id"]:
                            meta["session"] = ev["session_id"]
                            json.dump(meta, open(JOBS_DIR / f"{jid}.json", "w"))
                    if ev.get("type") == "assistant":
                        for c in ev.get("message", {}).get("content", []):
                            if c.get("type") == "text" and c["text"].strip():
                                note(c["text"].strip().replace("\n", " "))
                            elif c.get("type") == "tool_use":
                                inp = c.get("input", {})
                                what = inp.get("action") or inp.get("url") or inp.get("command") or inp.get("file_path") or ""
                                note(f"▸ {c['name'].split('__')[-1]} {str(what)[:120]}")
                    elif ev.get("type") == "result":
                        result, ok = ev.get("result", ""), not ev.get("is_error", False)
                        with _lock:
                            jobs[jid]["denied"] = [d.get("tool_input", {}).get("command") or d.get("tool_name", "")
                                                   for d in ev.get("permission_denials", [])]
                    continue
                if not _alive(meta["pid"]):
                    break
                if time.time() > end:
                    try:
                        os.killpg(meta["pid"], 9)
                    except OSError:
                        pass
                    note("Stopped: took too long"); ok = False
                    break
                time.sleep(0.5)
        res = {"status": "done" if ok else "error", "result": result.strip() or ("" if ok else "Claude stopped")}
        with _lock:
            jobs[jid].update(res)
            done = dict(jobs[jid])
        meta["final"] = res
        json.dump(meta, open(JOBS_DIR / f"{jid}.json", "w"))
        if not meta.get("announced"):
            _announce(jid, done)
    threading.Thread(target=work, daemon=True).start()


def _session_of(jid):
    """Claude's session id for a job (from its meta, or the first lines of its output)."""
    try:
        m = json.load(open(JOBS_DIR / f"{jid}.json"))
        if m.get("session"):
            return m["session"], m
        for line in open(JOBS_DIR / f"{jid}.jsonl"):
            if '"session_id"' in line:
                return json.loads(line).get("session_id"), m
        return None, m
    except (OSError, ValueError):
        return None, {}


def find_job(text):
    """The newest job started FOR `text` (e.g. a video's folder named in its prompt); otherwise the newest whose own
    work (tool calls) touched it."""
    metas = sorted(JOBS_DIR.glob("*.json"), key=lambda p: -p.stat().st_mtime)
    for m in metas:
        try:
            if text in json.load(open(m)).get("prompt", ""):
                return m.stem
        except (OSError, ValueError):
            continue
    for m in metas:
        try:
            out = (JOBS_DIR / f"{m.stem}.jsonl").read_text()
            if f"mkdir -p {text}" in out or f"/{text}/clips" in out or f"/{text}/kf" in out:
                return m.stem
        except OSError:
            continue
    return None


def reply(jid, text, kind=None):
    """The owner answers a job from the phone: Claude continues THAT session (it remembers everything it did)."""
    session, m = _session_of(jid)
    if not m:
        raise ValueError("unknown job")
    args = m.get("args") or ["--chrome", *FULL_CONTROL]
    cwd = m.get("cwd") or config.KFS
    extra = {**m.get("extra", {}), **({"kind": kind} if kind else {}), "reply_to": jid}
    msg = (f"The owner answers you from their phone: \"{text}\"\nDo what they say. Continue the same work from where you "
           "stopped, reuse everything already made, and keep following the same rules and files as before.")
    if session:
        return start_claude_live(msg, cwd, ["--resume", session, *args], m.get("timeout", 3 * 3600), extra)
    return start_claude_live(m["prompt"] + "\n\n" + msg, cwd, args, m.get("timeout", 3 * 3600), extra)


def resume_jobs():
    """After a server restart: load finished jobs from the last day and follow the ones still running."""
    if not JOBS_DIR.exists():
        return
    for m in sorted(JOBS_DIR.glob("*.json")):
        try:
            meta = json.load(open(m))
        except (OSError, ValueError):
            continue
        jid = m.stem
        if meta.get("final"):
            if time.time() - meta["started"] < 86400:
                with _lock:
                    jobs[jid] = {"prompt": meta["prompt"], "started": meta["started"], "log": [], **meta.get("extra", {}), **meta["final"]}
            continue
        if _alive(meta["pid"]) or (JOBS_DIR / f"{jid}.jsonl").exists():
            _follow(jid, meta)   # re-reads its whole output, then keeps following


def _announce(jid, j):
    """Tells the phone a long job finished (and if it stopped for permission)."""
    import notify
    from studio import suggest_rules
    denied = suggest_rules(j.get("denied", []))
    short = (j.get("result") or "").strip().replace("\n", " ")[:300]
    if j.get("kind") == "agent":
        notify.push("permission" if denied else "agent", "Mac agent needs permission" if denied else "Mac agent finished",
                    short or j.get("prompt", "")[:200], "Assistant")
    elif j.get("kind") == "flow":
        if denied:
            notify.push("permission", "Video stopped: needs your permission", ", ".join(denied), "Studio",
                        {"label": "Allow & continue", "path": "/studio/flow_retry", "body": {"allow": denied}})
        else:
            notify.push("video", "Video finished" if j["status"] == "done" else "Video stopped", short, "Studio")


def job(jid):
    with _lock:
        return dict(jobs.get(jid, {}))


def job_list():
    with _lock:
        return sorted(({"id": k, **{x: y for x, y in v.items() if x != "log"}} for k, v in jobs.items()), key=lambda j: -j["started"])[:20]


# ---------- Mac agent: Claude with tools, memory of the chat, and owner-approved permissions ----------

AGENT_DIR = Path.home()
AGENT_ALLOW = Path(__file__).parent / "agent_allow.json"
AGENT_TOOLS = ("Read", "Glob", "Grep", "WebSearch", "WebFetch", "Write", "Edit", "TodoWrite", "mcp__claude-in-chrome__*",
               "Bash(open:*)", "Bash(osascript:*)", "Bash(/opt/homebrew/bin/cliclick:*)", "Bash(cliclick:*)", "Bash(shortcuts:*)",
               "Bash(ls:*)", "Bash(cat:*)", "Bash(head:*)", "Bash(tail:*)", "Bash(grep:*)", "Bash(find:*)", "Bash(mdfind:*)",
               "Bash(wc:*)", "Bash(du:*)", "Bash(df:*)", "Bash(date:*)", "Bash(cal:*)", "Bash(ps:*)", "Bash(top -l 1:*)",
               "Bash(pmset:*)", "Bash(sw_vers:*)", "Bash(system_profiler:*)", "Bash(vm_stat:*)", "Bash(uptime:*)",
               "Bash(pbcopy:*)", "Bash(pbpaste:*)", "Bash(say:*)", "Bash(screencapture:*)", "Bash(mkdir:*)", "Bash(cp:*)",
               "Bash(mv:*)", "Bash(python3:*)", "Bash(curl -s:*)", "Bash(echo:*)", "Bash(sort:*)", "Bash(file:*)",
               "Bash(networksetup -getairportnetwork:*)", "Bash(ipconfig getifaddr:*)", "Bash(brew list:*)", "Bash(git status:*)",
               "Bash(git log:*)", "Bash(ollama list:*)")
AGENT_SYSTEM = """You are the owner's personal agent on their Mac mini. They message you from their phone, often in Roman Urdu or
English, sometimes by voice (so expect typos). You act; you don't just advise. Use the tools to really do the task end to end:
open apps and sites, use Chrome through Claude in Chrome, read and write files, check the system, search the web, control apps
with osascript and cliclick, then verify it worked. Plan multi-step tasks and keep going until done.
Rules: never delete files or empty trash; never enter passwords, payment details or create accounts; never send messages, emails
or posts without the owner's explicit OK in this chat; never delete Ollama models.
You have full control of the Mac with no permission prompts; a few destructive commands (rm, sudo, diskutil, security...)
are blocked on purpose: to remove files, move them to the Trash with Finder instead.
Speed matters (the owner is often outside on a slow phone link): prefer one quick command over many screenshots.
Their Mac: code projects are in {projects} (list that folder to find one). "Code kholo" = Visual Studio Code (`open -a "Visual Studio Code" <folder>`);
"Cursor" / "Antigravity" are their other editors. "Claude kholo" = the Claude desktop app (`open -a Claude`); to open a chat or
project in it, open the app, then use its sidebar / search (cliclick + osascript) and check with a screenshot.
"Spotlight" = cmd+space then type. Match project names loosely (spelling may be off).
Final reply: short and friendly, written for a phone screen, in {lang}. Say what you did and the result, no long reports."""


# Full control: no permission prompts, except these, which stay blocked even then (destructive or security-sensitive).
BLOCKED = ("Bash(rm:*)", "Bash(srm:*)", "Bash(sudo:*)", "Bash(su:*)", "Bash(diskutil:*)", "Bash(dd:*)", "Bash(mkfs:*)",
           "Bash(security:*)", "Bash(dscl:*)", "Bash(passwd:*)", "Bash(sysadminctl:*)", "Bash(csrutil:*)", "Bash(spctl:*)",
           "Bash(shutdown:*)", "Bash(reboot:*)", "Bash(halt:*)", "Bash(ollama rm:*)", "Bash(git push --force:*)",
           "Bash(git push -f:*)", "Bash(git reset --hard:*)", "Bash(git clean:*)", "Bash(chown:*)", "Bash(launchctl bootout:*)",
           "Bash(launchctl unload:*)", "Bash(launchctl remove:*)", "Bash(defaults delete:*)", "Bash(tmutil delete:*)",
           f"Read({SERVER_DIR}/devices.json)", f"Read({SERVER_DIR}/config.json)", f"Edit({SERVER_DIR}/**)", f"Write({SERVER_DIR}/**)",
           "Read(~/Library/Keychains/**)", "Read(~/.ssh/**)")
FULL_CONTROL = ("--permission-mode", "bypassPermissions", "--disallowedTools", *BLOCKED)


def agent_allowed():
    try:
        return json.load(open(AGENT_ALLOW))
    except (OSError, ValueError):
        return []


def gemini_answer(text, lang="en"):
    """Gemini (Antigravity, free on the AI Pro plan): answers and writes, but can't click or run things on the Mac."""
    agy = Path.home() / ".local" / "bin" / "agy"
    p = (f"Answer the owner's message briefly for a phone screen, in {'Roman Urdu' if lang == 'roman' else 'English'}. "
         f"Don't run any commands. Message: {text}")
    try:
        out = subprocess.run([str(agy), "-p", p, "--print-timeout", "120s"], capture_output=True, text=True, timeout=150).stdout.strip()
    except subprocess.TimeoutExpired:
        out = ""
    return {"answer": out or "Gemini abhi jawab nahi de saka, dobara try karo.", "engine": "gemini"}


def agent(text, lang="en", session=None, allow=(), engine="claude"):
    """Starts one agent turn; `session` continues the earlier chat so Claude remembers it."""
    if engine == "gemini":
        return gemini_answer(text, lang)
    if allow:
        if any(not re.match(r"^Bash\([^()`$;&|<>]{1,200}\)$", r) for r in allow):
            raise ValueError("bad rule")
        keep = agent_allowed(); keep += [r for r in allow if r not in keep]
        json.dump(keep, open(AGENT_ALLOW, "w"), indent=1)
    if session and not re.match(r"^[0-9a-f-]{36}$", session):
        session = None
    args = ["--chrome", "--model", "sonnet", "--append-system-prompt", AGENT_SYSTEM.format(lang="Roman Urdu" if lang == "roman" else "English", projects=config.CFG["projects_dir"]),
            *FULL_CONTROL]
    if session:
        args = ["--resume", session, *args]
    return {"job_id": start_claude_live(text, AGENT_DIR, args, 1800, {"kind": "agent"})}


def agent_job(jid, known=()):
    j = job(jid)
    if not j:
        return {"error": "unknown job"}
    from studio import suggest_rules
    return {"status": j["status"], "session": j.get("session", ""), "result": j.get("result", ""),
            "steps": [l[6:] for l in j.get("log", [])][-40:],
            "denied": [r for r in suggest_rules(j.get("denied", [])) if r not in AGENT_TOOLS and r not in agent_allowed()]}


# ---------- phone as a display: mirror (default) / extend (2nd display) / main (phone only, real screens black) ----------

VDISPLAY = str(Path(__file__).parent / "macvdisplay")
_vd = {"proc": None, "mode": "mirror", "id": None}
screen_seen = {"t": time.time()}   # last time the phone was watching the screen (set by the stream routes)


def display_mode(mode=None, w=1920, h=886):
    if mode is None:
        return _display_state()
    if mode not in ("mirror", "extend", "main"):
        raise ValueError("mode must be mirror, extend or main")
    p = _vd["proc"]
    if p and p.poll() is None:
        p.stdin.close(); p.wait(timeout=5)
    _vd.update(proc=None, mode="mirror", id=None)
    if mode != "mirror":
        w, h = max(800, min(3200, int(w))), max(400, min(2000, int(h)))
        p = subprocess.Popen([VDISPLAY, mode, str(w), str(h)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        line = p.stdout.readline().strip()
        if not line.isdigit():
            raise RuntimeError("macOS didn't create the phone display")
        _vd.update(proc=p, mode=mode, id=int(line))
        screen_seen["t"] = time.time()
        time.sleep(1)
    return _display_state()


def _display_state():
    ds = displays()
    idx = next((i for i, d in enumerate(ds) if d.get("id") == _vd["id"]), None)
    return {"mode": _vd["mode"], "phone_index": idx, "displays": len(ds)}


def _display_guard():
    """Phone-only mode turns the real screens black; if the phone stops watching for 10 minutes, switch back."""
    while True:
        time.sleep(30)
        if _vd["mode"] == "main" and time.time() - screen_seen["t"] > 600:
            try:
                display_mode("mirror")
            except Exception:
                pass


threading.Thread(target=_display_guard, daemon=True).start()


# ---------- keep awake ----------

AWAKE_FILE = Path(__file__).parent / "keep_awake.json"
_awake = {"proc": None}


def _awake_on():
    try:
        return json.load(open(AWAKE_FILE)).get("on", True)
    except (OSError, ValueError):
        return True  # default: the Mac never falls into full sleep, so the phone can always reach it


def keep_awake(on=None):
    """Holds a 'no system sleep' assertion (caffeinate -i -s) while ON; the display may still turn off."""
    if on is not None:
        json.dump({"on": bool(on)}, open(AWAKE_FILE, "w"))
    want = _awake_on()
    p = _awake["proc"]
    if want and (p is None or p.poll() is not None):
        _awake["proc"] = subprocess.Popen(["caffeinate", "-i", "-s"])
    elif not want and p is not None and p.poll() is None:
        p.terminate(); _awake["proc"] = None
    return {"on": want, "wake": wake_info()}


def wake_info():
    """What the phone needs to send Wake-on-LAN packets: every wired/Wi-Fi MAC with its home IP and broadcast."""
    out = []
    for dev in _run("ifconfig", "-l").stdout.split():
        if not dev.startswith("en"):
            continue
        cfg = _run("ifconfig", dev).stdout
        mac = re.search(r"ether ([0-9a-f:]{17})", cfg)
        ip = re.search(r"inet (\d+\.\d+\.\d+\.\d+) netmask \S+ broadcast (\S+)", cfg)
        if mac:
            out.append({"dev": dev, "mac": mac.group(1), "ip": ip.group(1) if ip else "", "broadcast": ip.group(2) if ip else ""})
    import guard
    return {"targets": out, "tailscale": guard.tailscale_ip() or ""}


# ---------- wake / unlock ----------

LOCKED = str(Path(__file__).parent / "maclocked")
UNLOCK_KEY = "englishcoach-unlock"  # Keychain item name (kept from the original app); the owner adds it themself; the password never leaves the Mac


def is_locked():
    return {"locked": _run(LOCKED).stdout.strip() == "locked"}


def lock():
    """Locks the Mac (lock screen, same as Ctrl-Cmd-Q)."""
    _run(LOCKED, "lock")
    time.sleep(1.5)
    ok = is_locked()["locked"]
    return {"ok": ok, "result": "Mac locked" if ok else "Couldn't lock the Mac"}


_unlocking = threading.Lock()


def unlock():
    """Wakes the display and, if the lock screen is up, types the login password stored in this Mac's Keychain.
    Only one attempt runs at a time, so tapping the button again can't type the password twice."""
    if not _unlocking.acquire(blocking=False):
        return {"ok": False, "result": "Already unlocking, wait a moment"}
    try:
        _run("caffeinate", "-u", "-t", "3")
        time.sleep(1.5)
        if _run(LOCKED).stdout.strip() != "locked":
            return {"ok": True, "result": "Screen is on and unlocked"}
        pw = _run("security", "find-generic-password", "-s", UNLOCK_KEY, "-w").stdout.rstrip("\n")
        if not pw:
            return {"ok": False, "setup": True, "result": "Screen is on but locked. Set up unlock once on the Mac (see app)."}
        # wake the field with a harmless key, then type by key code (password goes over stdin, never on a command line)
        subprocess.run([LOCKED, "type"], input="", text=True, capture_output=True, timeout=30)
        time.sleep(1.0)
        subprocess.run([LOCKED, "type"], input=pw, text=True, capture_output=True, timeout=30)
        for _ in range(10):
            time.sleep(0.5)
            if _run(LOCKED).stdout.strip() != "locked":
                return {"ok": True, "result": "Unlocked"}
        return {"ok": False, "result": "Still locked: check the password saved in Keychain"}
    finally:
        _unlocking.release()


# ---------- remote screen ----------

DISPLAYS = str(Path(__file__).parent / "macdisplays")


def displays():
    """Active displays in global top-left points, main display first."""
    try:
        found = json.loads(_run(DISPLAYS).stdout or "[]")
    except ValueError:
        found = []
    if not found:
        w, h = _screen_size()
        found = [{"id": 0, "x": 0, "y": 0, "w": w, "h": h, "main": True}]
    return sorted(found, key=lambda d: not d["main"])


def screen_jpeg(display=0):
    """A fresh, downscaled screenshot of one display (0 = main). Needs Screen Recording permission for Python."""
    r = _run("screencapture", "-x", "-D", str(int(display) + 1), "-t", "jpg", str(SHOT))
    if r.returncode != 0 or not SHOT.exists():
        return None
    _run("sips", "-Z", "1280", "-s", "formatOptions", "55", str(SHOT))
    return SHOT.read_bytes()


def _screen_size():
    out = _osa('tell application "Finder" to get bounds of window of desktop')
    nums = [int(n) for n in re.findall(r"-?\d+", out)]
    return (nums[2], nums[3]) if len(nums) == 4 else (1920, 1080)


SCROLL = str(Path(__file__).parent / "macscroll")
MODS = {"cmd", "alt", "ctrl", "shift", "fn"}
# cliclick's named keys (kp:); anything else that's one character is typed with t:
NAMED_KEYS = {"return", "esc", "tab", "space", "delete", "fwd-delete", "home", "end", "page-up", "page-down",
              "arrow-up", "arrow-down", "arrow-left", "arrow-right", "enter", "mute", "volume-up", "volume-down",
              "brightness-up", "brightness-down", "play-pause", "play-next", "play-previous",
              *[f"f{i}" for i in range(1, 17)]}


_origin = (0, 0)  # top-left of the display being controlled, set per request


def _pt(data, w, h, kx="x", ky="y"):
    return _origin[0] + int(float(data.get(kx, 0)) * w), _origin[1] + int(float(data.get(ky, 0)) * h)


_input_lock = threading.Lock()


def screen_input(data):
    """One event, or {"batch": [events]} sent together by the phone; done strictly one after another."""
    with _input_lock:
        if isinstance(data.get("batch"), list):
            r = {}
            for ev in data["batch"][:200]:
                r = _screen_input_one({**ev, "display": data.get("display", ev.get("display", 0))})
            return r
        return _screen_input_one(data)


def _screen_input_one(data):
    """Mouse, keyboard and scroll on the Mac. Coordinates are 0..1 fractions of the screen.

    kinds: tap/double/right (at x,y) · click/rclick (where the cursor is) · move (x,y) · nudge (dx,dy pixels)
           drag (x,y -> x2,y2) · scroll (dy, dx pixels) · type (text) · key (key, mods) · hold/release (mods)
    """
    ds = displays()
    d = ds[min(int(data.get("display", 0)), len(ds) - 1)]
    w, h = d["w"], d["h"]
    global _origin
    _origin = (d["x"], d["y"])
    kind = data.get("kind")
    mods = ",".join(m for m in data.get("mods", []) if m in MODS)
    if kind in ("tap", "double", "right"):
        x, y = _pt(data, w, h)
        verb = {"tap": "c", "double": "dc", "right": "rc"}[kind]
        r = _run(CLICLICK, f"{verb}:{x},{y}")
    elif kind in ("click", "rclick", "dclick"):
        r = _run(CLICLICK, {"click": "c:.", "rclick": "rc:.", "dclick": "dc:."}[kind])
    elif kind == "move":
        x, y = _pt(data, w, h)
        r = _run(CLICLICK, f"m:{x},{y}")
    elif kind == "nudge":  # trackpad mode: relative mouse movement
        dx, dy = int(float(data.get("dx", 0))), int(float(data.get("dy", 0)))
        r = _run(CLICLICK, f"m:{dx:+d},{dy:+d}")
    elif kind == "drag":
        x1, y1 = _pt(data, w, h)
        x2, y2 = _pt(data, w, h, "x2", "y2")
        r = _run(CLICLICK, f"dd:{x1},{y1}", f"dm:{(x1 + x2) // 2},{(y1 + y2) // 2}", f"du:{x2},{y2}")
    elif kind == "scroll":
        r = _run(SCROLL, str(int(float(data.get("dy", 0)))), str(int(float(data.get("dx", 0)))))
    elif kind == "type":
        r = _run(CLICLICK, "t:" + str(data.get("text", "")))
    elif kind == "key":
        key = str(data.get("key", ""))
        press = f"kp:{key}" if key in NAMED_KEYS else f"t:{key[:1]}"
        r = _run(CLICLICK, *([f"kd:{mods}", press, f"ku:{mods}"] if mods else [press]))
    elif kind in ("down", "dragmove", "up"):  # live drag: press, follow the finger, release
        x, y = _pt(data, w, h)
        verb = {"down": "dd", "dragmove": "dm", "up": "du"}[kind]
        r = _run(CLICLICK, f"{verb}:{x},{y}")
    elif kind == "open_app":
        r = _run("open", "-a", str(data.get("name", "")))
    elif kind == "spot":   # "Spotlight" from the phone: open the app / folder / file whose name matches (works without ⌘Space)
        q = str(data.get("text", "")).strip()
        if not q:
            return {"ok": False, "error": "Kya kholna hai?"}
        import glob as _g
        apps = _g.glob("/Applications/*.app") + _g.glob("/System/Applications/*.app") + _g.glob("/System/Applications/Utilities/*.app") + \
            _g.glob(str(Path.home() / "Applications/*.app"))
        ql = q.lower()
        hit = next((a for a in apps if Path(a).stem.lower() == ql), None) or next((a for a in apps if Path(a).stem.lower().startswith(ql)), None) \
            or next((a for a in apps if ql in Path(a).stem.lower()), None)
        if not hit:
            proj = Path.home() / "Documents" / "projects"
            hit = next((str(p) for p in proj.iterdir() if ql.replace(" ", "") in p.name.lower().replace(" ", "").replace("-", "")), None) if proj.exists() else None
        if not hit:
            m = _run("mdfind", "-name", q, timeout=10).stdout.splitlines()
            m = [x for x in m if "/Library/" not in x and "/." not in x]
            hit = m[0] if m else None
        if not hit:
            return {"ok": False, "error": f"'{q}' Mac par nahi mila"}
        r = _run("open", hit)
        return {"ok": r.returncode == 0, "opened": Path(hit).name, "error": None if r.returncode == 0 else r.stderr[:200]}
    elif kind == "display_sleep":
        r = _run("pmset", "displaysleepnow")
    elif kind == "mac_sleep":
        r = _run("pmset", "sleepnow")
    elif kind in ("hold", "release"):
        r = _run(CLICLICK, f"{'kd' if kind == 'hold' else 'ku'}:{mods}") if mods else None
        return {"ok": True}
    else:
        return {"error": "unknown"}
    return {"ok": r.returncode == 0, "error": r.stderr.strip() or None}
