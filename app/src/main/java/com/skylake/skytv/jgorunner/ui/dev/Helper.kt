package com.skylake.skytv.jgorunner.ui.dev

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.skylake.skytv.jgorunner.data.SkySharedPref

object Helper {
    private const val TAG = "[JGO]"
    private const val EASY_MODE = 0
    private const val EXPERT_MODE = 1

    fun setEasyMode(context: Context) {
        val preferenceManager = SkySharedPref(context)
        Toast.makeText(context, "Setting operation mode to EASY", Toast.LENGTH_SHORT).show()
        Toast.makeText(context, "Applying default settings", Toast.LENGTH_SHORT).show()
        Toast.makeText(context, "Restart JTV-Go App", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Setting operation mode to EASY")

        preferenceManager.myPrefs.autoStartServer = true
        preferenceManager.myPrefs.loginChk = true

        preferenceManager.myPrefs.jtvGoServerPort = 5350
        preferenceManager.myPrefs.iptvAppPackageName = "tvzone"


        preferenceManager.myPrefs.operationMODE = EASY_MODE
        preferenceManager.savePreferences()
    }

    fun setExpertMode(context: Context) {
        val preferenceManager = SkySharedPref(context)
        Toast.makeText(context, "Setting operation mode to EXPERT", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Setting operation mode to EXPERT")

        preferenceManager.myPrefs.iptvAppPackageName = ""

        preferenceManager.myPrefs.operationMODE = EXPERT_MODE
        preferenceManager.savePreferences()
    }



}
