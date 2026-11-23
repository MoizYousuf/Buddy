"""Run with the video project's Python (config youtube.python). Prints the channel's stats, every upload's numbers, and (if the YouTube Analytics API
is enabled in the Google project) the last 28 days of views / watch time / subscribers per day."""
import datetime
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from config import KF  # noqa: E402
sys.path.insert(0, str(KF))
import youtube_upload  # noqa: E402
from googleapiclient.discovery import build  # noqa: E402

c = youtube_upload._creds()
yt = build("youtube", "v3", credentials=c, cache_discovery=False)
ch = yt.channels().list(part="snippet,statistics,contentDetails", mine=True).execute()["items"][0]
up = ch["contentDetails"]["relatedPlaylists"]["uploads"]
ids, tok = [], None
while True:
    r = yt.playlistItems().list(part="contentDetails", playlistId=up, maxResults=50, pageToken=tok).execute()
    ids += [i["contentDetails"]["videoId"] for i in r["items"]]
    tok = r.get("nextPageToken")
    if not tok:
        break
videos = []
for i in range(0, len(ids), 50):
    r = yt.videos().list(part="snippet,statistics,contentDetails,status", id=",".join(ids[i:i + 50])).execute()
    for v in r["items"]:
        d = v["contentDetails"]["duration"]
        m = re.match(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?", d)
        secs = sum(int(x or 0) * k for x, k in zip(m.groups(), (3600, 60, 1))) if m else 0
        st = v["statistics"]
        videos.append({"id": v["id"], "title": v["snippet"]["title"], "published": v["snippet"]["publishedAt"],
                       "views": int(st.get("viewCount", 0)), "likes": int(st.get("likeCount", 0)),
                       "comments": int(st.get("commentCount", 0)), "seconds": secs, "short": "#short" in v["snippet"]["title"].lower() or ("#shorts" in v["snippet"].get("description", "").lower() and secs <= 60),
                       "privacy": v["status"]["privacyStatus"], "publish_at": v["status"].get("publishAt", "")})
out = {"channel": {"id": ch["id"], "title": ch["snippet"]["title"], "subs": int(ch["statistics"].get("subscriberCount", 0)),
                   "views": int(ch["statistics"].get("viewCount", 0)), "videos": int(ch["statistics"].get("videoCount", 0))},
       "videos": videos}
try:
    ya = build("youtubeAnalytics", "v2", credentials=c, cache_discovery=False)
    end = datetime.date.today(); start = end - datetime.timedelta(days=28)
    r = ya.reports().query(ids="channel==MINE", startDate=str(start), endDate=str(end),
                           metrics="views,estimatedMinutesWatched,averageViewDuration,subscribersGained,subscribersLost",
                           dimensions="day", sort="day").execute()
    out["daily"] = [dict(zip(["day", "views", "minutes", "avg_secs", "subs_gained", "subs_lost"], row)) for row in r.get("rows", [])]
except Exception as e:
    msg = str(e)
    out["analytics_error"] = "disabled" if "has not been used" in msg or "disabled" in msg else msg[:200]
print(json.dumps(out))
