package com.skylake.skytv.jgorunner.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

fun createJsonFile(context: Context, fileName: String, jsonData: String) {
    val file = File(context.filesDir, fileName)

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
        Log.d("FileCreation", "File created and data written successfully.")
    } catch (e: Exception) {
        Log.e("FileCreation", "Error creating file: ${e.message}")
    }
}
