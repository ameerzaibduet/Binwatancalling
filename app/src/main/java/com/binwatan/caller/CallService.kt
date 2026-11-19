package com.binwatan.caller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Foreground service that keeps the calling queue running, one customer at a time.
class CallService : Service() {

    companion object {
        const val ACTION_START = "com.binwatan.caller.START"
        const val ACTION_STOP = "com.binwatan.caller.STOP"

        private const val CHANNEL_ID = "caller_queue"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false

        @Volatile
        var statusLine = "Stopped"

        private val logLines = ArrayList<String>()

        fun log(text: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            synchronized(logLines) {
                logLines.add("$time  $text")

                while (logLines.size > 60) {
                    logLines.removeAt(0)
                }
            }
        }

        fun logText(): String {
            synchronized(logLines) {
                return logLines.reversed().joinToString("\n")
            }
        }
    }

    @Volatile
    private var active = false

    private var worker: Thread? = null
    private var monitor: CallMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            log("Stopped by you")
            active = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        showForeground("Calling queue is running")
        startWorker()

        return START_STICKY
    }

    override fun onDestroy() {
        active = false
        isRunning = false
        statusLine = "Stopped"

        monitor?.stop()
        monitor = null

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun showForeground(text: String) {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Calling queue", NotificationManager.IMPORTANCE_LOW)
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Bin Watan Caller")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ------------------------------------------------------------------
    // Worker loop
    // ------------------------------------------------------------------

    private fun startWorker() {
        if (active) {
            return
        }

        active = true
        isRunning = true

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BinWatanCaller:loop")
        wakeLock?.acquire()

        monitor = CallMonitor(this).also { it.start() }

        worker = Thread { runLoop() }.also { it.start() }
    }

    private fun pause(milliseconds: Long) {
        val end = System.currentTimeMillis() + milliseconds

        while (active && System.currentTimeMillis() < end) {
            Thread.sleep(250)
        }
    }

    private fun runLoop() {
        val config = Config(this)
        val api = ApiClient(config)

        log("Service started")

        while (active) {
            try {
                // Never start a new call while the phone is busy.
                val phoneState = monitor?.state ?: TelephonyManager.CALL_STATE_IDLE

                if (phoneState != TelephonyManager.CALL_STATE_IDLE) {
                    statusLine = "Phone is busy, waiting..."
                    pause(3000)
                    continue
                }

                val response = api.next()

                when (response.optString("status")) {
                    "call" -> {
                        handleCall(api, response)
                        pause(3000)
                    }

                    "stopped" -> {
                        statusLine = "Queue is stopped in the admin panel"
                        pause(5000)
                    }

                    "empty" -> {
                        statusLine = "No customers due right now"
                        pause(8000)
                    }

                    "retry" -> pause(1000)

                    else -> {
                        log("Unexpected reply: ${response.toString().take(120)}")
                        pause(8000)
                    }
                }
            } catch (e: Exception) {
                statusLine = "Error: ${e.message}"
                log("Error: ${e.message}")
                pause(10000)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------
    // One call, start to finish
    // ------------------------------------------------------------------

    private fun reportWithRetry(
        api: ApiClient,
        attemptId: String,
        orderId: String,
        outcome: String,
        talkSeconds: Int,
        notes: String?
    ) {
        for (tryNumber in 1..3) {
            try {
                api.report(attemptId, orderId, outcome, talkSeconds, notes)
                log("Reported: $outcome")
                return
            } catch (e: Exception) {
                log("Report failed (try $tryNumber): ${e.message}")
                pause(2000)
            }
        }
    }

    private fun handleCall(api: ApiClient, response: JSONObject) {
        val attemptId = response.getString("attemptId")
        val order: JSONObject = response.getJSONObject("order")

        val orderId = order.optString("id")
        val name = order.optString("name")
        val dialNumber = order.optString("dialNumber")
        val ringSeconds = response.optInt("ringTimeoutSeconds", 60)
        val simSlot = response.optInt("simSlot", 0)

        statusLine = "Calling $name..."
        log("Calling $name ($dialNumber), attempt ${response.optInt("attemptNumber", 1)}")

        if (dialNumber.isEmpty()) {
            reportWithRetry(api, attemptId, orderId, "failed", 0, "No phone number on the order")
            return
        }

        val startMs = System.currentTimeMillis()

        val placed = try {
            CallPlacer.placeCall(this, dialNumber, simSlot)
        } catch (e: SecurityException) {
            log("Missing permission: ${e.message}")
            false
        }

        if (!placed) {
            reportWithRetry(
                api, attemptId, orderId, "failed", 0,
                "Could not start the call from SIM slot ${simSlot + 1}"
            )

            statusLine = "Stopped: could not call from SIM slot ${simSlot + 1}. Check permissions and the SIM."
            log(statusLine)

            // Stop here so the rest of the queue is not marked failed.
            active = false
            return
        }

        var sawOffhook = false
        var reportedAnswered = false

        while (active) {
            val state = monitor?.state ?: TelephonyManager.CALL_STATE_IDLE
            val elapsedMs = System.currentTimeMillis() - startMs

            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                sawOffhook = true
            }

            // The call never started.
            if (!sawOffhook && elapsedMs > 20000) {
                reportWithRetry(api, attemptId, orderId, "failed", 0, "The call did not start")
                return
            }

            // The call has ended.
            if (sawOffhook && state == TelephonyManager.CALL_STATE_IDLE) {
                val elapsedSeconds = (elapsedMs / 1000).toInt()

                if (reportedAnswered) {
                    log("Call ended after ${elapsedSeconds}s")
                    statusLine = "Call finished"
                    return
                }

                finishEndedCall(api, attemptId, orderId, dialNumber, startMs, elapsedSeconds, ringSeconds)
                return
            }

            // Still connected after the ring window: treat as answered.
            if (sawOffhook && !reportedAnswered && elapsedMs >= ringSeconds * 1000L) {
                reportedAnswered = true
                statusLine = "Customer answered, talk to them now"

                reportWithRetry(
                    api, attemptId, orderId, "answered", 0,
                    "Still connected after ${ringSeconds}s"
                )
            }

            Thread.sleep(500)
        }
    }

    // The call ended before the ring window was over: check the call history
    // to see whether the customer actually talked.
    private fun finishEndedCall(
        api: ApiClient,
        attemptId: String,
        orderId: String,
        dialNumber: String,
        startMs: Long,
        elapsedSeconds: Int,
        ringSeconds: Int
    ) {
        var talkSeconds: Int? = null

        for (i in 0 until 6) {
            pause(1000)

            talkSeconds = try {
                CallLogReader.lastOutgoingDurationSeconds(this, dialNumber, startMs)
            } catch (e: SecurityException) {
                null
            }

            if (talkSeconds != null) {
                break
            }
        }

        val outcome: String
        val talk: Int
        val note: String

        if (talkSeconds != null) {
            talk = talkSeconds

            if (talk > 0) {
                outcome = "answered"
                note = "Talked for ${talk}s"
            } else {
                outcome = "no_answer"
                note = "Call ended after ${elapsedSeconds}s with no talk time"
            }
        } else {
            // Not in the call history: decide from how long the call lasted.
            talk = 0

            if (elapsedSeconds >= ringSeconds) {
                outcome = "answered"
                note = "Call lasted ${elapsedSeconds}s (no call history entry)"
            } else {
                outcome = "no_answer"
                note = "Call ended after ${elapsedSeconds}s (no call history entry)"
            }
        }

        statusLine = if (outcome == "answered") {
            "Customer answered, talk to them now"
        } else {
            "No answer, moving on"
        }

        reportWithRetry(api, attemptId, orderId, outcome, talk, note)
    }
}
