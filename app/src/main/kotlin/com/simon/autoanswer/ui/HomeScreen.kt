@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.autoanswer.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.debug.NetworkAddress
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.diag.DiagnosticChecks
import com.simon.autoanswer.work.HeartbeatScheduler
import com.simon.autoanswer.work.LogEmailScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val enabled by prefs.enabled.collectAsState()
    val delayMs by prefs.delayMs.collectAsState()
    val testMode by prefs.testMode.collectAsState()
    val forceBt by prefs.forceBluetoothAudio.collectAsState()
    val cellularEnabled by prefs.cellularEnabled.collectAsState()
    val cellularWhitelistOnly by prefs.cellularWhitelistOnly.collectAsState()
    val cellularWhitelist by prefs.cellularWhitelist.collectAsState()
    val dndUntil by prefs.dndUntilMs.collectAsState()
    val heartbeatUrl by prefs.heartbeatUrl.collectAsState()
    val ttsAnnounce by prefs.ttsAnnounce.collectAsState()
    val loudChime by prefs.loudChime.collectAsState()
    val autoHangup by prefs.autoHangupSilence.collectAsState()
    val autoHangupMins by prefs.autoHangupMinutes.collectAsState()
    val remoteCmd by prefs.remoteCmdEnabled.collectAsState()
    val adminContacts by prefs.adminContacts.collectAsState()
    val debugServerEnabled by prefs.debugServerEnabled.collectAsState()
    val debugServerPort by prefs.debugServerPort.collectAsState()
    val logEmailUrl by prefs.logEmailUrl.collectAsState()
    val logEmailEnabled by prefs.logEmailEnabled.collectAsState()

    var permTick by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycle.addObserver(observer)
    }

    val a11y = remember(permTick) { PermissionStatus.isAccessibilityEnabled(context) }
    val notif = remember(permTick) { PermissionStatus.isNotificationListenerEnabled(context) }
    val battery = remember(permTick) { PermissionStatus.isIgnoringBatteryOptimizations(context) }
    val overlay = remember(permTick) { PermissionStatus.canDrawOverlays(context) }
    val stayAwake = remember(permTick) { PermissionStatus.isStayAwakeEnabled(context) }
    val devOptsUnlocked = remember(permTick) { PermissionStatus.isDeveloperOptionsUnlocked(context) }
    val answerCalls = remember(permTick) { PermissionStatus.hasAnswerCallsPermission(context) }
    val recordAudio = remember(permTick) { PermissionStatus.hasRecordAudioPermission(context) }
    val contactsPerm = remember(permTick) { PermissionStatus.hasContactsPermission(context) }

    val cellularPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permTick++
        if (results.values.all { it }) prefs.setCellularEnabled(true)
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permTick++ }
    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permTick++ }

    val now = System.currentTimeMillis()
    val allRequired = a11y && notif && battery
    val dndActive = dndUntil > now

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Answer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(allRequired, enabled, dndActive, dndUntil)

            DndControlCard(
                dndActive = dndActive,
                dndUntilMs = dndUntil,
                onSetDnd = prefs::setDndUntil,
            )

            SectionLabel("Required permissions")
            PermissionRow(
                title = "Accessibility service",
                rationale = "Taps the green Answer button on WhatsApp's call screen.",
                granted = a11y,
                onFix = { PermissionStatus.openAccessibilitySettings(context) },
            )
            PermissionRow(
                title = "Notification access",
                rationale = "Detects WhatsApp incoming-call notifications.",
                granted = notif,
                onFix = { PermissionStatus.openNotificationListenerSettings(context) },
            )
            PermissionRow(
                title = "Battery: unrestricted",
                rationale = "Stops Android from killing the service.",
                granted = battery,
                onFix = { PermissionStatus.openBatteryOptimizationSettings(context) },
            )

            SectionLabel("Recommended")
            PermissionRow(
                title = "Display over other apps",
                rationale = "Improves reliability when the screen is locked.",
                granted = overlay,
                onFix = { PermissionStatus.openOverlaySettings(context) },
            )
            PermissionRow(
                title = "Stay awake while charging",
                rationale = if (devOptsUnlocked) {
                    "Lives in Developer Options. Fix will open it — toggle 'Stay awake'."
                } else {
                    "Developer Options must be unlocked first: Settings → About tablet → tap Build number 7 times. Then Fix will open Developer Options where you toggle 'Stay awake'."
                },
                granted = stayAwake,
                onFix = { PermissionStatus.openDeveloperOptions(context) },
            )

            OutlinedButton(
                onClick = { PermissionStatus.openWhatsAppNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open WhatsApp notification settings")
            }

            SectionLabel("Behaviour")
            ToggleRow(
                title = "Enabled",
                subtitle = "Master switch. Turn off to pause all auto-answer.",
                checked = enabled,
                onCheckedChange = prefs::setEnabled,
            )
            ToggleRow(
                title = "Test mode",
                subtitle = "Shows a toast instead of answering. Verifies detection without bothering the caller.",
                checked = testMode,
                onCheckedChange = prefs::setTestMode,
            )
            ToggleRow(
                title = "Force Bluetooth audio",
                subtitle = "Pin call audio to the paired Bluetooth device (your Echo).",
                checked = forceBt,
                onCheckedChange = prefs::setForceBluetoothAudio,
            )
            ToggleRow(
                title = "Announce caller by name (TTS)",
                subtitle = "Speaks \"Call from X\" through the Echo before answering. Helpful if hard of hearing.",
                checked = ttsAnnounce,
                onCheckedChange = prefs::setTtsAnnounce,
            )
            ToggleRow(
                title = "Loud chime when call arrives",
                subtitle = "Play a distinctive ding-dong through the speaker. Overrides WhatsApp's quiet ring.",
                checked = loudChime,
                onCheckedChange = prefs::setLoudChime,
            )

            DelayCard(delayMs = delayMs, onChange = prefs::setDelayMs)

            SectionLabel("Cellular auto-answer")
            ToggleRow(
                title = "Auto-answer cellular calls",
                subtitle = "Also answer incoming SIM calls. Audio falls back to tablet speaker (Echo doesn't support cellular Bluetooth).",
                checked = cellularEnabled && answerCalls,
                onCheckedChange = { wanted ->
                    if (wanted && !answerCalls) {
                        cellularPermLauncher.launch(PermissionStatus.cellularPermissionList)
                    } else {
                        prefs.setCellularEnabled(wanted)
                    }
                },
            )
            if (cellularEnabled && !answerCalls) {
                PermissionRow(
                    title = "Phone & Answer-calls permission",
                    rationale = "Required for cellular auto-answer.",
                    granted = false,
                    onFix = { cellularPermLauncher.launch(PermissionStatus.cellularPermissionList) },
                )
            }
            if (cellularEnabled && answerCalls) {
                ToggleRow(
                    title = "Whitelist only (blocks spam)",
                    subtitle = "Only auto-answer numbers in the whitelist below. Off = answer anything (risky).",
                    checked = cellularWhitelistOnly,
                    onCheckedChange = prefs::setCellularWhitelistOnly,
                )
                NumberSetCard(
                    title = "Whitelisted phone numbers",
                    subtitle = "Comma-separated. International format preferred (+447…). Last 7 digits used for matching.",
                    values = cellularWhitelist,
                    onChange = prefs::setCellularWhitelist,
                )
            }

            SectionLabel("Safety")
            ToggleRow(
                title = "Auto-hangup on silence",
                subtitle = "Ends the call if no voice is detected, to avoid open-mic situations.",
                checked = autoHangup && recordAudio,
                onCheckedChange = { wanted ->
                    if (wanted && !recordAudio) {
                        micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else {
                        prefs.setAutoHangupSilence(wanted)
                    }
                },
            )
            if (autoHangup && !recordAudio) {
                PermissionRow(
                    title = "Microphone permission",
                    rationale = "Required to detect silence on calls.",
                    granted = false,
                    onFix = { micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                )
            }
            if (autoHangup && recordAudio) {
                HangupMinutesCard(autoHangupMins, onChange = prefs::setAutoHangupMinutes)
            }

            SectionLabel("Health monitoring")
            HeartbeatCard(
                url = heartbeatUrl,
                onUrlChange = prefs::setHeartbeatUrl,
                onPingNow = { HeartbeatScheduler.pingNow(context) },
            )

            SectionLabel("Remote commands (via WhatsApp)")
            ToggleRow(
                title = "Allow remote commands",
                subtitle = "Family can WhatsApp \"/aa status\", \"/aa dnd 2h\" etc. Only from admins below.",
                checked = remoteCmd,
                onCheckedChange = prefs::setRemoteCmdEnabled,
            )
            if (remoteCmd) {
                NumberSetCard(
                    title = "Admin contacts (whitelist)",
                    subtitle = "WhatsApp display names. Case-insensitive substring match.",
                    values = adminContacts,
                    onChange = prefs::setAdminContacts,
                )
            }

            SectionLabel("Daily log email")
            LogEmailCard(
                url = logEmailUrl,
                enabled = logEmailEnabled,
                onUrlChange = prefs::setLogEmailUrl,
                onEnabledChange = prefs::setLogEmailEnabled,
                onSendNow = { LogEmailScheduler.sendNow(context) },
            )

            SectionLabel("Debug dashboard (browser)")
            DebugServerCard(
                enabled = debugServerEnabled,
                port = debugServerPort,
                onEnabledChange = prefs::setDebugServerEnabled,
                onPortChange = prefs::setDebugServerPort,
            )

            SectionLabel("Diagnostics")
            DiagnosticsCard(
                onCheck = {
                    val report = DiagnosticChecks.collect(context)
                    val body = report.toBriefBody()
                    CrashLog.append(context, "manual diag check:\n$body")
                },
                onShareLog = { shareCrashLog(context) },
                onClearLog = { CrashLog.clear(context) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Auto-answers WhatsApp and (optionally) cellular calls. Caller audio routes to the paired Bluetooth device for VoIP; cellular uses tablet's own loudspeaker. Microphone capture is always the tablet's built-in mic.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Limitation: Bluetooth speakers without microphones (like Amazon Echo) cannot send the resident's voice back. Place the tablet within ~1 m of where the resident sits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StatusBanner(allRequired: Boolean, enabled: Boolean, dndActive: Boolean, dndUntilMs: Long) {
    val container = when {
        !allRequired -> Color(0xFFFFEDD5)
        !enabled -> Color(0xFFE0E0E0)
        dndActive -> Color(0xFFFEF3C7)
        else -> Color(0xFFD1FAE5)
    }
    val text = when {
        !allRequired -> "Setup not finished — grant the required permissions below."
        !enabled -> "Setup complete but auto-answer is paused. Turn on Enabled."
        dndActive -> "Do Not Disturb active until ${formatClock(dndUntilMs)}."
        else -> "Ready. Incoming calls will auto-answer."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (allRequired && enabled && !dndActive) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (allRequired && enabled && !dndActive) Color(0xFF065F46) else Color(0xFF92400E),
            )
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DndControlCard(dndActive: Boolean, dndUntilMs: Long, onSetDnd: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (dndActive) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (dndActive) "Do Not Disturb until ${formatClock(dndUntilMs)}"
                else "Do Not Disturb is off",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60 to "1h", 180 to "3h", 480 to "8h").forEach { (mins, label) ->
                    OutlinedButton(
                        onClick = { onSetDnd(System.currentTimeMillis() + mins * 60_000L) },
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
                if (dndActive) {
                    Button(
                        onClick = { onSetDnd(0L) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f),
                    ) { Text("End now") }
                }
            }
        }
    }
}

@Composable
private fun DelayCard(delayMs: Int, onChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Answer delay", style = MaterialTheme.typography.titleMedium)
            Text("$delayMs ms (${"%.1f".format(delayMs / 1000.0)} s)", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Slider(
                value = delayMs.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..5000f,
                steps = 9,
            )
            Text(
                "Time between detection and answer. Gives the ringtone/chime a moment.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HangupMinutesCard(minutes: Int, onChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Silence timeout: $minutes min", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
            )
        }
    }
}

@Composable
private fun HeartbeatCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onPingNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Heartbeat URL", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create a free check at healthchecks.io, paste its ping URL here. The app POSTs a status report every ~12 hours. If pings stop, you get an email.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("https://hc-ping.com/<uuid>") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onPingNow, enabled = url.isNotBlank()) {
                Text("Send test ping now")
            }
        }
    }
}

@Composable
private fun NumberSetCard(
    title: String,
    subtitle: String,
    values: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    var text by remember(values) { mutableStateOf(values.joinToString(", ")) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val parsed = text.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                onChange(parsed)
            }) { Text("Save") }
        }
    }
}

@Composable
private fun LogEmailCard(
    url: String,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSendNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily log email", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Posts the recent log + diagnostic snapshot to a Google Apps Script you own, which emails it to your Gmail. Once per day (with up to 2h jitter). Free; no third-party API keys.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                label = { Text("Apps Script web app URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Setup: visit script.google.com → New project → paste the script from SETUP.md → Deploy as Web App → Execute as: Me, Access: Anyone → copy the /exec URL → paste here.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onSendNow, enabled = enabled && url.isNotBlank()) {
                Text("Send test email now")
            }
        }
    }
}

@Composable
private fun DebugServerCard(
    enabled: Boolean,
    port: Int,
    onEnabledChange: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
) {
    val lanIp = remember(enabled) { if (enabled) NetworkAddress.lanIpAddress() else null }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Debug dashboard", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Runs a small web server on the tablet. Open in any browser to see live status, test the chime/TTS, trigger DND, and view the log. Useful when something isn't working.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            if (enabled) {
                Text("On this tablet, open Chrome and visit:", style = MaterialTheme.typography.bodySmall)
                Text(
                    "  http://localhost:$port",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (lanIp != null) {
                    Text(
                        "From another device on the same Wi-Fi:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "  http://$lanIp:$port",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Anyone on the same Wi-Fi network can reach this URL while the toggle is on. Turn it off when not actively debugging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = port.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onPortChange) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    onCheck: () -> Unit,
    onShareLog: () -> Unit,
    onClearLog: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            Text(
                "Run a one-off check or share the recent log file. Use when troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheck, modifier = Modifier.weight(1f)) { Text("Run check") }
                Button(onClick = onShareLog, modifier = Modifier.weight(1f)) { Text("Share log") }
                OutlinedButton(onClick = onClearLog, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

private fun shareCrashLog(context: android.content.Context) {
    val file = CrashLog.logFile(context)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share Auto Answer log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.UK).format(Date(ms))
