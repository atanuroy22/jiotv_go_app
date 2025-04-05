package com.skylake.skytv.jgorunner.ui.dev

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlinx.coroutines.launch
import androidx.compose.material3.Text as CText
import com.bumptech.glide.integration.compose.GlideImage
import com.skylake.skytv.jgorunner.activities.ExoplayerActivity
import com.skylake.skytv.jgorunner.data.SkySharedPref
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.foundation.clickable
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TVTabLayout(context: Context) {
    val scope = rememberCoroutineScope()
    val channelsResponse = remember { mutableStateOf<ChannelResponse?>(null) }
    val filteredChannels = remember { mutableStateOf<List<Channel>>(emptyList()) }
    val preferenceManager = SkySharedPref.getInstance(context)
    val localPORT by remember {
        mutableIntStateOf(preferenceManager.myPrefs.jtvGoServerPort)
    }
    val basefinURL = "http://localhost:$localPORT"
    var fetched by remember { mutableStateOf(false) }

    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var epgData by remember { mutableStateOf<EpgProgram?>(null) }

    val focusRequester = remember { FocusRequester() }


    LaunchedEffect(Unit) {
        if (true) {
            scope.launch {
                val response = ChannelUtils.fetchChannels("$basefinURL/channels")
                channelsResponse.value = response

                if (response != null) {
                    val categories = preferenceManager.myPrefs.filterCI
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    val languages = preferenceManager.myPrefs.filterLI
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

                    Log.d("DIX#2", "CAT:$categories, Lang:$languages")

                    val filtered = when {
                        categories.isNullOrEmpty() && languages.isNullOrEmpty() -> ChannelUtils.filterChannels(response)
                        categories.isNullOrEmpty() -> ChannelUtils.filterChannels(
                            response,
                            languageIds = languages?.mapNotNull { it.toIntOrNull() }.takeIf { it!!.isNotEmpty() }
                        )
                        languages.isNullOrEmpty() -> ChannelUtils.filterChannels(
                            response,
                            categoryIds = categories.mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
                        )
                        else -> ChannelUtils.filterChannels(
                            response,
                            categoryIds = categories.mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() },
                            languageIds = languages.mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
                        )
                    }

                    filteredChannels.value = filtered
                }
                fetched = true
            }
        }
    }

    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            val epgURLc = "$basefinURL/epg/${selectedChannel?.channel_id}/0"
            epgData = ChannelUtils.fetchEpg(epgURLc)
        }
    }


    if (!fetched) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp)
            )
        }
    } else {

        // Content Above the Grid (EPG Details)
        if (epgData != null) {
            Column(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .padding(5.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Description Column
                    Column(
                        modifier = Modifier
                            .padding(5.dp)
                            .weight(1f)
                            .background(Color.Transparent),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (epgData != null) {
                            Text(
                                text = epgData!!.channel_name,
                                style = TextStyle(fontSize = 14.sp)
                            )
                            Text(
                                text = epgData!!.showname,
                                maxLines = 1,
                                style = TextStyle(fontSize = 22.sp)
                            )
                            Text(
                                text = epgData!!.description,
                                style = TextStyle(fontSize = 12.sp),
                                maxLines = 3
                            )

                        } else {
                            Text(
                                text = selectedChannel?.channel_name ?: "No channel selected",
                                style = TextStyle(fontSize = 24.sp)
                            )
                            Text(
                                text = "No EPG data available",
                                style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                            )
                        }
                    }

                    GlideImage(
                        model = "$basefinURL/jtvposter/${epgData?.episodePoster}",
                        contentDescription = null,
                        modifier = Modifier
                            .height(75.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }


        } else {
            Log.d("HANA4k", "EPG ERROR")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filteredChannels.value) { channel ->
                var isFocused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) 1.1f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ), label = ""
                )

                ElevatedCard(
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .height(120.dp)
                        .scale(scale)
                        .focusRequester(focusRequester)
                        .onFocusEvent { focusState ->
                            isFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                selectedChannel = channel
                            }
                        }
                        .clickable {
                            Log.d("HT", channel.channel_name)
                            val intent = Intent(context, ExoplayerActivity::class.java).apply {
                                putExtra("video_url", channel.channel_url)
                            }
                            startActivity(context, intent, null)

                            val recentChannelsJson = preferenceManager.myPrefs.recentChannels
                            val type = object : TypeToken<List<Channel>>() {}.type
                            val recentChannels: MutableList<Channel> =
                                Gson().fromJson(recentChannelsJson, type)
                                    ?: mutableListOf()

                            val existingIndex =
                                recentChannels.indexOfFirst { it.channel_id == channel.channel_id }

                            if (existingIndex != -1) {
                                val existingChannel = recentChannels[existingIndex]
                                recentChannels.removeAt(existingIndex)
                                recentChannels.add(0, existingChannel)
                            } else {
                                recentChannels.add(0, channel)
                                if (recentChannels.size > 25) {
                                    recentChannels.removeAt(recentChannels.size - 1)
                                }
                            }

                            val gson = Gson()
                            val recentChannelsJsonx = gson.toJson(recentChannels)
                            preferenceManager.myPrefs.recentChannels = recentChannelsJsonx
                            preferenceManager.savePreferences()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                ) {
                    Column {
                        // Image
                        val imageUrl =
                            "http://localhost:${SkySharedPref.getInstance(context).myPrefs.jtvGoServerPort}/jtvimage/${channel.logoUrl}"

                        GlideImage(
                            model = imageUrl,
                            contentDescription = channel.channel_name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Title
                        Text(
                            text = channel.channel_name,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

//    // Request focus on the first item when the grid is initialized
//    LaunchedEffect(filteredChannels.value.isNotEmpty()) {
//        if (filteredChannels.value.isNotEmpty()) {
//            focusRequester.requestFocus()
//        }
//    }
}
