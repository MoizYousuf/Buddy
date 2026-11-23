"""Buddy server for your Mac (stdlib only): English coach, Mac assistant and phone control.

Fast model (config "models.fast") corrects the sentence and asks the next question.
Explanations come from hand-written templates instantly; when no template fits,
the slow model (config "models.slow") writes one in the background.

  POST /turn     {"session": "s1", "role": "HR manager ...", "text": "...", "lang": "en"|"roman"}
  GET  /explain?id=<id>   -> {"status": "pending"|"ready", ...}

Run: python3 server.py   (listens on this Mac's Tailscale and home-network addresses, port from config.json, default 8787)
"""
import json
import os
import re
import shutil
import subprocess
import threading
import time
import urllib.request
import uuid

import assistant
import notify
import reviews
import brief
import transfer
import routines
import naat
import autopilot
import credits
import videorules
import etsy
import money
import storeauto
import extras4
import ideas
import appassist
etsy.start_watch(lambda *a: notify.push(*a))
money.start_watch(lambda *a: notify.push(*a))
storeauto.start_watch(lambda *a: notify.push(*a))
ideas.nudge(lambda *a: notify.push(*a))
import analytics
import interview
import activity
import cvstudio
import practice
import cleanup
import guard
from config import CFG, owner
import config
import pronounce
import resume
import studio
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

OLLAMA = "http://localhost:11434/api/chat"
FAST, SLOW = CFG["models"]["fast"], CFG["models"]["slow"]  # e4b explains in clean Urdu ~3x faster than gemma3:12b
TEMPLATES = json.loads((Path(__file__).parent / "templates.json").read_text())
CATEGORY_LIST = "\n".join(f"- {k}: {v['label']}" for k, v in TEMPLATES.items())
GENERIC = {"word_choice"}  # only used when nothing more specific matched
# A category is kept only if what the learner said contains one of its trigger words,
# so a mis-tag from the fast model falls through to the AI explanation instead.
TRIGGERS = {"since_for": ["since"], "experience_of": ["experience"], "discuss_about": ["about"],
            "can_able": ["able"], "myself_intro": ["myself"], "belong_from": ["belong"],
            "done_graduation": ["graduation"], "do_the_needful": ["needful", "revert"],
            "having_state": ["having", "knowing", "wanting", "belonging"],
            "present_perfect_continuous": ["since", "for"],
            "past_tense": ["yesterday", "last ", " ago", "earlier", "in 20"]}
PHRASE_RULES = {"do_the_needful": ["do the needful", "revert back"], "discuss_about": ["discuss about"],
                "can_able": ["can able"], "myself_intro": ["myself "], "belong_from": ["belonging from"],
                "done_graduation": ["done my graduation", "done graduation"]}
# Phrases that must never survive into a correction; the fast model is asked again if they do.
BAD_PHRASES = ["revert back", "do the needful", "discuss about", "can able", "am having", "is having",
               "myself ", "belonging from", "since two", "since three", "since one", "have done my graduation",
               "mention about", "return back", "repeat again"]

FAST_SYSTEM = """You are {role}. You are also the candidate's English speaking coach.
The candidate may speak English, Urdu, Roman Urdu or a mix.
Reply with ONLY this JSON:
{{"has_mistake": true/false,
  "correct": "the learner's OWN sentence with the smallest possible fixes",
  "natural": "the SAME meaning said more naturally",
  "categories": ["the most specific ids from the list below; use word_choice only if nothing else fits"],
  "next_question": "your next question in English, following up on their answer"}}
Rules for "correct" and "natural":
- Keep the learner's own words, facts and meaning. Only fix grammar and word choice.
- Do NOT answer the question yourself, do NOT add new details, do NOT write a better answer.
- If they spoke Urdu, "correct" is a direct English translation of what they said.
- If there is no mistake, "correct" is their sentence unchanged.
Example: question "Tell me about yourself", learner "I am working as Android developer since 2021"
-> correct "I have been working as an Android developer since 2021", natural "I've been an Android developer since 2021"
(NOT "I am a passionate developer with strong skills in Kotlin...")
Mistake categories:
{categories}"""

SLOW_SYSTEM = """You explain English mistakes to a Pakistani learner.
Given what they said and the correct sentence, explain the mistake in 1-2 short, simple sentences.
Reply with ONLY this JSON: {"en": "...", "roman": "... in Roman Urdu (Urdu written in English letters, no Punjabi)", "urdu": "... in Urdu script"}"""

sessions = {}      # session id -> chat history for the fast model
explanations = {}  # explanation id -> result dict
lock = threading.Lock()


def chat(model, messages, temperature=0.3, max_tokens=260):
    body = {"model": model, "stream": False, "format": "json", "think": False,
            "keep_alive": "30m", "options": {"temperature": temperature, "num_predict": max_tokens}, "messages": messages}
    req = urllib.request.Request(OLLAMA, json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(json.load(r)["message"]["content"])


def chat_stream(model, messages, on_text, temperature=0.3):
    """Like chat(), but streams: on_text(text_so_far) is called as tokens arrive."""
    body = {"model": model, "stream": True, "format": "json", "think": False,
            "keep_alive": "30m", "options": {"temperature": temperature, "num_predict": 260}, "messages": messages}
    req = urllib.request.Request(OLLAMA, json.dumps(body).encode(), {"Content-Type": "application/json"})
    acc = ""
    with urllib.request.urlopen(req, timeout=300) as r:
        for line in r:
            part = json.loads(line)
            acc += part.get("message", {}).get("content", "")
            on_text(acc)
            if part.get("done"):
                break
    return json.loads(acc)


def _overlap(said, sentence):
    """Share of the learner's words that are still in the sentence (0..1)."""
    w = lambda x: set(re.findall(r"[a-z']+", x.lower().replace("’", "'")))
    a = w(said)
    return len(a & w(sentence)) / len(a) if a else 1.0


def _is_english(text):
    """Mostly Latin letters and few Roman Urdu words: then 'correct' must stay close to what they said."""
    if re.search(r"[\u0600-\u06FF\u0900-\u097F]", text):
        return False
    roman_urdu = {"mujhe", "nahi", "hai", "hain", "main", "mein", "kaise", "kya", "aur", "ke", "ki", "ko", "se", "hoon", "tha", "bhi"}
    words = re.findall(r"[a-z]+", text.lower())
    return not words or sum(x in roman_urdu for x in words) / len(words) < 0.2


def _drop_own_questions(text, sentence):
    """The learner made a statement, so a question inside the correction is the model's own: drop it."""
    if "?" in text:
        return sentence
    kept = [p for p in re.split(r"(?<=[.!?])\s+", sentence.strip()) if not p.endswith("?")]
    return " ".join(kept) if kept else sentence


def explain_slow(eid, said, correct):
    try:
        out = chat(SLOW, [{"role": "system", "content": SLOW_SYSTEM},
                          {"role": "user", "content": f"They said: {said}\nCorrect: {correct}"}])
        result = {"status": "ready", "source": "ai", **{k: out.get(k, "") for k in ("en", "roman", "urdu")}}
    except Exception as e:  # keep the app working even if the slow model fails
        result = {"status": "error", "error": str(e)}
    with lock:
        explanations[eid] = result


def turn(data, emit=None):
    """One learner answer -> correction + next question.

    With emit (streaming), early events go out as soon as the model has written them:
      {"event": "mistake", "has_mistake": bool}  then  {"event": "correct", "text": ...}
    so the app can react and start speaking before the whole answer is ready. The full result follows.
    """
    sid, role, text = data.get("session", "default"), data.get("role", "an HR manager"), data["text"]
    planned = role.startswith(interview.MARK)
    if planned:
        role = role[len(interview.MARK):].strip() or "a hiring manager"
    t0 = time.time()
    with lock:
        history = sessions.setdefault(sid, [])
    system = FAST_SYSTEM.format(role=role, categories=CATEGORY_LIST)
    if data.get("use_resume"):
        system += resume.interviewer_note()
    messages = [{"role": "system", "content": system}, *history[-4:], {"role": "user", "content": text}]
    if emit:
        sent = set()

        def early(acc):
            m = re.search(r'"has_mistake"\s*:\s*(true|false)', acc)
            if m and "mistake" not in sent:
                sent.add("mistake")
                emit({"event": "mistake", "has_mistake": m.group(1) == "true", "seconds": round(time.time() - t0, 1)})
            c = re.search(r'"correct"\s*:\s*"((?:[^"\\]|\\.)*)"', acc)
            if c and "correct" not in sent:
                sent.add("correct")
                sentence = _drop_own_questions(text, json.loads(f'"{c.group(1)}"'))
                own = not _is_english(text) or _overlap(text, sentence) >= 0.5
                if own and not any(p in f" {sentence} ".lower() for p in BAD_PHRASES):  # else wait for the fixed one
                    emit({"event": "correct", "text": sentence, "seconds": round(time.time() - t0, 1)})
        out = chat_stream(FAST, messages, early)
    else:
        out = chat(FAST, messages)
    if _is_english(text) and _overlap(text, out.get("correct", "")) < 0.5:
        messages += [{"role": "assistant", "content": json.dumps(out)},
                     {"role": "user", "content": "Your \"correct\" is not my sentence. Keep MY words and only fix the grammar "
                                                 "of: " + text + " — reply with the same JSON."}]
        out = chat(FAST, messages, temperature=0.1)
        if _overlap(text, out.get("correct", "")) < 0.5:  # still off: show their sentence rather than an invented one
            out["correct"] = text
    if _is_english(text) and _overlap(text, out.get("natural", "")) < 0.3:
        out["natural"] = ""
    bad = [p for p in BAD_PHRASES if p in f" {out.get('correct', '')} {out.get('natural', '')} ".lower()]
    if bad:
        messages += [{"role": "assistant", "content": json.dumps(out)},
                     {"role": "user", "content": f"Your correction still contains {bad}, which is itself a mistake. "
                                                 "Fix it and reply with the same JSON."}]
        out = chat(FAST, messages, temperature=0.1)
    with lock:
        history += [{"role": "user", "content": text}, {"role": "assistant", "content": json.dumps(out)}]
    if planned:   # CV interview: save the answer and ask the next question from the plan, in order
        nxt = interview.system_note(sid, data.get("question", ""), text, out.get("correct", ""))
        if nxt:
            out["next_question"] = nxt

    for k in ("correct", "natural"):
        out[k] = _drop_own_questions(text, out.get(k, ""))
    said = text.lower()
    cats = [c for c in out.get("categories", []) if c in TEMPLATES
            and any(w in said for w in TRIGGERS.get(c, [""]))]
    # Fixed phrases are detected directly, whether or not the model tagged them.
    for c, phrases in PHRASE_RULES.items():
        if c not in cats and any(p in said for p in phrases):
            cats.append(c)
    if any(c not in GENERIC for c in cats):
        cats = [c for c in cats if c not in GENERIC]
    eid = uuid.uuid4().hex[:10]
    if not out.get("has_mistake"):
        explanation = {"status": "ready", "source": "none"}
    elif cats:
        explanation = {"status": "ready", "source": "template",
                       "items": [{k: TEMPLATES[c][k] for k in ("en", "roman", "urdu")} for c in cats]}
    else:
        explanation = {"status": "pending"}
        threading.Thread(target=explain_slow, args=(eid, text, out.get("correct", "")), daemon=True).start()
    with lock:
        explanations.setdefault(eid, explanation)
    return {"explain_id": eid, "explanation": explanation, "seconds": round(time.time() - t0, 1),
            **{k: out.get(k) for k in ("has_mistake", "correct", "natural", "next_question")}, "categories": cats}


def _sh(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=10).stdout


def mac_stats():
    """Disk, memory, CPU and Ollama models, for the app's Mac tab."""
    disk = shutil.disk_usage("/")
    total_mem = int(_sh("sysctl", "-n", "hw.memsize") or 0)
    page = 16384
    vm = dict(re.findall(r"^(.+?):\s+(\d+)\.", _sh("vm_stat"), re.M))
    used_pages = sum(int(vm.get(k, 0)) for k in ("Pages active", "Pages wired down", "Pages occupied by compressor"))
    cpu = re.search(r"CPU usage: ([\d.]+)% user, ([\d.]+)% sys", _sh("top", "-l", "1", "-n", "0"))
    models, loaded = [], []
    try:
        with urllib.request.urlopen("http://localhost:11434/api/tags", timeout=5) as r:
            models = [{"name": m["name"], "bytes": m["size"]} for m in json.load(r)["models"]]
        with urllib.request.urlopen("http://localhost:11434/api/ps", timeout=5) as r:
            loaded = [m["name"] for m in json.load(r)["models"]]
    except Exception:
        pass
    return {
        "chip": _sh("sysctl", "-n", "machdep.cpu.brand_string").strip(),
        "disk_total": disk.total, "disk_used": disk.total - disk.free, "disk_free": disk.free,
        "mem_total": total_mem, "mem_used": used_pages * page,
        "cpu": round(float(cpu[1]) + float(cpu[2]), 1) if cpu else None,
        "models": models, "loaded": loaded,
        "uptime": _sh("uptime").strip(),
    }


def _cv_job(prompt, cwd):
    return assistant.start_claude_live(prompt, cwd, assistant.FULL_CONTROL, 1200, {"kind": "agent"})


def _job_reply(d):
    """Owner's instruction to a running/stopped video job, optionally with extra credits for it."""
    text = (d.get("text") or "").strip()
    extra = max(0, min(300, int(d.get("extra_credits") or 0)))
    folder = d.get("folder") or studio.FLOW_JOB.get("folder", "")
    jid = d.get("job_id") or (assistant.find_job(Path(folder).name) if folder else None) or studio.FLOW_JOB.get("id")
    if not jid:
        raise ValueError("No video job to answer")
    if extra:
        cur = credits.summary(assistant.job)
        credits.allow_extra(cur["extra_today"] + extra)          # today's credit guard
        try:                                                     # the video's own limit (the daily skill reads this)
            qf = studio.DATA / "flow_queue.json"; q = json.load(open(qf))
            for it in q:
                if folder and it.get("folder") == folder:
                    it["credit_limit_today"] = 60 + extra; it["credit_limit_date"] = time.strftime("%Y-%m-%d")
                    if it.get("status") in ("failed", "done", "skipped"):
                        it["status"] = "in_progress"
            json.dump(q, open(qf, "w"), ensure_ascii=False, indent=1)
        except (OSError, ValueError):
            pass
        text += f" (The owner approves up to {extra} extra Flow credits for this video, so its limit is now {60 + extra} today.)"
    new = assistant.reply(jid, text or "Continue.", "flow")
    if folder or studio.FLOW_JOB.get("id") == jid:
        title = studio.FLOW_JOB.get("title") if studio.FLOW_JOB.get("id") == jid else Path(folder).name.split("_", 1)[-1].replace("_", " ")
        studio.FLOW_JOB.clear(); studio.FLOW_JOB.update(id=new, mode="", title=title, folder=folder); studio.save_flow_job()
    return {"job_id": new, "replied_to": jid}


def _etsy_job(prompt, cwd):
    return assistant.start_claude_live(prompt, cwd, ("--chrome", *assistant.FULL_CONTROL), 1800, {"kind": "design"})


def _guard(need):
    """None when the credit guard allows `need` credits today, else an error answer for the phone."""
    ok, why, _ = credits.check(need, assistant.job)
    return None if ok else {"error": why, "credit_guard": True}


def _autopilot_job(prompt):
    jid = assistant.start_claude_live(prompt, studio.KF, ("--chrome", *assistant.FULL_CONTROL), 4 * 3600, {"kind": "flow"})
    studio.FLOW_JOB.clear()
    studio.FLOW_JOB.update(id=jid, mode="", title="Autopilot: today's video", folder="", auto=True)
    studio.save_flow_job()
    return jid


# Pages answered without a signature: health check, English-practice explanations, the pairing check, basic Mac stats,
# and review videos (those need their own signed link key).
PUBLIC_GET = {"/health", "/explain", "/paired", "/stats", "/review/file"}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _signed(self, body=b""):
        return guard.authorized(self.client_address[0], self.headers, self.command, self.path, body)

    def _guarded(self, body=b""):
        """True if the request is signed by a paired device of mine; otherwise answers 403 and returns False.
        Every allowed action (and every blocked attempt) goes into the activity log.
        A request is checked once: its one-time nonce would fail a second check as a replay."""
        if getattr(self, "_verified", False):
            return True
        if self._signed(body):
            self._verified = True
            try:
                activity.log(self.command, self.path, body, self.client_address[0])
            except Exception:
                pass
            return True
        activity.blocked(self.path, self.client_address[0])
        self._send(403, {"error": "Not paired"})
        return False

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/family/paid":   # family page form: secret link + home network only, no app signature
            from urllib.parse import parse_qs as _pq
            k = _pq(urlparse(self.path).query).get("k", [""])[0]
            form = _pq(self.rfile.read(int(self.headers.get("Content-Length", 0) or 0)).decode())
            if not extras4.family_ok(k, self.client_address[0]):
                return self._send(403, {"error": "forbidden"})
            try:
                a = extras4.family_paid(form.get("amount", ["0"])[0], form.get("who", [""])[0])
                notify.push("money", "💵 Ghar se paise aaye?", f"{form.get('who', ['Ghar'])[0] or 'Ghar'}: PKR {a:,.0f} · Money book mein confirm karo", "Money")
                body = "<meta charset=utf-8><meta name=viewport content='width=device-width'><h2 style='font-family:sans-serif;padding:30px'>✅ Shukriya! {owner('Owner')} ko bata diya.</h2>".encode()
            except ValueError as e:
                body = f"<h2>{e}</h2>".encode()
            self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8"); self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body); return
        if path == "/turn/stream":
            data = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            self.send_response(200)
            self.send_header("Content-Type", "application/x-ndjson")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()

            def emit(obj):
                self.wfile.write((json.dumps(obj, ensure_ascii=False) + "\n").encode())
                self.wfile.flush()
            try:
                emit({"event": "done", **turn(data, emit)})
            except Exception as e:
                emit({"event": "error", "error": str(e)})
            return
        if path == "/naat/upload":  # body: the song file from Suno; ?id=&name=
            n = int(self.headers.get("Content-Length", 0))
            if n > 200 * 1024 * 1024:
                return self._send(413, {"error": "Song too big"})
            raw = self.rfile.read(n)
            if not self._guarded(raw):
                return
            q = parse_qs(urlparse(self.path).query)
            try:
                return self._send(200, naat.save_song(q.get("id", [""])[0], q.get("name", [""])[0], raw))
            except Exception as e:
                return self._send(400, {"error": str(e)})
        if path == "/practice/backup":  # body: the phone's progress.json; paired phone only
            raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            if not self._guarded(raw):
                return
            try:
                return self._send(200, practice.save(raw))
            except Exception as e:
                return self._send(400, {"error": str(e)})
        if path == "/files/upload":  # body: the raw file; ?name=<file name>; paired phone only
            n = int(self.headers.get("Content-Length", 0))
            if n > transfer.MAX:
                return self._send(413, {"error": "File too big (1 GB max)"})
            raw = self.rfile.read(n)
            if not self._guarded(raw):
                return
            notify_name = parse_qs(urlparse(self.path).query).get("name", [""])[0]
            return self._send(200, transfer.save(notify_name, raw))
        if path == "/resume":  # body is the CV as a PDF; paired phone only
            raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            if not self._guarded(raw):
                return
            try:
                return self._send(200, resume.upload(raw, lambda m: chat(FAST, m, max_tokens=1200)))
            except Exception as e:
                return self._send(500, {"error": str(e)})
        if path == "/pronounce":  # body: 16 kHz WAV of the learner saying ?target=<sentence>
            audio = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            target = parse_qs(urlparse(self.path).query).get("target", [""])[0]
            try:
                return self._send(200, pronounce.score(audio, target, WHISPER_MODEL))
            except Exception as e:
                return self._send(500, {"error": str(e)})
        if path == "/transcribe":  # body is a 16 kHz mono WAV; ?lang=auto|en|ur
            audio = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            try:
                lang = parse_qs(urlparse(self.path).query).get("lang", ["auto"])[0]
                return self._send(200, transcribe(audio, lang if lang in ("auto", "en", "ur") else "auto"))
            except Exception as e:
                return self._send(500, {"error": str(e)})
        if path.startswith("/studio/"):  # YouTube dashboard; paired phone only
            raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            if not self._guarded(raw):
                return
            try:
                data = json.loads(raw or b"{}")
                if path == "/studio/open_folder":
                    return self._send(200, studio.open_folder(data["folder"]))
                if path == "/studio/chrome_open":
                    return self._send(200, studio.chrome_open(data.get("dir") or CFG["chrome"]["profile_dir"], data.get("flow", True)))
                if path == "/studio/credits_check":
                    jid = assistant.start_claude_live(studio.credits_prompt(), studio.KF, ("--chrome", *assistant.FULL_CONTROL), 600, {"kind": "credits"})
                    return self._send(200, {"job_id": jid})
                if path == "/studio/flow_retry":
                    if not studio.FLOW_JOB:
                        return self._send(200, {"error": "No video to continue"})
                    data = {"allow": data.get("allow", []), "mode": studio.FLOW_JOB.get("mode", ""),
                            "folder": studio.FLOW_JOB.get("folder", ""), "title": studio.FLOW_JOB.get("title", "")}
                    path = "/studio/flow_start"
                if path == "/studio/flow_start":
                    if studio.FLOW_JOB and assistant.job(studio.FLOW_JOB["id"]).get("status") == "running":
                        return self._send(200, {"error": "A video is already being made"})
                    if not data.get("folder") or data.get("allow"):   # finishing an existing video spends no new credits
                        need = int(data.get("credits") or 60)
                        ok, why, _ = credits.check(need, assistant.job)
                        if not ok and not data.get("folder") and not data.get("override"):   # owner can override after the warning
                            return self._send(200, {"error": why, "credit_guard": True})
                    if data.get("allow"):
                        studio.allow(data["allow"])
                    if data.get("folder"):  # finish a video whose clips already exist
                        prompt, title = studio.resume_prompt(data["folder"], data.get("title", "")), data.get("title", "")
                    else:
                        prompt, title = studio.flow_prompt(data["mode"]), studio.today_title(data["mode"])
                    jid = assistant.start_claude_live(prompt, studio.KF, studio.flow_args(), studio.FLOW_TIMEOUT, {"kind": "flow"})
                    studio.FLOW_JOB.clear()
                    studio.FLOW_JOB.update(id=jid, mode=data.get("mode", ""), title=title, folder=data.get("folder", ""))
                    studio.save_flow_job()
                    return self._send(200, {"job_id": jid})
                return self._send(200, studio.call("POST", path[8:], body=data))
            except Exception as e:
                return self._send(500, {"error": str(e)})
        ip = self.client_address[0]
        open_routes = {
            "/turn": turn,
            "/pair/start": lambda d: guard.pair_start(ip),
            "/pair/finish": lambda d: (lambda r: (activity.note("🔑", "A new device paired with the Mac" if r.get("token") else
                                       f"Wrong pairing code tried from {ip}", "security", ip), r)[1])(guard.pair_finish(ip, d.get("code", ""))),
        }
        mac_routes = {
            "/assistant": lambda d: assistant.handle(lambda m: chat(FAST, m), d["text"], d.get("lang", "en")),
            "/assistant/confirm": lambda d: assistant.confirm(d["id"]),
            "/mac/unlock": lambda d: assistant.unlock(),
            "/mac/lock": lambda d: assistant.lock(),
            "/mac/awake": lambda d: assistant.keep_awake(d.get("on")),
            "/mac/display_mode": lambda d: assistant.display_mode(d["mode"], d.get("w", 1920), d.get("h", 886)),
            "/review/choose": lambda d: reviews.choose(d["id"], d["choice"], notify, d.get("days"), d.get("time")),
            "/cv/create": lambda d: cvstudio.create(d.get("link", ""), d.get("text", ""), d.get("note", ""), _cv_job),
            "/cv/save": lambda d: cvstudio.save(d["id"], d["text"]),
            "/cv/restore": lambda d: cvstudio.restore(d["id"], d["version"]),
            "/cv/ai_edit": lambda d: cvstudio.ai_edit(d["id"], d["note"], _cv_job),
            "/interview/feedback": lambda d: interview.start_feedback(d["session"]),
            "/autopilot/settings": lambda d: autopilot.save(d.get("on", True), d.get("time", "08:45"), d.get("credits", 60)),
            "/autopilot/run": lambda d: _guard(autopilot.settings()["credits"]) or autopilot.run(_autopilot_job, notify, force=bool(d.get("force"))),
            "/credits/settings": lambda d: credits.save(d.get("daily_limit"), d.get("renew_day"), d.get("low_alert")),
            "/credits/extra": lambda d: credits.allow_extra(d.get("amount", 60)),
            "/naat/create": lambda d: naat.create(d.get("idea", ""), d.get("style", "vocals"),
                lambda prompt, cwd: assistant.start_claude_live(prompt, cwd, assistant.FULL_CONTROL, 900, {"kind": "agent"}),
                d.get("voice", "kids"), d.get("kids", True)),
            "/naat/revise": lambda d: naat.revise(d["id"], d["note"],
                lambda prompt, cwd: assistant.start_claude_live(prompt, cwd, assistant.FULL_CONTROL, 900, {"kind": "agent"})),
            "/naat/make": lambda d: (None if d.get("override") else _guard(int(d.get("credits", 200)))) or (lambda jid: (naat.mark_making(d["id"], jid, d.get("credits", 200)), {"job_id": jid})[1])(
                assistant.start_claude_live(naat.video_prompt(d["id"], d.get("credits", 200), d.get("shorts", True)),
                                            naat.ROOT / d["id"], ("--chrome", *assistant.FULL_CONTROL), 4 * 3600, {"kind": "flow"})),
            "/routines/save": lambda d: routines.save(d["routines"]),
            "/routines/run": lambda d: routines.run(d["id"], assistant, notify),
            "/clip/set": lambda d: transfer.set_clip(d.get("text", "")),
            "/files/reveal": lambda d: transfer.reveal(d["place"], d.get("sub", ""), d["name"]),
            "/brief/settings": lambda d: brief.save(d["time"], d.get("on", True)),
            "/jobs/reply": lambda d: _job_reply(d),
            "/etsy/search": lambda d: etsy.search(d.get("store", ""), d.get("query")),
            "/etsy/listed": lambda d: etsy.mark_listed(d["id"], d.get("url", "")),
            "/etsy/pack": lambda d: etsy.start_pack(d["id"], d.get("link", "")),
            "/etsy/layers": lambda d: etsy.relayer(d["id"]),
            "/etsy/auto": lambda d: storeauto.auto_make(d["run"], d.get("idea", 0), _etsy_job, assistant.job, lambda *a: notify.push(*a), d.get("prompt")),
            "/etsy/design": lambda d: etsy.design(d["run"], d.get("idea", 0), _etsy_job, d.get("prompt")),
            "/money/scan": lambda d: money.scan(d["image"]),
            "/money/add": lambda d: money.add(d),
            "/money/people": lambda d: money.set_people(d.get("names", [])),
            "/money/recurring": lambda d: money.set_recurring(d.get("items", [])),
            "/money/bill": lambda d: money.add_bill(d["id"], d.get("amount")),
            "/money/parse": lambda d: money.parse(d.get("text", "")),
            "/money/goal": lambda d: money.set_goal(d),
            "/money/advice": lambda d: money.advice(d.get("month", "")),
            "/money/claim": lambda d: money.claim(d.get("ids")),
            "/money/remind": lambda d: money.set_remind(d.get("on", True)),
            "/video/score": lambda d: extras4.video_score(d["id"]),
            "/family/confirm": lambda d: extras4.family_confirm(d["id"], bool(d.get("ok"))),
            "/ideas/add": lambda d: ideas.add(d.get("text", ""), d.get("said_at", ""), lambda *a: notify.push(*a), d.get("local_id", "")),
            "/ideas/change": lambda d: ideas.change(d["id"], d.get("done"), bool(d.get("delete"))),
            "/app/assist": lambda d: appassist.assist(d.get("text", ""), d.get("engine", "claude"),
                lambda p, cwd: assistant.start_claude_live(p, cwd, tuple(assistant.FULL_CONTROL), 3600, {"kind": "agent"}), lambda *a: notify.push(*a)),
            "/money/attach": lambda d: money.attach(d["image"]),
            "/money/smart": lambda d: money.smart(d.get("text", "")),
            "/money/undo": lambda d: [money.delete(i) for i in d.get("ids", [])][-1:] and money.listing() or money.listing(),
            "/money/delete": lambda d: money.delete(d["id"]),
            "/money/budget": lambda d: money.set_budget(d["category"], d.get("amount", 0)),
            "/rules/draft": lambda d: videorules.draft(d.get("problem"), d.get("for", "all")),
            "/rules/add": lambda d: videorules.add(d.get("rules", []), d.get("for", "all")),
            "/rules/change": lambda d: videorules.change(d["id"], d.get("on"), bool(d.get("delete")), d.get("text")),
            "/review/reschedule": lambda d: reviews.reschedule(d["id"], d.get("days"), d.get("time")),
            "/review/schedule": lambda d: reviews.save_schedule(d["per_day"], d["slots"], d.get("auto", True)),
            "/review/best_time": lambda d: {"job_id": assistant.start_claude_live(reviews.best_time_prompt(), reviews.KF,
                                     ("--chrome", *assistant.FULL_CONTROL), 900, {"kind": "best"})},
            "/review/fix": lambda d: {"job_id": assistant.start_claude_live(reviews.fix_prompt(d["id"], d["note"]), reviews.KF,
                                     ("--chrome", *assistant.FULL_CONTROL), 3 * 3600, {"kind": "agent"})},
            "/alerts/clear": lambda d: notify.clear(),
            "/alerts/note": lambda d: {"id": notify.push("system", str(d.get("title", ""))[:120], str(d.get("body", ""))[:400], str(d.get("screen", "")))},
            "/alerts/test": lambda d: {"id": notify.push("system", "Alerts are working ✅", "Your Mac can now notify this phone.", "")},
            "/assistant/agent": lambda d: assistant.agent(d["text"], d.get("lang", "en"), d.get("session"), d.get("allow", []), d.get("engine", "claude")),
            "/screen/input": assistant.screen_input,
            "/cleanup/scan": lambda d: cleanup.start_scan(),
            "/cleanup/trash": lambda d: cleanup.move_to_trash(d.get("paths", [])),
            "/cleanup/decide": lambda d: cleanup.decide(d["path"], d["decision"]),
            "/cleanup/empty_trash": lambda d: cleanup.empty_trash(),
            "/resume/delete": lambda d: resume.delete(),
        }
        if path not in open_routes and path not in mac_routes:
            return self._send(404, {"error": "not found"})
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if path in mac_routes and not self._guarded(raw):
            return
        try:
            data = json.loads(raw or b"{}")
            self._send(200, (open_routes.get(path) or mac_routes[path])(data))
        except Exception as e:
            self._send(500, {"error": str(e)})

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/explain":
            eid = parse_qs(url.query).get("id", [""])[0]
            with lock:
                return self._send(200, explanations.get(eid, {"status": "unknown"}))
        if url.path == "/health":
            return self._send(200, {"ok": True, "app": "Buddy", "owner": CFG["owner_name"], "fast": FAST, "slow": SLOW, "lan": _current.get("home network"), "port": PORT})
        if url.path == "/stats":
            return self._send(200, mac_stats())
        if url.path == "/review/file":  # the phone's player can't sign each range request, so it uses a short-lived token
            q = parse_qs(url.query)
            ip = self.client_address[0]
            f = (guard.ip_allowed(ip) or guard.is_lan(ip)) and reviews.file_for(q.get("t", [""])[0], q.get("id", [""])[0], q.get("what", ["video"])[0])
            if not f:
                return self._send(403, {"error": "forbidden"})
            return self._send_file(f)
        if url.path == "/paired":
            return self._send(200, {"paired": self._signed()})
        if url.path == "/family":
            k = parse_qs(url.query).get("k", [""])[0]
            if not extras4.family_ok(k, self.client_address[0]):
                return self._send(403, {"error": "forbidden"})
            body = extras4.family_html().encode()
            self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8"); self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body); return
        # Default deny: every page needs the paired phone's signature, except these (no private data in them).
        if url.path not in PUBLIC_GET and not self._guarded():
            return
        if url.path.startswith("/studio/"):
            if not self._guarded():
                return
            try:
                if url.path == "/studio/credits":
                    return self._send(200, studio.credits())
                if url.path == "/studio/flow_progress":
                    return self._send(200, studio.flow_progress(assistant.job))
                if url.path == "/studio/chrome_profiles":
                    return self._send(200, studio.chrome_profiles())
                if url.path == "/studio/today":
                    return self._send(200, studio.today())
                if url.path.startswith("/studio/kf/"):
                    m = studio.media(unquote(url.path[11:]))
                    if not m:
                        return self._send(404, {"error": "not found"})
                    self.send_response(200)
                    self.send_header("Content-Type", m[1])
                    self.send_header("Content-Length", str(len(m[0])))
                    self.end_headers()
                    return self.wfile.write(m[0])
                return self._send(200, studio.call("GET", url.path[8:], url.query))
            except Exception as e:
                return self._send(500, {"error": str(e)})
        if url.path == "/displays":
            return self._send(200, {"displays": assistant.displays()}) if self._guarded() else None
        if url.path == "/resume":
            return self._send(200, resume.get())
        if url.path == "/cleanup/review":
            return self._send(200, cleanup.review())
        if url.path == "/cleanup/result":
            return self._send(200, cleanup.result())
        if url.path == "/jobs":
            return self._send(200, {"jobs": assistant.job_list()})
        if url.path == "/review/list":
            return self._send(200, reviews.items())
        if url.path == "/review/uploaded":
            return self._send(200, reviews.uploaded(parse_qs(url.query).get("force", ["0"])[0] == "1"))
        if url.path == "/review/token":
            return self._send(200, reviews.media_token())
        if url.path == "/events":
            q = parse_qs(url.query)
            return self._send(200, notify.since(int(q.get("after", ["0"])[0]), int(q.get("wait", ["25"])[0])))
        if url.path in ("/offline/info", "/offline/model"):   # the on-device model the phone downloads once
            m = Path(__file__).parent / "models" / "qwen2.5-1.5b-q8.task"
            ready = m.exists() and m.stat().st_size > 1_500_000_000
            if url.path == "/offline/info":
                return self._send(200, {"ready": ready, "bytes": m.stat().st_size if m.exists() else 0, "name": "Qwen 2.5 1.5B (q8)"})
            return self._send_file(str(m)) if ready else self._send(404, {"error": "The Mac is still downloading the model"})
        if url.path == "/practice/backup":
            return self._send(200, practice.latest())
        if url.path == "/cv/list":
            return self._send(200, cvstudio.listing())
        if url.path == "/cv/get":
            pid = parse_qs(url.query).get("id", [""])[0]
            cvstudio.refresh_stage(pid, assistant.job)
            g = cvstudio.get(pid)
            j = assistant.job(g["status"].get("job", "")) if g["status"].get("job") else {}
            g["steps"] = [l[6:] for l in j.get("log", [])][-6:]; g["running"] = j.get("status") == "running"
            return self._send(200, g)
        if url.path == "/cv/pdf":
            f = cvstudio.pdf_path(parse_qs(url.query).get("id", [""])[0])
            return self._send_file(f) if f else self._send(404, {"error": "no PDF yet"})
        if url.path.startswith(("/etsy/", "/money/")):
            q = {k: v[0] for k, v in parse_qs(url.query).items()}
            if url.path == "/etsy/packfile":
                f = etsy.pack_file(q.get("id", ""), q.get("name", ""))
                return self._send_file(str(f)) if f else self._send(404, {"error": "no file"})
            if url.path == "/money/pdf":
                try:
                    return self._send_file(str(money.pdf(q.get("month", ""), q.get("person", ""))))
                except (ValueError, OSError, subprocess.SubprocessError) as e:
                    return self._send(200, {"error": str(e)})
            if url.path == "/money/photo":
                f = money.photo_path(q.get("name", ""))
                return self._send_file(str(f)) if f else self._send(404, {"error": "no photo"})
            if url.path == "/etsy/image":
                f = etsy.image_path(q.get("id", ""), q.get("name", ""))
                return self._send_file(str(f)) if f else self._send(404, {"error": "no image"})
            try:
                fn = {"/etsy/trends": lambda: storeauto.trends(), "/money/ideas": lambda: ideas.listing(), "/money/family": lambda: {"key": extras4.family_key(), "pending": extras4.family_pending()},
                      "/money/ytstats": lambda: extras4.yt_stats(), "/money/list": lambda: money.listing(q.get("month", "")), "/money/report": lambda: money.report_text(q.get("month", "")),
                      "/etsy/overview": lambda: etsy.overview(), "/etsy/store": lambda: etsy.store(q.get("name", "")),
                      "/etsy/result": lambda: etsy.result(q.get("id", "")), "/etsy/history": lambda: etsy.history(q.get("store")),
                      "/etsy/designs": lambda: etsy.designs(q.get("store")),
                      "/etsy/pack_info": lambda: etsy.pack_info(q.get("id", "")),
                      "/etsy/phrases": lambda: etsy.buyer_phrases(q.get("store")),
                      "/etsy/ranks": lambda: etsy.rank_history(q.get("store", "")),
                      "/etsy/signals": lambda: etsy.listing_signals(q.get("store", "")),
                      "/etsy/performance": lambda: etsy.performance(),
                      "/etsy/design_status": lambda: etsy.design_status(q.get("id", ""), assistant.job)}[url.path]
            except KeyError:
                return self._send(404, {"error": "not found"})
            try:
                return self._send(200, fn())
            except (ValueError, OSError) as e:
                return self._send(200, {"error": str(e)})
        if url.path == "/rules/list":
            return self._send(200, videorules.listing())
        if url.path == "/activity":
            q = parse_qs(url.query)
            return self._send(200, activity.listing(q.get("kind", [""])[0], int(q.get("limit", ["300"])[0])))
        if url.path == "/interview/plan":
            return self._send(200, interview.plan(parse_qs(url.query).get("refresh", ["0"])[0] == "1"))
        if url.path == "/interview/feedback":
            return self._send(200, interview.get_feedback(parse_qs(url.query).get("session", [""])[0]))
        if url.path == "/analytics":
            return self._send(200, analytics.summary(lambda m: chat(FAST, m), parse_qs(url.query).get("force", ["0"])[0] == "1"))
        if url.path == "/credits":
            return self._send(200, credits.summary(assistant.job))
        if url.path == "/autopilot":
            return self._send(200, autopilot.status())
        if url.path == "/naat":
            return self._send(200, naat.listing())
        if url.path == "/naat/progress":
            return self._send(200, naat.progress(parse_qs(url.query).get("id", [""])[0], assistant.job))
        if url.path == "/naat/get":
            return self._send(200, naat.get(parse_qs(url.query).get("id", [""])[0]))
        if url.path == "/routines":
            return self._send(200, routines.listing())
        if url.path == "/clip":
            return self._send(200, transfer.get_clip())
        if url.path == "/files/list":
            q = parse_qs(url.query)
            return self._send(200, transfer.listing(q.get("place", ["From Phone"])[0], q.get("sub", [""])[0]))
        if url.path == "/files/get":
            q = parse_qs(url.query)
            f = transfer.file_path(q.get("place", [""])[0], q.get("sub", [""])[0], q.get("name", [""])[0])
            return self._send_file(f) if f else self._send(404, {"error": "not found"})
        if url.path == "/brief":
            return self._send(200, {**brief.build(list(assistant.jobs.values())), "settings": brief.settings()})
        if url.path == "/mac/awake":
            return self._send(200, assistant.keep_awake())
        if url.path == "/mac/display_mode":
            return self._send(200, assistant.display_mode())
        if url.path == "/mac/locked":
            return self._send(200, assistant.is_locked())
        if url.path == "/assistant/job":
            return self._send(200, assistant.agent_job(parse_qs(url.query).get("id", [""])[0]))
        if url.path in ("/screen.stream", "/screen.h264", "/screen.audio", "/screen.audio"):
            # live frames until the phone disconnects: JPEG [len][jpeg]..., or H.264 [len][kind][annex-b]... (much less data)
            q = parse_qs(url.query)
            d = max(0, min(8, int(q.get("d", ["0"])[0])))
            w = max(480, min(1920, int(q.get("w", ["1280"])[0])))
            if url.path == "/screen.audio":   # raw PCM 16-bit LE of everything the Mac plays
                rate = 48000 if q.get("rate", ["48000"])[0] == "48000" else 24000
                cmd = [str(Path(__file__).parent / "macaudio"), str(rate), "1" if q.get("ch", ["2"])[0] == "1" else "2"]
            elif url.path == "/screen.h264":
                kbps = max(300, min(8000, int(q.get("kbps", ["1500"])[0])))
                cmd = [str(Path(__file__).parent / "macstream264"), str(d), str(w), str(kbps), "24"]
            else:
                qual = max(0.2, min(0.9, float(q.get("q", ["0.5"])[0])))
                cmd = [str(Path(__file__).parent / "macstream"), str(d), str(w), str(qual), "24"]
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if url.path == "/screen.stream" and q.get("lowlag", ["0"])[0] == "1":
                # slow link (Tailscale / mobile data): always send the NEWEST frame and drop the ones the phone
                # couldn't take yet, so the picture is never minutes behind (fps just goes down instead)
                import struct, threading as _th
                latest = {"f": None, "n": 0, "end": False}
                cv = _th.Condition()

                def reader():
                    try:
                        while True:
                            hdr = p.stdout.read(4)
                            if len(hdr) < 4:
                                break
                            n = struct.unpack(">I", hdr)[0]
                            buf = b""
                            while len(buf) < n:
                                c = p.stdout.read(n - len(buf))
                                if not c:
                                    break
                                buf += c
                            with cv:
                                latest["f"] = hdr + buf; latest["n"] += 1; cv.notify()
                    finally:
                        with cv:
                            latest["end"] = True; cv.notify()
                _th.Thread(target=reader, daemon=True).start()
                sent = 0
                try:
                    while True:
                        with cv:
                            while latest["n"] == sent and not latest["end"]:
                                cv.wait(5)
                            if latest["end"]:
                                break
                            frame, sent = latest["f"], latest["n"]
                        self.wfile.write(frame); self.wfile.flush()
                        assistant.screen_seen["t"] = time.time()
                except OSError:
                    pass
                finally:
                    p.kill()
                return
            try:
                while True:
                    chunk = p.stdout.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk); self.wfile.flush()
                    assistant.screen_seen["t"] = time.time()
            except OSError:
                pass
            finally:
                p.kill()
            return
        if url.path == "/screen.jpg":
            img = assistant.screen_jpeg(int(parse_qs(url.query).get("d", ["0"])[0]))
            if img is None:
                return self._send(503, {"error": "Screen Recording permission needed for Python"})
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(img)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            return self.wfile.write(img)
        self._send(404, {"error": "not found"})

    def _send_file(self, path):
        """Serves a file with HTTP Range support, so a video can start at once and seek."""
        size = os.path.getsize(path)
        import mimetypes
        ctype = mimetypes.guess_type(path)[0] or "application/octet-stream"
        rng = self.headers.get("Range", "")
        start, end = 0, size - 1
        m = re.match(r"bytes=(\d*)-(\d*)", rng)
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                start = int(m.group(1)); end = int(m.group(2)) if m.group(2) else size - 1
            else:
                start = size - int(m.group(2))
            end = min(end, size - 1)
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        with open(path, "rb") as fh:
            fh.seek(start)
            left = end - start + 1
            try:
                while left > 0:
                    chunk = fh.read(min(1 << 20, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk); left -= len(chunk)
            except OSError:
                pass

    def log_message(self, *args):
        pass


PORT = int(os.environ.get("BUDDY_PORT") or CFG["port"])


WHISPER = CFG["whisper"]["cli"]
WHISPER_MODEL = str(config.path(CFG["whisper"]["model"]))


WHISPER_HINT = "Interview practice in English. The speaker may mix in Urdu."
# Phrases Whisper is known to invent on silence or noise (said alone, they are not a real answer).
MADE_UP = {"thank you", "thanks for watching", "thank you for watching", "bye", "you", "okay", "subscribe",
           "please subscribe", "thank you very much", "music", "applause", "silence", "the end"}


def _is_silent(wav):
    """True when a 16-bit PCM WAV has almost no sound (loudest 50 ms window is very quiet)."""
    import array
    try:
        pcm = array.array("h", wav[44:])
    except ValueError:
        return False
    if len(pcm) < 1600:
        return True
    win, best = 800, 0
    for i in range(0, len(pcm) - win, win):
        chunk = pcm[i:i + win]
        rms = (sum(x * x for x in chunk[::4]) / (win // 4)) ** 0.5
        best = max(best, rms)
    return best < 350


def _made_up(text):
    """Text Whisper invented: its own hint echoed back, a lone filler phrase, or bracketed noise tags."""
    t = re.sub(r"[^a-z' ]", " ", text.lower()).split()
    if not t:
        return True
    if " ".join(t) in MADE_UP or re.fullmatch(r"\s*[\[(][^\])]*[\])]\s*", text):
        return True
    hint = set(re.sub(r"[^a-z' ]", " ", WHISPER_HINT.lower()).split())
    share = sum(w in hint for w in t) / len(t)
    return len(t) >= 3 and (share >= 0.6 or ("speaker" in t and share >= 0.35))


def transcribe(wav, lang="auto"):
    """Speech to text with whisper.cpp on the Mac (Metal GPU). lang: auto | en | ur.

    whisper-cli is used per request: loading the quantized model takes ~0.2 s, and it ran faster
    than whisper-server here (2.2 s vs 4 s for a short answer with a fixed language).
    """
    import tempfile
    if _is_silent(wav):                       # nothing said: don't let Whisper make something up
        return {"text": "", "lang": lang, "seconds": 0, "silent": True}
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        f.write(wav)
        name = f.name
    try:
        t0 = time.time()
        r = subprocess.run([WHISPER, "-m", WHISPER_MODEL, "-f", name, "-l", lang, "-nt", "-np", "-t", "6", "-sns",
                            "--prompt", WHISPER_HINT], capture_output=True, text=True, timeout=120)
        detected = re.search(r"auto-detected language: (\w+)", r.stderr)
        text = " ".join(r.stdout.split()).strip()
        if _made_up(text):
            text = ""
        return {"text": text, "lang": detected.group(1) if detected else lang, "seconds": round(time.time() - t0, 1)}
    finally:
        os.unlink(name)


def _wifi_ip():
    """The Mac's home-network address (Wi-Fi or cable, whichever carries the default route)."""
    route = subprocess.run(["route", "-n", "get", "default"], capture_output=True, text=True).stdout
    m = re.search(r"interface: (\S+)", route)
    for iface in ([m.group(1)] if m else []) + ["en0", "en1"]:
        ip = subprocess.run(["ipconfig", "getifaddr", iface], capture_output=True, text=True).stdout.strip()
        if ip.startswith(guard.LAN_PREFIX):
            return ip
    return None


_current = {}  # listener name -> address it's on now, so /health answers instantly


def _follow(name, find_ip):
    """Keep a listener on whatever address find_ip() returns; move or retry it as the network changes.

    Used for both the home network and Tailscale, so the server keeps running when either one is down
    (e.g. Tailscale not started yet after a reboot) and picks it up as soon as it appears.
    """
    current, srv = None, None
    while True:
        ip = find_ip()
        if ip != current or (ip and srv is None):
            if srv:
                srv.shutdown()
                srv.server_close()
                srv = None
            if ip:
                try:
                    srv = ThreadingHTTPServer((ip, PORT), Handler)
                    threading.Thread(target=srv.serve_forever, daemon=True).start()
                    print(f"Listening on {name}", ip, flush=True)
                except OSError as e:
                    print(f"Waiting for {name} {ip}: {e}", flush=True)
            current = ip
            _current[name] = ip
        time.sleep(15)


def _tailscale_ip():
    return guard.tailscale_ip()


if __name__ == "__main__":
    print(f"Buddy server starting (port {PORT})", flush=True)
    notify.start()
    import perms
    perms.start()
    activity.trim()
    assistant.resume_jobs()
    reviews.watch_problems(notify)
    autopilot.start(_autopilot_job, notify)
    credits.start_alerts(notify, assistant.job)
    assistant.keep_awake()
    brief.start(notify, lambda: list(assistant.jobs.values()))
    threading.Thread(target=_follow, args=("home network", _wifi_ip), daemon=True).start()
    threading.Thread(target=_follow, args=("Tailscale", _tailscale_ip), daemon=True).start()
    # announce the server on the home network (Bonjour) so the app finds it without Tailscale
    if CFG["bonjour"]:
        subprocess.Popen(["dns-sd", "-R", "Buddy", "_englishcoach._tcp", "local", str(PORT)],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    while True:
        time.sleep(3600)
