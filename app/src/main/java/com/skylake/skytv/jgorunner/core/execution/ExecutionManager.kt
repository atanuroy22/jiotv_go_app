package com.skylake.skytv.jgorunner.core.execution

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun runBinary(
    activity: ComponentActivity,
    arguments: Array<String>,
    onRunSuccess: () -> Unit,
    onOutput: (String) -> Unit,
    forceStart: Boolean = false
) {
    // If already running and not forcing a restart, skip re-starting it
    if (BinaryService.isRunning && !forceStart) {
        CoroutineScope(Dispatchers.IO).launch {
            onRunSuccess()
            withContext(Dispatchers.Main) {
                BinaryService.instance?.binaryOutput?.observe(activity) { output ->
                    onOutput(output)
                }
            }
        }
        return
    }

    val preferenceManager = SkySharedPref.getInstance(activity)

    // If forceStart is requested and binary is running, stop it first then restart
    if (forceStart && BinaryService.isRunning) {
        val stopIntent = Intent(activity, BinaryService::class.java).apply {
            action = BinaryService.ACTION_STOP_BINARY
        }
        activity.startService(stopIntent)
        CoroutineScope(Dispatchers.IO).launch {
            // Wait for service to fully stop
            while (BinaryService.isRunning) delay(100)
            // Now start fresh
            startBinaryService(activity, preferenceManager, arguments, onRunSuccess, onOutput)
        }
        return
    }

    val intent = Intent(activity, BinaryService::class.java).apply {
        putExtra(
            "binaryFileLocation",
            preferenceManager.myPrefs.jtvGoBinaryName?.let {
                File(
                    activity.filesDir,
                    it
                ).absolutePath
            })
        putExtra("arguments", arguments)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.startForegroundService(intent)
    } else {
        activity.startService(intent)
    }

    CoroutineScope(Dispatchers.IO).launch {
        // Wait until the binary service is running
        while (!BinaryService.isRunning) delay(100)

        onRunSuccess()
        withContext(Dispatchers.Main) {
            BinaryService.instance?.binaryOutput?.observe(activity) { output ->
                onOutput(output)
            }
        }
    }
}

private fun startBinaryService(
    activity: ComponentActivity,
    preferenceManager: SkySharedPref,
    arguments: Array<String>,
    onRunSuccess: () -> Unit,
    onOutput: (String) -> Unit
) {
    val intent = Intent(activity, BinaryService::class.java).apply {
        putExtra(
            "binaryFileLocation",
            preferenceManager.myPrefs.jtvGoBinaryName?.let {
                File(activity.filesDir, it).absolutePath
            })
        putExtra("arguments", arguments)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.startForegroundService(intent)
    } else {
        activity.startService(intent)
    }
    CoroutineScope(Dispatchers.IO).launch {
        while (!BinaryService.isRunning) delay(100)
        onRunSuccess()
        withContext(Dispatchers.Main) {
            BinaryService.instance?.binaryOutput?.observe(activity) { output ->
                onOutput(output)
            }
        }
    }
}

fun stopBinary(
    context: Context,
    onBinaryStopped: () -> Unit
) {
    val intent = Intent(context, BinaryService::class.java).apply {
        action = BinaryService.ACTION_STOP_BINARY
    }

    context.startService(intent)
    onBinaryStopped()
}