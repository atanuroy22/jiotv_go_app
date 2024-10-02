package com.skylake.skytv.jgorunner.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Config2DL_alt {

    private const val TAG = "Config2DL"
    private const val FILE_NAME = "majorbin"

    fun startDownloadAndSave(context: Context) {

        val preferenceManager = SkySharedPref(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var expectedFileSize = preferenceManager.getKey("expectedFileSize")?.toIntOrNull()
                if (expectedFileSize == null || expectedFileSize == 0) {
                    expectedFileSize = fetchExpectedFileSize(context)
                }

                val xCRL = String(Base64.decode("aHR0cHM6Ly9iaXQubHkvbWFqb3JiaW4=", Base64.DEFAULT))
                val saved = downloadLatestRelease(xCRL, context, expectedFileSize)

                if (saved) {
                    Log.d(TAG, "File downloaded and saved as $FILE_NAME")
                } else {
                    Log.e(TAG, "Failed to download or save the file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading or saving file: ${e.message}")
            }
        }
    }

    private suspend fun fetchExpectedFileSize(context: Context): Int? {
        return withContext(Dispatchers.IO) {

            val preferenceManager = SkySharedPref(context)

            try {
                val APXCRL = String(Base64.decode("aHR0cHM6Ly9iaXQubHkvbWFqb3JiaW5hcGk=", Base64.DEFAULT))
                val url = URL(APXCRL)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = urlConnection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val assets = jsonObject.getJSONArray("assets")
                    val decodedName = String(Base64.decode("amlvdHZfZ28tYW5kcm9pZDUtYXJtdjc=", Base64.DEFAULT))

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name") == decodedName) {
                            val size = asset.getInt("size") // Get expected file size
                            preferenceManager.setKey("expectedFileSize", size.toString())
                            Log.d(TAG, "Expected file size: $size bytes")
                            return@withContext size
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to fetch release information: HTTP ${urlConnection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching expected file size: ${e.message}")
            }
            return@withContext null
        }
    }

    private suspend fun downloadLatestRelease(fileUrl: String, context: Context, expectedFileSize: Int?): Boolean {
        return withContext(Dispatchers.IO) {
            val fileDir = context.filesDir
            val file = File(fileDir, FILE_NAME)

            // Check if the file already exists
            if (file.exists()) {
                if (isFileCorrupt(file, expectedFileSize)) {
                    Log.d(TAG, "File already exists at: ${file.absolutePath} but is corrupt. Re-downloading.")
                } else {
                    Log.d(TAG, "File already exists at: ${file.absolutePath}. Skipping download.")
                    return@withContext true
                }
            }

            return@withContext try {
                val url = URL(fileUrl)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP ${urlConnection.responseCode}")
                    return@withContext false
                }

                urlConnection.inputStream.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(1024) // 1 KB buffer
                        var bytesRead: Int
                        var totalBytesRead = 0

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            if (totalBytesRead % (1024 * 1024) == 0) {
                                Log.d(TAG, "Downloaded ${totalBytesRead / (1024 * 1024)} MB")
                            }
                        }
                    }
                }

                if (isFileCorrupt(file, expectedFileSize)) {
                    Log.e(TAG, "Downloaded file is corrupt or incomplete.")
                    return@withContext false
                }

                Log.d(TAG, "File saved to: ${file.absolutePath}")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading or saving the file: ${e.message}")
                return@withContext false
            }
        }
    }

    private fun isFileCorrupt(file: File, expectedFileSize: Int?): Boolean {
        return expectedFileSize == null || !file.exists() || file.length() != expectedFileSize.toLong()
    }
}
