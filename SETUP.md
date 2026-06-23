# Tablet Auto Answer — Setup Guide

End-to-end guide for getting hands-free WhatsApp + cellular auto-answer working on a Coopers of Stortford "Easy to Use" Android tablet (or any other Android 8+ tablet), with audio routed to an Amazon Echo via Bluetooth and optional video mirroring to a TV via Google TV Streamer.

This is the operational manual. Follow it top-to-bottom on first install.

---

## 1. What this is

A custom Android app that:

- Auto-answers incoming **WhatsApp** voice and video calls (notification action + accessibility tap fallback)
- Auto-answers incoming **cellular SIM** calls if the caller is on a whitelist
- Speaks the **caller's name through the Echo** before answering ("Call from John") so the resident knows who is calling
- Plays a **loud chime** through the Echo when a call arrives
- **Auto-ends the call** after configurable minutes of silence (prevents open-mic situations)
- Pings **healthchecks.io** twice a day so you get an email if the system stops working
- Accepts **WhatsApp remote commands** from family admins (`/aa status`, `/aa dnd 2h`, `/aa pause`)
- Exposes an **in-browser debug dashboard** for remote troubleshooting

## 2. Hardware checklist

| Item | Spec / model | Notes |
|---|---|---|
| Tablet | Android 8+ with Google Play Services. Stock-Android variants preferred. | Coopers of Stortford "Easy to Use" tablet (Android 14) works — actual hardware is a rebadged Hyundai P634. Mid-range Samsung Galaxy Tab A9 is the recommended upgrade if reliability is critical. |
| Tablet charger + dock | Permanent power | These budget tablets can't survive a day in standby. Plug in always. |
| Bluetooth speaker | Amazon Echo (any) | Plays caller's voice. **Cannot** carry resident's voice back (no HFP) — that goes via tablet's built-in mic. |
| Wi-Fi | 2.4 / 5 GHz | Required for WhatsApp and heartbeat pings. |
| SIM | Cellular service if you want to auto-answer SIM calls | Smarty SIM works. |
| **Optional**: Google TV Streamer 4K | ASIN B0DBLWTQCT (~£99) | Mirrors tablet display to a TV via Google Cast. Cheaper alternative: Chromecast with Google TV HD (B0BG6MX1CG, ~£40). **Do NOT buy generic "Miracast" dongles** — Spreadtrum chipset's Miracast support is unreliable. |
| **Optional**: Better speakerphone | Jabra Speak 510 (~£100) or Anker PowerConf S3 | If two-way audio quality matters; replaces Echo for calls. Echo can stay for music. |

### Placement guidance

- Tablet **within 1 m** of where the resident sits
- Tablet **screen up**, nothing covering the bottom edge (mic holes)
- Echo **within Bluetooth range** (~10 m line of sight)
- Tablet **permanently on charger**

## 3. Build the APK

### One-time setup

1. Install **Android Studio** from developer.android.com/studio
2. Run the first-launch wizard, accept SDK licences, let it download (~5 GB, ~15 min)
3. **File → Open** → select `C:\Users\simon\tablet-autoanswer`
4. Wait for Gradle sync (~5–15 min first time, much faster after)

### Build the APK

From Android Studio menu: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**

When it finishes, the bottom-right notification has a `locate` link. The file is at:

```
C:\Users\simon\tablet-autoanswer\app\build\outputs\apk\debug\app-debug.apk
```

If Android Studio's GUI is unavailable, build from a terminal using its bundled Gradle and JBR (Windows-Bash syntax):

```bash
cd /c/Users/simon/tablet-autoanswer
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  /c/Users/simon/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle.bat \
  assembleDebug
```

## 4. Install on the tablet

### Get the APK onto the tablet

**Easiest path: Google Drive.** Gmail's APK scanner blocks `.apk` attachments (even inside `.zip` archives), so go via Drive.

1. On your PC: open **drive.google.com**, drag `app-debug.apk` into the browser
2. On the tablet: open the **Drive** app, tap `app-debug.apk`, three-dot menu → **Download**
3. Open the **Files** app → **Downloads** → tap `app-debug.apk` to install

### First install — allow unknown apps

Android will say *"For your security, your tablet currently isn't allowed to install unknown apps from this source"*.

1. Tap **Settings** in that dialog → toggle **Allow from this source** for the Files app → back arrow
2. Tap the APK again → **Install**
3. If Play Protect warns *"App not checked"* → tap **More details** → **Install anyway**

## 5. First-run permissions wizard

Open the app. You'll see a status screen with red rows for each missing permission. Tap each **Fix** button in order.

### 5.1 Accessibility service ⚠ Android 14 catch

The first thing you'll hit: Android 14 **Restricted Settings** blocks Accessibility access for sideloaded apps with no obvious "fix" button.

**Workflow when it triggers:**

1. App opens Settings → Accessibility → tap Auto Answer
2. You toggle it on → Android pops *"Restricted setting. For your security, this setting is currently unavailable."*
3. **Back out** of Settings
4. **Settings → Apps → Auto Answer**
5. Tap the **⋮ (three-dot menu, top right)** → **Allow restricted settings**
6. Return to **Accessibility → Auto Answer** → toggle now works

The same unlock applies to **Notification access**.

### 5.2 Remaining required permissions

- **Notification access** (same Restricted-Settings unlock if needed)
- **Battery: unrestricted** — confirm the system dialog
- (Recommended) **Display over other apps** — toggle on
- (Recommended) **Stay awake while charging** — opens Developer Options. If Developer Options isn't visible: Settings → About tablet → tap **Build number** seven times to unlock it.

### 5.3 Make sure WhatsApp's notifications are visible

Tap **Open WhatsApp notification settings** in the app → make sure the **Calls** notification channel is enabled with **Heads-up / Pop-up** style. If WhatsApp's call notification doesn't pop up, the app can't see it.

## 6. Configure features

The app's home screen is sectioned. Work through each section.

### 6.1 Master switch + answer delay

- **Enabled** → on
- **Answer delay** → 1500–2500 ms is sensible. Gives the chime/ringtone a moment to alert anyone nearby before the call answers.
- **Test mode** → leave **off** for real use; turn on when validating behaviour without bothering callers.

### 6.2 Echo audio routing

- **Force Bluetooth audio** → on (default)

Before relying on this, **test routing manually**: ask someone to WhatsApp-call the tablet, answer it, confirm caller's voice plays through Echo. If it goes to the tablet speaker, tap the speaker/Bluetooth icon during the call and pick the Echo — the app pins routing automatically once it sees Echo as the active Bluetooth media device.

**Limitation to internalise**: Echo plays the caller's voice (A2DP). The resident's voice goes to the caller via the **tablet's built-in mic** because Echo doesn't support the Bluetooth HFP profile. Tablet placement matters.

### 6.3 Cellular auto-answer + whitelist

Only enable if you want SIM calls auto-answered.

1. Toggle **Auto-answer cellular calls** → Android requests Phone + Contacts permissions; grant them
2. **Whitelist only (blocks spam)** → keep ON (default). With this off, any incoming SIM call auto-answers — risky for spam.
3. **Whitelisted phone numbers** → comma-separated, international format preferred:
   ```
   +447712345678, +447898765432, +442012345678
   ```
   The app matches on the last 7 digits, so the format is forgiving.
4. Tap **Save**

**Audio caveat for cellular**: Cellular calls route via Bluetooth HFP, which Echo doesn't support. The app forces speakerphone-on so call audio plays through the **tablet's own loudspeaker** (not the earpiece). Echo stays silent for cellular calls.

### 6.4 TTS announce + chime

- **Announce caller by name (TTS)** → on. Speaks "Call from X" through Echo at high volume before answering.
- **Loud chime when call arrives** → on. Plays a generated ding-dong (880Hz → 587Hz) through Echo before the announcement.

Both override WhatsApp's quiet ring. Volume is forced to 90% of max on the music stream.

### 6.5 Auto-hangup on silence

Closes the open-mic risk: if the resident wanders away mid-call, the call shouldn't stay live indefinitely.

1. Toggle **Auto-hangup on silence** → Android requests Microphone permission; grant it
2. **Silence timeout** slider → default 5 minutes. The app monitors mic levels every 5 sec; if peak audio doesn't exceed a threshold for the configured duration, the call ends.

Hard ceiling: any call is capped at 2 hours regardless of voice activity (safety net).

### 6.6 Heartbeat to healthchecks.io

For email/SMS alerts if the system stops working.

1. Sign up at **healthchecks.io** (free, no card)
2. **Add Check** → name "Tablet Auto-Answer"
3. Schedule: **every 12 hours, grace 4 hours** (catches a missed cycle)
4. **Integrations** → add your email (and Slack/SMS if you like)
5. Copy the **Ping URL** — looks like `https://hc-ping.com/abc-def-uuid`
6. In the app: **Heartbeat URL** field → paste → tap **Send test ping now**
7. Refresh healthchecks.io page → should turn green within 30 seconds

What the app POSTs to that URL: a plain-text snapshot of every diagnostic field. The healthchecks.io dashboard shows it in the "last ping" details. The ping URL is hit if all required permissions are green; the `/fail` variant is hit if any required permission is missing.

### 6.7 WhatsApp remote commands

For remotely toggling state without physical access.

1. Toggle **Allow remote commands** → on
2. **Admin contacts (whitelist)** → your WhatsApp display names exactly as they appear in the tablet's WhatsApp (case-insensitive substring match). Example:
   ```
   Simon Knowles, Kev
   ```
3. Tap **Save**

From your WhatsApp on your phone, send the tablet:

| Command | Effect |
|---|---|
| `/aa status` | Triggers an immediate healthchecks.io ping (so you can see "is it alive?" from the dashboard) |
| `/aa pause` or `/aa off` | Disable auto-answer entirely |
| `/aa resume` or `/aa on` | Re-enable |
| `/aa dnd 2h` | Do Not Disturb for 2 hours |
| `/aa dnd 30m` | Do Not Disturb for 30 minutes |
| `/aa dnd off` | Clear DND |
| `/aa test` | Toggle test mode |
| `/aa ping` | Same as `status` |

The app doesn't reply on WhatsApp (Android third-party apps can't programmatically send WhatsApp messages). Verify by checking healthchecks.io or the debug dashboard.

### 6.8 Debug dashboard

A small HTTP server inside the app that serves a live status page.

1. Toggle **Debug dashboard** → on
2. The app displays the URLs:
   - `http://localhost:8765` — open in the tablet's own Chrome
   - `http://<tablet-ip>:8765` — open from any device on the same Wi-Fi
3. Bookmark the LAN URL on your phone / laptop

**The dashboard shows**: traffic light status, all permission states, Bluetooth state, cellular config, DND, healthcheck config, the rolling log, plus buttons to test the chime / TTS / heartbeat / DND without making a real call.

**Security**: no authentication. Anyone on your home Wi-Fi can reach it. **Turn the toggle off when not actively debugging.** The persistent notification shows `· debug :8765` to remind you when it's on.

## 7. Test sequence

Run this end-to-end on first install AND any time you change permissions.

1. **Test mode ON** in the app
2. Open the **Debug dashboard** from your laptop — confirm traffic light is GREEN
3. Click **Play chime** on the dashboard → ding-dong should play through Echo
4. Click **Speak TTS** with name "Mum" → Echo says "Call from Mum"
5. Click **Send heartbeat** → healthchecks.io shows the ping within 30 sec
6. From your phone, **WhatsApp-call** the tablet → expect chime + TTS + toast "Test mode: would auto-answer WhatsApp from '<your name>'"
7. **Test mode OFF**
8. WhatsApp-call again → chime + TTS + call answers automatically → speak from where resident will sit, confirm caller hears you clearly → end call from your phone
9. If cellular enabled, place a SIM call from a **whitelisted** number → chime + TTS + answers, audio on tablet loudspeaker
10. Place a SIM call from a **non-whitelisted** number → tablet rings normally, no auto-answer (correct)
11. Tap a **DND 1h** button → call again → tablet rings, banner shows DND active, no auto-answer (correct)
12. From your admin WhatsApp: `/aa dnd off` → DND banner clears within a few seconds
13. **Turn off Debug dashboard** (security)

## 8. TV mirroring (Google TV Streamer)

Only relevant if you've bought a Google TV Streamer 4K (B0DBLWTQCT) or Chromecast with Google TV HD.

1. **Plug in**: HDMI → TV; USB-C power → outlet
2. **TV input** → switch to that HDMI source
3. **On-screen wizard**: pair remote, connect Wi-Fi (same SSID as tablet), sign in to Google
4. Name device something obvious like `Living Room TV`
5. **On the tablet**: Quick Settings → **Cast Screen** → pick `Living Room TV`
6. Choose **Mirror display only** (audio stays on Echo) OR **display + audio** (TV speakers take over). Picking display-only is the recommended pattern: caller's voice from Echo, video on TV.
7. **Leave mirroring running**. Tablet's screen now appears on the TV always. When a call answers, the call UI appears on the TV too.

If mirroring drops after a Wi-Fi blip, re-tap **Cast Screen** → re-pick the TV. 5 sec, no reboot.

## 9. Troubleshooting

Use the debug dashboard first — it surfaces 80% of issues. For the remaining 20%, work the table.

### Detection (call rings but doesn't auto-answer)

| Symptom | Likely cause | Fix |
|---|---|---|
| Tablet rings, never answers | Notification listener not bound | Settings → Apps → Auto Answer → ⋮ → Allow restricted settings; re-enable Notification access |
| Tablet rings, accessibility tries but call doesn't answer | WhatsApp UI changed (Answer button view ID renamed) | Use debug dashboard → check log for "Element not found"; update `ID_HINTS` in `CallAccessibilityService.kt`; rebuild |
| Detection fires sometimes but not always | WhatsApp call notification has been silenced or grouped | Settings → Apps → WhatsApp → Notifications → Calls → Pop on screen ON; check Do Not Disturb isn't filtering it |
| Cellular ringing detected (test mode says so) but live call not answered | `acceptRingingCall()` failed silently | Settings → Apps → Auto Answer → Permissions → enable Phone and Contacts; reboot tablet (the API can hold onto a stale state) |

### Audio (call answers but sound is wrong)

| Symptom | Likely cause | Fix |
|---|---|---|
| Caller's voice plays from tablet speaker, not Echo | Bluetooth A2DP not active OR `setCommunicationDevice()` couldn't grab Echo | Reconnect Bluetooth from Settings; debug dashboard's Audio panel shows "A2DP connected" state |
| Caller's voice routes correctly but resident can't be heard | Tablet's mic too far / covered | Move tablet within 1 m, screen up, mic holes uncovered |
| TTS is silent (no "Call from X") | Music stream volume muted OR TTS engine not installed | Settings → Languages & input → Text-to-speech output → Google TTS installed and set as preferred |
| Chime is silent | Same as TTS | Same fix |
| Cellular call answers but audio goes to tablet earpiece, very quiet | Speakerphone wasn't forced | Check Audio Mode in debug log; on call screen tap speaker icon manually as workaround |

### Cellular auto-answer never triggers

| Symptom | Likely cause | Fix |
|---|---|---|
| Cellular toggle is on but nothing happens on incoming SIM call | Restricted Settings blocking permissions | Settings → Apps → Auto Answer → ⋮ → Allow restricted settings; re-grant Phone + Contacts |
| Whitelisted number rings but doesn't auto-answer | Number format mismatch | Confirm last 7 digits match. Caller display showing as "Withheld" → cannot match |

### Background killing (works at first, stops after hours/days)

| Symptom | Likely cause | Fix |
|---|---|---|
| Auto-answer stops working overnight, resumes when you tap the app | Battery optimisation re-imposed | Settings → Battery → Auto Answer = Unrestricted (re-check weekly for a month) |
| Persistent notification disappears | Foreground service killed | Reboot tablet; if it recurs, OEM aggressive power management — investigate manufacturer-specific autostart settings |

### Network

| Symptom | Likely cause | Fix |
|---|---|---|
| Heartbeat URL test ping returns error | URL pasted with trailing spaces / wrong UUID | Trim whitespace, copy-paste again from healthchecks.io dashboard |
| Heartbeat works initially, then stops | Tablet lost Wi-Fi | Debug dashboard → Audio panel will also show false flags; physically check router |
| Healthchecks.io alerts that "tablet hasn't pinged" | Tablet rebooted and didn't auto-restart properly OR battery optimisation killed WorkManager | Reboot tablet, walk permission wizard again |

### WhatsApp updates broke detection

About once every few months WhatsApp ships a UI change that renames the Answer button's view ID. The notification-action path (the primary detection mechanism) keeps working because Android's notification action API is stable — but the accessibility-tap fallback breaks until updated.

**To diagnose**: debug dashboard log → search for "Element not found".

**To fix**:
1. Make a test WhatsApp call to the tablet
2. While call is ringing, use Android Studio's Layout Inspector OR run `adb shell uiautomator dump` against the tablet
3. Find the Answer button's new view ID (something like `com.whatsapp:id/accept_call_btn` → `com.whatsapp:id/accept_incoming_call_view`)
4. Add the new ID to `ID_HINTS` in `app/src/main/kotlin/com/simon/autoanswer/service/CallAccessibilityService.kt`
5. Rebuild + reinstall

### Debug dashboard unreachable

| Symptom | Likely cause | Fix |
|---|---|---|
| Browser shows "ERR_CONNECTION_REFUSED" | Server not running | Toggle Debug dashboard off and on in the app; check persistent notification mentions `· debug :8765` |
| localhost works but LAN IP doesn't | Tablet on different Wi-Fi or AP isolation enabled | Confirm both devices same SSID; check router AP isolation setting |
| Dashboard loads but says "SERVER UNREACHABLE" | Server died after page load | Reboot tablet |

### Remote commands not working

| Symptom | Likely cause | Fix |
|---|---|---|
| `/aa status` typed in WhatsApp but tablet doesn't respond | Sender's WhatsApp display name doesn't match admin list | Find your name as the tablet sees you in WhatsApp; update admin contacts; case is ignored but substring must match |
| Command appears to fire but state doesn't change | Notification listener not running | Walk permission wizard |

## 10. Maintenance checklist

Do this monthly:

- [ ] Visit healthchecks.io — confirm green ticks for the last 30 days
- [ ] WhatsApp-call the tablet → confirm chime + answer + audio
- [ ] Open debug dashboard → confirm all required green
- [ ] Battery still showing "Unrestricted" for Auto Answer
- [ ] No accumulated unused apps competing for resources
- [ ] Tablet's clock + timezone are correct (affects DND scheduling)

Quarterly:

- [ ] WhatsApp updated — if so, run a test call; if accessibility fallback fires (debug log), the primary path is broken and a code update is overdue
- [ ] Android security updates applied
- [ ] Echo firmware up to date (Alexa app → Devices)

## 11. Hardware limits to remember

These are **fundamental** — no software change will fix them.

| Limit | Implication |
|---|---|
| Echo supports A2DP only, not HFP | Caller's voice plays from Echo; resident's voice goes to caller via tablet mic. Position tablet ≤ 1 m from resident. |
| Cellular voice uses HFP | Cellular calls play from tablet speaker, not Echo. Always. |
| Tablet's USB-C is data/charge only | No HDMI output. Use Google TV Streamer + Cast Screen for TV display. |
| Spreadtrum chipset has flaky Miracast | Stick to Google Cast (Chromecast / Google TV Streamer). Avoid generic "Miracast" dongles. |
| 2 GB RAM on the Coopers tablet | Don't install many other apps; one heavy app + the auto-answer stack + WhatsApp + Cast is the practical ceiling. |
| Tablet's built-in mic has poor pickup beyond ~1 m | If resident moves around the room, replace Echo with a Jabra Speak 510 for proper omnidirectional pickup. |

## 12. Quick reference

### File locations

```
C:\Users\simon\tablet-autoanswer\               — project root
  README.md                                     — brief, points here
  SETUP.md                                      — this guide
  CLAUDE.md                                     — context for future Claude sessions
  app\build\outputs\apk\debug\app-debug.apk     — built APK
  app\src\main\kotlin\com\simon\autoanswer\
    service\CallNotificationListener.kt         — main detection
    service\CallAccessibilityService.kt         — fallback tap (UPDATE THIS when WhatsApp UI changes)
    service\CellularAnswerer.kt                 — SIM call handling
    service\WatchdogService.kt                  — foreground service + persistent notification
    debug\DebugServer.kt                        — embedded HTTP server
    debug\Dashboard.kt                          — the HTML/JS dashboard page
```

### Build command (Bash on Windows)

```bash
cd /c/Users/simon/tablet-autoanswer
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  /c/Users/simon/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle.bat \
  assembleDebug
```

### WhatsApp commands

```
/aa status        ← ping healthchecks.io now
/aa pause         ← disable auto-answer
/aa resume        ← re-enable
/aa dnd 2h        ← Do Not Disturb for 2 hours
/aa dnd off       ← clear DND
/aa test          ← toggle test mode
```

### Default ports / URLs

```
Debug dashboard       http://localhost:8765
                      http://<tablet-lan-ip>:8765
Healthchecks.io       https://hc-ping.com/<your-uuid>
```
