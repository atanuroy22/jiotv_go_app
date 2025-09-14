package com.skylake.skytv.jgorunner.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
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

    LaunchedEffect(lifecycleOwner.lifecycle.currentState) {
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
            .onKeyEvent { event ->
                // if (event.type == KeyEventType.KeyUp &&
                //     (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                // ) {
                //     (exoPlayerView)?.showController()
                    
                // Prevent PlayerView's default 5s seek on LEFT by consuming KeyDown LEFT and opening the panel
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    panelSelectedIndex = currentIndex
                    showChannelPanel = channelList?.isNotEmpty() == true
                    return@onKeyEvent true
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
                        return@onKeyEvent true
                    }
                }
                // Global ENTER shows controller if panel isn't open
                if (event.type == KeyEventType.KeyUp) {
                    // Commit numeric entry immediately on ENTER
                    if (numericBuffer.isNotEmpty() && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                        numericJob?.cancel()
                        commitNumericEntryLocal(channelList)
                        return@onKeyEvent true
                    }
                    if (!showChannelPanel && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
                        (exoPlayerView)?.showController()
                        return@onKeyEvent true
                    }
                    // Toggle panel
                    if (event.key == Key.Menu || event.key == Key.DirectionLeft) {
                        panelSelectedIndex = currentIndex
                        showChannelPanel = channelList?.isNotEmpty() == true
                        return@onKeyEvent true
                    }
                    if (showChannelPanel && (event.key == Key.DirectionRight || event.key == Key.Back)) {
                        showChannelPanel = false
                        return@onKeyEvent true
                    }
                    if (showChannelPanel) {
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    panelSelectedIndex = (panelSelectedIndex - 1 + channelList.size) % channelList.size
                                }
                                return@onKeyEvent true
                            }
                            Key.DirectionDown -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    panelSelectedIndex = (panelSelectedIndex + 1) % channelList.size
                                }
                                return@onKeyEvent true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (channelList != null && channelList.isNotEmpty()) {
                                    currentIndex = panelSelectedIndex.coerceIn(0, channelList.size - 1)
                                    showChannelPanel = false
                                }
                                return@onKeyEvent true
                            }
                            else -> {}
                        }
                    }
                

                    // Normal channel navigation when panel is closed
                    return@onKeyEvent handleTVRemoteKey(
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
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
                    setResizeMode(RESIZE_MODE_FIT)
                    player = exoPlayer
                    exoPlayerView = this
                }
            },
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
        )

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

        // Three-dots menu button (top-right)
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
                        .padding(vertical = 12.dp)
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
