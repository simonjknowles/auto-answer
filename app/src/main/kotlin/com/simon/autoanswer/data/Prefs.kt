package com.simon.autoanswer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs private constructor(private val sp: SharedPreferences) {

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _delayMs = MutableStateFlow(sp.getInt(KEY_DELAY_MS, DEFAULT_DELAY_MS))
    val delayMs: StateFlow<Int> = _delayMs.asStateFlow()

    private val _testMode = MutableStateFlow(sp.getBoolean(KEY_TEST_MODE, false))
    val testMode: StateFlow<Boolean> = _testMode.asStateFlow()

    private val _forceBluetoothAudio = MutableStateFlow(sp.getBoolean(KEY_FORCE_BT, true))
    val forceBluetoothAudio: StateFlow<Boolean> = _forceBluetoothAudio.asStateFlow()

    private val _cellularEnabled = MutableStateFlow(sp.getBoolean(KEY_CELLULAR, false))
    val cellularEnabled: StateFlow<Boolean> = _cellularEnabled.asStateFlow()

    private val _cellularWhitelistOnly = MutableStateFlow(sp.getBoolean(KEY_CELL_WHITELIST_ONLY, true))
    val cellularWhitelistOnly: StateFlow<Boolean> = _cellularWhitelistOnly.asStateFlow()

    private val _cellularWhitelist = MutableStateFlow<Set<String>>(
        sp.getStringSet(KEY_CELL_WHITELIST, emptySet())?.toSet() ?: emptySet()
    )
    val cellularWhitelist: StateFlow<Set<String>> = _cellularWhitelist.asStateFlow()

    private val _dndUntilMs = MutableStateFlow(sp.getLong(KEY_DND_UNTIL, 0L))
    val dndUntilMs: StateFlow<Long> = _dndUntilMs.asStateFlow()

    private val _heartbeatUrl = MutableStateFlow(sp.getString(KEY_HEARTBEAT_URL, "") ?: "")
    val heartbeatUrl: StateFlow<String> = _heartbeatUrl.asStateFlow()

    private val _ttsAnnounce = MutableStateFlow(sp.getBoolean(KEY_TTS_ANNOUNCE, true))
    val ttsAnnounce: StateFlow<Boolean> = _ttsAnnounce.asStateFlow()

    private val _autoHangupSilence = MutableStateFlow(sp.getBoolean(KEY_AUTO_HANGUP, true))
    val autoHangupSilence: StateFlow<Boolean> = _autoHangupSilence.asStateFlow()

    private val _autoHangupMinutes = MutableStateFlow(sp.getInt(KEY_AUTO_HANGUP_MIN, 5))
    val autoHangupMinutes: StateFlow<Int> = _autoHangupMinutes.asStateFlow()

    private val _loudChime = MutableStateFlow(sp.getBoolean(KEY_LOUD_CHIME, true))
    val loudChime: StateFlow<Boolean> = _loudChime.asStateFlow()

    private val _remoteCmdEnabled = MutableStateFlow(sp.getBoolean(KEY_REMOTE_CMD, false))
    val remoteCmdEnabled: StateFlow<Boolean> = _remoteCmdEnabled.asStateFlow()

    private val _adminContacts = MutableStateFlow<Set<String>>(
        sp.getStringSet(KEY_ADMIN_CONTACTS, emptySet())?.toSet() ?: emptySet()
    )
    val adminContacts: StateFlow<Set<String>> = _adminContacts.asStateFlow()

    private val _debugServerEnabled = MutableStateFlow(sp.getBoolean(KEY_DEBUG_SERVER, false))
    val debugServerEnabled: StateFlow<Boolean> = _debugServerEnabled.asStateFlow()

    private val _debugServerPort = MutableStateFlow(sp.getInt(KEY_DEBUG_PORT, 8765))
    val debugServerPort: StateFlow<Int> = _debugServerPort.asStateFlow()

    fun setEnabled(value: Boolean) {
        sp.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    fun setDelayMs(value: Int) {
        val clamped = value.coerceIn(0, 10_000)
        sp.edit { putInt(KEY_DELAY_MS, clamped) }
        _delayMs.value = clamped
    }

    fun setTestMode(value: Boolean) {
        sp.edit { putBoolean(KEY_TEST_MODE, value) }
        _testMode.value = value
    }

    fun setForceBluetoothAudio(value: Boolean) {
        sp.edit { putBoolean(KEY_FORCE_BT, value) }
        _forceBluetoothAudio.value = value
    }

    fun setCellularEnabled(value: Boolean) {
        sp.edit { putBoolean(KEY_CELLULAR, value) }
        _cellularEnabled.value = value
    }

    fun setCellularWhitelistOnly(value: Boolean) {
        sp.edit { putBoolean(KEY_CELL_WHITELIST_ONLY, value) }
        _cellularWhitelistOnly.value = value
    }

    fun setCellularWhitelist(numbers: Set<String>) {
        sp.edit { putStringSet(KEY_CELL_WHITELIST, numbers) }
        _cellularWhitelist.value = numbers
    }

    fun setDndUntil(ms: Long) {
        sp.edit { putLong(KEY_DND_UNTIL, ms) }
        _dndUntilMs.value = ms
    }

    fun setHeartbeatUrl(url: String) {
        sp.edit { putString(KEY_HEARTBEAT_URL, url) }
        _heartbeatUrl.value = url
    }

    fun setTtsAnnounce(value: Boolean) {
        sp.edit { putBoolean(KEY_TTS_ANNOUNCE, value) }
        _ttsAnnounce.value = value
    }

    fun setAutoHangupSilence(value: Boolean) {
        sp.edit { putBoolean(KEY_AUTO_HANGUP, value) }
        _autoHangupSilence.value = value
    }

    fun setAutoHangupMinutes(value: Int) {
        val clamped = value.coerceIn(1, 60)
        sp.edit { putInt(KEY_AUTO_HANGUP_MIN, clamped) }
        _autoHangupMinutes.value = clamped
    }

    fun setLoudChime(value: Boolean) {
        sp.edit { putBoolean(KEY_LOUD_CHIME, value) }
        _loudChime.value = value
    }

    fun setRemoteCmdEnabled(value: Boolean) {
        sp.edit { putBoolean(KEY_REMOTE_CMD, value) }
        _remoteCmdEnabled.value = value
    }

    fun setAdminContacts(numbers: Set<String>) {
        sp.edit { putStringSet(KEY_ADMIN_CONTACTS, numbers) }
        _adminContacts.value = numbers
    }

    fun setDebugServerEnabled(value: Boolean) {
        sp.edit { putBoolean(KEY_DEBUG_SERVER, value) }
        _debugServerEnabled.value = value
    }

    fun setDebugServerPort(value: Int) {
        val clamped = value.coerceIn(1024, 65535)
        sp.edit { putInt(KEY_DEBUG_PORT, clamped) }
        _debugServerPort.value = clamped
    }

    companion object {
        const val DEFAULT_DELAY_MS = 1500

        private const val FILE = "auto_answer_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DELAY_MS = "delay_ms"
        private const val KEY_TEST_MODE = "test_mode"
        private const val KEY_FORCE_BT = "force_bt"
        private const val KEY_CELLULAR = "cellular_enabled"
        private const val KEY_CELL_WHITELIST_ONLY = "cellular_whitelist_only"
        private const val KEY_CELL_WHITELIST = "cellular_whitelist"
        private const val KEY_DND_UNTIL = "dnd_until_ms"
        private const val KEY_HEARTBEAT_URL = "heartbeat_url"
        private const val KEY_TTS_ANNOUNCE = "tts_announce"
        private const val KEY_AUTO_HANGUP = "auto_hangup"
        private const val KEY_AUTO_HANGUP_MIN = "auto_hangup_min"
        private const val KEY_LOUD_CHIME = "loud_chime"
        private const val KEY_REMOTE_CMD = "remote_cmd"
        private const val KEY_ADMIN_CONTACTS = "admin_contacts"
        private const val KEY_DEBUG_SERVER = "debug_server"
        private const val KEY_DEBUG_PORT = "debug_port"

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
