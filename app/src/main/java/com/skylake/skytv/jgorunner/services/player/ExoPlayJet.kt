package com.skylake.skytv.jgorunner.services.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.ExoPlayJetScreen
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme

class ExoPlayJet : ComponentActivity() {

    private var initialVideoUrl: String = "http://localhost:5001/live/143.m3u8"
    private var channelLogoUrl: String = "https://www.sonypicturesnetworks.com/images/logos/SET%20LOGO.png"
    private var channelName: String = "HANA4k"

    private var TAG: String = "ExoJetPack"

    @SuppressLint("WrongConstant", "NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefManager = SkySharedPref.getInstance(this)

        val channelList = intent.getParcelableArrayListExtra<ChannelInfo>("channel_list_data")
        val currentChannelIndex = intent.getIntExtra("current_channel_index", -1)

        if (!channelList.isNullOrEmpty() && currentChannelIndex in channelList.indices) {
            val currentChannel: ChannelInfo = channelList[currentChannelIndex]
            initialVideoUrl = currentChannel.videoUrl ?: initialVideoUrl
            channelLogoUrl = currentChannel.logoUrl ?: channelLogoUrl
            channelName = currentChannel.channelName ?: channelName

            Log.d(TAG, "Loaded initial channel from list: $channelName at index $currentChannelIndex")
        } else {
            initialVideoUrl = intent.getStringExtra("video_url") ?: initialVideoUrl
            channelLogoUrl = intent.getStringExtra("logo_url") ?: channelLogoUrl
            channelName = intent.getStringExtra("ch_name") ?: channelName
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsets.Type.systemBars()) // Hides both status & nav bar
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE // Allow swipe to show

        setContent {
            JGOTheme(themeOverride = prefManager.myPrefs.darkMODE) {
                ExoPlayJetScreen(
                    preferenceManager = prefManager,
                    videoUrl = initialVideoUrl,
                    logoUrl = channelLogoUrl,
                    channelName = channelName,
                    channelList = channelList,
                    currentChannelIndex = currentChannelIndex
                )
            }
        }
    }

}
