package com.simon.autoanswer.diag

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.ui.PermissionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticReport(
    val accessibility: Boolean,
    val notification: Boolean,
    val batteryUnrestricted: Boolean,
    val overlay: Boolean,
    val stayAwake: Boolean,
    val answerCallsGranted: Boolean,
    val internet: Boolean,
    val bluetoothOn: Boolean,
    val bluetoothA2dpConnected: Boolean,
    val callsEnabled: Boolean,
    val cellularEnabled: Boolean,
    val dndActive: Boolean,
    val device: String,
    val sdkInt: Int,
    val collectedAt: Long,
) {
    fun allRequiredOk(): Boolean =
        accessibility && notification && batteryUnrestricted && internet

    fun toBriefBody(): String = buildString {
        appendLine("Auto Answer health · $device · ${formatTs(collectedAt)}")
        appendLine("Android API $sdkInt")
        appendLine()
        appendLine("Required:")
        appendLine("  accessibility       : ${boolStr(accessibility)}")
        appendLine("  notification access : ${boolStr(notification)}")
        appendLine("  battery unrestricted: ${boolStr(batteryUnrestricted)}")
        appendLine("  internet            : ${boolStr(internet)}")
        appendLine()
        appendLine("Recommended:")
        appendLine("  display over apps   : ${boolStr(overlay)}")
        appendLine("  stay awake          : ${boolStr(stayAwake)}")
        appendLine()
        appendLine("Cellular:")
        appendLine("  enabled             : ${boolStr(cellularEnabled)}")
        appendLine("  answer-calls perm   : ${boolStr(answerCallsGranted)}")
        appendLine()
        appendLine("Audio:")
        appendLine("  bluetooth on        : ${boolStr(bluetoothOn)}")
        appendLine("  bluetooth A2DP sink : ${boolStr(bluetoothA2dpConnected)}")
        appendLine()
        appendLine("State:")
        appendLine("  calls enabled       : ${boolStr(callsEnabled)}")
        appendLine("  dnd active          : ${boolStr(dndActive)}")
    }

    private fun boolStr(b: Boolean) = if (b) "ok" else "MISSING"
    private fun formatTs(ms: Long) =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.UK).format(Date(ms))
}

object DiagnosticChecks {

    fun collect(context: Context): DiagnosticReport {
        val prefs = Prefs.get(context)
        return DiagnosticReport(
            accessibility = PermissionStatus.isAccessibilityEnabled(context),
            notification = PermissionStatus.isNotificationListenerEnabled(context),
            batteryUnrestricted = PermissionStatus.isIgnoringBatteryOptimizations(context),
            overlay = PermissionStatus.canDrawOverlays(context),
            stayAwake = PermissionStatus.isStayAwakeEnabled(context),
            answerCallsGranted = PermissionStatus.hasAnswerCallsPermission(context),
            internet = isInternetAvailable(context),
            bluetoothOn = isBluetoothOn(context),
            bluetoothA2dpConnected = isBluetoothA2dpConnected(context),
            callsEnabled = prefs.enabled.value,
            cellularEnabled = prefs.cellularEnabled.value,
            dndActive = prefs.dndUntilMs.value > System.currentTimeMillis(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdkInt = Build.VERSION.SDK_INT,
            collectedAt = System.currentTimeMillis(),
        )
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isBluetoothOn(context: Context): Boolean {
        val bm = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter: BluetoothAdapter? = bm.adapter
        return adapter?.isEnabled == true
    }

    private fun isBluetoothA2dpConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return am.isBluetoothA2dpOn
        return am.availableCommunicationDevices.any { dev ->
            dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    }

    @Suppress("unused")
    fun isDevicePowerSaving(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isPowerSaveMode
    }
}
