package com.skylake.skytv.jgorunner.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@SuppressLint("AutoboxingStateCreation", "DefaultLocale")
@Composable
fun ExoPlayJetScreen(
    preferenceManager: SkySharedPref,
    videoUrl: String,
    logoUrl: String,
    channelName: String,
    channelList: ArrayList<ChannelInfo>?,
    currentChannelIndex: Int
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val focusRequester = remember { FocusRequester() }
    var currentIndex by remember { mutableStateOf(currentChannelIndex) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isKeyRemapActive by remember { mutableStateOf(true) }
    var showChannelOverlay by remember { mutableStateOf(false) }

    val tvNAV = "0"
//    val tvNAV = preferenceManager.myPrefs.selectedRemoteNavTV

    SideEffect {
        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            WindowInsetsControllerCompat(it.window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer == null) {
                        exoPlayer = initializePlayer(
                            videoUrl = channelList?.getOrNull(currentIndex)?.videoUrl ?: videoUrl,
                            context = context
                        )
                    } else {
                        exoPlayer?.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer?.playWhenReady = false
                }
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer?.release()
                    exoPlayer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DisposableEffect(lifecycleOwner, isKeyRemapActive) {
        val observer = LifecycleEventObserver { _, event ->
            isKeyRemapActive = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            isKeyRemapActive = false
        }
    }

    LaunchedEffect(currentIndex) {
        exoPlayer?.let { player ->
            showChannelOverlay = true
            channelList?.getOrNull(currentIndex)?.videoUrl?.let {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(it)))
                player.prepare()
                player.playWhenReady = true
            }
            delay(3000)
            showChannelOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (isKeyRemapActive) {
                    handleTVRemoteKey(
                        event = event,
                        tvNAV = tvNAV,
                        context = context,
                        channelList = channelList,
                        currentIndexState = { currentIndex },
                        onChannelChange = { currentIndex = it }
                    )
                } else false
            }
    ) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = true
                        controllerAutoShow = false
                        setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
                        setResizeMode(RESIZE_MODE_FIT)
                        this.player = player
                    }
                },
                modifier = Modifier
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            )
        }

        AnimatedVisibility(visible = showChannelOverlay && channelList != null, enter = fadeIn(), exit = fadeOut()) {
            ChannelInfoOverlay(channelList, currentIndex)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ChannelInfoOverlay(channelList: List<ChannelInfo>?, currentIndex: Int) {
    channelList?.getOrNull(currentIndex)?.let { channel ->
        Box {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
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
            }
        }
    }
}

@UnstableApi
fun initializePlayer(videoUrl: String, context: Context): ExoPlayer {
    val mediaItem = MediaItem.Builder()
        .setUri(Uri.parse(videoUrl))
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build()

    val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)

    val player = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()

    var retryCount = 0
    val maxRetries = 5

    player.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (retryCount++ < maxRetries) {
                player.playWhenReady = false
                player.seekTo(0)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } else {
                Toast.makeText(context, "Playback failed after $maxRetries attempts", Toast.LENGTH_SHORT).show()
            }
        }
    })

    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true

    return player
}

fun handleTVRemoteKey(
    event: KeyEvent,
    tvNAV: String?,
    context: Context,
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
