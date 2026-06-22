# Tablet Auto Answer

Sideloaded Android app that auto-answers incoming WhatsApp and cellular calls on a budget tablet, so an unattended elderly resident doesn't have to touch the screen. Caller audio routes to a Bluetooth-paired Amazon Echo; resident's voice goes via the tablet's built-in mic.

Not a Play Store app — single-device sideload deployment.

## Documentation

- **[SETUP.md](SETUP.md)** — End-to-end setup, configuration, and troubleshooting guide. Start here if you want to get this working.
- **[CLAUDE.md](CLAUDE.md)** — Architecture, key files, gotchas. Read this if you're modifying the code (or are an AI assistant doing so).

## What's in the box

| Capability | Notes |
|---|---|
| Auto-answer WhatsApp voice + video | Primary path: notification action; fallback: accessibility tap |
| Auto-answer cellular calls | Optional, with phone-number whitelist to block spam |
| Echo audio routing | VoIP only — cellular always falls back to tablet loudspeaker (Echo doesn't support HFP) |
| TTS caller announcement | "Call from X" through Echo before answering |
| Loud distinctive chime | Overrides WhatsApp's quiet ring |
| Auto-hangup on silence | Closes open-mic risk |
| DND with quick-set durations | 1h / 3h / 8h / custom |
| Foreground watchdog + boot receiver | Survives reboots; resists battery-killer |
| Heartbeat to healthchecks.io | Email alert if the tablet stops working |
| WhatsApp remote commands | `/aa status`, `/aa dnd 2h`, `/aa pause`, etc. from admin contacts |
| In-browser debug dashboard | `http://<tablet-ip>:8765` — live status, log, test buttons |
| Crash log + share intent | Local rolling log, sharable via Drive/Gmail for diagnosis |

## Build & install (TL;DR — see SETUP.md for full steps)

```bash
# From Android Studio menu: Build → Build APK(s)
# Or from a terminal on Windows-Bash:
cd /c/Users/simon/tablet-autoanswer
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  /c/Users/simon/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle.bat \
  assembleDebug

# Output:
#   app/build/outputs/apk/debug/app-debug.apk  (~22 MB)
```

Distribute via Google Drive (Gmail blocks `.apk` attachments). Install on tablet, walk the in-app permission wizard, including the Android 14 Restricted Settings unblock for Accessibility + Notification access.

## Hardware limits (read these)

- **Echo can't act as a Bluetooth mic** — A2DP only, not HFP. Tablet placement matters: keep within ~1 m of where the resident sits.
- **Cellular calls don't route to Echo** — they use HFP, which Echo doesn't support. Audio plays from tablet's loudspeaker for cellular.
- **Coopers tablet's USB-C is charge-only** — no DisplayPort Alt Mode. TV mirroring requires a Google TV Streamer + Google Cast, NOT a USB-C-to-HDMI cable.

## Project layout

```
app/src/main/
  AndroidManifest.xml
  kotlin/com/simon/autoanswer/
    AutoAnswerApp.kt              — Application + notification channels
    MainActivity.kt               — Compose host (FLAG_KEEP_SCREEN_ON)
    audio/AudioRouter.kt          — Pin call audio to Bluetooth A2DP
    audio/Chime.kt                — Generated ding-dong PCM
    data/Prefs.kt                 — SharedPreferences + StateFlow settings
    data/ContactLookup.kt         — Phone number normalisation + matching
    debug/DebugServer.kt          — NanoHTTPD diagnostic server
    debug/Dashboard.kt            — Embedded HTML/JS dashboard
    debug/NetworkAddress.kt       — LAN IP discovery
    diag/CrashLog.kt              — Rolling 256 KB local log
    diag/DiagnosticChecks.kt      — System state snapshot
    service/AnswerState.kt        — Shared armed/consumed flag
    service/BootReceiver.kt       — Auto-restart on boot
    service/CallAccessibilityService.kt — Fallback UI scrape + tap
    service/CallNotificationListener.kt — Primary WhatsApp + remote-cmd dispatch
    service/CellularAnswerer.kt   — SIM call handling
    service/RemoteCommandParser.kt — /aa command grammar
    service/SilenceWatchdog.kt    — Mic monitor + auto-hangup
    service/WatchdogService.kt    — Foreground service + persistent notification
    tts/CallerAnnouncer.kt        — TTS "Call from X"
    ui/HomeScreen.kt              — Compose configuration UI
    ui/HomeScreenComponents.kt    — Reusable cards/rows
    ui/PermissionStatus.kt        — Permission checks + Settings intents
    ui/theme/Theme.kt
    work/HeartbeatWorker.kt       — Periodic ping to healthchecks.io
    work/HeartbeatScheduler.kt    — WorkManager scheduler
  res/
    xml/accessibility_service_config.xml
    xml/file_paths.xml
    values/{strings,themes,colors}.xml
    drawable/ic_launcher_foreground.xml
    mipmap-anydpi-v26/ic_launcher{,_round}.xml
```
