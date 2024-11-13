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
        private const val TAG = "BootReceiver"
        private const val AUTO_BOOT_PREF_KEY = "isFlagSetForAutoStartOnBoot"
        private const val AUTO_BOOT_ENABLED = true
        private const val AUTO_BOOT_STATE_PREF_KEY = "isFlagSetForAutoBootIPTV"
        private const val AUTO_BOOT_STATE = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            handleBootCompleted(context)
        }
    }

    private fun handleBootCompleted(context: Context) {
        val preferenceManager = SkySharedPref(context)
        val isAutobootEnabled = preferenceManager.getBoolean(AUTO_BOOT_PREF_KEY)
        val stateBG = preferenceManager.getBoolean(AUTO_BOOT_STATE_PREF_KEY)

        if (isAutobootEnabled == AUTO_BOOT_ENABLED) {
            if (AUTO_BOOT_STATE == stateBG) {
                startBinaryService(context)
            } else {
                preferenceManager.setBoolean("isFlagSetForAutoStartServer", true)
                startBinaryServiceFG(context)
            }
        }
    }

    private fun startBinaryService(context: Context) {
        Toast.makeText(context, "[JGO] Running Server in Background", Toast.LENGTH_SHORT).show()
        val serviceIntent = Intent(context, BinaryService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        Log.d(TAG, "BinaryService started in the background.")
    }

    private fun startBinaryServiceFG(context: Context) {
        Toast.makeText(context, "[JGO] Running Server in Foreground", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "BinaryService started in the foreground.")
        launchMainActivity(context)
    }

    private fun launchMainActivity(context: Context) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Toast.makeText(context, "[JGO] App launched in the foreground.", Toast.LENGTH_SHORT)
                .show()
            Log.d(TAG, "App launched in the foreground.")
        } else {
            Toast.makeText(
                context,
                "[JGO] Unable to launch app: Launch intent is null.",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "Unable to launch app: Launch intent is null.")
        }
    }
}
