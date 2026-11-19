package com.binwatan.caller

import android.content.Context

// Stores the website address and the shared API key on the phone.
class Config(context: Context) {

    private val prefs =
        context.getSharedPreferences("caller_config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) {
            prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()
        }

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("api_key", value.trim()).apply()
        }

    val isComplete: Boolean
        get() = baseUrl.isNotEmpty() && apiKey.isNotEmpty()
}
