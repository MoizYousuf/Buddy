"""Run with the video project's Python (config youtube.python): prints YouTube stats for the video ids given on stdin (JSON list) as JSON.
Uses the channel's existing OAuth token through <video project>/youtube_upload.py (read-only calls)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from config import KF  # noqa: E402
sys.path.insert(0, str(KF))
import youtube_upload  # noqa: E402
from googleapiclient.discovery import build  # noqa: E402

ids = json.load(sys.stdin)
yt = build("youtube", "v3", credentials=youtube_upload._creds(), cache_discovery=False)
out = {}
for i in range(0, len(ids), 50):
    r = yt.videos().list(part="statistics,status,snippet,contentDetails", id=",".join(ids[i:i + 50])).execute()
    for v in r.get("items", []):
        st, s, sn = v.get("statistics", {}), v.get("status", {}), v.get("snippet", {})
        out[v["id"]] = {"views": int(st.get("viewCount", 0)), "likes": int(st.get("likeCount", 0)),
                        "comments": int(st.get("commentCount", 0)), "privacy": s.get("privacyStatus", ""),
                        "publish_at": s.get("publishAt", ""), "published": sn.get("publishedAt", ""),
                        "thumb": (sn.get("thumbnails", {}).get("medium") or {}).get("url", ""),
                        "duration": v.get("contentDetails", {}).get("duration", "")}
print(json.dumps(out))
