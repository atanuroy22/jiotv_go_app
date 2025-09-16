package com.skylake.skytv.jgorunner.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
import android.widget.ImageButton
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING
import coil.compose.AsyncImage
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.tvhome.ChannelUtils
import com.skylake.skytv.jgorunner.ui.tvhome.extractChannelIdFromPlayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.currentStateAsState


const val TAG = "ExoJetScreen"

@OptIn(UnstableApi::class)
@SuppressLint("AutoboxingStateCreation", "DefaultLocale")
@Composable
fun ExoPlayJetScreen(
    preferenceManager: SkySharedPref,
    videoUrl: String,
    channelList: ArrayList<ChannelInfo>?,
    currentChannelIndex: Int
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val overlayDisplayTimeMs = 3000
    val localPORT by remember { mutableIntStateOf(preferenceManager.myPrefs.jtvGoServerPort) }
    val basefinURL = "http://localhost:$localPORT"
    val tvNAV = preferenceManager.myPrefs.selectedRemoteNavTV ?: "0"

    val focusRequester = remember { FocusRequester() }
    var currentIndex by remember { mutableStateOf(currentChannelIndex) }
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
    // Resize modes: Fit, Zoom (Crop), Wide (Fixed Width), Stretch (Fill)
    val resizeModes = remember {
        listOf(
            // 'Default' provided as an alias to the standard FIT behavior so user can always return to baseline quickly.
            Triple(RESIZE_MODE_FIT, "Default", "DEF"),
            Triple(RESIZE_MODE_FIT, "Fit", "FIT"),
            Triple(RESIZE_MODE_ZOOM, "Zoom", "ZOOM"),
            Triple(RESIZE_MODE_FIXED_WIDTH, "Wide", "WIDE"),
            Triple(RESIZE_MODE_FILL, "Stretch", "STRETCH")
        )
    }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    var showResizeOverlay by remember { mutableStateOf(false) }
    var resizeOverlayLabel by remember { mutableStateOf(resizeModes.first().second) }
    var resizeOverlayJob by remember { mutableStateOf<Job?>(null) }
    // Track real video aspect ratio reported by decoder (fallback 16:9)
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }

    fun commitNumericEntryLocal(list: ArrayList<ChannelInfo>?) {
        val num = numericBuffer.toIntOrNull()
        if (num != null && list != null && list.isNotEmpty()) {
            val idx = (num - 1).coerceIn(0, list.size - 1)
            currentIndex = idx
        }
        numericBuffer = ""
        showNumericOverlay = false
    }


    val exoPlayer = remember {
        initializePlayer(
            getCurrentVideoUrl = { channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl },
            context = context,
            retryCountRef = retryCountRef
        )
    }

    // Listen for video size changes to adjust aspect ratio dynamically (except for Stretch / Fill)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height != 0) {
                    val ar = videoSize.width.toFloat() / videoSize.height.toFloat()
                    // Guard against extreme values
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
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(lifecycleOwner.lifecycle.currentStateAsState().value) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentIndex) {
        retryCountRef.value = 0
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        showChannelOverlay = true
        delay(overlayDisplayTimeMs.toLong())
        showChannelOverlay = false
    }

    LaunchedEffect(currentIndex) {
        val channelId = channelList?.getOrNull(currentIndex)?.videoUrl?.let { extractChannelIdFromPlayUrl(it) }
        currentProgramName = channelId?.let { fetchCurrentProgram(basefinURL, it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // if (event.type == KeyEventType.KeyUp &&
                //     (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                // ) {
                //     (exoPlayerView)?.showController()
                    
                // Prevent PlayerView's default 5s seek on LEFT by consuming KeyDown LEFT and opening the panel
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    panelSelectedIndex = currentIndex
                    showChannelPanel = channelList?.isNotEmpty() == true
                    return@onPreviewKeyEvent true
                }
                // handleTVRemoteKey(
                //     event = event,
                //     tvNAV = tvNAV,
                //     channelList = channelList,
                //     currentIndexState = { currentIndex },
                //     onChannelChange = { currentIndex = it }
                // )

                // Numeric channel entry (KeyDown to collect digits)
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
                        if (!(numericBuffer.isEmpty() && digit == 0)) { // avoid leading zero
                            if (numericBuffer.length < 4) {
                                numericBuffer += digit.toString()
                                showNumericOverlay = true
                                // restart commit timer
                                numericJob?.cancel()
                                numericJob = scope.launch {
                                    delay(1200)
                                    commitNumericEntryLocal(channelList)
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                // Global ENTER shows controller if panel isn't open
                if (event.type == KeyEventType.KeyUp) {
                    // Commit numeric entry immediately on ENTER
                    if (numericBuffer.isNotEmpty() && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                        numericJob?.cancel()
                        commitNumericEntryLocal(channelList)
                        return@onPreviewKeyEvent true
                    }
                    if (!showChannelPanel && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                        (exoPlayerView)?.showController()
                        return@onPreviewKeyEvent true
                    }
                    // Toggle panel
                    if (event.key == Key.Menu || event.key == Key.DirectionLeft) {
                        panelSelectedIndex = currentIndex
                        showChannelPanel = channelList?.isNotEmpty() == true
                        return@onPreviewKeyEvent true
                    }
                    if (showChannelPanel && (event.key == Key.DirectionRight || event.key == Key.Back)) {
                        showChannelPanel = false
                        return@onPreviewKeyEvent true
                    }
                    if (showChannelPanel) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    panelSelectedIndex = (panelSelectedIndex - 1 + channelList.size) % channelList.size
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionDown -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    panelSelectedIndex = (panelSelectedIndex + 1) % channelList.size
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    currentIndex = panelSelectedIndex.coerceIn(0, channelList.size - 1)
                                    showChannelPanel = false
                                }
                                return@onPreviewKeyEvent true
                            }
                            else -> {}
                        }
                    }
                

                    // Normal channel navigation when panel is closed
                    return@onPreviewKeyEvent handleTVRemoteKey(
                        event = event,
                        tvNAV = tvNAV,
                        channelList = channelList,
                        currentIndexState = { currentIndex },
                        onChannelChange = { currentIndex = it }
                    )
                }
                false
            }

    ) {
        // Determine layout modifier based on selected resize mode
        val currentResizeMode = resizeModes[resizeModeIndex].first
        val isDefaultMode = resizeModeIndex == 0 // Our injected 'Default' entry
        val aspectForModifier = when {
            isDefaultMode -> 16f / 9f
            currentResizeMode == RESIZE_MODE_FIXED_WIDTH -> 16f / 9f // force a wide cinematic frame
            currentResizeMode == RESIZE_MODE_FILL -> videoAspect
            else -> videoAspect
        }

        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
                    setResizeMode(resizeModes[resizeModeIndex].first)
                    player = exoPlayer
                    exoPlayerView = this

                    // Inject a resize button into the controller (to the left of overflow/settings area if possible)
                    // We search for the controller layout after it's inflated.
                    post {
                        try {
                            val controller = findViewById<View>(androidx.media3.ui.R.id.exo_controller) as? ViewGroup ?: return@post
                            // Attempt to locate an action bar row (common id exo_basic_controls / exo_bottom_bar variations)
                            var targetBar: ViewGroup? = null
                            val candidateIds = listOf(
                                androidx.media3.ui.R.id.exo_basic_controls,
                                androidx.media3.ui.R.id.exo_bottom_bar,
                                androidx.media3.ui.R.id.exo_controls_background
                            )
                            for (cid in candidateIds) {
                                val v = controller.findViewById<View>(cid)
                                if (v is ViewGroup) { targetBar = v; break }
                            }
                            if (targetBar == null) {
                                // fallback: deepest child with most children
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

                            val resizeButton = ImageButton(context).apply {
                                setBackgroundResource(android.R.color.transparent)
                                setImageResource(android.R.drawable.ic_menu_crop)
                                contentDescription = "Resize mode"
                                imageAlpha = 220
                                val pad = (4 * resources.displayMetrics.density).toInt()
                                setPadding(pad, pad, pad, pad)
                                setOnClickListener {
                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                    val mode = resizeModes[resizeModeIndex]
                                    exoPlayerView?.setResizeMode(mode.first)
                                    // Force a layout pass so visual change applies immediately
                                    exoPlayerView?.requestLayout()
                                    resizeOverlayLabel = mode.second
                                    showResizeOverlay = true
                                    resizeOverlayJob?.cancel()
                                    resizeOverlayJob = scope.launch {
                                        delay(1200)
                                        showResizeOverlay = false
                                    }
                                }
                            }

                            // Insert before the last element (typically overflow/settings) if possible
                            val count = targetBar.childCount
                            val insertIndex = if (count > 0) count - 1 else count
                            try {
                                targetBar.addView(resizeButton, insertIndex)
                            } catch (_: Exception) {
                                targetBar.addView(resizeButton)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to position resize button: ${e.message}")
                        }
                    }
                }
            },
            modifier = when {
                currentResizeMode == RESIZE_MODE_FILL -> Modifier.fillMaxSize().align(Alignment.Center)
                isDefaultMode -> Modifier.aspectRatio(16f / 9f).align(Alignment.Center)
                else -> Modifier.fillMaxWidth().aspectRatio(aspectForModifier).align(Alignment.Center)
            }
        )

        // Ensure root regains focus when channel panel opens for continued remote navigation
        LaunchedEffect(showChannelPanel) {
            if (showChannelPanel) {
                focusRequester.requestFocus()
            }
        }

        AnimatedVisibility(
            visible = showChannelOverlay && channelList != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ChannelInfoOverlay(
                channelList = channelList,
                currentIndex = currentIndex,
                currentProgramName = currentProgramName
            )
        }
        CurrentTimeOverlay(visible = showChannelOverlay && channelList != null)


        // Numeric overlay display while typing channel number
        if (showNumericOverlay && numericBuffer.isNotEmpty()) {
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

        // Keep only the menu (three dots) icon overlay for channel list toggle
        IconButton(
            onClick = {
                panelSelectedIndex = currentIndex
                showChannelPanel = channelList?.isNotEmpty() == true && !showChannelPanel
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Toggle channel list",
                tint = Color.White
            )
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
            // LazyListState to allow programmatic scrolling with remote navigation
            val channelListState = rememberLazyListState()

            // Ensure selected item is always visible when it changes
            LaunchedEffect(panelSelectedIndex, showChannelPanel) {
                if (showChannelPanel && channelList.isNotEmpty()) {
                    val safeIndex = panelSelectedIndex.coerceIn(0, channelList.lastIndex)
                    // Use animate for smoother UX; fallback to scroll if already close
                    runCatching { channelListState.animateScrollToItem(safeIndex) }
                        .onFailure { channelListState.scrollToItem(safeIndex) }
                }
            }
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
                        // Add extra start padding to avoid overlap with scrollbar + arrow buttons
                        .padding(start = 28.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    state = channelListState
                ) {
                    itemsIndexed(channelList) { idx, ch ->
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
                            // Logo thumbnail
                            AsyncImage(
                                model = ch.logoUrl,
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
                            Text(
                                text = ch.channelName,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Custom vertical scrollbar (visual + draggable) aligned to the LEFT edge of the panel now with arrow buttons
                val totalItems = channelList.size
                if (totalItems > 0) {
                    val visibleItems = channelListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                    val firstVisible = channelListState.firstVisibleItemIndex
                    // Track dimensions
                    var trackHeightPx by remember { mutableStateOf(0) }
                    val scrollRange = (totalItems - visibleItems).coerceAtLeast(1)
                    val scrollFraction = (firstVisible / scrollRange.toFloat()).coerceIn(0f, 1f)
                    val thumbFraction = (visibleItems / totalItems.toFloat()).coerceIn(0.08f, 1f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(20.dp) // widened to host arrows + bar
                            .padding(vertical = 12.dp)
                            .onGloballyPositioned { trackHeightPx = it.size.height }
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        // Up Arrow
                        IconButton(
                            onClick = {
                                if (firstVisible > 0) {
                                    val target = (firstVisible - visibleItems).coerceAtLeast(0)
                                    coroutineScope.launch { channelListState.animateScrollToItem(target) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Scroll Up",
                                tint = Color.White.copy(alpha = if (firstVisible > 0) 0.9f else 0.3f)
                            )
                        }

                        // Down Arrow
                        IconButton(
                            onClick = {
                                val maxFirst = (totalItems - visibleItems).coerceAtLeast(0)
                                if (firstVisible < maxFirst) {
                                    val target = (firstVisible + visibleItems).coerceAtMost(maxFirst)
                                    coroutineScope.launch { channelListState.animateScrollToItem(target) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Scroll Down",
                                tint = Color.White.copy(alpha = if (firstVisible < (totalItems - visibleItems)) 0.9f else 0.3f)
                            )
                        }

                        // Scrollbar track with thumb centered between arrows
                        val arrowReservedSpacePx = 40 // approx (two 20dp buttons) not including padding
                        // Track
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxHeight(fraction = 1f)
                                .padding(top = 20.dp, bottom = 20.dp)
                                .width(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        if (trackHeightPx > 0) {
                            // Recalculate track effective height minus arrow zones
                            val effectiveTrackHeightPx = trackHeightPx - arrowReservedSpacePx
                            val thumbHeightPx = (effectiveTrackHeightPx * thumbFraction).roundToInt()
                            val maxOffset = (effectiveTrackHeightPx - thumbHeightPx).coerceAtLeast(0)
                            val offsetY = (maxOffset * scrollFraction).roundToInt()
                            var dragging by remember { mutableStateOf(false) }
                            val density = LocalDensity.current
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .offset { IntOffset(0, 20.dp.roundToPx() + offsetY) }
                                    .height(with(density) { thumbHeightPx.toDp() })
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (dragging) Color.White.copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.55f)
                                    )
                                    .pointerInput(totalItems, effectiveTrackHeightPx, thumbHeightPx) {
                                        if (effectiveTrackHeightPx <= 0) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = { dragging = true },
                                            onDragEnd = { dragging = false },
                                            onDragCancel = { dragging = false },
                                            onDrag = { change, dragAmount ->
                                                val maxOffsetLocal = (effectiveTrackHeightPx - thumbHeightPx).coerceAtLeast(0)
                                                val itemsScrollable = (totalItems - visibleItems).coerceAtLeast(1)
                                                val itemsPerPixel = if (maxOffsetLocal > 0) itemsScrollable.toFloat() / maxOffsetLocal.toFloat() else 0f
                                                val newPixelOffset = (offsetY + dragAmount.y).roundToInt().coerceIn(0, maxOffsetLocal)
                                                val targetFirstVisible = (newPixelOffset * itemsPerPixel).roundToInt().coerceIn(0, itemsScrollable)
                                                coroutineScope.launch {
                                                    channelListState.scrollToItem(targetFirstVisible)
                                                }
                                                change.consume()
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}


suspend fun fetchCurrentProgram(basefinURL: String, channelId: String): String? {
    val epgURLc = "$basefinURL/epg/${channelId}/0"
    Log.d("NANOdix1", epgURLc)
    val epgData = ChannelUtils.fetchEpg(epgURLc)
//    Log.d(TAG, "Now playing: ${epgData?.showname}")
    return epgData?.showname
}


//fun extractChannelIdFromPlayUrl(playUrl: String): String? {
//    val regex = """.*/(\d+)(?:\.m3u8?|)?$""".toRegex()
//    Log.d("NANOdix0", ">> $playUrl")
//    return regex.find(playUrl)?.groups?.get(1)?.value
//}



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
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
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
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    return String.format("%02d:%02d %s", if (hour == 0) 12 else hour, minute, amPm)
}



@UnstableApi
fun initializePlayer(
    getCurrentVideoUrl: () -> String,
    context: Context,
    retryCountRef: MutableState<Int>
): ExoPlayer {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)

    val player = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()
    val maxRetries = 5
    retryCountRef.value = 0

    fun prepareAndPlay() {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(getCurrentVideoUrl()))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    prepareAndPlay()

    player.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (retryCountRef.value < maxRetries) {
                retryCountRef.value++
                Log.d(TAG, "Retrying playback: attempt ${retryCountRef.value}")
                player.stop()
                prepareAndPlay()
            } else {
                Toast.makeText(context, "Playback failed after $maxRetries attempts", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Playback permanently failed.")
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
            Key.ChannelDown -> if (currentIndex - 1 < 0) (channelList?.size ?: 1) - 1 else currentIndex - 1
            else -> return false
        }
        "1" -> when (event.key) {
            Key.DirectionUp -> (currentIndex + 1) % (channelList?.size ?: return false)
            Key.DirectionDown -> if (currentIndex - 1 < 0) (channelList?.size ?: 1) - 1 else currentIndex - 1
            else -> return false
        }
        else -> return false
    }

    onChannelChange(newIndex)
    return true
}

private fun commitNumericEntry(
    channelList: ArrayList<ChannelInfo>?,
    onChannelSelected: (Int) -> Unit
) {
    // Access composition-local states via globals not possible; this function expects numericBuffer/showNumericOverlay managed by caller
    // Implementation is provided in the composable scope below using helper that references current state
}
