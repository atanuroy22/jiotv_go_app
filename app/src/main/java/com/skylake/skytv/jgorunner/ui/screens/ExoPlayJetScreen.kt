package com.skylake.skytv.jgorunner.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.receivers.PipActionReceiver
import com.skylake.skytv.jgorunner.services.player.PlayerCommandBus
import com.skylake.skytv.jgorunner.ui.tvhome.ChannelUtils
import com.skylake.skytv.jgorunner.ui.tvhome.extractChannelIdFromPlayUrl
import com.skylake.skytv.jgorunner.utils.DeviceUtils
import com.skylake.skytv.jgorunner.utils.cleanupPlaybackLogic
import com.skylake.skytv.jgorunner.utils.containsAnyId
import com.skylake.skytv.jgorunner.utils.normalizePlaybackUrl
import com.skylake.skytv.jgorunner.utils.setupCustomPlaybackLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

const val TAG = "ExoJetScreen"

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("AutoboxingStateCreation", "DefaultLocale")
@Composable
fun ExoPlayJetScreen(
    preferenceManager: SkySharedPref,
    videoUrl: String,
    channelList: ArrayList<ChannelInfo>?,
    currentChannelIndex: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayDisplayTimeMs = 3000
    val localPORT by remember { mutableIntStateOf(preferenceManager.myPrefs.jtvGoServerPort) }
    val basefinURL = "http://localhost:$localPORT"
    val tvNAV = preferenceManager.myPrefs.selectedRemoteNavTV ?: "0"
    val pipEnabled = preferenceManager.myPrefs.enablePip
    val isTv = remember {
        try {
            val pm = context.packageManager
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        } catch (_: Exception) {
            false
        }
    }
    val focusRequester = remember { FocusRequester() }
    var currentIndex by remember { mutableStateOf(currentChannelIndex) }
    var overrideVideoUrl by remember { mutableStateOf<String?>(null) }
    var showChannelPanel by remember { mutableStateOf(false) }
    var panelSelectedIndex by remember { mutableStateOf(currentChannelIndex.coerceAtLeast(0)) }
    var currentProgramName by remember { mutableStateOf<String?>(null) }
    var showChannelOverlay by remember { mutableStateOf(false) }
    val retryCountRef = remember { mutableStateOf(0) }
    var exoPlayerView: PlayerView? by remember { mutableStateOf(null) }
    var numericBuffer by remember { mutableStateOf("") }
    var showNumericOverlay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var numericJob: Job? by remember { mutableStateOf(null) }
    var isControllerVisible by remember { mutableStateOf(false) }
    var zoneWebView: WebView? by remember { mutableStateOf(null) }
    var isWebLoading by remember { mutableStateOf(false) }
    var lastLoadedZoneDrmUrl by remember { mutableStateOf<String?>(null) }

    val activeUrlRaw = overrideVideoUrl ?: channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl
    val normalizedActiveUrl = remember(activeUrlRaw, currentIndex, channelList, videoUrl) {
        normalizePlaybackUrl(context, activeUrlRaw)
    }
    val useZoneDrmWebPlayer = remember(activeUrlRaw, normalizedActiveUrl) {
        // Detect DRM from raw URL first; normalization can transform /play/ into /live/*.m3u8.
        isLikelyDrmRoute(activeUrlRaw) || isLikelyDrmRoute(normalizedActiveUrl)
    }
    val zoneDrmStartupUrl = remember(activeUrlRaw, localPORT, preferenceManager.myPrefs.filterQX) {
        toZoneDrmUrl(
            inputUrl = activeUrlRaw,
            localPort = localPORT,
            quality = preferenceManager.myPrefs.filterQX
        )
    }


    // Keep local state in sync when a new intent provides a different index.
    // Only update when a real index is given (>= 0). -1 means "play by URL" (e.g. plugin/Zee
    // channels) and must not be coerced to 0, which would wrongly play channelList[0].
    LaunchedEffect(currentChannelIndex) {
        if (currentChannelIndex >= 0 && currentChannelIndex != currentIndex) {
            currentIndex = currentChannelIndex
        }
    }

    // --- Epg fetch ---
    val epgCache = remember { mutableStateMapOf<String, Pair<Long, String?>>() }
    LaunchedEffect(showChannelPanel, channelList) {
        if (showChannelPanel && channelList != null) {
            while (showChannelPanel) {
                val visibleChannels = channelList
                withContext(Dispatchers.IO) {
                    visibleChannels.mapNotNull { channel ->
                        val channelId = extractChannelIdFromPlayUrl(channel.videoUrl)
                        if (channelId != null) {
                            async {
                                val now = System.currentTimeMillis()
                                val cached = epgCache[channelId]
                                if (cached == null || now - cached.first > 900_000) {
                                    val epgName = fetchCurrentProgram(basefinURL, channelId)
                                    epgCache[channelId] = now to epgName
                                }
                            }
                        } else null
                    }.awaitAll()
                }
                delay(900_000)
            }
        }
    }

    // --- Resize Config ---
    val resizeModes = remember {
        listOf(
            Triple(RESIZE_MODE_FIT, "Default", "DEF"),
            Triple(RESIZE_MODE_FILL, "Stretch", "STRETCH")
        )
    }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var showResizeOverlay by remember { mutableStateOf(false) }
    var resizeOverlayLabel by remember { mutableStateOf(resizeModes.first().second) }
    var resizeOverlayJob by remember { mutableStateOf<Job?>(null) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }

    // --- Key Num Entry ---
    fun commitNumericEntryLocal(list: ArrayList<ChannelInfo>?) {
        val num = numericBuffer.toIntOrNull()
        if (num != null && !list.isNullOrEmpty()) {
            val idx = (num - 1).coerceIn(0, list.size - 1)
            currentIndex = idx
        }
        numericBuffer = ""
        showNumericOverlay = false
    }

    val exoPlayer = remember {
        initializePlayer(
            getCurrentVideoUrl = { overrideVideoUrl ?: channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl },
            context = context,
            retryCountRef = retryCountRef
        )
    }

    // --- Resize Config ---
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height != 0) {
                    val ar = videoSize.width.toFloat() / videoSize.height.toFloat()
                    if (ar.isFinite() && ar > 0.1f && ar < 10f) {
                        videoAspect = ar
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (useZoneDrmWebPlayer) {
                        zoneWebView?.onResume()
                    } else {
                        exoPlayer.playWhenReady = true
                    }
                }

                Lifecycle.Event.ON_PAUSE -> if (!PlayerCommandBus.isInPipMode && !PlayerCommandBus.isEnteringPip) {
                    if (useZoneDrmWebPlayer) {
                        zoneWebView?.onPause()
                    } else {
                        exoPlayer.playWhenReady = false
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    // Do not release here — onDispose always runs and is the
                    // single correct place to release. Releasing in both places
                    // causes a double-release which is undefined behaviour.
                    try {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    } catch (_: Exception) {
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                zoneWebView?.destroy()
            } catch (_: Exception) {
            }
            cleanupPlaybackLogic(exoPlayer)  // remove listener ref before release → no leak
            exoPlayer.release()
        }
    }

    // --- Custom Buffering ---
    var isBuffering by remember { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val bufferHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val showBufferingRunnable = Runnable { isBuffering = true }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    // Debounce: only show spinner if buffering persists > 1500ms.
                    // seekTo() and normal HLS segment fetches complete well within
                    // that window, so they never show the spinner at all.
                    bufferHandler.postDelayed(showBufferingRunnable, 1500)
                } else {
                    bufferHandler.removeCallbacks(showBufferingRunnable)
                    isBuffering = false
                }
                // Update PiP actions as play/pause state may have effectively changed
                PlayerCommandBus.notifyStateChanged()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerCommandBus.notifyStateChanged()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                PlayerCommandBus.notifyStateChanged()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onPositionDiscontinuity(reason: Int) {
                // Seek or track changes can flip playing state; refresh PiP actions
                PlayerCommandBus.notifyStateChanged()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            bufferHandler.removeCallbacks(showBufferingRunnable)
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(lifecycleOwner.lifecycle.currentStateAsState().value) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentIndex) {
        overrideVideoUrl = null
        retryCountRef.value = 0
        // Do NOT set isBuffering=true here — let the 800ms debounce in the Player.Listener
        // handle it. Setting it immediately causes the spinner to flash on every channel
        // switch even when the new channel starts playing within milliseconds.
        try {
            val currentUrlRaw = channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl
            val currentUrl = normalizePlaybackUrl(context, currentUrlRaw)
            if (currentUrl.isBlank()) {
                Toast.makeText(context, "Invalid stream URL", Toast.LENGTH_SHORT).show()
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    exoPlayer.playWhenReady = false
                } catch (_: Exception) {
                }
                return@LaunchedEffect
            }

            val isDrmRoute = isLikelyDrmRoute(currentUrlRaw) || isLikelyDrmRoute(currentUrl)
            if (isDrmRoute) {
                // DRM channels are rendered in embedded Shaka WebView to keep Zone UI shell.
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    exoPlayer.playWhenReady = false
                } catch (_: Exception) {
                }

                if (!PlayerCommandBus.isInPipMode) {
                    showChannelOverlay = true
                    delay(overlayDisplayTimeMs.toLong())
                    showChannelOverlay = false
                }
                return@LaunchedEffect
            }

            val mediaItem = buildMediaItemForPlaybackUrl(currentUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            setupCustomPlaybackLogic(exoPlayer, currentUrl)
        } catch (_: Exception) {
            Toast.makeText(context, "Stream not supported", Toast.LENGTH_SHORT).show()
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.playWhenReady = false
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        }

        if (!PlayerCommandBus.isInPipMode) {
            showChannelOverlay = true
            delay(overlayDisplayTimeMs.toLong())
            showChannelOverlay = false
        }
    }

    // Expose player controls to PiP actions
    DisposableEffect(channelList) {
        PlayerCommandBus.setHandlers(
            playPause = {
                try {
                    if (useZoneDrmWebPlayer) {
                        zoneWebView?.evaluateJavascript(
                            """
                            (function() {
                              var v = document.querySelector('video');
                              if (!v) return;
                              if (v.paused) { v.play(); } else { v.pause(); }
                            })();
                            """.trimIndent(),
                            null
                        )
                    } else {
                        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                    }
                } finally {
                    PlayerCommandBus.notifyStateChanged()
                    // Some launchers refresh PiP actions a tick later; send a follow-up update
                    scope.launch {
                        delay(120)
                        PlayerCommandBus.notifyStateChanged()
                    }
                }
            },
            next = {
                channelList?.let {
                    if (it.isNotEmpty()) {
                        val ni = (currentIndex + 1) % it.size
                        overrideVideoUrl = null
                        currentIndex = ni
                        PlayerCommandBus.requestSwitch(index = ni)
                    }
                }
            },
            prev = {
                channelList?.let {
                    if (it.isNotEmpty()) {
                        val pi = if (currentIndex - 1 < 0) it.size - 1 else currentIndex - 1
                        overrideVideoUrl = null
                        currentIndex = pi
                        PlayerCommandBus.requestSwitch(index = pi)
                    }
                }
            },
            // Use ExoPlayer.isPlaying so PiP icon matches actual playback (pause shows Play icon, play shows Pause icon)
            isPlaying = { if (useZoneDrmWebPlayer) true else exoPlayer.isPlaying }
        )
        PlayerCommandBus.setOnStopPlayback {
            try {
                if (useZoneDrmWebPlayer) {
                    zoneWebView?.onPause()
                } else {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    exoPlayer.playWhenReady = false
                }
            } catch (_: Exception) {
            }
        }
        onDispose {
            PlayerCommandBus.setOnStopPlayback(null)
            PlayerCommandBus.clearHandlers()
        }
    }

    // Respond to external switch requests (e.g., when a new channel is picked while in PiP)
    DisposableEffect(channelList) {
        PlayerCommandBus.setOnSwitchRequest { url, index ->
            try {
                var targetUrl: String? = null
                if (index != null && !channelList.isNullOrEmpty() && index in channelList.indices) {
                    // Index switch has priority
                    overrideVideoUrl = null
                    currentIndex = index
                    targetUrl = channelList[index].videoUrl
                } else if (!url.isNullOrEmpty()) {
                    val normalizedIncoming = normalizePlaybackUrl(context, url)
                    val incomingId = extractChannelIdFromPlayUrl(normalizedIncoming)
                    val foundIdx = if (incomingId != null && !channelList.isNullOrEmpty()) {
                        channelList.indexOfFirst {
                            val candidate = it.videoUrl ?: return@indexOfFirst false
                            extractChannelIdFromPlayUrl(normalizePlaybackUrl(context, candidate)) == incomingId
                        }
                    } else {
                        -1
                    }
                    if (foundIdx >= 0 && !channelList.isNullOrEmpty()) {
                        overrideVideoUrl = null
                        currentIndex = foundIdx
                        targetUrl = channelList[foundIdx].videoUrl
                    } else {
                        overrideVideoUrl = url
                        targetUrl = url
                    }
                }
                if (!targetUrl.isNullOrEmpty()) {
                    retryCountRef.value = 0
                    val finalUrl = normalizePlaybackUrl(context, targetUrl)
                    if (finalUrl.isBlank()) return@setOnSwitchRequest
                    val mediaItem = buildMediaItemForPlaybackUrl(finalUrl)
                    // Do NOT stop() — stop() blanks the surface. Set the new item
                    // directly; keepContentOnPlayerReset keeps the last frame
                    // visible until the new channel's first frame arrives.
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            } catch (_: Exception) {
            }
        }
        onDispose { PlayerCommandBus.setOnSwitchRequest(null) }
    }

    LaunchedEffect(currentIndex) {
        val channelId =
            channelList?.getOrNull(currentIndex)?.videoUrl?.let { extractChannelIdFromPlayUrl(it) }
        currentProgramName = channelId?.let { epgCache[it]?.second }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(panelSelectedIndex, showChannelPanel) {
        if (showChannelPanel) {
            val safeIndex = panelSelectedIndex.coerceIn(0, (channelList?.size ?: 1) - 1)
            listState.animateScrollToItem(safeIndex)
        }
    }

    // Back-to-PiP: pressing Back enters PiP (YouTube-like); fallback to finish if PiP unsupported
    BackHandler {
        when {
            showChannelPanel -> {

                showChannelPanel = false
            }

            isControllerVisible -> {

                exoPlayerView?.hideController()
            }

            else -> {
                val act = (context as? Activity)
                val pm = context.packageManager
                val supportsPip = try {
                    pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                } catch (_: Exception) {
                    false
                }
                if (!isTv && supportsPip && pipEnabled) {
                    try {
                        PlayerCommandBus.isEnteringPip = true
                        val params = android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()
                        act?.enterPictureInPictureMode(params)
                    } catch (_: Exception) {
                        act?.finish()
                    }
                } else {
                    act?.finish()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isOkKey =
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    if (isControllerVisible) {
                        return@onPreviewKeyEvent false
                    } else {
                        panelSelectedIndex = currentIndex
                        showChannelPanel = channelList != null
                        return@onPreviewKeyEvent true
                    }
                }

                if (event.type == KeyEventType.KeyDown) {
                    val digit = when (event.key) {
                        Key.Zero -> 0
                        Key.One -> 1
                        Key.Two -> 2
                        Key.Three -> 3
                        Key.Four -> 4
                        Key.Five -> 5
                        Key.Six -> 6
                        Key.Seven -> 7
                        Key.Eight -> 8
                        Key.Nine -> 9
                        else -> null
                    }
                    if (digit != null) {
                        if (!(numericBuffer.isEmpty() && digit == 0) && numericBuffer.length < 4) {
                            numericBuffer += digit.toString()
                            showNumericOverlay = true
                            numericJob?.cancel()
                            numericJob = scope.launch {
                                delay(1200)
                                commitNumericEntryLocal(channelList)
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                }

                if (event.type == KeyEventType.KeyUp && isOkKey) {
                    if (showChannelPanel) {
                        if (!channelList.isNullOrEmpty()) {
                            currentIndex = panelSelectedIndex.coerceIn(0, channelList.size - 1)
                            showChannelPanel = false
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (isControllerVisible) {
                        return@onPreviewKeyEvent false
                    } else {
                        if (useZoneDrmWebPlayer) {
                            return@onPreviewKeyEvent false
                        }
                        val androidKeyEvent = android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        )
                        val handled = exoPlayerView?.dispatchKeyEvent(androidKeyEvent) == true
                        return@onPreviewKeyEvent handled
                    }
                }

                if (event.type == KeyEventType.KeyUp) {
                    if (showChannelPanel && (event.key == Key.DirectionRight || event.key == Key.Back)) {
                        showChannelPanel = false
                        return@onPreviewKeyEvent true
                    }
                }

                if (showChannelPanel && event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (!channelList.isNullOrEmpty()) {
                                panelSelectedIndex =
                                    (panelSelectedIndex - 1 + channelList.size) % channelList.size
                            }
                            return@onPreviewKeyEvent true
                        }

                        Key.DirectionDown -> {
                            if (!channelList.isNullOrEmpty()) {
                                panelSelectedIndex = (panelSelectedIndex + 1) % channelList.size
                            }
                            return@onPreviewKeyEvent true
                        }

                        else -> {}
                    }
                }

                return@onPreviewKeyEvent handleTVRemoteKey(
                    event = event,
                    tvNAV = tvNAV,
                    channelList = channelList,
                    currentIndexState = { currentIndex },
                    onChannelChange = { currentIndex = it }
                )
            }


    ) {
        val currentResizeMode = resizeModes[resizeModeIndex].first
        val isDefaultMode = resizeModeIndex == 0
        val aspectForModifier = when {
            isDefaultMode -> 16f / 9f
            currentResizeMode == RESIZE_MODE_FILL -> videoAspect
            else -> videoAspect
        }

        if (useZoneDrmWebPlayer) {
            AndroidView(
                factory = {
                    WebView(it).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.defaultTextEncodingName = "utf-8"
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        isLongClickable = false
                        isHapticFeedbackEnabled = false
                        setOnLongClickListener { true }

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request == null) return
                                (context as? Activity)?.runOnUiThread {
                                    try {
                                        request.grant(request.resources)
                                    } catch (_: Exception) {
                                        request.deny()
                                    }
                                }
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR ||
                                    consoleMessage.message().contains("drm", ignoreCase = true) ||
                                    consoleMessage.message().contains("widevine", ignoreCase = true)
                                ) {
                                    Log.e(
                                        TAG,
                                        "ZoneWeb ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                                    )
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                isWebLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                isWebLoading = false
                                                                // Hard-hide page/chrome/shaka controls to keep a Zone-like shell UI.
                                view.evaluateJavascript(
                                    """
                                    (function() {
                                      try {
                                                                                var cssId = 'zone-shell-hide-ui';
                                                                                var css = `
                                                                                    header, nav, .navbar, .sticky, .top-0, .bottom-0,
                                                                                    .shaka-controls-container, .shaka-overflow-menu,
                                                                                    .shaka-settings-menu, .shaka-context-menu,
                                                                                    [class*="controls"], [class*="player-controls"],
                                                                                    [class*="header"], [class*="footer"] {
                                                                                        display: none !important;
                                                                                        opacity: 0 !important;
                                                                                        visibility: hidden !important;
                                                                                        pointer-events: none !important;
                                                                                    }
                                                                                    body, html { background: black !important; overflow: hidden !important; width: 100% !important; height: 100% !important; margin: 0 !important; }
                                                                                    iframe, .player, .shaka-video-container { width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; margin: 0 auto !important; }
                                                                                    video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }
                                                                                `;

                                                                                var style = document.getElementById(cssId);
                                                                                if (!style) {
                                                                                    style = document.createElement('style');
                                                                                    style.id = cssId;
                                                                                    document.head.appendChild(style);
                                                                                }
                                                                                style.textContent = css;

                                                                                var tryPlayAndCenter = function() {
                                                                                    var v = document.querySelector('video');
                                                                                    if (!v) return;
                                                                                    v.controls = false;
                                                                                    v.style.width = '100vw';
                                                                                    v.style.height = '100vh';
                                                                                    v.style.maxWidth = '100vw';
                                                                                    v.style.maxHeight = '100vh';
                                                                                    v.style.objectFit = 'contain';
                                                                                    v.muted = true;
                                                                                    var p = v.play();
                                                                                    if (p && p.then) {
                                                                                        p.then(function() {
                                                                                            setTimeout(function() { v.muted = false; }, 300);
                                                                                        }).catch(function() {});
                                                                                    }
                                                                                };
                                                                                tryPlayAndCenter();

                                                                                if (!window.__zoneShellUiObserverInstalled) {
                                                                                    window.__zoneShellUiObserverInstalled = true;
                                                                                    var applyHide = function() {
                                                                                        var vv = document.querySelector('video');
                                                                                        if (vv) {
                                                                                            vv.controls = false;
                                                                                            vv.style.width = '100vw';
                                                                                            vv.style.height = '100vh';
                                                                                            vv.style.maxWidth = '100vw';
                                                                                            vv.style.maxHeight = '100vh';
                                                                                            vv.style.objectFit = 'contain';
                                                                                            if (vv.paused) {
                                                                                                var pp = vv.play();
                                                                                                if (pp && pp.catch) pp.catch(function(){});
                                                                                            }
                                                                                        }
                                                                                    };
                                                                                    var obs = new MutationObserver(applyHide);
                                                                                    obs.observe(document.documentElement || document.body, { childList: true, subtree: true, attributes: true });
                                                                                    setInterval(applyHide, 800);
                                                                                }
                                      } catch(e) {}
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }

                        zoneWebView = this
                        if (zoneDrmStartupUrl.isNotBlank()) {
                            loadUrl(zoneDrmStartupUrl)
                            lastLoadedZoneDrmUrl = zoneDrmStartupUrl
                        }
                    }
                },
                update = { webView ->
                    zoneWebView = webView
                    if (zoneDrmStartupUrl.isNotBlank() && zoneDrmStartupUrl != lastLoadedZoneDrmUrl) {
                        webView.loadUrl(zoneDrmStartupUrl)
                        lastLoadedZoneDrmUrl = zoneDrmStartupUrl
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
        } else AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    // Keep the last rendered frame frozen on screen during buffering,
                    // retries, and player resets — this is the primary fix for black screens.
                    setKeepContentOnPlayerReset(true)
                    controllerAutoShow = false
                    controllerShowTimeoutMs = 3000
                    setResizeMode(resizeModes[resizeModeIndex].first)
                    player = exoPlayer
                    exoPlayerView = this

                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            isControllerVisible = visibility == View.VISIBLE
                        }
                    )

                    // Inject resize btn
                    post {
                        try {
                            val controller =
                                findViewById<View>(androidx.media3.ui.R.id.exo_controller) as? ViewGroup
                                    ?: return@post

                            //---
                            val nextBtn = controller.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_next)
                            val prevBtn = controller.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_prev)

                            nextBtn?.setImageResource(R.drawable.ic_skip_next_24)
                            prevBtn?.setImageResource(R.drawable.ic_skip_previous_24)
                            nextBtn?.visibility = View.VISIBLE
                            prevBtn?.visibility = View.VISIBLE
                            nextBtn?.isEnabled = true
                            prevBtn?.isEnabled = true


                            nextBtn?.setOnClickListener {
                                try {
                                    val intent = Intent(context, PipActionReceiver::class.java).apply {
                                        action = PipActionReceiver.ACTION_NEXT
                                    }
                                    context.sendBroadcast(intent)
                                } catch (e: Exception) {
                                    Log.e("ExoCustom", "Failed to send NEXT: ${e.message}")
                                }
                            }

                            prevBtn?.setOnClickListener {
                                try {
                                    val intent = Intent(context, PipActionReceiver::class.java).apply {
                                        action = PipActionReceiver.ACTION_PREV
                                    }
                                    context.sendBroadcast(intent)
                                } catch (e: Exception) {
                                    Log.e("ExoCustom", "Failed to send PREV: ${e.message}")
                                }
                            }

                            Log.d("ExoCustom", "ExoPlayer next/prev hooked to PipActionReceiver")
                            //---
                            var targetBar: ViewGroup? = null
                            val candidateIds = listOf(
                                androidx.media3.ui.R.id.exo_basic_controls,
                                androidx.media3.ui.R.id.exo_bottom_bar,
                                androidx.media3.ui.R.id.exo_controls_background
                            )
                            for (cid in candidateIds) {
                                val v = controller.findViewById<View>(cid)
                                if (v is ViewGroup) {
                                    targetBar = v; break
                                }
                            }
                            if (targetBar == null) {
                                fun deepest(group: ViewGroup): ViewGroup {
                                    var best = group
                                    (0 until group.childCount).forEach { i ->
                                        val c = group.getChildAt(i)
                                        if (c is ViewGroup) {
                                            val d = deepest(c)
                                            if (d.childCount > best.childCount) best = d
                                        }
                                    }
                                    return best
                                }
                                targetBar = deepest(controller)
                            }

                            // PiP button (left of resize) — phones/tablets only
                            val isTV = try {
                                val pm = context.packageManager
                                pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                            } catch (_: Exception) {
                                false
                            }
                            val supportsPip = try {
                                (context as? Activity)?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
                            } catch (_: Exception) {
                                false
                            }

                            val pipButton = ImageButton(context).apply {
                                setBackgroundResource(android.R.color.transparent)
                                setImageResource(R.drawable.ic_pip_24)
                                contentDescription = "Picture-in-Picture"
                                imageAlpha = 230
                                val pad = (4 * resources.displayMetrics.density).toInt()
                                setPadding(pad, pad, pad, pad)
                                setOnClickListener {
                                    try {
                                        (context as? Activity)?.let { act ->
                                            // Mark entering PiP so onPause doesn't pause playback
                                            PlayerCommandBus.isEnteringPip = true
                                            val params =
                                                android.app.PictureInPictureParams.Builder()
                                                    .setAspectRatio(android.util.Rational(16, 9))
                                                    .build()
                                            act.enterPictureInPictureMode(params)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed PiP enter: ${e.message}")
                                    }
                                }
                                val params = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                params.gravity = Gravity.CENTER_VERTICAL
                                layoutParams = params
                            }

                            val resizeButton = ImageButton(context).apply {
                                setBackgroundResource(android.R.color.transparent)
                                setImageResource(R.drawable.ic_aspect_ratio_24)
                                contentDescription = "Resize mode"
                                imageAlpha = 220
                                val pad = (4 * resources.displayMetrics.density).toInt()
                                setPadding(pad, pad, pad, pad)
                                setOnClickListener {
                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                    val mode = resizeModes[resizeModeIndex]
                                    exoPlayerView?.setResizeMode(mode.first)
                                    exoPlayerView?.requestLayout()
                                    resizeOverlayLabel = mode.second
                                    showResizeOverlay = true
                                    resizeOverlayJob?.cancel()
                                    resizeOverlayJob = scope.launch {
                                        delay(1200)
                                        showResizeOverlay = false
                                    }
                                }
                                val params = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                params.gravity = Gravity.CENTER_VERTICAL
                                params.marginStart = (8 * resources.displayMetrics.density).toInt()
                                layoutParams = params
                            }

                            val count = targetBar.childCount
                            val insertIndex = if (count > 0) count - 1 else count
                            if (!isTv && !isTV && supportsPip && pipEnabled) {
                                try {
                                    // Add PiP first, then resize so PiP sits left of resize
                                    targetBar.addView(pipButton, insertIndex)
                                } catch (_: Exception) {
                                    targetBar.addView(pipButton)
                                }
                            }
                            try {
                                targetBar.addView(resizeButton, insertIndex + 1)
                            } catch (_: Exception) {
                                targetBar.addView(resizeButton)
                            }

                            hookExoControllerButtons(this@apply, context)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to position resize button: ${e.message}")
                        }
                    }
                }
            },
            modifier = when {
                currentResizeMode == RESIZE_MODE_FILL -> Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)

                isDefaultMode -> Modifier
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)

                else -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectForModifier)
                    .align(Alignment.Center)
            }
        )

        if ((isBuffering && !useZoneDrmWebPlayer) || (isWebLoading && useZoneDrmWebPlayer)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    stroke = Stroke(width = 4.dp.toPx()),
                    gapSize = 8.dp,
                    amplitude = 0.75f,
                )
            }
        }

        // When PiP mode changes, immediately hide controllers/overlays
        DisposableEffect(Unit) {
            PlayerCommandBus.setOnPipModeChanged { isPip ->
                if (isPip) {
                    try {
                        showChannelPanel = false
                        isControllerVisible = false
                        exoPlayerView?.useController = false
                        exoPlayerView?.hideController()
                    } catch (_: Exception) {
                    }
                } else {
                    try {
                        exoPlayerView?.useController = true
                    } catch (_: Exception) {
                    }
                }
            }
            onDispose { PlayerCommandBus.setOnPipModeChanged(null) }
        }

        LaunchedEffect(showChannelPanel) {
            if (showChannelPanel) {
                focusRequester.requestFocus()
            }
        }

        AnimatedVisibility(
            visible = !PlayerCommandBus.isInPipMode && ((showChannelOverlay && channelList != null) || isControllerVisible),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ChannelInfoOverlay(
                channelList = channelList,
                currentIndex = currentIndex,
                currentProgramName = currentProgramName
            )
            CurrentTimeOverlay(
                visible = !PlayerCommandBus.isInPipMode && ((showChannelOverlay && channelList != null) || isControllerVisible)
            )
        }

        // --- Key Num Config ---
        if (!PlayerCommandBus.isInPipMode && showNumericOverlay && numericBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = numericBuffer,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
        }

        if (!PlayerCommandBus.isInPipMode && isControllerVisible && !useZoneDrmWebPlayer) {
            IconButton(
                onClick = {
                    panelSelectedIndex = currentIndex
                    showChannelPanel = channelList != null && !showChannelPanel
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesomeMotion,
                    contentDescription = "Toggle channel list",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        if (showResizeOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = resizeOverlayLabel,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        // Left-side channel panel
        if (showChannelPanel && channelList != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .width(280.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = channelList,
                        key = { idx, _ -> idx }
                    ) { idx, ch ->
                        val isSelected = idx == panelSelectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color(0x33FFFFFF) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Transparent)
                                .clickable {
                                    panelSelectedIndex = idx
                                    currentIndex = idx
                                    showChannelPanel = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ch.logoUrl)
                                    .size(80)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = String.format("%02d", idx + 1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val channelId = extractChannelIdFromPlayUrl(ch.videoUrl)
                            Column {
                                Text(
                                    text = ch.channelName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 1
                                )
                                EpgText(channelId, epgCache)
                            }
                        }
                    }
                }
            }
        }
    }

    // Also handle direct video URL changes (e.g., new intent while in PiP with no channel list/index)
    LaunchedEffect(videoUrl) {
        if (channelList.isNullOrEmpty() || currentIndex < 0) {
            val rawUrl = channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl
            val url = normalizePlaybackUrl(context, rawUrl)
            if (url.isBlank()) return@LaunchedEffect
            val isDrmRoute = isLikelyDrmRoute(rawUrl) || isLikelyDrmRoute(url)
            if (isDrmRoute) return@LaunchedEffect
            val mediaItem = buildMediaItemForPlaybackUrl(url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Clear sentinel on leaving the player so future autoplay sessions can trigger again
    DisposableEffect(Unit) {
        onDispose {
            try {
                preferenceManager.myPrefs.currChannelUrl = ""
                preferenceManager.savePreferences()
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun Dp.toPx(): Float = with(LocalDensity.current) { this@toPx.toPx() }

@Composable
fun EpgText(
    channelId: String?,
    epgCache: Map<String, Pair<Long, String?>>
) {
    val epg = channelId?.let { epgCache[it]?.second }
    if (!epg.isNullOrBlank()) {
        Text(
            text = epg,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}


suspend fun fetchCurrentProgram(basefinURL: String, channelId: String): String? {
    val epgURLc = "$basefinURL/epg/${channelId}/0"
//    Log.d("NANOdix1", epgURLc)
    val epgData = ChannelUtils.fetchEpg(epgURLc)
//    Log.d(TAG, "Now playing: ${epgData?.showname}")
    return epgData?.showname
}

@SuppressLint("DefaultLocale")
@Composable
fun ChannelInfoOverlay(
    channelList: List<ChannelInfo>?,
    currentIndex: Int,
    currentProgramName: String?
) {
    channelList?.getOrNull(currentIndex)?.let { channel ->
        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Logo
                        Card(
                            modifier = Modifier.size(60.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.DarkGray.copy(
                                    alpha = 0.5f
                                )
                            )
                        ) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = "Channel Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(60.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp), clip = false)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            // Channel Number + Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = String.format("%02d", currentIndex + 1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .widthIn(min = 36.dp)
                                )
                                Text(
                                    text = channel.channelName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    maxLines = 1
                                )
                            }

                            currentProgramName?.let { programName ->
                                Spacer(modifier = Modifier.height(0.dp))
                                Text(
                                    text = programName,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentTimeOverlay(visible: Boolean) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AnimatedVisibility(
        visible = visible && isLandscape,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                var currentTime by remember { mutableStateOf(getCurrentFormattedTime()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentTime = getCurrentFormattedTime()
                        delay(60000L)
                    }
                }
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun getCurrentFormattedTime(): String {
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR)
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return String.format("%02d:%02d %s", if (hour == 0) 12 else hour, minute, amPm)
}

private fun buildMediaItemForPlaybackUrl(url: String): MediaItem {
    val builder = MediaItem.Builder().setUri(url.toUri())
    val mimeType = inferPlaybackMimeType(url)
    if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)

    // For HLS live streams: stay 15 s behind the live edge.
    // ExoPlayer silently pre-fetches the next 15 s of segments in the background,
    // so any network hiccup shorter than 15 s is absorbed with zero stall or black
    // screen. Playback may start 15 s delayed (acceptable for TV live streams).
    val isLikelyLive = mimeType == MimeTypes.APPLICATION_M3U8 || mimeType == null
    if (isLikelyLive) {
        builder.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(15_000)   // play 15 s behind live edge
                .setMinOffsetMs(8_000)       // never closer than 8 s to live edge
                .setMaxOffsetMs(25_000)      // never further than 25 s behind
                .setMinPlaybackSpeed(0.97f)  // allow slight slowdown to hold offset
                .setMaxPlaybackSpeed(1.03f)  // allow slight speedup to catch up
                .build()
        )
    }

    return builder.build()
}

private fun inferPlaybackMimeType(url: String): String? {
    val cleaned = url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        cleaned.endsWith(".m3u8") || cleaned.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        cleaned.endsWith(".mpd") || cleaned.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        // Plugin channels (e.g. /zee5/...) served by the local server have no file extension.
        // All local-server endpoints ultimately serve HLS, so hint ExoPlayer accordingly.
        cleaned.contains("localhost") && !cleaned.substringAfterLast("/").contains(".") -> MimeTypes.APPLICATION_M3U8
        else -> null
    }
}

private fun isLikelyDrmRoute(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("/play/") ||
            u.contains("/mpd/") ||
            u.contains(".mpd") ||
            u.contains("widevine") ||
            u.contains("render.dash")
}

private fun toZoneDrmUrl(inputUrl: String, localPort: Int, quality: String?): String {
    val cleaned = inputUrl.trim()
    if (cleaned.isBlank()) return cleaned
    if (cleaned.contains("/mpd/", ignoreCase = true) || cleaned.contains(".mpd", ignoreCase = true)) {
        return cleaned
    }

    val playRegex = Regex(".*/play/(\\d+)(?:[/?].*)?$")
    val match = playRegex.find(cleaned) ?: return cleaned
    val id = match.groupValues.getOrNull(1).orEmpty()
    if (id.isBlank()) return cleaned

    val q = quality?.takeIf { it.isNotBlank() } ?: "high"
    return "http://localhost:$localPort/mpd/$id?q=$q&pm=hd"
}

@UnstableApi
fun initializePlayer(
    getCurrentVideoUrl: () -> String,
    context: Context,
    retryCountRef: MutableState<Int>
): ExoPlayer {
    // Short timeouts: if a segment fetch hangs, fail fast (8 s) and retry
    // instead of waiting the OS default ~15 s with a black screen.
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(8_000)
//        .setUserAgent(userAgent) //Future Ref
    val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpDataSourceFactory)
                // Pre-fetch segments 15 s ahead of playback position at the factory level
                // (complements the per-MediaItem live configuration below).
                .setLiveTargetOffsetMs(15_000)

    // Buffer tuning for smooth live-stream playback:
    //   minBufferMs  20 s  – start refilling as soon as ahead-buffer < 20 s
    //   maxBufferMs  60 s  – keep up to 60 s downloaded ahead
    //   bufferForPlaybackMs  2.5 s  – fast cold start
    //   bufferForPlaybackAfterRebufferMs  6 s  – after a stall, wait for 6 s
    //       before resuming so we don't stutter again 2 s later
    //   setPrioritizeTimeOverSizeThresholds  – buffer is measured in time not
    //       bytes, so behaviour is consistent across bitrate switches
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs                     */ 14_000,
            /* maxBufferMs                     */ 45_000,
            /* bufferForPlaybackMs             */ 1_200,
            /* bufferForPlaybackAfterRebufferMs */ 1_200
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()
    retryCountRef.value = 0
    val retryHandler = Handler(Looper.getMainLooper())
    val stallHandler = Handler(Looper.getMainLooper())
    val fastStallThresholdMs = 1_800L
    var stallRecoveries = 0
    val maxStallRecoveries = 8

    fun prepareAndPlay(seekToPosition: Long = 0L) {
        val normalizedUrl = normalizePlaybackUrl(context, getCurrentVideoUrl())
        if (normalizedUrl.isBlank()) return
        val mediaItem = buildMediaItemForPlaybackUrl(normalizedUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (seekToPosition > 0L) {
            player.seekTo(seekToPosition)
        }
        player.playWhenReady = true
    }

    prepareAndPlay()

    // Always Retry
    player.addListener(object : Player.Listener {
        private var isBufferingLong = false
        private val bufferingStallRunnable = Runnable {
            if (!player.playWhenReady || player.playbackState != Player.STATE_BUFFERING) return@Runnable
            isBufferingLong = true
            if (stallRecoveries >= maxStallRecoveries) return@Runnable
            stallRecoveries += 1

            val currentUrl = getCurrentVideoUrl()
            try {
                // Exo can get stuck in STATE_BUFFERING without emitting onPlayerError.
                // Re-preparing the current media item forces a playlist refresh and
                // usually recovers from token/segment expiry stalls.
                if (currentUrl.containsAnyId()) {
                    prepareAndPlay(player.currentPosition)
                } else {
                    prepareAndPlay()
                }
            } catch (_: Exception) {
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val attempt = retryCountRef.value + 1
            retryCountRef.value = attempt
            if (attempt > 12) {
                try {
                    player.stop()
                    player.clearMediaItems()
                    player.playWhenReady = false
                } catch (_: Exception) {
                }
                return
            }
            val currentUrl = getCurrentVideoUrl()
            val retryDelayMs = (attempt * 150L).coerceAtMost(900L)
            retryHandler.removeCallbacksAndMessages(null)
            retryHandler.postDelayed(
                {
                    try {
                        // Do NOT call player.stop() — stop() clears the video surface
                        // causing a black screen. Instead, set the media item and call
                        // prepare() directly; the last rendered frame stays visible on
                        // screen until new video frames arrive.
                        if (currentUrl.containsAnyId()) {
                            prepareAndPlay(player.currentPosition)
                        } else {
                            prepareAndPlay()
                        }
                    } catch (_: Exception) {
                    }
                },
                retryDelayMs
            )
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    if (player.playWhenReady) {
                        stallHandler.removeCallbacks(bufferingStallRunnable)
                        stallHandler.postDelayed(bufferingStallRunnable, fastStallThresholdMs)
                    }
                }

                Player.STATE_READY -> {
                    isBufferingLong = false
                    stallRecoveries = 0
                    stallHandler.removeCallbacks(bufferingStallRunnable)
                }

                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    stallHandler.removeCallbacks(bufferingStallRunnable)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                stallHandler.removeCallbacks(bufferingStallRunnable)
            } else if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING && !isBufferingLong) {
                stallHandler.removeCallbacks(bufferingStallRunnable)
                stallHandler.postDelayed(bufferingStallRunnable, fastStallThresholdMs)
            }
        }
    })


    return player
}

fun handleTVRemoteKey(
    event: KeyEvent,
    tvNAV: String?,
    channelList: ArrayList<ChannelInfo>?,
    currentIndexState: () -> Int,
    onChannelChange: (Int) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyUp) return false

    val currentIndex = currentIndexState()
    val newIndex = when (tvNAV) {
        "0" -> when (event.key) {
            Key.ChannelUp -> (currentIndex + 1) % (channelList?.size ?: return false)
            Key.ChannelDown -> if (currentIndex - 1 < 0) (channelList?.size
                ?: 1) - 1 else currentIndex - 1

            else -> return false
        }

        "1" -> when (event.key) {
            Key.DirectionUp -> (currentIndex + 1) % (channelList?.size ?: return false)
            Key.DirectionDown -> if (currentIndex - 1 < 0) (channelList?.size
                ?: 1) - 1 else currentIndex - 1

            else -> return false
        }

        else -> return false
    }

    onChannelChange(newIndex)
    return true
}

private fun hookExoControllerButtons(playerView: PlayerView, context: Context) {
    playerView.post {
        try {
            val controller = playerView.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_controller) ?: return@post



        } catch (e: Exception) {
            Log.e("ExoCustom", "Failed to hook Exo buttons: ${e.message}")
        }
    }
}
