package com.skylake.skytv.jgorunner.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JGO_BootReceiver"
        private const val ACTION_LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Received boot action: ${intent.action}")
                handleBootCompleted(context)
            }
            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        try {
            val preferenceManager = SkySharedPref.getInstance(context)
            val prefs = preferenceManager.myPrefs ?: run {
                Log.e(TAG, "Preferences object is null. Aborting.")
                return
            }

            if (prefs.autoStartOnBoot) {
                Log.d(TAG, "Auto-start is enabled. Proceeding...")
                if (prefs.autoStartOnBootForeground) {
                    prefs.autoStartServer = true
                    preferenceManager.savePreferences()
                    launchAppInForeground(context)
                } else {
                    startServerInBackground(context)
                }
            } else {
                Log.d(TAG, "Auto-start on boot is disabled in preferences.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "An error occurred in handleBootCompleted", e)
            Toast.makeText(context, "[JGO] Error during boot startup.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startServerInBackground(context: Context) {
        Log.d(TAG, "Starting BinaryService in the background.")
        Toast.makeText(context, "[JGO] Starting server in background...", Toast.LENGTH_SHORT).show()

        val serviceIntent = Intent(context, BinaryService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "BinaryService successfully started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BinaryService", e)
            Toast.makeText(context, "[JGO] Failed to start server.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchAppInForeground(context: Context) {
        Log.d(TAG, "Attempting to launch app in the foreground.")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Toast.makeText(context, "[JGO] Launching app...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Launch intent sent to start the main activity.")
        } else {
            Log.e(TAG, "Could not get launch intent for package: ${context.packageName}")
            Toast.makeText(context, "[JGO] Error: Unable to launch app.", Toast.LENGTH_LONG).show()
        }
    }
}
