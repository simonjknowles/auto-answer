package com.simon.autoanswer.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

object AudioRouter {

    private const val TAG = "AudioRouter"

    fun routeToBluetoothIfAvailable(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.i(TAG, "API<31, relying on system default A2DP routing")
            return
        }

        val bt = am.availableCommunicationDevices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        if (bt == null) {
            Log.w(TAG, "No Bluetooth communication device available")
            return
        }
        val ok = am.setCommunicationDevice(bt)
        Log.i(TAG, "setCommunicationDevice($bt) = $ok")
    }
}
