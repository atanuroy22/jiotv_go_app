package com.skylake.skytv.jgorunner.services.player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.app.PendingIntentCompat
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.ExoPlayJetScreen
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme
import android.graphics.drawable.Icon
import android.util.Rational
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.receivers.PipActionReceiver
import android.content.pm.PackageManager

@SuppressLint("MutableCollectionMutableState")
class ExoPlayJet : ComponentActivity() {

    private val TAG: String = "ExoJetPack"

    private var videoUrlState by mutableStateOf("http://localhost:5001/live/143.m3u8")
    private var logoUrlState by mutableStateOf("https://www.sonypicturesnetworks.com/images/logos/SET%20LOGO.png")
    private var channelNameState by mutableStateOf("HANA4k")
    private var signatureFallbackState by mutableStateOf("0x0")
    private var channelListState by mutableStateOf<ArrayList<ChannelInfo>?>(null)
    private var currentChannelIndexState by mutableStateOf(-1)
    // Bump this to force-refresh PiP actions (some launchers cache by PendingIntent equality)
    private var pipActionSeq: Int = 0

    private fun isTvDevice(): Boolean {
        val pm = packageManager
        return try {
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        } catch (_: Exception) { false }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("WrongConstant", "ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }


    val prefManager = SkySharedPref.getInstance(this)

        setContent {
            JGOTheme(themeOverride = prefManager.myPrefs.darkMODE) {
                ExoPlayJetScreen(
                    preferenceManager = prefManager,
                    videoUrl = videoUrlState,
//                    logoUrl = logoUrlState,
//                    channelName = channelNameState,
//                    signatureFallback = signatureFallbackState,
                    channelList = channelListState,
                    currentChannelIndex = currentChannelIndexState
                )
            }
        }

        // Update PiP actions when play/pause state changes
        PlayerCommandBus.setOnStateChanged {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PlayerCommandBus.isInPipMode && prefManager.myPrefs.enablePip) {
                runOnUiThread { updatePipActions() }
            }
        }

        // Allow PiP broadcast actions to restore or close
        PlayerCommandBus.setPipRequestHandlers(
            openApp = {
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                        // Leaving PiP returns to full-screen automatically
                        try { moveTaskToBack(false) } catch (_: Exception) {}
                        // Android restores UI when PiP is dismissed by bringing the task to foreground.
                    }
                }
            },
            closePip = {
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                        // Explicitly stop playback to avoid background audio
                        PlayerCommandBus.requestStopPlayback()
                        try { finishAndRemoveTask() } catch (_: Exception) { finish() }
                    }
                }
            }
        )
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If we are in PiP and a new channel was requested, switch playback in-place and stay in PiP
        val wasInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false

        // Parse minimal info for fast switch
        val list: ArrayList<ChannelInfo>? = IntentCompat.getParcelableArrayListExtra(intent, "channel_list_data", ChannelInfo::class.java)
        val idx = intent.getIntExtra("current_channel_index", -1)
        val urlFromIntent = intent.getStringExtra("video_url")

        if (wasInPip) {
            // Keep internal state (Compose) in sync for when the user restores fullscreen later
            handleIntent(intent)
            // Request the running Compose player to switch without restoring full-screen
            if (!list.isNullOrEmpty() && idx in list.indices) {
                PlayerCommandBus.requestSwitch(index = idx)
            } else if (!urlFromIntent.isNullOrEmpty()) {
                PlayerCommandBus.requestSwitch(url = urlFromIntent)
            }
            // We're already in PiP; just refresh the actions to reflect play/pause state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipActions()
        } else {
            // Normal path when not in PiP
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val defaultVideoUrl = "http://localhost:5001/live/143.m3u8"
        val defaultLogoUrl = "https://www.sonypicturesnetworks.com/images/logos/SET%20LOGO.png"
        val defaultChannelName = "HANA4k"

//        val channelList = intent.getParcelableArrayListExtra<ChannelInfo>("channel_list_data")

        val channelList: ArrayList<ChannelInfo>? = IntentCompat.getParcelableArrayListExtra(intent, "channel_list_data", ChannelInfo::class.java)

        val currentChannelIndex = intent.getIntExtra("current_channel_index", -1)

        val signature = intent.getStringExtra("zone")

        signatureFallbackState = if (signature.isNullOrEmpty()) "0x0" else signature
        channelListState = channelList
        currentChannelIndexState = currentChannelIndex

        if (!channelList.isNullOrEmpty() && currentChannelIndex in channelList.indices) {
            val currentChannel: ChannelInfo = channelList[currentChannelIndex]
            videoUrlState = currentChannel.videoUrl ?: defaultVideoUrl
            logoUrlState = currentChannel.logoUrl ?: defaultLogoUrl
            channelNameState = currentChannel.channelName ?: defaultChannelName

            Log.d(TAG, "Loaded channel from list: $channelNameState at index $currentChannelIndex")
        } else {
            videoUrlState = intent.getStringExtra("video_url") ?: defaultVideoUrl
            logoUrlState = intent.getStringExtra("logo_url") ?: defaultLogoUrl
            channelNameState = intent.getStringExtra("ch_name") ?: defaultChannelName
            Log.d(TAG, "Loaded channel from direct intent extras: $channelNameState")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefManager = SkySharedPref.getInstance(this)
        // Auto-enter PiP when user presses Home or swipes up (YouTube-like)
        if (prefManager.myPrefs.enablePip && !isTvDevice()) enterPipWithActions()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerCommandBus.isInPipMode = isInPictureInPictureMode
        PlayerCommandBus.isEnteringPip = false
        PlayerCommandBus.notifyPipModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            val prefManager = SkySharedPref.getInstance(this)
            if (prefManager.myPrefs.enablePip && !isTvDevice()) updatePipActions()
        } else {
            // Exiting PiP: if we are not coming back to fullscreen, ensure playback is stopped and close
            val prefManager = SkySharedPref.getInstance(this)
            if (prefManager.myPrefs.enablePip && !isTvDevice()) {
                // Wait a short moment to see if the Activity regains focus (Open/maximize case)
                window.decorView.postDelayed({
                    // If we still don't have focus, the PiP was likely dismissed/closed by gesture
                    if (!this@ExoPlayJet.hasWindowFocus()) {
                        PlayerCommandBus.requestStopPlayback()
                        try { finishAndRemoveTask() } catch (_: Exception) { finish() }
                    }
                }, 120)
            }
        }
    }

    private fun enterPipWithActions() {
        val prefManager = SkySharedPref.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && prefManager.myPrefs.enablePip && !isTvDevice()) {
            try {
                PlayerCommandBus.isEnteringPip = true
                pipActionSeq = (pipActionSeq + 1) % 1000
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(buildRemoteActions())
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enter PiP: ${e.message}")
            }
        }
    }

    private fun updatePipActions() {
        val prefManager = SkySharedPref.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && prefManager.myPrefs.enablePip && !isTvDevice()) {
            try {
                pipActionSeq = (pipActionSeq + 1) % 1000
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(buildRemoteActions())
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update PiP actions: ${e.message}")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // If we're going to background and not in PiP, ensure playback is halted (covers PiP dismiss gesture)
        if (!PlayerCommandBus.isInPipMode) {
            PlayerCommandBus.requestStopPlayback()
        }
    }

    override fun onPause() {
        super.onPause()
        // When not actively entering PiP, pause/stop playback on backgrounding to avoid lingering audio
        if (!PlayerCommandBus.isEnteringPip && !PlayerCommandBus.isInPipMode) {
            PlayerCommandBus.requestStopPlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure no background playback if Activity is destroyed outside PiP
        if (!PlayerCommandBus.isInPipMode || isFinishing) {
            PlayerCommandBus.requestStopPlayback()
        }
    }

    private fun buildRemoteActions(): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        fun pi(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(this, PipActionReceiver::class.java).setAction(action)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            // Make requestCode vary per refresh so launchers redraw action icons
            val req = requestCode + (pipActionSeq * 10)
            return PendingIntent.getBroadcast(this, req, intent, flags)
        }

        val iconPrev = Icon.createWithResource(this, R.drawable.ic_baseline_skip_previous_24)
        val iconToggle = if (PlayerCommandBus.isPlaying())
            Icon.createWithResource(this, R.drawable.ic_baseline_pause_24)
        else Icon.createWithResource(this, R.drawable.ic_baseline_play_arrow_24)
        val iconNext = Icon.createWithResource(this, R.drawable.ic_baseline_skip_next_24)
        val iconOpen = Icon.createWithResource(this, R.drawable.ic_open_in_app_24)
        val iconClose = Icon.createWithResource(this, R.drawable.ic_close_24)

        val actionPrev = RemoteAction(iconPrev, "Previous", "Previous", pi(PipActionReceiver.ACTION_PREV, 101))
        // Use the Play/Pause Unicode glyph to clearly indicate both states in a compact label
        val actionToggle = RemoteAction(iconToggle, "⏯", "Play/Pause", pi(PipActionReceiver.ACTION_TOGGLE, 102))
        val actionNext = RemoteAction(iconNext, "Next", "Next", pi(PipActionReceiver.ACTION_NEXT, 103))
        val openIntent = Intent(this, ExoPlayJet::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val openPiFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openPi = PendingIntent.getActivity(this, 104, openIntent, openPiFlags)
        val actionOpen = RemoteAction(iconOpen, "Open", "Open App", openPi)
        val actionClose = RemoteAction(iconClose, "Close", "Close PiP", pi(PipActionReceiver.ACTION_CLOSE, 105))
        // Order matters in PiP; keep transport in the center
        return listOf(actionPrev, actionToggle, actionNext, actionOpen, actionClose)
    }
}
