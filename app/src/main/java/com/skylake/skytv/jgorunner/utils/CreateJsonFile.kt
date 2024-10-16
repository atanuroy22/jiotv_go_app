package com.skylake.skytv.jgorunner.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

fun createJsonFile(context: Context, fileName: String, jsonData: String, useExternalStorage: Boolean) {
    val file: File

    if (useExternalStorage) {
        // Get external storage directory
        val externalStoragePath = Environment.getExternalStorageDirectory().path
        val folder = File(externalStoragePath, ".jiotv_go")

        // Create the folder if it doesn't exist
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e("FileCreation", "Failed to create directory: ${folder.absolutePath}")
                return
            }
        }

        file = File(folder, fileName)
    } else {
        // Internal storage
        file = File(context.filesDir, fileName)
    }

    // Check if the file already exists
    if (file.exists()) {
        Log.d("FileCreation", "File already exists.")
        return
    }

    try {
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                writer.write(jsonData)
            }
        }
        Log.d("FileCreation", "File created and data written successfully: ${file.absolutePath}")
    } catch (e: Exception) {
        Log.e("FileCreation", "Error creating file: ${e.message}")
    }
}
