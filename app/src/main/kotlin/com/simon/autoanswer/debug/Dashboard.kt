package com.simon.autoanswer.debug

object Dashboard {

    val PAGE: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Auto Answer · Diagnostic</title>
<style>
  :root {
    --ok: #1F8A3D; --warn: #B45309; --bad: #B91C1C;
    --bg: #0F172A; --panel: #1E293B; --text: #E2E8F0; --muted: #94A3B8;
    --accent: #38BDF8;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 16px;
    font-family: -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
    background: var(--bg); color: var(--text); font-size: 14px;
  }
  h1 { margin: 0 0 4px 0; font-size: 20px; }
  .device { color: var(--muted); margin-bottom: 16px; font-size: 12px; }
  .panel {
    background: var(--panel); border-radius: 12px; padding: 16px;
    margin-bottom: 12px;
  }
  .panel h2 {
    margin: 0 0 12px 0; font-size: 14px; text-transform: uppercase;
    letter-spacing: 0.5px; color: var(--muted);
  }
  .traffic {
    font-size: 24px; padding: 12px; border-radius: 8px;
    text-align: center; font-weight: 600;
  }
  .traffic.ok { background: #064E3B; color: #6EE7B7; }
  .traffic.warn { background: #78350F; color: #FCD34D; }
  .traffic.bad { background: #7F1D1D; color: #FCA5A5; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 6px 0; vertical-align: middle; }
  td.label { color: var(--muted); }
  td.value { text-align: right; }
  .dot {
    display: inline-block; width: 10px; height: 10px; border-radius: 50%;
    margin-right: 8px; vertical-align: middle;
  }
  .dot.ok { background: var(--ok); }
  .dot.bad { background: var(--bad); }
  .dot.warn { background: var(--warn); }
  button {
    background: var(--accent); color: #0C4A6E; border: none;
    padding: 10px 14px; border-radius: 6px; font-size: 13px;
    font-weight: 600; cursor: pointer; margin: 4px;
  }
  button.danger { background: #FCA5A5; color: #7F1D1D; }
  button.ghost { background: transparent; color: var(--accent); border: 1px solid var(--accent); }
  .row { display: flex; flex-wrap: wrap; gap: 4px; }
  pre {
    background: #0B1220; color: #D1D5DB; padding: 12px; border-radius: 6px;
    overflow: auto; max-height: 50vh; font-size: 11px; white-space: pre-wrap;
    word-break: break-word;
  }
  .pill {
    display: inline-block; padding: 2px 8px; border-radius: 10px;
    font-size: 11px; background: #334155; color: var(--text);
  }
  .footer { text-align: center; color: var(--muted); font-size: 11px; margin-top: 24px; }
</style>
</head>
<body>
<h1>Auto Answer · Diagnostic</h1>
<div class="device" id="device">Loading…</div>

<div class="panel">
  <div id="traffic" class="traffic">Loading…</div>
</div>

<div class="panel">
  <h2>Required</h2>
  <table id="required"></table>
</div>

<div class="panel">
  <h2>Recommended</h2>
  <table id="recommended"></table>
</div>

<div class="panel">
  <h2>Audio</h2>
  <table id="audio"></table>
</div>

<div class="panel">
  <h2>Cellular</h2>
  <table id="cellular"></table>
</div>

<div class="panel">
  <h2>State</h2>
  <table id="state"></table>
</div>

<div class="panel">
  <h2>Healthcheck</h2>
  <table id="healthcheck"></table>
</div>

<div class="panel">
  <h2>Tests</h2>
  <div class="row">
    <button onclick="act('chime')">Play chime</button>
    <button onclick="actWithName('tts')">Speak TTS</button>
    <button onclick="act('heartbeat')">Send heartbeat</button>
    <button onclick="act('toggle-test-mode')">Toggle test mode</button>
    <button onclick="act('toggle-enabled')">Toggle enabled</button>
  </div>
</div>

<div class="panel">
  <h2>Do Not Disturb</h2>
  <div class="row">
    <button onclick="dnd(60)">DND 1h</button>
    <button onclick="dnd(180)">DND 3h</button>
    <button onclick="dnd(480)">DND 8h</button>
    <button class="danger" onclick="act('dnd-clear')">Clear DND</button>
  </div>
</div>

<div class="panel">
  <h2>Log</h2>
  <div class="row" style="margin-bottom: 8px">
    <button class="ghost" onclick="refreshLog()">Refresh log</button>
    <button class="danger" onclick="if (confirm('Clear log?')) act('clear-log').then(refreshLog)">Clear log</button>
  </div>
  <pre id="log">Loading…</pre>
</div>

<div class="footer">
  Auto-refresh: <span id="lastRefresh">never</span> · port <span id="port"></span>
</div>

<script>
const $ = id => document.getElementById(id);
const port = location.port || '80';
$('port').textContent = port;

function dot(ok) { return '<span class="dot ' + (ok ? 'ok' : 'bad') + '"></span>'; }
function row(label, value, ok) {
  return '<tr><td class="label">' + dot(ok !== false) + label + '</td><td class="value">' + value + '</td></tr>';
}
function fmtTime(ms) {
  if (!ms || ms === 0) return '—';
  return new Date(ms).toLocaleString('en-GB', { hour12: false });
}
function fillTable(id, rows) { $(id).innerHTML = rows.join(''); }

async function poll() {
  try {
    const r = await fetch('/status.json', { cache: 'no-store' });
    const s = await r.json();
    $('device').textContent = s.device + ' · Android ' + s.sdk + ' · ' + fmtTime(s.collectedAt);
    const allReqOk = Object.values(s.required).every(v => v);
    const tr = $('traffic');
    if (!allReqOk) {
      tr.className = 'traffic bad';
      tr.textContent = 'NEEDS ATTENTION';
    } else if (!s.state.enabled || s.state.dndActive) {
      tr.className = 'traffic warn';
      tr.textContent = s.state.dndActive
        ? 'DND active until ' + fmtTime(s.state.dndUntilMs)
        : 'PAUSED';
    } else {
      tr.className = 'traffic ok';
      tr.textContent = 'READY';
    }
    fillTable('required', [
      row('Accessibility service', s.required.accessibility ? 'ok' : 'missing', s.required.accessibility),
      row('Notification access', s.required.notification ? 'ok' : 'missing', s.required.notification),
      row('Battery: unrestricted', s.required.batteryUnrestricted ? 'ok' : 'missing', s.required.batteryUnrestricted),
      row('Internet', s.required.internet ? 'ok' : 'missing', s.required.internet),
    ]);
    fillTable('recommended', [
      row('Display over apps', s.recommended.overlay ? 'ok' : 'missing', s.recommended.overlay),
      row('Stay awake', s.recommended.stayAwake ? 'ok' : 'missing', s.recommended.stayAwake),
    ]);
    fillTable('audio', [
      row('Bluetooth on', s.audio.bluetoothOn ? 'ok' : 'off', s.audio.bluetoothOn),
      row('Bluetooth A2DP sink', s.audio.bluetoothA2dpConnected ? 'connected' : 'disconnected', s.audio.bluetoothA2dpConnected),
    ]);
    fillTable('cellular', [
      row('Enabled', s.cellular.enabled ? 'yes' : 'no', s.cellular.enabled),
      row('Permission granted', s.cellular.permissionGranted ? 'yes' : 'no', s.cellular.permissionGranted),
      row('Whitelist only', s.cellular.whitelistOnly ? 'yes (safe)' : 'no (open)', true),
      row('Whitelist size', s.cellular.whitelistSize, true),
    ]);
    fillTable('state', [
      row('Calls enabled', s.state.enabled ? 'yes' : 'no', s.state.enabled),
      row('Test mode', s.state.testMode ? 'ON (no real answer)' : 'off', true),
      row('DND active', s.state.dndActive ? 'until ' + fmtTime(s.state.dndUntilMs) : 'no', !s.state.dndActive),
      row('Answer delay', s.state.answerDelayMs + ' ms', true),
      row('Loud chime', s.state.loudChime ? 'on' : 'off', true),
      row('TTS announce', s.state.ttsAnnounce ? 'on' : 'off', true),
      row('Auto-hangup', s.state.autoHangup ? ('after ' + s.state.autoHangupMinutes + ' min silence') : 'off', true),
      row('Force Bluetooth audio', s.state.forceBluetoothAudio ? 'on' : 'off', true),
    ]);
    fillTable('healthcheck', [
      row('URL configured', s.healthcheck.configured ? 'yes' : 'no', s.healthcheck.configured),
    ]);
    $('lastRefresh').textContent = new Date().toLocaleTimeString('en-GB', { hour12: false });
  } catch (e) {
    $('traffic').className = 'traffic bad';
    $('traffic').textContent = 'SERVER UNREACHABLE';
  }
}

async function refreshLog() {
  try {
    const r = await fetch('/log.txt', { cache: 'no-store' });
    const txt = await r.text();
    $('log').textContent = txt || '(log is empty)';
    $('log').scrollTop = $('log').scrollHeight;
  } catch (e) { $('log').textContent = 'Error loading log: ' + e; }
}

async function act(name, params) {
  const body = params ? new URLSearchParams(params).toString() : '';
  await fetch('/action/' + name, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  poll();
}

async function actWithName(name) {
  const who = prompt('Who is calling? (test name)', 'Mum') || 'Test caller';
  act(name, { name: who });
}

function dnd(minutes) { act('dnd', { minutes }); }

poll(); refreshLog();
setInterval(poll, 5000);
setInterval(refreshLog, 10000);
</script>
</body>
</html>
""".trimIndent()
}
