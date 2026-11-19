package com.binwatan.caller

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Watches the phone's call state: IDLE, RINGING or OFFHOOK (call in progress).
class CallMonitor(context: Context) {

    @Volatile
    var state: Int = TelephonyManager.CALL_STATE_IDLE
        private set

    private val telephony = context.getSystemService(TelephonyManager::class.java)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(newState: Int) {
            state = newState
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        telephony.registerTelephonyCallback(executor, callback)
    }

    fun stop() {
        try {
            telephony.unregisterTelephonyCallback(callback)
        } catch (e: Exception) {
            // already unregistered
        }

        executor.shutdown()
    }
}
