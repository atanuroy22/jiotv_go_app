package com.skylake.skytv.jgorunner.data

import android.content.Context
import android.util.Log
import com.skylake.skytv.jgorunner.utils.createJsonFile

fun applyConfigurations(context: Context, preferenceManager: SkySharedPref) {
    val isFlagSetForLOCAL = preferenceManager.getBoolean("isFlagSetForLOCAL")
    if (!isFlagSetForLOCAL) {
        preferenceManager.setKey("__Public", "")
        Log.d("DIX-isFlagSetForLOCAL", "No")
    } else {
        preferenceManager.setKey("__Public", " --public")
        Log.d("DIX-isFlagSetForLOCAL", "Public")
    }

    val isCustomSetForPORT = preferenceManager.getInt("isCustomSetForPORT", 5350)
    preferenceManager.setKey("__Port", " --port $isCustomSetForPORT")
    Log.d("DIX-isCustomSetForPORT", isCustomSetForPORT.toString())

    val isFlagSetForAutoStartOnBoot = preferenceManager.getBoolean("isFlagSetForAutoStartOnBoot")
    Log.d("DIX-isAutoStartOnBoot", isFlagSetForAutoStartOnBoot.toString())

    val isFlagSetForAutoBootIPTV = preferenceManager.getBoolean("isFlagSetForAutoBootIPTV")
    Log.d("DIX-isAutoBootIPTV", isFlagSetForAutoBootIPTV.toString())

    val isFlagSetForEPG = preferenceManager.getBoolean("isFlagSetForEPG")
    if (isFlagSetForEPG) {

        jsonmaker(context);

        preferenceManager.setKey("__EPG", " --config 'configtv.json'")
    } else {
        preferenceManager.setKey("__EPG", " ")
    }
    Log.d("DIX-isFlagSetForEPG", isFlagSetForEPG.toString())

    val isFlagSetForAutoStartServer = preferenceManager.getBoolean("isFlagSetForAutoStartServer")
    if (isFlagSetForAutoStartServer) {
        Log.d("DIX-AutoStartServer", "true")

        val isFlagSetForAutoIPTV = preferenceManager.getBoolean("isFlagSetForAutoIPTV")
        if (!isFlagSetForAutoIPTV) {
            Log.d("DIX-ForAutoIPTV", "false")
        }
    }

    val __Port = preferenceManager.getKey("__Port")
    val __Public = preferenceManager.getKey("__Public")
    val __EPG = preferenceManager.getKey("__EPG")

    preferenceManager.setKey("__MasterArgs_temp", " run$__Port$__Public$__EPG")
}

fun jsonmaker(context: Context)  {
    val jsonData = """
                    {
            "debug": false,
            "disable_ts_handler": false,
            "disable_logout": false,
            "drm": false,
            "title": "JTV-GO",
            "disable_url_encryption": false,
            "path_prefix": "",
            "proxy": ""
        }
        """.trimIndent()

    createJsonFile(context, "jiotv-config.json", jsonData,true)


}

