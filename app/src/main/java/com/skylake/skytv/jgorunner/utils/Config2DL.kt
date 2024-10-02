package com.skylake.skytv.jgorunner.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryExecutor
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Config2DL {

    private const val TAG = "Config2DL"
    private const val FILE_NAME = "majorbin"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun startDownloadAndSave(context: Context, callback: BinaryExecutor.OutputCallback?) {

        val preferenceManager = SkySharedPref(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var expectedFileSize = preferenceManager.getKey("expectedFileSize")?.toIntOrNull()
                if (expectedFileSize == null || expectedFileSize == 0 || expectedFileSize == 69) {
                    expectedFileSize = fetchExpectedFileSize(context, callback)
                }

                val xCRL = String(Base64.decode("aHR0cHM6Ly9iaXQubHkvbWFqb3JiaW4=", Base64.DEFAULT))
                val saved = downloadLatestRelease(xCRL, context, expectedFileSize, callback)

                if (saved) {
                    Log.d(TAG, "File downloaded and saved as $FILE_NAME")
                    callback?.onOutput("[#] Installed Binary Successfully")
                } else {
                    Log.e(TAG, "Failed to download or save the binary")
                    callback?.onOutput("[#] Failed to download or save the binary")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading or saving binary: ${e.message}")
                callback?.onOutput("[#] Error downloading or saving binary.")
            }
        }
    }


    private suspend fun fetchExpectedFileSize(context: Context, callback: BinaryExecutor.OutputCallback?): Int? {
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
                    val releaseName = jsonObject.getString("tag_name")
                    val decodedName = String(Base64.decode("amlvdHZfZ28tYW5kcm9pZDUtYXJtdjc=", Base64.DEFAULT))

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name") == decodedName) {
                            val size = asset.getInt("size") // Get expected file size
                            preferenceManager.setKey("releaseName", releaseName) // Get expected file size
                            preferenceManager.setKey("expectedFileSize", size.toString())
                            Log.d(TAG, "Expected file size: $size bytes, "+"Downloading: ${size / (1024 * 1024)} MB")
//                            callback?.onOutput("[#] Downloading: ${size / (1024 * 1024)} MB")
//                            callback?.onOutput("[#] Downloading Binary: ${size / (1024 * 1024)} MB")
                            callback?.onOutput("[#] Downloading: Binary $releaseName [${size / (1024 * 1024)} MB]")
                            return@withContext size
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to fetch release information: HTTP ${urlConnection.responseCode}")
                    callback?.onOutput("[#] Failed to fetch release information: HTTP ${urlConnection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching expected file size: ${e.message}")
                callback?.onOutput("[#] Error fetching expected file size: ${e.message}")
            }
            return@withContext null
        }
    }

    private suspend fun downloadLatestRelease(fileUrl: String, context: Context, expectedFileSize: Int?, callback: BinaryExecutor.OutputCallback?): Boolean {
        return withContext(Dispatchers.IO) {
            val fileDir = context.filesDir
            val file = File(fileDir, FILE_NAME)

            // Check if the file already exists
            if (file.exists()) {
                if (isFileCorrupt(file, expectedFileSize)) {
                    Log.d(TAG, "Downloading updated binary.")
                    callback?.onOutput("[#] Downloading updated binary.")
                } else {
                    Log.d(TAG, "Binary is up-to-date.")
                    callback?.onOutput("[#] Binary is up-to-date.")
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
                    callback?.onOutput("[#] Server returned HTTP ${urlConnection.responseCode}")
                    return@withContext false
                }

                urlConnection.inputStream.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(1024 * 1024) // 1 MB buffer
                        var bytesRead: Int
                        var totalBytesRead = 0
                        val fileSize = urlConnection.contentLength
                        val halfwayMark = fileSize / 2
                        val seventyFivePercentMark = (fileSize * 3) / 4
                        var halfwayMessageShown = false
                        var seventyFiveMessageShown = false
                        var completedMessageShown = false

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Output message when 50% of the download is done
                            if (!halfwayMessageShown && totalBytesRead >= halfwayMark) {
                                val message = "[#] Downloaded 50%"
                                Log.d(TAG, message)
                                callback?.onOutput(message)
                                halfwayMessageShown = true
                            }

                            // Output message when 75% of the download is done
                            if (!seventyFiveMessageShown && totalBytesRead >= seventyFivePercentMark) {
                                val message = "[#] Downloaded 75%"
                                Log.d(TAG, message)
                                callback?.onOutput(message)
                                seventyFiveMessageShown = true
                            }

//                            // Output message when the download is complete
//                            if (!completedMessageShown && totalBytesRead == fileSize) {
//                                val message = "[#] Download completed"
//                                Log.d(TAG, message)
//                                callback?.onOutput(message)
//                                completedMessageShown = true
//                            }
                        }
                    }
                }



                if (isFileCorrupt(file, expectedFileSize)) {
                    Log.e(TAG, "Downloaded file is corrupt or incomplete.")
                    callback?.onOutput("[#] Downloaded file is corrupt or incomplete.")
                    return@withContext false
                }

                Log.d(TAG, "File saved to: ${file.absolutePath}")
                callback?.onOutput("[#] Binary Downloaded Successfully")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading or saving the file: ${e.message}")
                callback?.onOutput("[#] Error downloading or saving the file: ${e.message}")
                return@withContext false
            }
        }
    }


    private fun isFileCorrupt(file: File, expectedFileSize: Int?): Boolean {
        return expectedFileSize == null || !file.exists() || file.length() != expectedFileSize.toLong()
    }

    fun isFileSizeSame(Xsize: Int?, callback: (Boolean) -> Unit) {
        val url = String(Base64.decode("aHR0cHM6Ly9iaXQubHkvbWFqb3JiaW5hcGk=", Base64.DEFAULT))

        CoroutineScope(Dispatchers.IO).launch {
            var result = true // Default to true, meaning no error

            var connection: HttpURLConnection? = null

            try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = urlConnection.inputStream
                    val responseData = inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseData)
                    val assetsArray = jsonObject.getJSONArray("assets")

                    var fileSize: Long? = null
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        if (asset.getString("name").contains(String(Base64.decode("amlvdHZfZ28tYW5kcm9pZDUtYXJtdjc=", Base64.DEFAULT)))) {
                            fileSize = asset.getLong("size")
                            break
                        }
                    }

                    if (fileSize != null) {
                        result = logFileSizeComparison(Xsize, fileSize)
                    } else {
                        log("File not found in the latest release.")
                        result = false // Set to false if file not found
                    }
                } else {
                    log("Failed to retrieve data: ${urlConnection.responseMessage}")
                    result = false // Set to false if there is an error retrieving data
                }
            } catch (e: Exception) {
                log("Error occurred: ${e.message}")
                result = false // Set to false if there is an exception
            } finally {
                connection?.disconnect()
            }

            // Call the callback with the result
            callback(result)
        }
    }

    private fun logFileSizeComparison(Xsize: Int?, fileSize: Long): Boolean {
        return if (Xsize?.toLong() == fileSize) {
            log("File sizes are the same: $Xsize , $fileSize bytes")
            false // Sizes match
        } else {
            log("File sizes are different: Xsize = $Xsize bytes, API size = $fileSize bytes")
            true // Sizes do not match
        }
    }

    private fun log(message: String) {
        // Replace with your preferred logging method, e.g., Log.d(TAG, message)
        Log.d(TAG + "FIX", message)
    }





}
