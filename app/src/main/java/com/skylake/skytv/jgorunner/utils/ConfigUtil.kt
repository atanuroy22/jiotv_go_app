package com.skylake.skytv.jgorunner.utils

import android.content.Context
import android.util.Log
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

object ConfigUtil {

    private const val TAG = "ConfigUtils"

    fun fetchAndSaveConfig(context: Context) {
        val preferenceManager = SkySharedPref(context)

        // Launch coroutine to handle background network operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configUrl = "https://raw.githubusercontent.com/siddharthsky/Extrix/main/JGO/syncrunner1.cfg"
                val configData = downloadFile(configUrl)

                if (configData != null) {
                    // Parse and save the config data into preferences
                    parseAndSaveConfigData(configData, preferenceManager)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching or parsing config: ${e.message}")
            }
        }
    }

    // Download the configuration file
    private suspend fun downloadFile(fileUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(fileUrl)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"

                val inputStream = urlConnection.inputStream
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                content
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading file: ${e.message}")
                null
            }
        }
    }

    // Parse the file contents and save each key-value pair to preferences
    private fun parseAndSaveConfigData(configData: String, preferenceManager: SkySharedPref) {
        val lines = configData.split("\n")
        for (line in lines) {
            if (line.contains("=")) {
                val parts = line.split("=")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].replace("\"", "").trim() // Remove any quotes
                    preferenceManager.setKey(key, value)
                    if (false) Log.d(TAG, "Saved Key: $key with Value: $value")
                }
            }
        }
    }
}
