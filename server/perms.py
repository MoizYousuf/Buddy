"""Which macOS permissions this server process has, as macOS sees it.

macOS gives permissions to the process that asks, so only the running server (started by its LaunchAgent) can tell
whether *it* may control the Mac or record the screen. It writes permissions.json every 10 minutes;
`./setup.sh --check` reads that file. Nothing here asks for a permission or shows a prompt.
"""
import ctypes
import json
import os
import sys
import threading
import time
from pathlib import Path

FILE = Path(__file__).parent / "permissions.json"


def _lib(path):
    try:
        return ctypes.cdll.LoadLibrary(path)
    except OSError:
        return None


def check():
    out = {"python": os.path.realpath(sys.executable), "checked": time.strftime("%Y-%m-%d %H:%M:%S")}
    ax = _lib("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices")
    if ax:
        ax.AXIsProcessTrusted.restype = ctypes.c_bool
        out["accessibility"] = bool(ax.AXIsProcessTrusted())
    cg = _lib("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics")
    if cg and hasattr(cg, "CGPreflightScreenCaptureAccess"):
        cg.CGPreflightScreenCaptureAccess.restype = ctypes.c_bool
        out["screen_recording"] = bool(cg.CGPreflightScreenCaptureAccess())
    return out


def start():
    def loop():
        while True:
            try:
                FILE.write_text(json.dumps(check(), indent=1))
            except OSError:
                pass
            time.sleep(600)
    threading.Thread(target=loop, daemon=True).start()


if __name__ == "__main__":
    print(json.dumps(check(), indent=1))
