package com.skylake.skytv.jgorunner.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

suspend fun checkServerStatus(
    port: Int = 5350,
    onLoginSuccess: () -> Unit,
    onLoginFailure: () -> Unit,
    onServerDown: () -> Unit,
) {
    val url = URL("http://localhost:$port/live/143.m3u8")
    val delayDurations = listOf(150L, 200L, 200L, 350L, 550L, 750L, 750L, 1000L, 1250L, 1500L, 1750L, 2000L, 2000L)
    var lastResponseCode: Int? = null

    for ((attempt, delayDuration) in delayDurations.withIndex()) {
        try {
            Log.d("ServerLoginCheck", "Attempt #${attempt + 1}: Checking server status at $url")
            val connection = (withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5000 // 5 seconds timeout
                readTimeout = 5000 // 5 seconds timeout
            }

            lastResponseCode = connection.responseCode
            connection.disconnect()

            when (lastResponseCode) {
                HttpURLConnection.HTTP_OK -> {
                    Log.d("ServerLoginCheck", "Server is up (Response Code: $lastResponseCode).")
                    onLoginSuccess()
                    return
                }
                HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                    Log.w("ServerLoginCheck", "Server returned an error (Response Code: $lastResponseCode).")
                    onLoginFailure()
                    return
                }
                else -> {
                    Log.w("ServerLoginCheck", "Unexpected Response Code: $lastResponseCode")
                }
            }
        } catch (er: Exception) {
            Log.e(
                "ServerLoginCheck",
                "Attempt #${attempt + 1}: Error occurred while checking server status.",
                er
            )
        }

        if (attempt < delayDurations.size - 1) {
            Log.d("ServerLoginCheck", "Delaying for $delayDuration ms before the next attempt.")
            delay(delayDuration)
        }
    }

    Log.e("ServerLoginCheck", "Max attempts reached. Last Response Code: $lastResponseCode")
    onServerDown()
}
