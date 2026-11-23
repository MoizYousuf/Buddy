#!/bin/bash
# Buddy setup for macOS (Apple Silicon).
#
#   ./setup.sh            install or update everything, then start Buddy's server
#   ./setup.sh --check    only check: missing tools, models, macOS permissions (changes nothing)
#   ./setup.sh --no-agent install everything but don't install/start the LaunchAgent
#   ./setup.sh --uninstall-agent   stop Buddy's server and remove its LaunchAgent (keeps your files)
#
# Safe to run again: steps that are already done are skipped.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SERVER="$ROOT/server"
MODELS="$ROOT/models"
CONFIG="$SERVER/config.json"
LABEL="com.khananiinnovations.buddy"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
WHISPER_FILE="ggml-large-v3-turbo-q5_0.bin"
WHISPER_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$WHISPER_FILE"
HELPERS_SWIFT="macscroll macdisplays macaudio maclocked macstream macstream264 maclayers macpack macsvg pdftext"

if [ -t 1 ]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; D=$'\e[2m'; N=$'\e[0m'; else B= G= Y= R= D= N=; fi
ok()   { echo "  ${G}✓${N} $*"; }
warn() { echo "  ${Y}!${N} $*"; WARN=$((WARN+1)); }
bad()  { echo "  ${R}✗${N} $*"; BAD=$((BAD+1)); }
info() { echo "    ${D}$*${N}"; }
step() { echo; echo "${B}$*${N}"; }
WARN=0; BAD=0

MODE="install"
case "${1:-}" in
  --check) MODE="check" ;;
  --no-agent) MODE="noagent" ;;
  --uninstall-agent) MODE="uninstall" ;;
  -h|--help) sed -n 2,9p "$0"; exit 0 ;;
  "") ;;
  *) echo "Unknown option $1 (try --help)"; exit 2 ;;
esac

# Python the server runs with (Homebrew's if present, so permissions go to one stable binary).
pick_python() {
  for p in /opt/homebrew/bin/python3 /usr/local/bin/python3 "$(command -v python3 2>/dev/null)"; do
    [ -n "$p" ] && [ -x "$p" ] && "$p" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)' 2>/dev/null && { echo "$p"; return; }
  done
}

cfg() {  # cfg models.fast -> value from config.json merged with defaults
  "$PY" -c "import sys; sys.path.insert(0, '$SERVER'); import config
v = config.CFG
for k in '$1'.split('.'): v = v[k]
print(v if not isinstance(v, (list, dict)) else ' '.join(map(str, v)))" 2>/dev/null
}

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# ---------------------------------------------------------------------------------------------------------------
if [ "$MODE" = "uninstall" ]; then
  launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null && echo "Stopped $LABEL" || echo "$LABEL was not running"
  rm -f "$PLIST" && echo "Removed $PLIST"
  exit 0
fi

echo "${B}Buddy setup${N} ${D}($ROOT)${N}"

step "1. This Mac"
if [ "$(uname -s)" != "Darwin" ]; then bad "Buddy's server runs on macOS only."; exit 1; fi
ok "macOS $(sw_vers -productVersion)"
if [ "$(uname -m)" = "arm64" ]; then ok "Apple Silicon"; else warn "Intel Mac: works, but the local AI models will be slow."; fi
RAM=$(( $(sysctl -n hw.memsize) / 1073741824 ))
if [ "$RAM" -ge 16 ]; then ok "${RAM} GB memory"; else warn "${RAM} GB memory: 16 GB is recommended (the AI models need ~8 GB)."; fi

step "2. Homebrew and tools"
BREW="$(command -v brew || ls /opt/homebrew/bin/brew 2>/dev/null || true)"
if [ -z "$BREW" ]; then
  bad "Homebrew is missing. Install it from https://brew.sh (one command), then run ./setup.sh again."
  [ "$MODE" = "check" ] || exit 1
else
  ok "Homebrew"
  #       formula      command it gives  why
  TOOLS="python3:python3:runs_Buddy's_server
ollama:ollama:local_AI_models
whisper-cpp:whisper-cli:speech_to_text
ffmpeg:ffmpeg:audio_and_video
cliclick:cliclick:mouse_clicks_for_the_Mac_agent
espeak-ng:espeak-ng:pronunciation_scoring
node:node:CV_PDFs,_optional"
  while IFS=: read -r formula cmd why; do
    why="${why//_/ }"
    [ "$formula" = "python3" ] && formula="python"
    if command -v "$cmd" >/dev/null 2>&1 || [ -x "/opt/homebrew/bin/$cmd" ]; then
      ok "$cmd ${D}($why)${N}"
    elif [ "$MODE" = "check" ]; then
      bad "$cmd is missing ($why): brew install $formula"
    else
      echo "  … installing $formula ($why)"
      "$BREW" install "$formula" >/dev/null && ok "$cmd installed" || bad "brew install $formula failed"
    fi
  done <<< "$TOOLS"
fi

PY="$(pick_python)"
if [ -z "$PY" ]; then bad "No Python 3.9+ found: brew install python"; exit 1; fi
ok "Python: $PY ($("$PY" -c 'import platform; print(platform.python_version())'))"
PY_REAL="$("$PY" -c 'import os, sys; print(os.path.realpath(sys.executable))')"

step "3. Your settings (server/config.json)"
if [ -f "$CONFIG" ]; then
  ok "config.json exists (edit it any time, then restart: ./setup.sh)"
elif [ "$MODE" = "check" ]; then
  bad "config.json is missing: run ./setup.sh (it creates it from server/config.example.json)"
else
  FIRST="$(id -F 2>/dev/null | awk '{print $1}')"
  "$PY" - "$SERVER/config.example.json" "$CONFIG" "$FIRST" <<'EOF'
import json, sys
src, dst, first = sys.argv[1:4]
c = json.load(open(src))
c.pop("_readme", None)
c["owner_name"] = first
c["chrome"].update({"profile_name": "", "google_account": ""})
json.dump(c, open(dst, "w"), indent=2, ensure_ascii=False)
open(dst, "a").write("\n")
EOF
  chmod 600 "$CONFIG"
  ok "Created config.json (owner_name: \"$FIRST\"). Open it to set your Chrome profile, YouTube channel and more."
fi
PORT="$(cfg port)"; PORT="${PORT:-8787}"

step "4. Local AI models (Ollama)"
FAST="$(cfg models.fast)"; SLOW="$(cfg models.slow)"
if command -v ollama >/dev/null 2>&1; then
  if ! ollama list >/dev/null 2>&1; then
    if [ "$MODE" = "check" ]; then bad "Ollama is installed but not running: brew services start ollama"
    else "$BREW" services start ollama >/dev/null 2>&1; sleep 3; fi
  fi
  if ollama list >/dev/null 2>&1; then
    ok "Ollama is running"
    for m in $FAST $SLOW; do
      if ollama list | awk 'NR>1{print $1}' | grep -qx "$m" || ollama list | awk 'NR>1{print $1}' | grep -qx "$m:latest"; then
        ok "model $m"
      elif [ "$MODE" = "check" ]; then
        bad "model $m is missing: ollama pull $m   (or change \"models\" in config.json to a model you have)"
      else
        echo "  … downloading $m (a few GB, once)"
        ollama pull "$m" && ok "model $m" || bad "ollama pull $m failed"
      fi
    done
  fi
else
  bad "Ollama is missing: brew install ollama"
fi

step "5. Speech to text (whisper.cpp + $WHISPER_FILE)"
WMODEL="$(cfg whisper.model)"; case "$WMODEL" in /*) ;; ~*) WMODEL="${WMODEL/#\~/$HOME}" ;; *) WMODEL="$ROOT/$WMODEL" ;; esac
if [ -s "$WMODEL" ] && [ "$(stat -f %z "$WMODEL")" -gt 500000000 ]; then
  ok "Whisper model: $WMODEL"
elif [ "$MODE" = "check" ]; then
  bad "Whisper model missing: run ./setup.sh (downloads ~550 MB to $WMODEL)"
else
  mkdir -p "$(dirname "$WMODEL")"
  echo "  … downloading the Whisper model (~550 MB, once)"
  curl -fL --retry 3 -C - -o "$WMODEL" "$WHISPER_URL" && ok "Whisper model downloaded" || bad "Download failed: $WHISPER_URL"
fi

step "6. Mac helpers (Swift)"
if ! command -v swiftc >/dev/null 2>&1; then
  bad "swiftc is missing: xcode-select --install  (Command Line Tools)"
else
  for h in $HELPERS_SWIFT; do
    src="$SERVER/$h.swift"; bin="$SERVER/$h"
    [ -f "$src" ] || continue
    if [ -x "$bin" ] && [ "$bin" -nt "$src" ]; then ok "$h"
    elif [ "$MODE" = "check" ]; then bad "$h is not built: run ./setup.sh"
    else swiftc -O "$src" -o "$bin" 2>/tmp/buddy-swiftc.log && ok "$h built" || { bad "$h failed to build (see /tmp/buddy-swiftc.log)"; }
    fi
  done
  src="$SERVER/macvdisplay.m"; bin="$SERVER/macvdisplay"
  if [ -x "$bin" ] && [ "$bin" -nt "$src" ]; then ok "macvdisplay"
  elif [ "$MODE" = "check" ]; then warn "macvdisplay (virtual display, optional) is not built: run ./setup.sh"
  else clang -O2 -fobjc-arc -framework Foundation -framework CoreGraphics "$src" -o "$bin" 2>/tmp/buddy-clang.log \
       && ok "macvdisplay built" || warn "macvdisplay (optional virtual display) did not build (see /tmp/buddy-clang.log)"
  fi
fi

if command -v npm >/dev/null 2>&1; then
  if [ -d "$SERVER/node_modules/pdfkit" ]; then ok "pdfkit (CV PDFs)"
  elif [ "$MODE" != "check" ]; then (cd "$SERVER" && npm install --silent --no-audit --no-fund pdfkit >/dev/null 2>&1) && ok "pdfkit installed (CV PDFs)" || warn "npm install pdfkit failed (only CV PDFs need it)"
  else warn "pdfkit (CV PDFs, optional) not installed: run ./setup.sh"; fi
fi

step "7. Apps and accounts"
CLAUDE_BIN="$HOME/.local/bin/claude"
if [ -x "$CLAUDE_BIN" ]; then ok "Claude Code CLI ($CLAUDE_BIN)"
elif command -v claude >/dev/null 2>&1; then warn "Claude Code is at $(command -v claude); Buddy looks in ~/.local/bin/claude. Run: claude install   (or symlink it there)"
else bad "Claude Code CLI is missing (Mac agent, Studio, CV Studio need it): curl -fsSL https://claude.ai/install.sh | bash   then run: claude   once to sign in"; fi
if [ -d "/Applications/Tailscale.app" ] || command -v tailscale >/dev/null 2>&1; then
  TSIP="$(ifconfig 2>/dev/null | awk '/inet 100\./{print $2}' | head -1)"
  if [ -n "$TSIP" ]; then ok "Tailscale is on: this Mac is ${B}$TSIP${N} (type this in the app when pairing)"
  else warn "Tailscale is installed but not connected: open Tailscale and sign in"; fi
else bad "Tailscale is missing (reach your Mac from anywhere): https://tailscale.com/download/mac"; fi
if [ -d "/Applications/Google Chrome.app" ]; then ok "Google Chrome"
else warn "Google Chrome is missing (only for YouTube autopilot, Studio, Etsy): https://www.google.com/chrome/"; fi
if command -v ntfy >/dev/null 2>&1; then ok "ntfy (optional)"
else info "ntfy not installed (optional; Buddy's app already shows the Mac's alerts as notifications)"; fi

step "8. macOS permissions"
PERMS="$SERVER/permissions.json"
if [ -f "$PERMS" ] && [ -n "$(find "$PERMS" -mmin -15 2>/dev/null)" ]; then
  SRC="the running server"
else
  "$PY" "$SERVER/perms.py" > /tmp/buddy-perms.json 2>/dev/null; PERMS=/tmp/buddy-perms.json
  SRC="this Terminal (the server may differ: check again after it runs)"
fi
pv() { "$PY" -c "import json; print(json.load(open('$PERMS')).get('$1', 'unknown'))" 2>/dev/null; }
info "as seen by $SRC; Python binary: $PY_REAL"
[ "$(pv accessibility)" = "True" ] && ok "Accessibility (mouse, keyboard, window control)" \
  || bad "Accessibility is off for Python: System Settings › Privacy & Security › Accessibility › + › add $PY_REAL"
[ "$(pv screen_recording)" = "True" ] && ok "Screen Recording (live screen on the phone)" \
  || bad "Screen Recording is off for Python: System Settings › Privacy & Security › Screen & System Audio Recording › + › add $PY_REAL"
if osascript -e 'tell application "System Events" to get name of first process' >/dev/null 2>&1; then
  ok "Automation (System Events) for this Terminal"
else
  bad "Automation is off: System Settings › Privacy & Security › Automation › allow System Events for Terminal/Python"
fi
info "Notifications: allow 'Script Editor' notifications so the 6-digit pairing code shows (System Settings › Notifications)."

# ---------------------------------------------------------------------------------------------------------------
if [ "$MODE" = "install" ]; then
  step "9. Start Buddy's server (LaunchAgent $LABEL)"
  if launchctl print "gui/$(id -u)/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null; sleep 1
  fi
  if port_in_use "$PORT"; then
    bad "Port $PORT is already used by another program: set \"port\" in server/config.json (the app takes host:port), then run ./setup.sh again"
  else
    mkdir -p "$HOME/Library/LaunchAgents"
    cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array><string>$PY</string><string>$SERVER/server.py</string></array>
  <key>WorkingDirectory</key><string>$SERVER</string>
  <key>EnvironmentVariables</key>
  <dict><key>PATH</key><string>/opt/homebrew/bin:/usr/local/bin:$HOME/.local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string></dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ThrottleInterval</key><integer>10</integer>
  <key>StandardOutPath</key><string>$SERVER/server.log</string>
  <key>StandardErrorPath</key><string>$SERVER/server.log</string>
</dict>
</plist>
EOF
    launchctl bootstrap "gui/$(id -u)" "$PLIST" && ok "Server started on port $PORT (log: server/server.log)" || bad "launchctl bootstrap failed"
    sleep 4
    for ip in $(ifconfig | awk '/inet (100|192\.168|10)\./{print $2}'); do
      if curl -s -m 3 "http://$ip:$PORT/health" | grep -q '"ok": true'; then ok "Answering at http://$ip:$PORT"; fi
    done
  fi
elif [ "$MODE" = "check" ]; then
  step "9. Server"
  if launchctl print "gui/$(id -u)/$LABEL" >/dev/null 2>&1; then ok "LaunchAgent $LABEL is loaded"; else warn "Server not installed as a LaunchAgent: run ./setup.sh"; fi
fi

echo
if [ "$BAD" -eq 0 ]; then
  echo "${G}${B}All set.${N} Next: install the app on your phone and pair it (docs/SETUP.md, steps 4-5)."
else
  echo "${R}${B}$BAD thing(s) to fix${N}, $WARN warning(s). Fix them, then run ${B}./setup.sh --check${N} again."
fi
[ "$BAD" -eq 0 ]
