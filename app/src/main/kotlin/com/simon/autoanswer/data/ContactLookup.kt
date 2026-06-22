package com.simon.autoanswer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ContactLookup {

    private const val TAG = "ContactLookup"

    fun normalise(number: String): String {
        return number.filter { it.isDigit() || it == '+' }
    }

    fun matches(known: String, incoming: String): Boolean {
        val a = normalise(known)
        val b = normalise(incoming)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val tailLen = 7
        return a.takeLast(tailLen) == b.takeLast(tailLen)
    }

    fun displayNameFor(context: Context, number: String): String? {
        if (number.isBlank()) return null
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Contacts permission denied", e)
            null
        }
    }
}
