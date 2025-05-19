package com.skylake.skytv.jgorunner.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.CastScreen
import com.skylake.skytv.jgorunner.ui.screens.FirstModeSelectorScreen
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme
import java.util.Locale

class FirstModeSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url: String?
        val DEFAULT_URL_TEMPLATE: String = "http://localhost:%d"
        val prefManager = SkySharedPref.getInstance(this)
        val savedPortNumber = prefManager.myPrefs.jtvGoServerPort
        val filterQ = prefManager.myPrefs.filterQ
        val filterL = prefManager.myPrefs.filterL
        val filterC = prefManager.myPrefs.filterC

        val extraFilterUrl = buildString {
            append("/")

            if (!filterQ.isNullOrEmpty()) append("?q=$filterQ")

            if (!filterL.isNullOrEmpty()) {
                if (isNotEmpty()) append("&")
                append("language=$filterL")
            }

            if (!filterC.isNullOrEmpty()) {
                if (isNotEmpty()) append("&")
                append("category=$filterC")
            }
        }

        url = String.format(Locale.getDefault(), DEFAULT_URL_TEMPLATE, savedPortNumber) + extraFilterUrl

        setContent {
            JGOTheme {
                FirstModeSelectorScreen(
                    preferenceManager = prefManager
                )
            }
        }
    }
}
