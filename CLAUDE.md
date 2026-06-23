# CLAUDE.md — tablet-autoanswer

Context for any Claude session working in this directory. **This is a personal-use side project, unrelated to `by-athena`.** If you're picking this up from a fresh session, read this whole file before touching code.

---

## 1. What this project is

A native Android app (Kotlin + Jetpack Compose, minSdk 26 / targetSdk 34) that auto-answers incoming WhatsApp and cellular calls on a Coopers of Stortford budget Android tablet, so an elderly resident can take calls hands-free without touching the screen. Audio routes to a Bluetooth-paired Amazon Echo.

It is **not** a Play Store app — it's sideloaded onto a single device. Don't worry about Play Store policy compliance (Accessibility Service restrictions, READ_CALL_LOG restrictions, etc. — sideload-only).

The end-user workflow is documented in `SETUP.md`. Read that before suggesting any UX change.

## 2. Target hardware (important for design decisions)

- **Tablet**: Coopers of Stortford "Easy to Use" — actual `Build.MODEL` is `Hyundai P634` (Coopers rebrand a Hyundai-branded budget tablet). Android 14, 1280×800 display, USB-C charge-only (no DisplayPort Alt Mode), has Google Play Store and Google Mobile Services. Older Coopers SKUs used the Spreadtrum SC7731E chipset; same architectural constraints apply.
- **Bluetooth speaker**: Amazon Echo (any). **Critical**: Echo supports A2DP only, NOT HFP. Caller's voice plays from Echo, resident's voice goes via tablet's built-in mic. This is a hardware limit — no app code can fix it.
- **TV mirroring (optional)**: Google TV Streamer 4K (ASIN B0DBLWTQCT) via Google Cast screen mirroring. **Do not recommend Miracast adapters** — Spreadtrum's Miracast support is unreliable.

## 3. Architecture

```
Application: com.simon.autoanswer.AutoAnswerApp
  ├── starts WatchdogService (foreground, persistent notification)
  ├── installs CrashLog uncaught-exception handler
  └── schedules HeartbeatScheduler (WorkManager, every 12h)

WatchdogService (foreground service, type "specialUse")
  ├── posts "Auto Answer is running" notification (low importance, ongoing)
  ├── ticks every 5 min to refresh notification text
  └── observes prefs.debugServerEnabled → starts/stops DebugServer

CallNotificationListener (NotificationListenerService)
  ├── primary WhatsApp detection — finds "Answer" PendingIntent in notification actions, fires it
  ├── parses /aa remote commands from admin contacts (RemoteCommandParser)
  ├── owns a CellularAnswerer instance (telephony state)
  └── stops SilenceWatchdog when WhatsApp call notification removed

CallAccessibilityService (AccessibilityService)
  ├── fallback path when notification listener can't find Answer action
  ├── triggered by AnswerState.arm()
  └── scrapes WhatsApp call screen for Answer button (by view ID, text, or swipe gesture)

CellularAnswerer (held by CallNotificationListener)
  ├── registers legacy PhoneStateListener (gets caller number — needs READ_CALL_LOG)
  ├── on RINGING: checks DND, whitelist, runs TTS, calls TelecomManager.acceptRingingCall()
  └── forces speakerphone-on (cellular doesn't go via Echo)

SilenceWatchdog
  ├── AudioRecord polling for voice activity during a call
  ├── ends call via TelecomManager.endCall() after N minutes of silence
  └── safety cap: 2-hour absolute max call duration

DebugServer (NanoHTTPD, port 8765 by default)
  ├── GET /              → Dashboard.PAGE (embedded HTML/JS, single page)
  ├── GET /status.json   → live diagnostic snapshot
  ├── GET /log.txt       → CrashLog content
  └── POST /action/{name} → chime / tts / heartbeat / dnd / toggle-*
```

### Layering

```
ui (Compose) → data (Prefs, ContactLookup) → service → audio/tts/diag
                                           ↘ work (WorkManager)
                                           ↘ debug (NanoHTTPD)
```

`Prefs` is a singleton SharedPreferences wrapper exposing `StateFlow`s. All settings live there. Do not invent a second persistence mechanism.

## 4. Key files and what they own

| File | Owns |
|---|---|
| `app/src/main/AndroidManifest.xml` | All permissions, service declarations, FileProvider, BootReceiver |
| `service/CallNotificationListener.kt` | Primary WhatsApp + cellular orchestration. WhatsApp answer dispatch logic. Wires chime, TTS, silence watchdog. |
| `service/CallAccessibilityService.kt` | Fallback UI-scraping for WhatsApp call screen. **`ID_HINTS` list breaks when WhatsApp updates — this is where you fix it.** |
| `service/CellularAnswerer.kt` | SIM call detection + whitelist check + accept |
| `service/SilenceWatchdog.kt` | Mic monitoring + auto-hangup |
| `service/WatchdogService.kt` | Foreground service + persistent notification + debug server lifecycle |
| `service/BootReceiver.kt` | Re-starts watchdog + heartbeat scheduler on boot |
| `service/RemoteCommandParser.kt` | `/aa` command grammar |
| `audio/AudioRouter.kt` | `setCommunicationDevice()` to pin to BT A2DP for VoIP |
| `audio/Chime.kt` | Generated ding-dong PCM (880Hz → 587Hz) |
| `tts/CallerAnnouncer.kt` | TTS "Call from X" with forced volume |
| `data/Prefs.kt` | All settings — `StateFlow`-backed |
| `data/ContactLookup.kt` | Phone number normalisation + match logic |
| `diag/CrashLog.kt` | Rolling 256 KB log file at `<filesDir>/crash.log` |
| `diag/DiagnosticChecks.kt` | Snapshot of permission + audio + network state |
| `work/HeartbeatWorker.kt` | Posts diag report to healthchecks.io URL (every 12h ± 1h) |
| `debug/DebugServer.kt` | NanoHTTPD wrapper exposing dashboard + actions |
| `debug/Dashboard.kt` | Self-contained HTML/JS dashboard (single string) |
| `debug/NetworkAddress.kt` | LAN IP discovery for display |
| `ui/HomeScreen.kt` | Main Compose UI — all configuration |
| `ui/PermissionStatus.kt` | Permission checks + intents to Android Settings |
| `ui/HomeScreenComponents.kt` | Reusable Compose cards/rows |

## 5. Build commands

Project uses Android Gradle Plugin 8.7.0, Kotlin 2.0.21, Compose Compiler plugin, JDK 17. Build via Android Studio menu OR from command line using Android Studio's bundled JBR + cached Gradle:

```bash
cd /c/Users/simon/tablet-autoanswer
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  /c/Users/simon/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle.bat \
  assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk` (~22 MB). Debug-signed; fine for sideload.

To distribute via Gmail, **Gmail's APK scanner blocks** both raw `.apk` AND `.zip`-wrapped APKs. Use **Google Drive** instead.

## 6. Gotchas worth knowing

### Android 14 Restricted Settings

Sideloaded apps cannot be granted Accessibility or Notification access via the normal toggle — Android pops "Restricted setting" with no fix button. Unblock: Settings → Apps → Auto Answer → ⋮ → **Allow restricted settings**. The setup screen warns about this; future Compose code should not try to detect this state (no public API).

### WhatsApp updates break the accessibility fallback

WhatsApp renames the Answer button view ID every few months. Primary path (notification action `PendingIntent`) keeps working because notification API is stable. To update fallback IDs:

1. Make a test WhatsApp call
2. `adb shell uiautomator dump` or Android Studio Layout Inspector
3. Find the green "Answer" node's `resource-id`
4. Add to `ID_HINTS` in `CallAccessibilityService.kt`
5. Rebuild

### Echo audio routing

`setCommunicationDevice()` works on API 31+ to pin call audio to A2DP. Older API levels fall back to relying on system default routing, which is usually correct for VoIP (uses media stream → A2DP) but can fail for cellular (uses voice call stream → tries SCO/HFP → Echo can't accept → tablet speaker).

We don't try to force HFP on Echo — it doesn't support the profile. Cellular always falls back to tablet loudspeaker. This is documented as intentional, not a bug.

### Material3 experimental APIs

Several Material3 components (TopAppBar, etc.) are still experimental and trigger Kotlin compiler errors without opt-in. Top of `HomeScreen.kt` carries `@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)`. Add the same line to any new file that uses experimental Material3 APIs.

### NanoHTTPD on Android 14

Foreground services with type `specialUse` (Android 14 requirement) need a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` element in the manifest. We have that on `WatchdogService`. Don't strip it — the service won't start without it.

### File sharing via FileProvider

The crash log share intent uses `androidx.core.content.FileProvider` with authority `${applicationId}.fileprovider`. The provider declaration in the manifest + `res/xml/file_paths.xml` are tightly coupled — change one without the other and the share intent crashes the receiving app.

### TTS engine availability

`CallerAnnouncer` assumes Google TTS is installed and configured. Coopers tablet ships with it preinstalled but a user could uninstall or disable it. If TTS announce stops working, check `Settings → Languages & input → Text-to-speech output`.

## 7. What NOT to do

- **Don't** make this a Play Store app. The whole architecture assumes sideload and Restricted Settings unlock. Going to Play would require pulling Accessibility Service, READ_CALL_LOG, and probably ANSWER_PHONE_CALLS — gutting the app.
- **Don't** add a second persistence layer. Everything lives in `Prefs`. Use it.
- **Don't** try to programmatically start screen-mirroring to a Chromecast. Android blocks this for third-party apps. Tell the user to do it manually via Quick Settings → Cast Screen.
- **Don't** try to programmatically send WhatsApp messages (for command replies). Not possible without WhatsApp Business API. The current pattern — accept commands silently, verify via heartbeat / debug dashboard — is correct.
- **Don't** add fancy crash reporting (Firebase, Sentry). Local file + manual share is enough for a single-device deployment and avoids dependencies.
- **Don't** "modernise" the PhoneStateListener to `TelephonyCallback`. We need the caller number, which the modern `CallStateListener` doesn't provide. The deprecation warning is intentionally suppressed.

## 8. Future work that's been discussed but not done

- **Home-screen widget for DND toggle** — designed but not built. Would add `AppWidgetProvider` + remote views. Useful for non-technical caregivers.
- **Contact picker integration for whitelists** — currently comma-separated text field. Could use `ContactsContract` picker intent for friendlier UX.
- **Auto-update WhatsApp Answer button view ID via OTA config** — could fetch a JSON file from a personal server containing the latest known ID. Avoids needing a rebuild every time WhatsApp updates.
- **Caller photo display on TV mirror** — once TV mirroring is set up, currently shows whatever WhatsApp shows. A pre-answer overlay with a big caller photo would help if the resident has reduced vision.

## 9. Project-specific deployment

- Single end user (Simon's elderly relative)
- Tablet sits in their home
- Updates happen by re-building the APK and re-uploading to Drive, then asking a visitor or remote helper to install
- No CI, no automatic distribution

## 10. End-user-facing documentation

`SETUP.md` is the comprehensive user setup + troubleshooting guide. Keep it in sync with code changes — if you add a feature, add it to SETUP.md too.

`README.md` is a brief landing page pointing at SETUP.md and CLAUDE.md. Don't bloat it.

## 11. Memory continuity

A summary of this project lives in the user's personal memory store at `C:\Users\simon\.claude\projects\c--Users-simon-by-athena-1\memory\project_tablet_autoanswer.md` so that cross-session context survives across Claude conversations.
