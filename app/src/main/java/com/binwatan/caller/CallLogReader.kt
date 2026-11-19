package com.binwatan.caller

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog

// Reads the phone's call history to find how long the customer actually talked.
object CallLogReader {

    // Returns the talk time (seconds) of the latest outgoing call to this number
    // made after sinceMs, or null if the call is not in the history yet.
    @SuppressLint("MissingPermission")
    fun lastOutgoingDurationSeconds(context: Context, number: String, sinceMs: Long): Int? {
        val wanted = number.filter { it.isDigit() }.takeLast(10)

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
            arrayOf(
                CallLog.Calls.OUTGOING_TYPE.toString(),
                (sinceMs - 5000).toString()
            ),
            "${CallLog.Calls.DATE} DESC"
        ) ?: return null

        cursor.use { rows ->
            while (rows.moveToNext()) {
                val logged = (rows.getString(0) ?: "").filter { c -> c.isDigit() }

                if (logged.endsWith(wanted)) {
                    return rows.getInt(2)
                }
            }
        }

        return null
    }
}
