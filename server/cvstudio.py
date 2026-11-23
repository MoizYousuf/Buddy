"""CV studio: a CV tailored to one job (link or pasted text), written to sound like a person and using only real facts
from the owner's CV/profile (apply-kit). Editable section by section on the phone, AI edits on request, every save kept as
a version, and a PDF made with the same look as apply-kit's CVs. Projects live in server/cvs/<id>/."""
import json
import re
import subprocess
import time
from pathlib import Path

HERE = Path(__file__).parent
import shutil as _sh
from config import CFG as _CFG, path as _cpath

KIT = _cpath(_CFG["cv"]["kit_dir"])     # your CV/profile folder (profile + tailored CVs), see docs/SETUP.md
ROOT = KIT / "tailored"                 # outside the server folder: agents may never write there
NODE = _CFG["cv"]["node"] or _sh.which("node") or "/opt/homebrew/bin/node"
# pdfkit: from `npm install` in server/ (setup.sh) or from the CV folder's own dashboard
NODE_PATH = ":".join([str(Path(__file__).parent / "node_modules"), str(KIT / "dashboard/node_modules")])

HUMAN = ("WRITE LIKE A REAL PERSON, NOT AN AI: plain verbs (built, led, cut, shipped, fixed, moved, set up), never these words: "
         "spearheaded, leveraged, utilized, seamless, robust, cutting-edge, passionate, dynamic, synergy, results-driven, "
         "delve, innovative, game-changer, proven track record, detail-oriented, go-getter. Vary how bullets start; mix short and "
         "long bullets; numbers only where the CV has them; a 2-3 sentence summary in first-person-implied style "
         "(no 'I', no 'he'); no em dashes; British/American mix is fine but be consistent; ATS-friendly plain text.")
TRUTH = ("TRUTH RULES: use ONLY facts from the CV and profile below. Never invent employers, titles, dates, degrees, numbers, "
         "tools or projects. You may reorder, choose what to highlight, and rephrase real work toward the job's words. If the job "
         "asks for something the candidate doesn't have, don't add it: list it in 'gaps' with an honest way to talk about it.")
FORMAT = ("CV FORMAT (plain text, exactly like the base CV): line 1 'NAME'; next lines contact info; then sections with UPPERCASE "
          "headings on their own line (PROFESSIONAL SUMMARY, SKILLS, EXPERIENCE, PROJECTS, EDUCATION...); each job as "
          "'Title | Company | City | Dates' on one line, then bullets starting with '• '. One page if possible, two at most.")


def _id(pid):
    if not re.match(r"^[a-z0-9-]{3,80}$", pid or ""):
        raise ValueError("bad id")
    d = ROOT / pid
    if not d.is_dir():
        raise ValueError("not found")
    return d


def _read(p, default=None):
    try:
        return json.loads(p.read_text())
    except (OSError, ValueError):
        return default


def listing():
    out = []
    if ROOT.exists():
        for d in sorted(ROOT.iterdir(), key=lambda p: -p.stat().st_mtime):
            j = _read(d / "job.json", {}); r = _read(d / "report.json", {}); st = _read(d / "status.json", {})
            out.append({"id": d.name, "title": j.get("title") or r.get("job_title") or "New CV", "company": j.get("company") or r.get("company", ""),
                        "score": r.get("match_score"), "stage": st.get("stage", ""), "updated": time.strftime("%Y-%m-%d %H:%M", time.localtime(d.stat().st_mtime)),
                        "has_pdf": (d / "cv.pdf").exists()})
    return {"items": out[:60]}


def get(pid):
    d = _id(pid)
    versions = sorted(p.name for p in d.glob("cv.v*.txt"))
    return {"id": pid, "job": _read(d / "job.json", {}), "report": _read(d / "report.json", {}), "status": _read(d / "status.json", {}),
            "cv": (d / "cv.txt").read_text() if (d / "cv.txt").exists() else "", "versions": versions, "has_pdf": (d / "cv.pdf").exists()}


def _status(d, **kw):
    st = _read(d / "status.json", {}); st.update(kw); (d / "status.json").write_text(json.dumps(st, indent=1))


def _base():
    return (KIT / "cv.txt").read_text(), (KIT / "data/profile.txt").read_text() if (KIT / "data/profile.txt").exists() else ""


def create(link, text, note, start_job):
    if not (link or text or note):
        raise ValueError("Share the job link or paste the job description")
    slug = re.sub(r"[^a-z0-9]+", "-", (note or link or text)[:40].lower()).strip("-") or "job"
    pid = time.strftime("%Y%m%d-%H%M") + "-" + slug[:30]
    d = ROOT / pid; d.mkdir(parents=True, exist_ok=True)
    (d / "job.json").write_text(json.dumps({"url": link, "description": text, "note": note}, ensure_ascii=False, indent=1))
    _status(d, stage="writing", created=time.strftime("%Y-%m-%d %H:%M"))
    cv, profile = _base()
    prompt = (f"Tailor the candidate's CV to ONE job and save the results in {d}.\n"
              + (f"Job link: {link} (read it with WebFetch; if it can't be read, use the text below).\n" if link else "")
              + (f"Job description pasted by the owner:\n{text}\n" if text else "")
              + (f"Owner's note: {note}\n" if note else "")
              + f"\n{TRUTH}\n{HUMAN}\n{FORMAT}\n\n"
              f"BASE CV:\n{cv}\n\nPROFILE (more facts, preferences):\n{profile}\n\n"
              f"Write these files: (1) {d}/cv.txt the tailored CV; (2) {d}/report.json with "
              '{"job_title", "company", "location", "match_score" (0-100, honest), "matching" (skills they have), '
              '"gaps" ([{"skill", "how_to_handle"}]), "changes" (what you changed and why, short list), '
              '"keywords" (job keywords now in the CV), "cover_note" (3-4 sentence message to the recruiter, human tone)}; '
              f"(3) update {d}/job.json adding title, company, location, description (the job text you read). "
              "Reply with the match score and the 3 biggest changes.")
    jid = start_job(prompt, d)
    _status(d, job=jid)
    return {"id": pid, "job_id": jid}


def _save_version(d):
    cur = d / "cv.txt"
    if cur.exists():
        n = len(list(d.glob("cv.v*.txt"))) + 1
        (d / f"cv.v{n:02d}.txt").write_text(cur.read_text())


def save(pid, text):
    d = _id(pid)
    if len(text.strip()) < 50:
        raise ValueError("The CV is empty")
    _save_version(d)
    (d / "cv.txt").write_text(text.strip() + "\n")
    return make_pdf(pid)


def restore(pid, version):
    d = _id(pid)
    if not re.match(r"^cv\.v\d+\.txt$", version) or not (d / version).exists():
        raise ValueError("no such version")
    return save(pid, (d / version).read_text())


def ai_edit(pid, note, start_job):
    d = _id(pid)
    _save_version(d)
    _status(d, stage="editing")
    cv, profile = _base()
    jid = start_job(f"Edit the tailored CV at {d}/cv.txt as the owner asks: \"{note}\".\n{TRUTH}\n{HUMAN}\n{FORMAT}\n"
                    f"Job: {d}/job.json. Base CV:\n{cv}\nProfile:\n{profile}\n"
                    f"Overwrite {d}/cv.txt, and update {d}/report.json 'changes' and 'match_score' if they changed. "
                    "Reply with what you changed in one or two lines.", d)
    _status(d, job=jid)
    return {"job_id": jid}


def make_pdf(pid):
    d = _id(pid)
    r = subprocess.run([NODE, str(HERE / "cvpdf.js"), str(d / "cv.txt"), str(d / "cv.pdf")], capture_output=True, text=True,
                       timeout=60, env={"NODE_PATH": NODE_PATH, "PATH": "/usr/bin:/bin"})
    if r.returncode != 0:
        raise RuntimeError("PDF failed: " + (r.stderr or r.stdout)[-200:])
    _status(d, stage="ready")
    return {"ok": True, "pdf": True}


def pdf_path(pid, name=None):
    d = _id(pid)
    p = d / "cv.pdf"
    return str(p) if p.exists() else None


def refresh_stage(pid, job):
    """After Claude finishes writing or editing: make the PDF and mark it ready."""
    d = _id(pid)
    st = _read(d / "status.json", {})
    if st.get("stage") in ("writing", "editing") and st.get("job"):
        j = job(st["job"])
        if not j or j.get("status") != "running":
            if (d / "cv.txt").exists():
                try:
                    make_pdf(pid)
                except Exception as e:
                    _status(d, stage="pdf failed", error=str(e)[:200])
            else:
                _status(d, stage="failed")
