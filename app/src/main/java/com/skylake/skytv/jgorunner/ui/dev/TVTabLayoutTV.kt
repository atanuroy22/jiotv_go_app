package com.skylake.skytv.jgorunner.ui.dev

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.ClassicCard
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.tv.material3.CardDefaults as CardDefaultsTV
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken



@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TVTabLayoutTV(context: Context) {
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
    val categoryMap = mapOf(
        "Reset" to null,
        "Entertainment" to 5,
        "Movies" to 6,
        "Kids" to 7,
        "Sports" to 8,
        "Lifestyle" to 9,
        "Infotainment" to 10,
        "News" to 12,
        "Music" to 13,
        "Devotional" to 15,
        "Business" to 16,
        "Educational" to 17,
        "Shopping" to 18,
        "JioDarshan" to 19
    )

    val categoryIdMap = categoryMap.filterValues { it != null }.map { it.value!! to it.key }.toMap()
    val savedCategoryIds = preferenceManager.myPrefs.filterCI
        ?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
    var selectedCategoryIds by remember { mutableStateOf(savedCategoryIds) }
    val selectedCategories = categoryMap.filterValues { it in selectedCategoryIds }.keys


    val sortedCategories = remember(selectedCategoryIds) {
        val resetCategoryName = "Reset"
        val allCategoryNames = categoryMap.keys.toList()
        val otherCategoryNames = allCategoryNames.filter { it != resetCategoryName }
        val (selectedOtherCategories, unselectedOtherCategories) = otherCategoryNames.partition { categoryName ->
            val categoryId = categoryMap[categoryName]
            categoryId != null && selectedCategoryIds.contains(categoryId)
        }
        listOf(resetCategoryName) + selectedOtherCategories + unselectedOtherCategories
    }




    LaunchedEffect(Unit) {
        if (!fetched) {
            var attempts = 0
            var success = false

            while (attempts < 2 && !success) {
                attempts++
                try {
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
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e("TVTabLayout", "Error fetching channels: ${e.message}")
                }

                if (!success) {
                    kotlinx.coroutines.delay(300)
                }
            }

            fetched = true
        }
    }

    LaunchedEffect(selectedCategoryIds) {
        channelsResponse.value?.let { response ->
            val languages = preferenceManager.myPrefs.filterLI
                ?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }

            val filtered = ChannelUtils.filterChannels(
                response,
                categoryIds = selectedCategoryIds.takeIf { it.isNotEmpty() }?.toList(),
                languageIds = languages
            )
            filteredChannels.value = filtered
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

        ////////////////////////////
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sortedCategories) { categoryName ->
                val categoryId = categoryMap[categoryName]
                val isSelected = categoryId != null && selectedCategoryIds.contains(categoryId)

                FilterChip(
                    onClick = {
                        if (categoryName == "Reset") {
                            selectedCategoryIds = mutableSetOf()
                        } else if (categoryId != null) {
                            // Toggle selection for other categories
                            selectedCategoryIds = if (isSelected) {
                                (selectedCategoryIds - categoryId).toMutableSet()
                            } else {
                                (selectedCategoryIds + categoryId).toMutableSet()
                            }
                        }
                        val updatedCI = selectedCategoryIds.joinToString(",")
                        preferenceManager.myPrefs.filterCI = updatedCI
                        preferenceManager.savePreferences()
                        Log.d("CHIP_SELECTION", "Updated filterCI for $categoryName = $updatedCI")
                    },
                    label = { Text(categoryName) },
                    selected = isSelected,
                    leadingIcon = when {
                        categoryName == "Reset" -> {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        isSelected -> {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "Done icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        else -> null
                    },
                )
            }
        }

        ////////////////////////////

        if (epgData != null) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, end = 12.dp)
                        ) {
                            Text(
                                text = epgData!!.channel_name,
                                style = TextStyle(fontSize = 14.sp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = epgData!!.showname,
                                maxLines = 1,
                                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = epgData!!.description,
                                style = TextStyle(fontSize = 13.sp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        GlideImage(
                            model = "$basefinURL/jtvposter/${epgData!!.episodePoster}",
                            contentDescription = null,

                            modifier = Modifier
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        } else {
            if (false)  {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.Transparent),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedChannel?.channel_name ?: "No channel selected",
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No EPG data available",
                        style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                    )
                }
            }
            Log.d("HANA4k", "EPG ERROR")
        }



        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filteredChannels.value) { channel ->
                ClassicCard(
                    modifier = Modifier
                        .height(120.dp)
                        .focusRequester(focusRequester)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                selectedChannel = channel
                            }
                        },
                    image = {
                        GlideImage(
                            model = "$basefinURL/jtvimage/${channel.logoUrl}",
                            contentDescription = channel.channel_name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )
                    },
                    title = {
                        CText(
                            text = channel.channel_name,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    },
                    onClick = {
                        Log.d("HT", channel.channel_name)
                        val intent =
                            Intent(context, ExoplayerActivity::class.java).apply {
                                putExtra("video_url", channel.channel_url)
                                putExtra("zone", "TV")
                            }
                        startActivity(context, intent, null)


                        val recentChannelsJson = preferenceManager.myPrefs.recentChannels
                        val type = object : TypeToken<List<Channel>>() {}.type
                        val recentChannels: MutableList<Channel> =
                            Gson().fromJson(recentChannelsJson, type) ?: mutableListOf()

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
                    colors = CardDefaultsTV.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                )
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
