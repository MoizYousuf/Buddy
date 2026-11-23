# Set up Buddy

About 30 minutes, most of it downloads. You need a Mac and an Android phone, both signed in to the same Tailscale account.

- [Requirements](#requirements)
- [Step 1: Get Buddy on your Mac](#step-1-get-buddy-on-your-mac)
- [Step 2: Run setup](#step-2-run-setup)
- [Step 3: Allow the Mac permissions](#step-3-allow-the-mac-permissions)
- [Step 4: Install the app on your phone](#step-4-install-the-app-on-your-phone)
- [Step 5: Pair your phone](#step-5-pair-your-phone)
- [Step 6: Turn on features](#step-6-turn-on-features)
- [Feature setup](#feature-setup)
- [Settings reference](#settings-reference-serverconfigjson)
- [Troubleshooting](#troubleshooting)

## Requirements

**Needed**

| What | Why |
|---|---|
| Mac with Apple Silicon (M1 or newer), macOS 14 Sonoma or newer | runs Buddy's server and the local AI |
| 16 GB RAM recommended (8 GB works with smaller models), ~20 GB free disk | the AI models are 3-6 GB each, Whisper ~550 MB |
| Android phone, Android 10 or newer | the Buddy app |
| [Android Studio](https://developer.android.com/studio) on the Mac | to build the Buddy APK (there is no ready-made download) |
| [Tailscale](https://tailscale.com/download) (free) on the Mac **and** the phone, same account | reach your Mac from anywhere, privately |
| [Homebrew](https://brew.sh) | setup.sh installs the tools with it |
| [Claude Code](https://claude.com/claude-code) with a Claude plan (Pro or Max), signed in | the Mac agent, Studio, CV Studio, app fixes |

**Optional, per feature**

| Feature | Needs |
|---|---|
| YouTube autopilot, Studio, song videos | Google Chrome + the Claude in Chrome extension, a Google AI Pro plan (Google Flow), your own video project folder (see [YouTube](#youtube-studio-and-autopilot)) |
| Etsy Store Studio designs | Gemini (Google AI Pro) in Chrome; optional Antigravity CLI for text |
| Song / naat videos | a [Suno](https://suno.com) account to make the song |
| CV Studio PDFs | Node.js (setup.sh installs it) |
| Extra push alerts | [ntfy](https://ntfy.sh) (not needed: the app already shows the Mac's alerts) |

## Step 1: Get Buddy on your Mac

Open **Terminal** and run:

```bash
git clone https://github.com/<you>/buddy.git ~/buddy
cd ~/buddy
```

## Step 2: Run setup

```bash
./setup.sh
```

It checks your Mac, then installs or updates (skipping what you already have):

1. Python 3, Ollama, whisper.cpp, ffmpeg, cliclick, espeak-ng, Node.js (with Homebrew)
2. the AI models from `server/config.json` (default `gemma3:4b` and `gemma4:e4b-it-qat`) with `ollama pull`
3. the Whisper speech model `ggml-large-v3-turbo-q5_0.bin` into `models/`
4. the small Swift helpers in `server/` (screen, scroll, displays, audio...)
5. `server/config.json`, your settings, copied from `server/config.example.json` with your first name filled in
6. a LaunchAgent (`com.khananiinnovations.buddy`) that starts the server at login and restarts it if it stops

At the end it prints your Mac's **Tailscale address** (like `100.101.102.103`). You need it in step 5.

Also install and sign in to Claude Code once, if you haven't:

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

```bash
claude
```

## Step 3: Allow the Mac permissions

macOS asks you to allow Buddy's server to control the Mac. Run the check to see what's missing:

```bash
./setup.sh --check
```

Open **System Settings › Privacy & Security** and allow these for the **Python** that `--check` prints
(something like `/opt/homebrew/Cellar/python@3.x/.../Python`). Click **+**, press **Cmd+Shift+G**, paste the path.

| Permission | Where | For |
|---|---|---|
| Accessibility | Privacy & Security › Accessibility | mouse, keyboard, scrolling, window control |
| Screen & System Audio Recording | Privacy & Security › Screen & System Audio Recording | live screen and sound on the phone |
| Automation | Privacy & Security › Automation (asked the first time) | controlling apps (System Events, Chrome, Finder) |
| Notifications | Notifications › Script Editor | the 6-digit pairing code appears as a notification |

After changing permissions, restart the server so it picks them up:

```bash
./setup.sh
```

Then `./setup.sh --check` shows what the running server itself sees.

## Step 4: Install the app on your phone

There is no ready-made APK to download: you build and install the APK yourself.

1. Install [Android Studio](https://developer.android.com/studio) on the Mac and open it once, so it sets up the Android SDK.
2. Build the APK:
   ```bash
   cd ~/buddy/android
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
   ```
   The APK is `android/app/build/outputs/apk/debug/app-debug.apk`.
3. Put it on the phone. With USB debugging on and the phone plugged in:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Or copy `app-debug.apk` to the phone any way you like, open it, and allow installs from that app when Android asks.
4. Install **Tailscale** from the Play Store and sign in with the same account as the Mac.

More detail: [Build the app yourself](../README.md#build-the-app-yourself).

## Step 5: Pair your phone

1. Open **Buddy** on the phone, go to **Mac** (Mac control).
2. On the pairing card, type your Mac's Tailscale address from step 2 (like `100.101.102.103`; `:8787` is added for you).
   At home on the same Wi-Fi the app can also find the Mac by itself.
3. Tap **Pair now**. A notification with a **6-digit code** appears on the Mac.
4. Type the code in the app (within 2 minutes; 5 tries). Done: the phone now holds a secret only your Mac knows.

To unpair, tap **Unpair** in the app. To remove all paired phones, delete `server/devices.json` on the Mac.

## Step 6: Turn on features

Everything personal is in **`server/config.json`**. Open it in any editor:

```bash
open -e ~/buddy/server/config.json
```

Change what you need (see [Settings reference](#settings-reference-serverconfigjson)), save, then restart the server:

```bash
./setup.sh
```

English practice, the Mac assistant, Mac control, file transfer, storage cleaner, routines, money book and the morning
brief work right away. The optional features below need a little more setup.

## Feature setup

### Mac assistant (Claude Code)

- Install Claude Code (step 2) and run `claude` once to sign in. Buddy runs it from `~/.local/bin/claude`.
- For tasks in websites, install the **Claude in Chrome** extension in Chrome and sign in.
- Set `"projects_dir"` to the folder with your code projects, so "open my project X" works.
- The agent has full control of the Mac except for blocked commands (`rm`, `sudo`, `diskutil`, keychain, ssh keys,
  Buddy's own server folder...). It never sends messages, enters passwords or pays without your OK.

### YouTube Studio and autopilot

These screens drive **your own video project**: a folder (config `"youtube.factory_dir"`) with the scripts that plan,
make, edit and upload videos. **It is not part of this repository**; Buddy shows it on the phone and tells Claude to
run it. The folder is expected to contain:

- `studio/`: a local dashboard served at `"youtube.studio_url"` (default `http://127.0.0.1:8765`) with `studio/data/`
  (`latest.json`, `calendar.json`, `schedule.json`, `flow_queue.json`) and `studio/agent.py`
- `daily/` (made videos), `uploads/pending.json` (videos waiting for your OK), `approvals.sh`
- `HANDOFF.md`, `FLOW_RULES.md`, `PROMPT_BIBLE.md` (instructions Claude follows in Google Flow)
- `youtube_upload.py` (YouTube API sign-in) and a Python with its packages (`"youtube.python"`)

Then set in `config.json`:

```json
"chrome":  { "profile_dir": "Profile 1", "profile_name": "Work", "google_account": "you@gmail.com" },
"youtube": { "channel_name": "My Channel", "channel_id": "UCxxxxxxxxxxxxxxxxxxxxxx", "about": "kids' stories, Shorts and 3-minute videos",
             "factory_dir": "~/my-videos", "python": "~/my-videos/.venv/bin/python",
             "skill_file": "~/.claude/scheduled-tasks/daily-video/SKILL.md" }
```

- `profile_dir` is the Chrome profile folder: open `chrome://version` in that profile and look at **Profile Path**.
- That Chrome profile must be signed in to Google Flow (Google AI Pro) and have the Claude in Chrome extension.
- Autopilot is **off** by default. Turn it on in the app (Studio › **Autopilot day**) and pick a time and a credit limit.
  It never uploads by itself: you approve each video in the app first.

### Song / naat videos

1. Make the song in [Suno](https://suno.com) (the app can write the lyrics and Suno style for you).
2. In the app, open the song and tap **Import song from phone**. Projects are kept in `"songs.project_dir"`.
3. Tap **Make the video**: Claude makes it in Google Flow with the credit budget you pick (same Chrome needs as YouTube).

### Etsy Store Studio

- Point `"etsy.data_dir"` to your Trend Studio data folder (`report.json` and snapshots, one entry per store).
- Design images are made in Gemini in your Chrome profile (Google AI Pro). Text answers use the Antigravity CLI
  (`~/.local/bin/agy`) if installed, else Claude.

### CV Studio

- Keep your CV profile in `"cv.kit_dir"`; tailored CVs are saved in its `tailored/` folder.
- PDFs need Node.js and `pdfkit` (setup.sh installs both into `server/node_modules`).

### Offline mode (phone)

The phone can download a small model (Qwen 2.5 1.5B, MediaPipe `.task`, ~1.6 GB) from the Mac once. Put the file at
`server/models/qwen2.5-1.5b-q8.task` on the Mac (for example a Qwen2.5-1.5B-Instruct q8 `.task` file from the
[LiteRT community on Hugging Face](https://huggingface.co/litert-community)), then tap **Download the model** in
Settings while on home Wi-Fi.

### Wake and unlock

- Wake-on-LAN works on the home network when **System Settings › Energy › Wake for network access** is on.
- Keep awake (More › Settings in the app) stops the Mac from sleeping while Buddy needs it.

## Settings reference (`server/config.json`)

| Key | Default | Meaning |
|---|---|---|
| `owner_name` | your macOS first name | the app greets you with it |
| `port` | `8787` | server port; if you change it, type `address:port` in the app |
| `bonjour` | `true` | announce the Mac on home Wi-Fi so the app finds it |
| `tailscale_ip` | empty = auto | fix the Mac's Tailscale address |
| `allowed_ips` | empty | if set, only these phone IPs (plus home Wi-Fi) may ask to pair |
| `lan_prefixes` | private ranges | what counts as your home network |
| `timezone_label` | the Mac's zone | time zone in words for the AI, e.g. "Pakistan time, UTC+5" |
| `models.fast`, `models.slow` | `gemma3:4b`, `gemma4:e4b-it-qat` | Ollama models for replies and explanations |
| `whisper.cli`, `whisper.model` | Homebrew path, `models/ggml-large-v3-turbo-q5_0.bin` | speech to text |
| `chrome.*` | `Default` | Chrome profile for Flow, YouTube Studio, Gemini |
| `youtube.*` | empty | channel and video project, see above |
| `songs.project_dir` | `~/song-factory/naat` | song projects |
| `etsy.data_dir` | `~/TrendStudio/data` | Trend Studio data |
| `cv.kit_dir`, `cv.node` | `~/Documents/cv-kit`, auto | CV Studio |
| `projects_dir` | `~/Documents/projects` | your code projects, for the Mac assistant |

Everything the server saves while running (jobs, events, money book, practice backup, paired phones...) stays in
`server/` and is git-ignored: `git pull` never touches it.

## Troubleshooting

**Run `./setup.sh --check` first.** It lists what is missing and how to fix it.

| Problem | Fix |
|---|---|
| App says the Mac is offline | Is Tailscale on (phone and Mac)? Is the address right (Settings › Connection)? On the Mac: `curl http://<tailscale-ip>:8787/health` should answer `"ok": true`. |
| Server not running | `launchctl print gui/$(id -u)/com.khananiinnovations.buddy`, then read `server/server.log`. Restart with `./setup.sh`. |
| "Port 8787 is already used" | Another program uses it. Set `"port": 8788` in config.json, run `./setup.sh`, type `100.x.y.z:8788` in the app. |
| No pairing code appears | Allow notifications for **Script Editor** (System Settings › Notifications). Focus mode can hide it too. |
| "This device is not allowed" when pairing | The phone must be on Tailscale or the same Wi-Fi. If you set `allowed_ips`, add the phone's Tailscale IP (Tailscale app › the phone). |
| "Not paired" after it worked | The Mac's `server/devices.json` was deleted or the app's data was cleared: pair again. Check the phone's clock is right (signatures expire after 60 s). |
| Live screen is black or fails | Screen Recording is off for Python (step 3). Restart the server after allowing it. |
| Clicks/typing don't work | Accessibility is off for Python (step 3). |
| Coaching replies fail | The model in `models.fast` isn't downloaded: `ollama list`, then `ollama pull <model>` or change config.json. Is Ollama running? `brew services start ollama` |
| Voice input says nothing | Check `whisper.model` exists (`./setup.sh` downloads it) and `whisper-cli` is installed. |
| Mac assistant does nothing | Run `claude` in Terminal once to sign in; check `~/.local/bin/claude` exists. |
| Studio / Videos screens are empty | They need your own video project, see [YouTube](#youtube-studio-and-autopilot). |
| App won't install | Allow installs from your browser/file manager; uninstall an older Buddy build signed with a different key. |

To stop Buddy's server and remove the LaunchAgent (your files stay):

```bash
./setup.sh --uninstall-agent
```
