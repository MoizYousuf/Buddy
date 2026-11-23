"""Who may control the Mac: only paired devices, over Tailscale or the home network.

Three checks guard every Mac-control request:
  1. the server listens on the Tailscale address only (see server.py), so the home Wi-Fi and the internet can't reach it;
  2. the caller must be a paired device's Tailscale IP (Tailscale IPs are bound to each device's WireGuard key), an IP in
     config.json "allowed_ips", or on the home network (config "lan_prefixes");
  3. the request must carry the secret token this Mac issued when the phone was paired.

Pairing: the app calls /pair/start, the Mac shows a 6-digit code as a notification, the user types it
into the app, /pair/finish returns a fresh random token. Codes expire after 2 minutes and allow 5 tries.
"""
import hashlib
import hmac
import json
import os
import secrets
import subprocess
import threading
import time
from pathlib import Path

from config import CFG

FILE = Path(__file__).parent / "devices.json"
ALLOWED_IPS = set(CFG["allowed_ips"])  # optional fixed list from config.json; paired phones are added automatically
_lock = threading.Lock()
_pairing = {}  # ip -> {"code", "expires", "tries"}


def _load():
    if FILE.exists():
        return json.loads(FILE.read_text())
    return {"tokens": {}}


def _save(data):
    FILE.write_text(json.dumps(data))
    os.chmod(FILE, 0o600)


def _tailnet(ip):
    """True for a Tailscale address (100.64.0.0/10)."""
    try:
        a, b = (int(x) for x in ip.split(".")[:2])
    except ValueError:
        return False
    return a == 100 and 64 <= b <= 127


def ip_allowed(ip):
    """A paired phone, or an address from config.json "allowed_ips"."""
    return ip in ALLOWED_IPS or ip in _load()["tokens"]


LAN_PREFIX = tuple(CFG["lan_prefixes"])   # the home network; only signed requests are accepted from it


def is_lan(ip):
    return ip.startswith(LAN_PREFIX)


def may_pair(ip):
    """Who may ask for a pairing code: the allow-list if one is set, otherwise any device on your tailnet or home
    network. The 6-digit code is only shown on the Mac's screen, so being able to ask is not enough to pair."""
    if ALLOWED_IPS:
        return ip in ALLOWED_IPS or is_lan(ip)
    return _tailnet(ip) or is_lan(ip) or ip == "127.0.0.1"
_seen = {}               # signature -> time, so a captured request can't be replayed


def authorized(ip, headers, method, path, body=b""):
    """A request is allowed when it is signed with a paired device's secret.

    The secret itself never travels: the app sends X-Coach-Ts (unix seconds) and
    X-Coach-Nonce (random) and X-Coach-Sig = HMAC-SHA256(secret, "METHOD\\nPATH\\nTS\\nNONCE\\nsha256(body)"). Signatures older than
    60 s, or seen before, are refused. Tailscale requests must come from the paired phone's
    Tailscale address; Wi-Fi requests must come from the home network.
    """
    if not (ip_allowed(ip) or is_lan(ip) or ip == "127.0.0.1"):
        return False
    ts, sig = headers.get("X-Coach-Ts", ""), headers.get("X-Coach-Sig", "")
    try:
        if abs(time.time() - int(ts)) > 60:
            return False
    except ValueError:
        return False
    nonce = headers.get("X-Coach-Nonce", "")
    # the nonce makes every request unique, so two identical requests in the same second both pass
    msg = f"{method}\n{path}\n{ts}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    for secret in _load()["tokens"].values():
        if hmac.compare_digest(hmac.new(secret.encode(), msg, hashlib.sha256).hexdigest(), sig):
            with _lock:
                now = time.time()
                for k in [k for k, t in _seen.items() if now - t > 120]:
                    del _seen[k]
                if sig in _seen:
                    return False
                _seen[sig] = now
            return True
    return False


def pair_start(ip):
    if not may_pair(ip):
        return {"error": "This device is not allowed. Use Tailscale or the same Wi-Fi as the Mac (or add its IP to allowed_ips in config.json)."}
    code = f"{secrets.randbelow(1_000_000):06d}"
    with _lock:
        _pairing[ip] = {"code": code, "expires": time.time() + 120, "tries": 0}
    subprocess.run(["osascript", "-e",
                    f'display notification "Pairing code: {code}" with title "Buddy" sound name "Glass"'])
    return {"ok": True}


def pair_finish(ip, code):
    with _lock:
        p = _pairing.get(ip)
        if not p or time.time() > p["expires"] or p["tries"] >= 5:
            _pairing.pop(ip, None)
            return {"error": "Code expired. Start pairing again."}
        p["tries"] += 1
        if not hmac.compare_digest(p["code"], str(code)):
            return {"error": "Wrong code."}
        _pairing.pop(ip)
    token = secrets.token_urlsafe(32)
    data = _load()
    data["tokens"][ip] = token
    _save(data)
    return {"token": token}


def tailscale_ip():
    """This Mac's Tailscale address: config "tailscale_ip" if set, else the 100.x address found on this Mac."""
    import re
    out = subprocess.run(["ifconfig"], capture_output=True, text=True).stdout
    fixed = CFG["tailscale_ip"]
    if fixed:
        return fixed if f"inet {fixed} " in out else None
    found = [ip for ip in re.findall(r"inet (100\.\d+\.\d+\.\d+) ", out) if _tailnet(ip)]
    return found[0] if found else None
