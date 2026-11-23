"""Buddy's per-Mac settings.

Everything personal (your name, your phone's address, Chrome profile, YouTube channel, Etsy stores...) lives in
config.json next to this file. config.json is never committed; config.example.json shows every key.
./setup.sh creates config.json from the example the first time. Missing keys fall back to DEFAULTS below,
so an old config.json keeps working after an update.

Use:  from config import CFG;  CFG["owner_name"],  CFG["youtube"]["channel_name"], ...
"""
import json
import os
from pathlib import Path

HERE = Path(__file__).parent
FILE = Path(os.environ.get("BUDDY_CONFIG", HERE / "config.json"))

DEFAULTS = {
    # Your first name. The app greets you with it and the assistant uses it in messages.
    "owner_name": "",
    # Port the server listens on (Tailscale + home network). The app's default is 8787.
    "port": 8787,
    # Announce the server on the home Wi-Fi (Bonjour) so the app finds the Mac without Tailscale.
    "bonjour": True,
    # This Mac's Tailscale address. Empty = found automatically (the 100.x.y.z address of this Mac).
    "tailscale_ip": "",
    # Phones that may start pairing over Tailscale. Empty = any device on your tailnet may ask; it still needs the
    # 6-digit code the Mac shows. Paired phones are always allowed after that.
    "allowed_ips": [],
    # Home-network address ranges that are accepted (only with a paired phone's signature).
    "lan_prefixes": ["192.168.", "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
                     "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31."],
    # Your local time zone as words for the AI, e.g. "Pakistan time, UTC+5". Empty = the Mac's own zone.
    "timezone_label": "",
    # Ollama models: fast = coaching replies, slow = Urdu explanations and longer writing.
    "models": {"fast": "gemma3:4b", "slow": "gemma4:e4b-it-qat"},
    # whisper.cpp for speech to text.
    "whisper": {"cli": "/opt/homebrew/bin/whisper-cli", "model": "models/ggml-large-v3-turbo-q5_0.bin"},
    # Chrome profile used by Claude in Chrome for Google Flow, YouTube Studio and Etsy.
    # profile_dir is the folder name in ~/Library/Application Support/Google/Chrome ("Default", "Profile 1"...),
    # profile_name is the name Chrome shows for it, google_account the account signed in there (optional).
    "chrome": {"profile_dir": "Default", "profile_name": "", "google_account": "", "other_profiles": []},
    # YouTube autopilot / Studio (optional). Needs your own video project; see docs/SETUP.md.
    "youtube": {
        "channel_name": "",
        "channel_id": "",
        # one line about the channel for the AI coach, e.g. "toddler stories and songs, Shorts and 1-3 minute videos"
        "about": "",
        "studio_url": "http://127.0.0.1:8765",
        # your video project: studio/ (dashboard + agent.py), daily/, uploads/pending.json, FLOW_RULES.md ...
        "factory_dir": "~/video-factory",
        # Python with that project's packages (YouTube API, whisper...), used for YouTube stats and scheduling
        "python": "~/video-factory/.venv/bin/python",
        # the Claude Code skill that makes the daily video
        "skill_file": "~/.claude/scheduled-tasks/daily-video/SKILL.md",
    },
    # Songs / naat videos (optional): folder where song projects are kept (one folder per song).
    "songs": {"project_dir": "~/song-factory/naat"},
    # Etsy Store Studio (optional): the Trend Studio data folder (report.json + snapshots, one entry per store).
    "etsy": {"data_dir": "~/TrendStudio/data"},
    # CV Studio (optional): folder with your CV profile; tailored CVs are saved in its tailored/ folder.
    # node: path to Node.js for making PDFs (empty = found automatically).
    "cv": {"kit_dir": "~/Documents/cv-kit", "node": ""},
    # Folder with your code projects, so "open my project X" works.
    "projects_dir": "~/Documents/projects",
}


def _merge(base, over):
    out = dict(base)
    for k, v in (over or {}).items():
        out[k] = _merge(base[k], v) if isinstance(v, dict) and isinstance(base.get(k), dict) else v
    return out


def load():
    try:
        data = json.loads(FILE.read_text())
    except (OSError, ValueError):
        data = {}
    data = {k: v for k, v in data.items() if not k.startswith("_")}
    return _merge(DEFAULTS, data)


CFG = load()


def path(p):
    """A config path with ~ expanded; relative paths are relative to the repo folder."""
    p = Path(os.path.expanduser(str(p)))
    return p if p.is_absolute() else HERE.parent / p


def owner(fallback="the owner"):
    return CFG["owner_name"] or fallback


def tz_label():
    if CFG["timezone_label"]:
        return CFG["timezone_label"]
    import time
    off = -time.altzone if time.daylight and time.localtime().tm_isdst else -time.timezone
    h, m = divmod(abs(off) // 60, 60)
    return f"the Mac's local time, UTC{'+' if off >= 0 else '-'}{h}" + (f":{m:02d}" if m else "")


def chrome_desc():
    """Words for the AI about which Chrome profile to use."""
    c = CFG["chrome"]
    name = c["profile_name"] or c["profile_dir"]
    return f"the Chrome profile '{name}'" + (f" ({c['google_account']})" if c["google_account"] else "")


KF = path(CFG["youtube"]["factory_dir"])     # the video project folder
KFS = str(KF)                                 # same, as text for prompts
VENV = path(CFG["youtube"]["python"])         # its Python
SONGS = path(CFG["songs"]["project_dir"])     # song / naat projects
