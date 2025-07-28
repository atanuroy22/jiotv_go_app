package com.skylake.skytv.jgorunner.ui.dev

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import com.skylake.skytv.jgorunner.data.SkySharedPref

object Helper {
    private const val TAG = "[JGO]"
    private const val EASY_MODE = 0
    private const val EXPERT_MODE = 1

    fun setEasyMode(context: Context) {
        val preferenceManager = SkySharedPref(context)
        Toast.makeText(context, "Setting operation mode to SIMPLE", Toast.LENGTH_SHORT).show()
//        Toast.makeText(context, "Applying default settings", Toast.LENGTH_SHORT).show()
        Toast.makeText(context, "Restart JTV-Go App", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Setting operation mode to SIMPLE")

        preferenceManager.myPrefs.autoStartServer = true
        preferenceManager.myPrefs.loginChk = true

        preferenceManager.myPrefs.jtvGoServerPort = 5350
        preferenceManager.myPrefs.iptvAppPackageName = "tvzone"
//        preferenceManager.myPrefs.startTvAutomatically = true


        preferenceManager.myPrefs.operationMODE = EASY_MODE


        preferenceManager.savePreferences()

        Log.d(TAG, "${preferenceManager.myPrefs.operationMODE},${preferenceManager.myPrefs.jtvGoServerPort},${preferenceManager.myPrefs.autoStartServer},")
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

fun extractChannelIdFromPlayUrl(playUrl: String): String? {
    Log.d("NANOdix0", ">> $playUrl")
    return if ("live" in playUrl) {
        val regex = """.*/(\d+)(?:\.m3u8?|)?$""".toRegex()
        regex.find(playUrl)?.groups?.get(1)?.value
    } else {
        extraLoader(playUrl)
    }
}

val channelDict = mapOf(
    "0-9-9z5383486" to "445",
    "0-9-zeemarathi" to "1360",
    "/-_w3Jbq3QoW-mFCM2YIzxA" to "1146",
    "0-9-zeeyuva" to "414",
    "0-9-9z5383489" to "153",
    "0-9-zeetalkies" to "1358",
    "0-9-394" to "2758",
    "0-9-zeetvhd" to "167",
    "HgaB-u6rSpGx3mo4Xu3sLw" to "291",
    "/UI4QFJ_uRk6aLxIcADqa_A" to "154",
    "/H_ZvXWqHRGKpHcdDE5RcDA" to "1393",
    "/rPzF28qORbKZkhci_04fdQ" to "474",
    "0-9-zeecinema" to "484",
    "0-9-zeecinemahd" to "165",
    "0-9-pictures" to "1839",
    "0-9-tvpictureshd" to "185",
    "/oJ-TGgVFSgSMBUoTkauvFQ" to "289",
    "/Qyqz40bSQriqSuAC7R8_Fw" to "476",
    "/4Jcu195QTpCNBXGnpw2I6g" to "483",
    "0-9-zeeclassic" to "487",
    "0-9-zeeaction" to "488",
    "0-9-176" to "1691",
    "0-9-zeeanmolcinema" to "415",
    "0-9-9z5543514" to "1349",
    "0-9-channel_2105335046" to "1322",
    )

fun extraLoader(playUrl: String): String {
    channelDict.forEach { (keyword, code) ->
        if (playUrl.contains(keyword, ignoreCase = true)) {
            return code
        }
    }
    return ""
}


