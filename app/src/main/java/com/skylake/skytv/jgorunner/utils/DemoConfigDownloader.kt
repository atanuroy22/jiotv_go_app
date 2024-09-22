package com.skylake.skytv.jgorunner.utils

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class DemoConfigDownloader : ComponentActivity() {

    private lateinit var preferenceManager: SkySharedPref
    private val TAG = "ConfigDownloader"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = SkySharedPref(this)

        // Fetch and parse the config file
        fetchAndParseConfig()
    }

    private fun fetchAndParseConfig() {
        // Use a coroutine to perform network requests on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configUrl = "https://raw.githubusercontent.com/siddharthsky/Extrix/main/JGO/syncrunner1.cfg"
                val configData = downloadFile(configUrl)

                if (configData != null) {
                    // Parse the file contents and save to preferences
                    parseConfigData(configData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching or parsing config: ${e.message}")
            }
        }
    }

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

    private fun parseConfigData(configData: String) {
        // Split the content into lines
        val lines = configData.split("\n")

        // Loop through each line and extract key-value pairs
        for (line in lines) {
            if (line.contains("=")) {
                val parts = line.split("=")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].replace("\"", "").trim() // Remove quotes if present
                    saveToPreferences(key, value)
                }
            }
        }
    }

    private fun saveToPreferences(key: String, value: String) {
        // Log the key-value pair before saving it
        //Log.d(TAG, "Saving to preferences: Key = $key, Value = $value")

        // Store the key-value pair in SkySharedPref
        preferenceManager.setKey(key, value)

        // Log confirmation after saving
        Log.d(TAG, "Saved Key: $key with Value: $value")
    }
}
