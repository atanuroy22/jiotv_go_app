package com.skylake.skytv.jgorunner.activities

import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.CastScreen
import java.util.Locale

class CastActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url: String?
        val defaultUrlTemplate = "http://localhost:%d"
        val prefManager = SkySharedPref.getInstance(this)
        val savedPortNumber = prefManager.myPrefs.jtvGoServerPort
        val filterQ = prefManager.myPrefs.filterQ
        val filterL = prefManager.myPrefs.filterL
        val filterC = prefManager.myPrefs.filterC

        val queryParams = buildList {
            if (!filterQ.isNullOrEmpty()) add("q=${Uri.encode(filterQ)}")
            if (!filterL.isNullOrEmpty()) add("language=${Uri.encode(filterL)}")
            if (!filterC.isNullOrEmpty()) add("category=${Uri.encode(filterC)}")
        }
        val extraFilterUrl = if (queryParams.isEmpty()) {
            "/"
        } else {
            "/?" + queryParams.joinToString("&")
        }

        url = String.format(Locale.getDefault(), defaultUrlTemplate, savedPortNumber) + extraFilterUrl

        setContent {
            CastScreen(
                context = this,
                viewURL = url)
        }
    }
}
