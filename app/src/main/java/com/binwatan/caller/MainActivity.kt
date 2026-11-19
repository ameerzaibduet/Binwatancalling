package com.binwatan.caller

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var config: Config
    private lateinit var urlInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var statusView: TextView
    private lateinit var logView: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = Config(this)

        setContentView(buildUi())

        urlInput.setText(config.baseUrl)
        keyInput.setText(config.apiKey)

        if (!hasAllPermissions()) {
            requestPermissions(requiredPermissions(), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )

        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun saveSettings() {
        config.baseUrl = urlInput.text.toString()
        config.apiKey = keyInput.text.toString()

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    private fun toggleService() {
        if (CallService.isRunning) {
            startService(Intent(this, CallService::class.java).setAction(CallService.ACTION_STOP))
            return
        }

        saveSettings()

        if (!config.isComplete) {
            Toast.makeText(this, "Enter the website address and API key first", Toast.LENGTH_LONG).show()
            return
        }

        if (!config.baseUrl.startsWith("https://")) {
            Toast.makeText(this, "The website address must start with https://", Toast.LENGTH_LONG).show()
            return
        }

        if (!hasAllPermissions()) {
            requestPermissions(requiredPermissions(), 1)
            Toast.makeText(this, "Allow all permissions, then press Start again", Toast.LENGTH_LONG).show()
            return
        }

        startForegroundService(Intent(this, CallService::class.java).setAction(CallService.ACTION_START))
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAutostartSettings() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun updateStatus() {
        if (CallService.isRunning) {
            toggleButton.text = "Stop service"
            statusView.text = "Running: ${CallService.statusLine}"
        } else {
            toggleButton.text = "Start service"
            statusView.text = "Stopped"
        }

        logView.text = CallService.logText()
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun label(text: String, size: Float = 14f, bold: Boolean = false): TextView {
        val view = TextView(this)

        view.text = text
        view.textSize = size

        if (bold) {
            view.setTypeface(null, Typeface.BOLD)
        }

        return view
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val view = Button(this)

        view.text = text
        view.isAllCaps = false
        view.setOnClickListener { onClick() }

        return view
    }

    private fun buildUi(): ScrollView {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(20), dp(32), dp(20), dp(24))

        column.addView(label("Bin Watan Caller", 26f, true))
        column.addView(label("Calls your pending orders from SIM slot 1, one customer at a time."))

        column.addView(label("Website address", 13f, true).apply { setPadding(0, dp(20), 0, 0) })

        urlInput = EditText(this)
        urlInput.hint = "https://your-website.com"
        urlInput.setSingleLine(true)
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        column.addView(urlInput)

        column.addView(label("API key (CALLING_API_KEY)", 13f, true).apply { setPadding(0, dp(12), 0, 0) })

        keyInput = EditText(this)
        keyInput.hint = "Paste the key from your website settings"
        keyInput.setSingleLine(true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        column.addView(keyInput)

        column.addView(button("Save settings") { saveSettings() })

        column.addView(
            button("Allow permissions") {
                requestPermissions(requiredPermissions(), 1)
            }
        )

        column.addView(button("Battery: set to No restrictions") { openBatterySettings() })
        column.addView(button("Autostart settings (Xiaomi / POCO)") { openAutostartSettings() })

        toggleButton = button("Start service") { toggleService() }
        column.addView(toggleButton)

        statusView = label("Stopped", 16f, true)
        statusView.setPadding(0, dp(16), 0, dp(8))
        column.addView(statusView)

        logView = label("", 12f)
        logView.typeface = Typeface.MONOSPACE
        column.addView(logView)

        val scroll = ScrollView(this)
        scroll.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scroll.addView(column)

        return scroll
    }
}
