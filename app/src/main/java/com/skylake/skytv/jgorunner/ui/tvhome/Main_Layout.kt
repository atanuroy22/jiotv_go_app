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
import com.skylake.skytv.jgorunner.core.execution.runBinary
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import com.skylake.skytv.jgorunner.ui.screens.AppStartTracker
import com.skylake.skytv.jgorunner.utils.withQuality
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TV_STARTUP_TIMEOUT_MS = 8_000L
private const val TV_STARTUP_POLL_DELAY_MS = 350L
private const val TV_STARTUP_MAX_FALLBACK_CHANNELS = 4
private const val TV_STREAM_CONNECT_TIMEOUT_MS = 1_250L
private const val TV_STREAM_READ_TIMEOUT_MS = 1_250L
private const val TV_STREAM_CALL_TIMEOUT_MS = 1_750L
private const val TV_PREFLIGHT_CONNECT_TIMEOUT_MS = 650L
private const val TV_PREFLIGHT_READ_TIMEOUT_MS = 650L
private const val TV_PREFLIGHT_CALL_TIMEOUT_MS = 900L

private data class TvStartupReadiness(
    val ready: Boolean,
    val reason: String? = null
)

private sealed class TvStartupOutcome {
    data object Idle : TvStartupOutcome()
    data object Checking : TvStartupOutcome()
    data class Timeout(val reason: String) : TvStartupOutcome()
    data class Failure(val reason: String) : TvStartupOutcome()
}

private suspend fun probeStreamEndpoint(
    url: String,
    connectTimeoutMs: Long = TV_STREAM_CONNECT_TIMEOUT_MS,
    readTimeoutMs: Long = TV_STREAM_READ_TIMEOUT_MS,
    callTimeoutMs: Long = TV_STREAM_CALL_TIMEOUT_MS
): Boolean {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun execute(method: String): Int? {
            val request = Request.Builder()
                .url(url)
                .method(method, null)
                .build()

            return client.newCall(request).execute().use { response ->
                response.code
            }
        }

        try {
            val headCode = execute("HEAD")
            if (headCode != null && headCode in 200..299) {
                return@withContext true
            }

            if (headCode == 405 || headCode == 501) {
                val getCode = execute("GET")
                return@withContext getCode != null && getCode in 200..299
            }

            false
        } catch (_: Exception) {
            false
        }
    }
}

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
    var startupOutcome by remember { mutableStateOf<TvStartupOutcome>(TvStartupOutcome.Idle) }
    var startupRetryToken by rememberSaveable { mutableIntStateOf(0) }
    var startupLaunchSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var startupLaunchInProgress by remember { mutableStateOf(false) }
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

    suspend fun ensureServerReady(): TvStartupReadiness {
        val port = preferenceManager.myPrefs.jtvGoServerPort
        val url = "http://localhost:$port/live/143.m3u8"

        val activity = context as? ComponentActivity
            ?: return TvStartupReadiness(false, "TV autoplay requires an activity context")

        if (!BinaryService.isRunning) {
            Log.d("TVAutoplay", "Service start requested before readiness probe")
            withContext(Dispatchers.Main) {
                runBinary(
                    activity = activity,
                    arguments = emptyArray(),
                    onRunSuccess = {
                        Log.d("TVAutoplay", "Binary service start acknowledged")
                    },
                    onOutput = { },
                    forceStart = false
                )
            }
        }

        var lastProbeReason: String? = null
        val readiness: TvStartupReadiness? = withTimeoutOrNull(TV_STARTUP_TIMEOUT_MS) {
            var attempt = 0
            while (true) {
                attempt++
                Log.d("TVAutoplay", "HTTP ready probe attempt=$attempt url=$url")
                if (probeStreamEndpoint(url)) {
                    Log.d("TVAutoplay", "HTTP ready true after attempt=$attempt")
                    return@withTimeoutOrNull TvStartupReadiness(ready = true)
                }
                lastProbeReason = "Attempt $attempt did not return a playable HTTP response"
                delay(TV_STARTUP_POLL_DELAY_MS)
            }
            TvStartupReadiness(
                ready = false,
                reason = lastProbeReason ?: "Timed out after ${TV_STARTUP_TIMEOUT_MS / 1000}s waiting for $url"
            )
        }

        return readiness ?: TvStartupReadiness(
            ready = false,
            reason = lastProbeReason ?: "Timed out after ${TV_STARTUP_TIMEOUT_MS / 1000}s waiting for $url"
        )
    }

    suspend fun launchFirstChannel(channelsToUse: List<Channel>, sessionKey: String): Boolean {
        if (channelsToUse.isEmpty()) {
            return false
        }

        if (startupLaunchInProgress || startupLaunchSessionKey == sessionKey) {
            Log.d("TVAutoplay", "Skipping autoplay launch for session=$sessionKey")
            return false
        }

        startupLaunchInProgress = true
        startupLaunchSessionKey = sessionKey
        return try {
            startupOutcome = TvStartupOutcome.Checking
            showLoading = true

            val readiness = ensureServerReady()
            if (!readiness.ready) {
                val reason = readiness.reason ?: "Server readiness timed out"
                Log.w("TVAutoplay", reason)
                startupOutcome = TvStartupOutcome.Timeout(reason)
                showLoading = false
                return false
            }

            val candidates = channelsToUse.take(TV_STARTUP_MAX_FALLBACK_CHANNELS)
            for ((candidateIndex, candidate) in candidates.withIndex()) {
                Log.d(
                    "TVAutoplay",
                    "Autoplay attempt index=${candidateIndex + 1}/${candidates.size} channel=${candidate.channel_name}"
                )
                if (!probeStreamEndpoint(
                        url = candidate.channel_url,
                        connectTimeoutMs = TV_PREFLIGHT_CONNECT_TIMEOUT_MS,
                        readTimeoutMs = TV_PREFLIGHT_READ_TIMEOUT_MS,
                        callTimeoutMs = TV_PREFLIGHT_CALL_TIMEOUT_MS
                    )) {
                    Log.w(
                        "TVAutoplay",
                        "Skipping candidate index=$candidateIndex due to preflight failure"
                    )
                    continue
                }
                val (channelWindow, relativeIndex) = buildChannelInfoWindow(
                    context = context,
                    channels = channelsToUse,
                    basefinURL = basefinURL,
                    centerIndex = candidateIndex
                )
                val intent = Intent(context, ExoPlayJet::class.java).apply {
                    putExtra("zone", "TV")
                    if (candidate.channel_id.all { it.isDigit() }) putExtra("channel_list_kind", "jio")
                    putExtra("current_channel_index", relativeIndex)
                    putParcelableArrayListExtra("channel_list_data", channelWindow)
                    putExtra("video_url", candidate.channel_url)
                    putExtra(
                        "logo_url",
                        if (candidate.logoUrl.startsWith("http")) candidate.logoUrl else "http://localhost:$localPORT/jtvimage/${candidate.logoUrl}"
                    )
                    putExtra("ch_name", candidate.channel_name)
                }

                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(intent)
                }

                val recentChannelsJson = preferenceManager.myPrefs.recentChannels
                val type = object : TypeToken<List<Channel>>() {}.type
                val recentChannels: MutableList<Channel> =
                    Gson().fromJson(recentChannelsJson, type) ?: mutableListOf()

                val existingIndex = recentChannels.indexOfFirst { it.channel_id == candidate.channel_id }

                if (existingIndex != -1) {
                    val existingChannel = recentChannels[existingIndex]
                    recentChannels.removeAt(existingIndex)
                    recentChannels.add(0, existingChannel)
                } else {
                    recentChannels.add(0, candidate)
                    if (recentChannels.size > 25) {
                        recentChannels.removeAt(recentChannels.size - 1)
                    }
                }
                preferenceManager.myPrefs.currChannelUrl = candidate.channel_url
                preferenceManager.myPrefs.recentChannels = Gson().toJson(recentChannels)
                preferenceManager.savePreferences()

                AppStartTracker.shouldPlayChannel = true
                startupOutcome = TvStartupOutcome.Idle
                showLoading = false
                Log.d("TVAutoplay", "Autoplay launched channel index=$candidateIndex")
                return true
            }

            val failureReason = "All ${candidates.size} autoplay candidates failed preflight"
            Log.w("TVAutoplay", failureReason)
            startupOutcome = TvStartupOutcome.Failure(failureReason)
            showLoading = false
            false
        } catch (_: Exception) {
            val failureReason = "Autoplay launch failed unexpectedly"
            startupOutcome = TvStartupOutcome.Failure(failureReason)
            showLoading = false
            false
        } finally {
            startupLaunchInProgress = false
        }
    }

    suspend fun watchdogAutoplay(channels: List<Channel>, sessionKey: String): Boolean {
        repeat(5) {
            if (preferenceManager.myPrefs.currChannelUrl.isNullOrEmpty()) {
                val channelsToUse = channels.ifEmpty { fetchFromBackend() }
                if (channelsToUse.isNotEmpty()) {
                    if (launchFirstChannel(channelsToUse, sessionKey)) {
                        return true
                    }
                }
            }
            delay(2000)
        }
        if (startupOutcome is TvStartupOutcome.Checking) {
            startupOutcome = TvStartupOutcome.Failure("No playable channels became available during startup")
        }
        showLoading = false
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

    // Fetch and filter channels (cache/network), then gate autoplay through one startup session.
    LaunchedEffect(reloadTrigger, startupRetryToken) {
        val sessionKey = "$reloadTrigger:$startupRetryToken"
        showLoading = true
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
                launchFirstChannel(channelsForAutoplay, sessionKey)
            } else {
                watchdogAutoplay(channelsForAutoplay, sessionKey)
            }
        } else {
            showLoading = false
            startupOutcome = TvStartupOutcome.Idle
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

    // UI: Startup readiness, recovery and content.
    val currentStartupOutcome = startupOutcome
    if (currentStartupOutcome is TvStartupOutcome.Timeout || currentStartupOutcome is TvStartupOutcome.Failure) {
        val reason = if (currentStartupOutcome is TvStartupOutcome.Timeout) {
            currentStartupOutcome.reason
        } else {
            (currentStartupOutcome as TvStartupOutcome.Failure).reason
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentStartupOutcome is TvStartupOutcome.Timeout) {
                        "Startup timed out"
                    } else {
                        "Startup failed"
                    },
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = reason,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(20.dp))
                ElevatedCard(
                    onClick = {
                        startupOutcome = TvStartupOutcome.Checking
                        showLoading = true
                        startupRetryToken++
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Retry startup"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry startup")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedCard(
                    onClick = {
                        startupOutcome = TvStartupOutcome.Idle
                        showLoading = !fetched
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Open channel list"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open channel list")
                    }
                }
            }
        }
    } else if (showLoading || currentStartupOutcome is TvStartupOutcome.Checking) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContainedLoadingIndicator(modifier = Modifier.size(100.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (currentStartupOutcome is TvStartupOutcome.Checking) {
                        "Waiting for server readiness..."
                    } else {
                        "Loading channels..."
                    },
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    color = Color.White
                )
            }
        }
    } else if (fetched && filteredChannels.value.isEmpty()) {
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
    } else {
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

