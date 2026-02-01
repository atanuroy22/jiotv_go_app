package com.skylake.skytv.jgorunner.services.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.ExoPlayJetScreen
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme
import com.skylake.skytv.jgorunner.ui.tvhome.ChannelResponse
import com.skylake.skytv.jgorunner.ui.tvhome.M3UChannelExp
import com.skylake.skytv.jgorunner.utils.DeviceUtils
import com.skylake.skytv.jgorunner.utils.normalizePlaybackUrl
import com.skylake.skytv.jgorunner.utils.withQuality

@SuppressLint("MutableCollectionMutableState")
class ExoPlayJet : ComponentActivity() {

    private val tag = "ExoJetPack"

    private var videoUrlState by mutableStateOf("http://localhost:5001/live/143.m3u8")
    private var logoUrlState by mutableStateOf("https://www.sonypicturesnetworks.com/images/logos/SET%20LOGO.png")
    private var channelNameState by mutableStateOf("HANA4k")
    private var signatureFallbackState by mutableStateOf("0x0")
    private var channelListState by mutableStateOf<ArrayList<ChannelInfo>?>(null)
    private var currentChannelIndexState by mutableIntStateOf(-1)

    private val prefManager by lazy { SkySharedPref.getInstance(this) }
    private val pipController by lazy { PipController(this) }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyIntent(intent)

        applyImmersive(this)

        setContent {
            JGOTheme(themeOverride = prefManager.myPrefs.darkMODE) {
                ExoPlayJetScreen(
                    preferenceManager = prefManager,
                    videoUrl = videoUrlState,
                    channelList = channelListState,
                    currentChannelIndex = currentChannelIndexState
                )
            }
        }

        PlayerCommandBus.setOnStateChanged {
            if (PlayerCommandBus.isInPipMode && prefManager.myPrefs.enablePip) {
                runOnUiThread { pipController.updatePipActionsIfAllowed() }
            }
        }

        PlayerCommandBus.setPipRequestHandlers(
            openApp = {
                runOnUiThread {
                    if (isInPictureInPictureMode) {
                        try {
                            moveTaskToBack(false)
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            closePip = {
                runOnUiThread {
                    if (isInPictureInPictureMode) {
                        PlayerCommandBus.requestStopPlayback()
                        try {
                            finishAndRemoveTask()
                        } catch (_: Exception) {
                            finish()
                        }
                    }
                }
            }
        )
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val wasInPip =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false

        val idx = intent.getIntExtra("current_channel_index", -1)
        val urlFromIntent = intent.getStringExtra("video_url")

        if (wasInPip) {
            applyIntent(intent)
            val currentList = channelListState
            if (!currentList.isNullOrEmpty() && idx in currentList.indices) {
                PlayerCommandBus.requestSwitch(index = idx)
            } else if (!urlFromIntent.isNullOrEmpty()) {
                PlayerCommandBus.requestSwitch(url = urlFromIntent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipController.updatePipActionsIfAllowed()
        } else {
            applyIntent(intent)
        }
    }

    private fun applyIntent(intent: Intent?) {
        val parsed = PlayerIntentHandler.parse(intent)
        signatureFallbackState = parsed.signature ?: "0x0"
        channelListState = parsed.channelList
        currentChannelIndexState = parsed.currentChannelIndex
        if (channelListState.isNullOrEmpty()) {
            channelListState = loadChannelListFromCache(intent)
        }

        var activeList = channelListState
        val incomingUrl = parsed.videoUrl
        if (!activeList.isNullOrEmpty() && !incomingUrl.isNullOrEmpty()) {
            val normalizedIncoming = normalizePlaybackUrl(this, incomingUrl)
            val idx = currentChannelIndexState
            val idxMatches =
                idx in activeList.indices &&
                        normalizePlaybackUrl(this, activeList[idx].videoUrl ?: "") == normalizedIncoming
            if (!idxMatches) {
                val found = activeList.indexOfFirst {
                    normalizePlaybackUrl(this, it.videoUrl ?: "") == normalizedIncoming
                }
                if (found >= 0) {
                    currentChannelIndexState = found
                }
            }
        }

        activeList = channelListState
        if (!activeList.isNullOrEmpty() && currentChannelIndexState in activeList.indices) {
            val cur = activeList[currentChannelIndexState]
            videoUrlState = cur.videoUrl ?: videoUrlState
            logoUrlState = cur.logoUrl ?: logoUrlState
            channelNameState = cur.channelName ?: channelNameState
            Log.d(
                tag,
                "Loaded channel from list: $channelNameState at index $currentChannelIndexState"
            )
        } else {
            videoUrlState = parsed.videoUrl ?: videoUrlState
            logoUrlState = parsed.logoUrl ?: logoUrlState
            channelNameState = parsed.channelName ?: channelNameState
            Log.d(tag, "Loaded channel from direct intent extras: $channelNameState")
        }
    }

    private fun loadChannelListFromCache(intent: Intent?): ArrayList<ChannelInfo>? {
        val kind = intent?.getStringExtra("channel_list_kind")?.lowercase()
        val port = prefManager.myPrefs.jtvGoServerPort
        val basefinURL = "http://localhost:$port"
        return try {
            when (kind) {
                "jio" -> {
                    val cachedJson = getSharedPreferences("channel_cache", MODE_PRIVATE)
                        .getString("channels_json", null)
                        ?.takeIf { it.isNotBlank() }
                        ?: return null
                    val response = Gson().fromJson(cachedJson, ChannelResponse::class.java) ?: return null
                    ArrayList(
                        response.result.map { ch ->
                            ChannelInfo(
                                withQuality(this, ch.channel_url),
                                "$basefinURL/jtvimage/${ch.logoUrl}",
                                ch.channel_name
                            )
                        }
                    )
                }
                "m3u" -> {
                    val json = prefManager.myPrefs.channelListJson?.takeIf { it.isNotBlank() } ?: return null
                    val type = object : TypeToken<List<M3UChannelExp>>() {}.type
                    val channels: List<M3UChannelExp> = Gson().fromJson(json, type) ?: return null
                    ArrayList(channels.map { ch -> ChannelInfo(ch.url, ch.logo ?: "", ch.name) })
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("WrongConstant")
    fun applyImmersive(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (prefManager.myPrefs.enablePip && !DeviceUtils.isTvDevice(this)) {
            pipController.enterPipIfAllowed()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerCommandBus.isInPipMode = isInPictureInPictureMode
        PlayerCommandBus.isEnteringPip = false
        PlayerCommandBus.notifyPipModeChanged(isInPictureInPictureMode)

        if (isInPictureInPictureMode) {
            if (prefManager.myPrefs.enablePip && !DeviceUtils.isTvDevice(this)) pipController.updatePipActionsIfAllowed()
        } else {
            if (prefManager.myPrefs.enablePip && !DeviceUtils.isTvDevice(this)) {
                window.decorView.postDelayed({
                    if (!this@ExoPlayJet.hasWindowFocus()) {
                        PlayerCommandBus.requestStopPlayback()
                        try {
                            finishAndRemoveTask()
                        } catch (_: Exception) {
                            finish()
                        }
                    }
                }, 120)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!PlayerCommandBus.isInPipMode) {
            PlayerCommandBus.requestStopPlayback()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!PlayerCommandBus.isEnteringPip && !PlayerCommandBus.isInPipMode) {
            PlayerCommandBus.requestStopPlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!PlayerCommandBus.isInPipMode || isFinishing) {
            PlayerCommandBus.requestStopPlayback()
        }
    }
}
