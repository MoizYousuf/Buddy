"""The learner's CV: stored only on this Mac, summarised once, then used to make interviews about their real work.

Upload (paired phone only) -> PDFKit extracts the text -> the fast model writes a short structured summary ->
resume.json. The interviewer prompt gets that summary, never the raw file.
"""
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).parent
PDF = HERE / "resume.pdf"
SUMMARY = HERE / "resume.json"
PDFTEXT = str(HERE / "pdftext")

SUMMARY_PROMPT = """Read this CV and reply with ONLY this JSON (use short phrases, no invented facts; leave a field empty if the CV doesn't say):
{"name": "", "title": "current job title", "years": "total years of experience",
 "skills": ["up to 12"], "projects": [{"name": "", "what": "one line: what it was and the result", "tech": ""}],
 "roles": [{"title": "", "company": "", "dates": ""}], "education": "", "summary": "two sentences about this person, using their name (no he/she)"}"""


def upload(pdf_bytes, chat):
    if not pdf_bytes.startswith(b"%PDF"):
        return {"error": "Please choose a PDF file."}
    PDF.write_bytes(pdf_bytes)
    PDF.chmod(0o600)
    text = subprocess.run([PDFTEXT, str(PDF)], capture_output=True, text=True, timeout=60).stdout.strip()
    if len(text) < 80:
        return {"error": "I couldn't read text from this PDF (it may be a scanned image)."}
    summary = chat([{"role": "system", "content": SUMMARY_PROMPT}, {"role": "user", "content": text[:12000]}])
    SUMMARY.write_text(json.dumps(summary, ensure_ascii=False))
    SUMMARY.chmod(0o600)
    return {"summary": summary}


def get():
    return {"summary": json.loads(SUMMARY.read_text())} if SUMMARY.exists() else {"summary": None}


def delete():
    for f in (PDF, SUMMARY):
        f.unlink(missing_ok=True)
    return {"ok": True}


def interviewer_note():
    """Text added to the interviewer's instructions, or '' when there's no CV."""
    if not SUMMARY.exists():
        return ""
    s = json.loads(SUMMARY.read_text())
    projects = "; ".join(f"{p.get('name')}: {p.get('what')} ({p.get('tech')})" for p in s.get("projects", [])[:5])
    roles = "; ".join(f"{r.get('title')} at {r.get('company')} {r.get('dates')}" for r in s.get("roles", [])[:4])
    return (f"\nThe candidate's CV: {s.get('title')}, {s.get('years')} experience. Roles: {roles}. "
            f"Projects: {projects}. Skills: {', '.join(s.get('skills', [])[:12])}. "
            "Ask about THEIR real projects, decisions, results and the skills listed, one question at a time, "
            "and follow up on specifics they mention.")
