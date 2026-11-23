"""Run with the video project's Python (config youtube.python). stdin: {"id": youtube_id, "publish_at": UTC ISO or "now"}.
Changes when a scheduled (private) video goes public; "now" makes it public at once."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from config import KF  # noqa: E402
sys.path.insert(0, str(KF))
import youtube_upload  # noqa: E402
from googleapiclient.discovery import build  # noqa: E402

a = json.load(sys.stdin)
yt = build("youtube", "v3", credentials=youtube_upload._creds(), cache_discovery=False)
v = yt.videos().list(part="status", id=a["id"]).execute()["items"][0]
st = v["status"]
if st.get("privacyStatus") == "public":
    print(json.dumps({"error": "Already public: YouTube can only reschedule private/scheduled videos"})); sys.exit()
if a["publish_at"] == "now":
    st = {"privacyStatus": "public", "selfDeclaredMadeForKids": True}
else:
    st = {"privacyStatus": "private", "publishAt": a["publish_at"], "selfDeclaredMadeForKids": True}
r = yt.videos().update(part="status", body={"id": a["id"], "status": st}).execute()
print(json.dumps({"ok": True, "privacy": r["status"].get("privacyStatus"), "publish_at": r["status"].get("publishAt", "")}))
