"""CV interview: a 10-question plan made from the owner's CV (Claude, cached until the CV changes), asked in order during a
practice session, every answer saved, and a detailed feedback report at the end (score, STAR, better answer, English)."""
import hashlib
import json
import subprocess
import threading
from pathlib import Path

HERE = Path(__file__).parent
PLAN = HERE / "interview_plan.json"
CLAUDE = str(Path.home() / ".local/bin/claude")
MARK = "CV_INTERVIEW"          # the app sends this in the role for a CV interview
answers = {}                    # session -> [{"q", "a", "correct"}]
feedback = {}                   # session -> {"status", ...}
_lock = threading.Lock()


def _cv():
    return json.loads((HERE / "resume.json").read_text())


def _claude_json(prompt, timeout=240):
    r = subprocess.run([CLAUDE, "-p", prompt], capture_output=True, text=True, timeout=timeout)
    txt = r.stdout.strip()
    s, e = txt.find("{"), txt.rfind("}")
    return json.loads(txt[s:e + 1])


def plan(refresh=False):
    """The interview plan for the current CV: {title, questions: [{round, q, looking_for}]}."""
    cv = _cv()
    key = hashlib.md5(json.dumps(cv, sort_keys=True).encode()).hexdigest()
    if PLAN.exists() and not refresh:
        p = json.loads(PLAN.read_text())
        if p.get("cv") == key:
            return p
    p = _claude_json(
        "You are a senior hiring manager. From this CV, write a realistic 10-question job interview for the candidate's target "
        "role, in this order: 1 intro ('tell me about yourself' style), 2-3 deep dives into THEIR real projects and results, "
        "2 technical questions on THEIR main stack, 1 problem-solving/design question, 2 behavioural questions (team, conflict, "
        "failure), 1 HR question (notice period / salary expectation / why this company). Use their real project and company "
        "names. Questions in simple, natural spoken English, one sentence each. Reply with ONLY JSON: "
        '{"title": "the role being interviewed for", "questions": [{"round": "Intro|Projects|Technical|Problem solving|'
        'Behavioural|HR", "q": "...", "looking_for": "what a strong answer includes, 1 short line"}]}\nCV: '
        + json.dumps(cv, ensure_ascii=False))
    p["cv"] = key
    PLAN.write_text(json.dumps(p, ensure_ascii=False, indent=1))
    return p


def system_note(sid, prev_question, answer, correct):
    """Saves the answer and returns (instruction for the interviewer, next planned question or '')."""
    p = plan()
    with _lock:
        lst = answers.setdefault(sid, [])
        lst.append({"q": prev_question, "a": answer, "correct": correct})
        n = len(lst)
    nxt = p["questions"][n]["q"] if n < len(p["questions"]) else ""
    return nxt


def start_feedback(sid):
    with _lock:
        qa = list(answers.get(sid, []))
        feedback[sid] = {"status": "working"}
    if not qa:
        feedback[sid] = {"status": "error", "error": "No answers in this interview yet"}
        return feedback[sid]
    p = plan()

    def work():
        try:
            fb = _claude_json(
                "You are an expert interview coach for a Pakistani developer practising English job interviews. For each "
                "answer below, judge it like a real hiring manager for the role, using the CV for context. Reply with ONLY "
                'JSON: {"overall": {"score": 0-100, "verdict": "Strong hire|Hire|Maybe|Not yet", "strengths": ["..."], '
                '"improve": ["..."], "summary": "2 short sentences"}, "answers": [{"q": "...", "score": 1-10, '
                '"star": "which of Situation/Task/Action/Result were there", "missing": "what was missing, 1 line", '
                '"better": "a better answer in natural spoken English (3-5 sentences) using THEIR real CV facts", '
                '"english": "the main English fix, or empty"}]}. Be honest and specific, kind but not soft.\n'
                f"Role: {p.get('title')}\nCV: {json.dumps(_cv(), ensure_ascii=False)}\n"
                f"Interview: {json.dumps(qa, ensure_ascii=False)}", timeout=300)
            fb["status"] = "ready"
        except Exception as e:
            fb = {"status": "error", "error": str(e)[:200]}
        with _lock:
            feedback[sid] = fb
    threading.Thread(target=work, daemon=True).start()
    return {"status": "working"}


def get_feedback(sid):
    with _lock:
        return feedback.get(sid, {"status": "none"})
