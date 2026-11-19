package com.binwatan.caller

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Talks to the two website routes: /api/calling/next and /api/calling/result.
class ApiClient(private val config: Config) {

    private fun open(path: String, method: String): HttpURLConnection {
        val connection = URL(config.baseUrl + path).openConnection() as HttpURLConnection

        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("x-calling-key", config.apiKey)
        connection.setRequestProperty("Accept", "application/json")

        return connection
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream

        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    // Asks the website for the next customer to call.
    fun next(): JSONObject {
        val connection = open("/api/calling/next", "GET")

        try {
            val code = connection.responseCode
            val body = readBody(connection, code)

            if (code !in 200..299) {
                throw RuntimeException("Server error $code: ${body.take(200)}")
            }

            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    // Tells the website how a call ended.
    fun report(
        attemptId: String,
        orderId: String,
        outcome: String,
        talkSeconds: Int,
        notes: String?
    ) {
        val connection = open("/api/calling/result", "POST")

        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val json = JSONObject()
                .put("attemptId", attemptId)
                .put("orderId", orderId)
                .put("outcome", outcome)
                .put("talkSeconds", talkSeconds)

            if (notes != null) {
                json.put("notes", notes)
            }

            connection.outputStream.use { it.write(json.toString().toByteArray()) }

            val code = connection.responseCode
            val body = readBody(connection, code)

            if (code !in 200..299) {
                throw RuntimeException("Result rejected $code: ${body.take(200)}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
