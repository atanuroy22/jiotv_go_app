package com.skylake.skytv.jgorunner.ui.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.core.checkServerStatus
import com.skylake.skytv.jgorunner.core.execution.runBinary
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import com.skylake.skytv.jgorunner.ui.screens.AppStartTracker
import com.skylake.skytv.jgorunner.utils.withQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Main_Layout(context: Context, reloadTrigger: Int) {
    rememberCoroutineScope()
    val channelsResponse = remember { mutableStateOf<ChannelResponse?>(null) }
    val filteredChannels = remember { mutableStateOf<List<Channel>>(emptyList()) }
    val preferenceManager = SkySharedPref.getInstance(context)
    val localPORT by remember {
        mutableIntStateOf(preferenceManager.myPrefs.jtvGoServerPort)
    }
    val basefinURL = "http://localhost:$localPORT"
    val channelCachePrefsName = "channel_cache"
    val channelCacheJsonKey = "channels_json"
    val channelCacheUpdatedAtKey = "channels_cache_updated_at_ms"
    val channelCacheTtlMs = 12L * 60L * 60L * 1000L
    var fetched by remember { mutableStateOf(false) }

    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var epgData by remember { mutableStateOf<EpgProgram?>(null) }
    var isEpgLoading by remember { mutableStateOf(false) }
    var epgError by remember { mutableStateOf(false) }
    var showLoading by remember { mutableStateOf(false) }
    var reloadAttemptCount by rememberSaveable { mutableIntStateOf(0) }
    var waitingDots by remember { mutableStateOf("") }
    var autoLoadCountdown by remember { mutableIntStateOf(5) }
    var autoRetryLoopRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    remember { FocusRequester() }
    val categoryMap = mapOf(
        "All" to null,
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

    val savedCategoryIds = preferenceManager.myPrefs.filterCI
        ?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
//    var selectedCategoryIds by remember { mutableStateOf(savedCategoryIds) }
    var selectedCategoryIds by rememberSaveable { mutableStateOf(savedCategoryIds.toSet()) }
    val languageNameById = mapOf(
        1 to "Hindi",
        2 to "Marathi",
        3 to "Punjabi",
        4 to "Urdu",
        5 to "Bengali",
        6 to "English",
        7 to "Malayalam",
        8 to "Tamil",
        9 to "Gujarati",
        10 to "Odia",
        11 to "Telugu",
        12 to "Bhojpuri",
        13 to "Kannada",
        14 to "Assamese",
        15 to "Nepali",
        16 to "French",
        18 to "Other"
    )
    val secondLanguageIdForUi = preferenceManager.myPrefs.filterLI2
        ?.trim()
        ?.toIntOrNull()
    val secondLanguageNameForUi = secondLanguageIdForUi?.let { languageNameById[it] } ?: "Language"
    val secondLanguageAddonCategoryIds = categoryMap.values.filterNotNull()
    val savedSecondLanguageAddonCategoryIds = preferenceManager.myPrefs.filterCI2
        ?.split(",")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.toSet()
        ?: emptySet()
    fun secondLanguageAddonLabel(categoryId: Int): String {
        val originalName = categoryMap.entries.firstOrNull { it.value == categoryId }?.key?.trim().orEmpty()
        if (originalName.isBlank()) {
            return "$secondLanguageNameForUi-Category-$categoryId"
        }
        return if (originalName.startsWith(secondLanguageNameForUi, ignoreCase = true)) {
            originalName.replace(" ", "-")
        } else {
            "$secondLanguageNameForUi-${originalName.replace(" ", "-")}"
        }
    }
    var selectedSecondLanguageAddonCategoryIds by rememberSaveable {
        mutableStateOf(savedSecondLanguageAddonCategoryIds)
    }
    val showSecondLanguageAddonSelector = secondLanguageIdForUi != null

    val sortedCategories = remember(selectedCategoryIds) {
        val allCategoryName = "All"
        val allCategoryNames = categoryMap.keys.toList()
        val otherCategoryNames = allCategoryNames.filter { it != allCategoryName }
        val (selectedOtherCategories, unselectedOtherCategories) = otherCategoryNames.partition { categoryName ->
            val categoryId = categoryMap[categoryName]
            categoryId != null && selectedCategoryIds.contains(categoryId)
        }
        listOf(allCategoryName) + selectedOtherCategories + unselectedOtherCategories
    }

    fun currentLanguageIdsFromPrefs(): List<Int>? {
        return preferenceManager.myPrefs.filterLI
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
    }

    fun applyMainFilters(response: ChannelResponse): List<Channel> {
        val languageIds = currentLanguageIdsFromPrefs()

        val baseFiltered = ChannelUtils.filterChannels(
            response,
            categoryIds = selectedCategoryIds.takeIf { it.isNotEmpty() }?.toList(),
            languageIds = languageIds
        )

        if (secondLanguageIdForUi == null || selectedSecondLanguageAddonCategoryIds.isEmpty()) {
            return baseFiltered
        }

        val secondLanguageAddonFiltered = ChannelUtils.filterChannels(
            response,
            categoryIds = selectedSecondLanguageAddonCategoryIds.toList(),
            languageIds = listOf(secondLanguageIdForUi)
        )

        return (baseFiltered + secondLanguageAddonFiltered)
            .distinctBy { "${it.channel_id}|${it.channel_url}" }
    }


    // Helper functions for enhanced autoplay
    suspend fun fetchFromBackend(): List<Channel> {
        return try {
            ChannelUtils.fetchChannels("$basefinURL/channels")?.let { response ->
                channelsResponse.value = response
                context.getSharedPreferences(channelCachePrefsName, Context.MODE_PRIVATE).edit().apply {
                    putString(channelCacheJsonKey, Gson().toJson(response))
                    putLong(channelCacheUpdatedAtKey, System.currentTimeMillis())
                    apply()
                }
                val filtered = applyMainFilters(response)
                filteredChannels.value = filtered
                filtered
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun ensureServerReady(): Boolean {
        val port = preferenceManager.myPrefs.jtvGoServerPort

        suspend fun waitForHttpServer(maxWaitMs: Long): Boolean {
            val response = withTimeoutOrNull(maxWaitMs) {
                val result = CompletableDeferred<Boolean>()
                checkServerStatus(
                    port = port,
                    onLoginSuccess = { result.complete(true) },
                    onLoginFailure = { result.complete(false) },
                    onServerDown = { result.complete(false) },
                    baseDelay = 100L,
                    maxDelay = 600L,
                    maxAttempts = 5
                )
                result.await()
            }
            return response == true
        }

        if (BinaryService.isRunning) {
            // Keep this short so autoplay does not stall UI flow when endpoint warm-up is slow.
            return waitForHttpServer(2500L) || true
        }

        val activity = context as? ComponentActivity ?: return false
        withContext(Dispatchers.Main) {
            runBinary(
                activity = activity,
                arguments = emptyArray(),
                onRunSuccess = {},
                onOutput = { },
                forceStart = false
            )
        }

        // Give binary a brief moment to bind sockets before probe.
        delay(300)

        return waitForHttpServer(9000L) || BinaryService.isRunning
    }

    suspend fun launchFirstChannel(channelsToUse: List<Channel>): Boolean {
        return try {
            if (!ensureServerReady()) {
                return false
            }

            val firstChannel = channelsToUse.first()
            val (channelWindow, relativeIndex) = buildChannelInfoWindow(
                context = context,
                channels = channelsToUse,
                basefinURL = basefinURL,
                centerIndex = 0
            )
            val intent = Intent(context, ExoPlayJet::class.java).apply {
                putExtra("zone", "TV")
                if (firstChannel.channel_id.all { it.isDigit() }) putExtra("channel_list_kind", "jio")
                putExtra("current_channel_index", relativeIndex)
                putParcelableArrayListExtra("channel_list_data", channelWindow)
                putExtra("video_url", firstChannel.channel_url)
                putExtra(
                    "logo_url",
                    if (firstChannel.logoUrl.startsWith("http")) firstChannel.logoUrl else "http://localhost:$localPORT/jtvimage/${firstChannel.logoUrl}"
                )
                putExtra("ch_name", firstChannel.channel_name)
            }

            delay(1000)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val recentChannelsJson = preferenceManager.myPrefs.recentChannels
            val type = object : TypeToken<List<Channel>>() {}.type
            val recentChannels: MutableList<Channel> =
                Gson().fromJson(recentChannelsJson, type) ?: mutableListOf()

            val existingIndex =
                recentChannels.indexOfFirst { it.channel_id == firstChannel.channel_id }

            if (existingIndex != -1) {
                val existingChannel = recentChannels[existingIndex]
                recentChannels.removeAt(existingIndex)
                recentChannels.add(0, existingChannel)
            } else {
                recentChannels.add(0, firstChannel)
                if (recentChannels.size > 25) {
                    recentChannels.removeAt(recentChannels.size - 1)
                }
            }
            preferenceManager.myPrefs.recentChannels = Gson().toJson(recentChannels)
            preferenceManager.savePreferences()

            AppStartTracker.shouldPlayChannel = true
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun watchdogAutoplay(channels: List<Channel>): Boolean {
        repeat(5) {
            if (preferenceManager.myPrefs.currChannelUrl.isNullOrEmpty()) {
                val channelsToUse = channels.ifEmpty { fetchFromBackend() }
                if (channelsToUse.isNotEmpty()) {
                    if (launchFirstChannel(channelsToUse)) {
                        return true
                    }
                }
            }
            delay(2000)
        }
        return false
    }

    suspend fun performReloadAttempt(): List<Channel> {
        reloadAttemptCount++
        showLoading = true
        val fetchedChannels = fetchFromBackend()
        filteredChannels.value = fetchedChannels
        showLoading = false
        return fetchedChannels
    }

    // Fetch and filter channels (cache/network)
    LaunchedEffect(reloadTrigger) {
        val sharedPref = context.getSharedPreferences(channelCachePrefsName, Context.MODE_PRIVATE)
        var cachedChannels: ChannelResponse? = null
        var hasValidCachedChannels = false

        val cachedJson = sharedPref.getString(channelCacheJsonKey, null)
        val cacheUpdatedAt = sharedPref.getLong(channelCacheUpdatedAtKey, 0L)
        val isCacheFresh = cacheUpdatedAt > 0L &&
            (System.currentTimeMillis() - cacheUpdatedAt) <= channelCacheTtlMs

        if (!cachedJson.isNullOrEmpty()) {
            try {
                cachedChannels = Gson().fromJson(cachedJson, ChannelResponse::class.java)
                hasValidCachedChannels = cachedChannels != null
                if (hasValidCachedChannels) {
                    channelsResponse.value = cachedChannels
                }
            } catch (_: Exception) {
                sharedPref.edit {
                    remove(channelCacheJsonKey)
                    remove(channelCacheUpdatedAtKey)
                }
            }
        }

        if (hasValidCachedChannels && isCacheFresh && cachedChannels != null) {
            val filtered = applyMainFilters(cachedChannels)
            filteredChannels.value = filtered
            fetched = true
        } else {
            var attempts = 0
            var success = false
            while (attempts < 2 && !success) {
                attempts++
                try {
                    val response = ChannelUtils.fetchChannels("$basefinURL/channels")
                    channelsResponse.value = response
                    if (response != null) {
                        val responseJsonString = Gson().toJson(response)
                        sharedPref.edit {
                            putString(channelCacheJsonKey, responseJsonString)
                            putLong(channelCacheUpdatedAtKey, System.currentTimeMillis())
                        }
                        val filtered = applyMainFilters(response)
                        filteredChannels.value = filtered
                        success = true
                    }
                } catch (_: Exception) {
                    // ignore, retry
                }
                if (!success) {
                    kotlinx.coroutines.delay(300)
                }
            }

            // If refresh fails, keep app usable by falling back to stale cache.
            if (!success && hasValidCachedChannels && cachedChannels != null) {
                val filtered = applyMainFilters(cachedChannels)
                filteredChannels.value = filtered
            }
            fetched = true
        }

        val canAutoplay = !AppStartTracker.shouldPlayChannel ||
            preferenceManager.myPrefs.currChannelUrl.isNullOrEmpty()

        if (preferenceManager.myPrefs.startTvAutomatically && canAutoplay) {
            var channelsForAutoplay = filteredChannels.value
            if (channelsForAutoplay.isEmpty()) {
                channelsForAutoplay = fetchFromBackend()
            }

            if (channelsForAutoplay.isNotEmpty()) {
                launchFirstChannel(channelsForAutoplay)
            } else {
                watchdogAutoplay(channelsForAutoplay)
            }
        }

    }

    // If channels arrive later (e.g., from retry loop), try autoplay again while sentinel is false.
    LaunchedEffect(filteredChannels.value) {
        val canAutoplay = !AppStartTracker.shouldPlayChannel ||
            preferenceManager.myPrefs.currChannelUrl.isNullOrEmpty()

        if (
            preferenceManager.myPrefs.startTvAutomatically &&
            filteredChannels.value.isNotEmpty() &&
            canAutoplay
        ) {
            launchFirstChannel(filteredChannels.value)
        }
    }

    LaunchedEffect(showSecondLanguageAddonSelector, secondLanguageIdForUi) {
        if (!showSecondLanguageAddonSelector && selectedSecondLanguageAddonCategoryIds.isNotEmpty()) {
            selectedSecondLanguageAddonCategoryIds = emptySet()
            preferenceManager.myPrefs.filterCI2 = ""
            preferenceManager.savePreferences()
        }
    }

    // Re-filter channels when category or second-language addon selection changes
    LaunchedEffect(selectedCategoryIds, selectedSecondLanguageAddonCategoryIds, secondLanguageIdForUi) {
        channelsResponse.value?.let { response ->
            val filtered = applyMainFilters(response)
            filteredChannels.value = filtered
        }
    }

    // Auto-retry loading channels: first wait 5s, then retry every 10s until channels arrive.
    LaunchedEffect(fetched, filteredChannels.value) {
        if (fetched && filteredChannels.value.isEmpty() && !autoRetryLoopRunning) {
            autoRetryLoopRunning = true
            try {
                var waitSeconds = 5
                while (filteredChannels.value.isEmpty()) {
                    for (i in waitSeconds downTo 1) {
                        if (filteredChannels.value.isNotEmpty()) break
                        autoLoadCountdown = i
                        waitingDots = ".".repeat(((waitSeconds - i) % 3) + 1)
                        delay(1000)
                    }

                    if (filteredChannels.value.isNotEmpty()) {
                        break
                    }

                    performReloadAttempt()
                    waitSeconds = 10
                }
            } finally {
                autoRetryLoopRunning = false
            }
        }
    }

    // Fetch EPG data for selected channel
    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            isEpgLoading = true
            epgError = false
            val epgURL = "$basefinURL/epg/${selectedChannel!!.channel_id}/0"
            Log.d("EPG_FETCH", epgURL)

            try {
                val fetchedEpg = ChannelUtils.fetchEpg(epgURL)
                if (fetchedEpg != null) {
                    epgData = fetchedEpg
                } else {
                    epgData = null
                    epgError = true
                }
            } catch (_: Exception) {
                epgData = null
                epgError = true
            } finally {
                isEpgLoading = false
            }
        } else {
            epgData = null
            epgError = false
            isEpgLoading = false
        }
    }

    LaunchedEffect(fetched) {
        if (!fetched) {
            delay(100)
            if (!fetched) showLoading = true
        } else {
            showLoading = false
        }
    }

    // UI: Loading state
    if (showLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            ContainedLoadingIndicator(modifier = Modifier.size(100.dp))
        }
    }
    // UI: Empty state
    else if (fetched && filteredChannels.value.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No channels found",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = waitingDots, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Auto-retrying channel load in ${autoLoadCountdown}s...",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = Color.Blue
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Click Reload to retry now, or wait for auto-retry",
                    style = TextStyle(fontSize = 14.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Retry attempts: $reloadAttemptCount",
                    style = TextStyle(fontSize = 13.sp, color = Color.Gray)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Check your internet connection\n• Go to Main Screen for detailed information",
                    style = TextStyle(fontSize = 13.sp, color = Color.Gray)
                )
                Spacer(modifier = Modifier.height(24.dp))
                ElevatedCard(
                    onClick = {
                        coroutineScope.launch {
                            performReloadAttempt()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reload App")
                    }
                }
            }
        }
    }
    // UI: Main content
    else {
        // CATEGORY CHIPS
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
                        if (categoryName == "All") {
                            selectedCategoryIds = emptySet()
                        } else if (categoryId != null) {
                            selectedCategoryIds = if (isSelected) {
                                selectedCategoryIds - categoryId
                            } else {
                                selectedCategoryIds + categoryId
                            }
                        }
                        val updatedCI = selectedCategoryIds.joinToString(",")
                        preferenceManager.myPrefs.filterCI = updatedCI
                        preferenceManager.savePreferences()
                    },
                    label = { Text(categoryName) },
                    selected = if (categoryName == "All") {
                        selectedCategoryIds.isEmpty()
                    } else {
                        isSelected
                    },
                    leadingIcon = when {
                        categoryName == "All" && selectedCategoryIds.isEmpty() -> {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "All selected icon",
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
                    }
                )
            }
        }

        if (showSecondLanguageAddonSelector) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(secondLanguageAddonCategoryIds) { categoryId ->
                    val label = secondLanguageAddonLabel(categoryId)
                    val isSelected = selectedSecondLanguageAddonCategoryIds.contains(categoryId)

                    FilterChip(
                        onClick = {
                            selectedSecondLanguageAddonCategoryIds = if (isSelected) {
                                selectedSecondLanguageAddonCategoryIds - categoryId
                            } else {
                                selectedSecondLanguageAddonCategoryIds + categoryId
                            }
                            preferenceManager.myPrefs.filterCI2 =
                                selectedSecondLanguageAddonCategoryIds.joinToString(",")
                            preferenceManager.savePreferences()
                        },
                        label = { Text(label) },
                        selected = isSelected,
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "Done icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        // EPG CARD (null-safe)
        if (isEpgLoading || epgData != null || epgError) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    when {
                        isEpgLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading EPG...",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                )
                            }
                        }

                        epgError -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No EPG available",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                )
                            }
                        }

                        epgData != null -> {
                            val epg = epgData!!
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
                                        .heightIn(max = 110.dp)
                                ) {
                                    Text(
                                        text = epg.channel_name,
                                        style = TextStyle(fontSize = 14.sp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = epg.showname,
                                        maxLines = 1,
                                        style = TextStyle(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = epg.description,
                                        style = TextStyle(fontSize = 13.sp),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                GlideImage(
                                    model = "$basefinURL/jtvposter/${epg.episodePoster}",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }

        ChannelGridMain(
            context = context,
            filteredChannels = filteredChannels.value,
            selectedChannelSetter = { selectedChannel = it },
            localPORT = localPORT,
            preferenceManager = preferenceManager
        )


    }
}

