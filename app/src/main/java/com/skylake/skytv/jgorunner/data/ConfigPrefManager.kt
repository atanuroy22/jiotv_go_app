package com.skylake.skytv.jgorunner.data

import android.content.Context
import android.util.Log
import com.skylake.skytv.jgorunner.utils.createJsonFile

fun applyConfigurations(context: Context, preferenceManager: SkySharedPref) {
    val isFlagSetForLOCAL = preferenceManager.getKey("isFlagSetForLOCAL")
    if (isFlagSetForLOCAL == "No") {
        preferenceManager.setKey("__Public", "")
        Log.d("DIX-isFlagSetForLOCAL", isFlagSetForLOCAL)
    } else {
        preferenceManager.setKey("__Public", " --public")
        Log.d("DIX-isFlagSetForLOCAL", "Public")
    }

    val isFlagSetForPORT = preferenceManager.getKey("isFlagSetForPORT")
    if (isFlagSetForPORT == "5350") {
        preferenceManager.setKey("__Port", " --port 5350")
        Log.d("DIX-isFlagSetForPORT", isFlagSetForPORT)
    } else {
        preferenceManager.setKey("__Port", " --port $isFlagSetForPORT")
        Log.d("DIX-isFlagSetForPORT", "Custom Port $isFlagSetForPORT")
    }

    val isFlagSetForAutoStartOnBoot = preferenceManager.getKey("isFlagSetForAutoStartOnBoot")
    if (isFlagSetForAutoStartOnBoot == "Yes") {
        Log.d("DIX-isAutoStartOnBoot", " $isFlagSetForAutoStartOnBoot")
    } else {
        Log.d("DIX-isAutoStartOnBoot", " $isFlagSetForAutoStartOnBoot")
    }

    val isFlagSetForAutoBootIPTV = preferenceManager.getKey("isFlagSetForAutoBootIPTV")
    if (isFlagSetForAutoBootIPTV == "Yes") {
        Log.d("DIX-isisFlagSetForAutoBootIPTV", " $isFlagSetForAutoBootIPTV")
    } else {
        Log.d("DIX-isisFlagSetForAutoBootIPTV", " $isFlagSetForAutoBootIPTV")
    }

    val isFlagSetForEPG = preferenceManager.getKey("isFlagSetForEPG")
    if (isFlagSetForEPG == "Yes") {
        val jsonData = """
            {
                "epg": false,
                "disable_logout": true,
                "title": "花より団子",
  
            }
        """.trimIndent()

        createJsonFile(context, "jiotv_go.json", jsonData)

        preferenceManager.setKey("__EPG", " --config 'jiotv_go.json'")


        Log.d("DIX-isFlagSetForEPG", isFlagSetForEPG)
    } else {
        preferenceManager.setKey("__EPG", " ")
        Log.d("DIX-isFlagSetForEPG", "No EPG")
    }

    val isFlagSetForAutoStartServer = preferenceManager.getKey("isFlagSetForAutoStartServer")
    if (isFlagSetForAutoStartServer == "Yes") {
        Log.d("DIX-AutoStartServer", isFlagSetForAutoStartServer)

        val isFlagSetForAutoIPTV = preferenceManager.getKey("isFlagSetForAutoIPTV")
        if (isFlagSetForAutoIPTV == "No") {
            Log.d("DIX-ForAutoIPTV", isFlagSetForAutoIPTV)
        }
    }


    val __Port = preferenceManager.getKey("__Port")
    val __Public = preferenceManager.getKey("__Public")
    val __EPG = preferenceManager.getKey("__EPG")

    preferenceManager.setKey("__MasterArgs_temp", " run$__Port$__Public$__EPG")
}

