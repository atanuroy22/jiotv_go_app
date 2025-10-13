package com.skylake.skytv.jgorunner.ui.tvhome

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import android.content.pm.PackageManager
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import com.skylake.skytv.jgorunner.ui.tvhome.Channel
import com.skylake.skytv.jgorunner.ui.tvhome.M3UChannelExp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CardChannelLayout(
    context: Context,
    filteredChannels: List<Channel>,
    categoryMap: Map<String, Int?>,
    selectedChannelSetter: (Channel) -> Unit,
    localPORT: Int,
    preferenceManager: SkySharedPref
) {
    val basefinURL = remember(localPORT) { "http://localhost:$localPORT" }
    val categories = remember(categoryMap) {
        categoryMap.filter { (name, id) -> name != "Reset" && id != null }.toList()
    }
    val groupedChannels = remember(filteredChannels) {
        filteredChannels.groupBy { it.channelCategoryId }
    }
    val firstCategoryToFocus = remember(categories, groupedChannels) {
        categories.firstOrNull { groupedChannels[it.second]?.isNotEmpty() == true } ?: categories.firstOrNull()
    }
    var lastFocusedCategoryId by rememberSaveable { mutableStateOf(firstCategoryToFocus?.second) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(categories, firstCategoryToFocus) {
        val validCategoryIds = categories.mapNotNull { it.second }.toSet()
        if (lastFocusedCategoryId != null && lastFocusedCategoryId !in validCategoryIds) {
            lastFocusedCategoryId = null
        }
        if (lastFocusedCategoryId == null) {
            lastFocusedCategoryId = firstCategoryToFocus?.second
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp)
            .focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        itemsIndexed(categories, key = { _, entry -> entry.second ?: -1 }) { index, (categoryName, categoryId) ->
            val channelsInCategory = groupedChannels[categoryId].orEmpty()
            val requestInitialFocus = when {
                lastFocusedCategoryId != null -> lastFocusedCategoryId == categoryId
                firstCategoryToFocus == null -> index == 0
                else -> firstCategoryToFocus.second == categoryId
            }

            CategoryCarousel(
                context = context,
                categoryName = categoryName,
                categoryId = categoryId,
                channels = channelsInCategory,
                allChannels = filteredChannels,
                basefinURL = basefinURL,
                preferenceManager = preferenceManager,
                selectedChannelSetter = selectedChannelSetter,
                requestInitialFocus = requestInitialFocus,
                onCategoryFocused = { focusedId -> lastFocusedCategoryId = focusedId },
                selectedChannelId = selectedChannelId,
                onChannelSelected = { channelId -> selectedChannelId = channelId }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CategoryCarousel(
    context: Context,
    categoryName: String,
    categoryId: Int?,
    channels: List<Channel>,
    allChannels: List<Channel>,
    basefinURL: String,
    preferenceManager: SkySharedPref,
    selectedChannelSetter: (Channel) -> Unit,
    requestInitialFocus: Boolean,
    onCategoryFocused: (Int?) -> Unit,
    selectedChannelId: String?,
    onChannelSelected: (String) -> Unit
) {
    val cardWidth = 180.dp
    val cardHeight = 140.dp
    val cardSpacing = 20.dp
    val rowPadding = 48.dp
    val lazyRowState = rememberLazyListState()
    val density = LocalDensity.current
    val isTvDevice = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    // Hide empty categories
    if (channels.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
    ) {
        Text(
            text = categoryName,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val cardWidthPx = with(density) { cardWidth.toPx() }
            val spacingPx = with(density) { cardSpacing.toPx() }
            val baseRowPaddingPx = with(density) { rowPadding.toPx() }

            LazyRow(
                state = lazyRowState,
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                contentPadding = PaddingValues(horizontal = rowPadding),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.channel_id }) { index, channel ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        var initialFocusRequested by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (isFocused) 1.15f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "cardFocusScale"
                        )
                        val isFirstItem = index == 0
                        val isLastItem = index == channels.lastIndex

                        LaunchedEffect(requestInitialFocus, initialFocusRequested) {
                            if (requestInitialFocus && isFirstItem && !initialFocusRequested) {
                                delay(120)
                                focusRequester.requestFocus()
                                initialFocusRequested = true
                            }
                        }

                        LaunchedEffect(isFocused) {
                            if (isFocused) {
                                onCategoryFocused(categoryId)
                                delay(50) // Small delay to prevent competing scrolls
                                // Disney+ style centering: scroll so item is at center of viewport
                                // LazyRow scrolls by moving content, where scrollOffset is pixels from item start
                                val viewportCenterPx = containerWidthPx / 2f
                                val cardCenterOffsetPx = cardWidthPx / 2f
                                // Target: align card center with viewport center
                                val targetScrollOffset = -viewportCenterPx + cardCenterOffsetPx
                                lazyRowState.animateScrollToItem(
                                    index = index,
                                    scrollOffset = targetScrollOffset.roundToInt()
                                )
                            }
                        }

                        ChannelCard(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .width(cardWidth)
                                .height(cardHeight)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                }
                                .scale(scale)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                if (isFirstItem) {
                                                    true // Consume the event to prevent navigation
                                                } else {
                                                    false // Allow normal navigation
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if (isLastItem) {
                                                    true // Consume the event to prevent navigation
                                                } else {
                                                    false // Allow normal navigation
                                                }
                                            }
                                            Key.DirectionCenter, Key.Enter -> {
                                                selectedChannelSetter(channel)
                                                handleChannelPlay(
                                                    context = context,
                                                    channel = channel,
                                                    basefinURL = basefinURL,
                                                    allChannels = allChannels,
                                                    preferenceManager = preferenceManager
                                                )
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusable()
                                .focusProperties {
                                    if (isFirstItem) left = FocusRequester.Cancel
                                    if (isLastItem) right = FocusRequester.Cancel
                                },
                            channel = channel,
                            basefinURL = basefinURL,
                            isHighlighted = if (isTvDevice) isFocused else false,
                            onClick = {
                                selectedChannelSetter(channel)
                                handleChannelPlay(
                                    context = context,
                                    channel = channel,
                                    basefinURL = basefinURL,
                                    allChannels = allChannels,
                                    preferenceManager = preferenceManager
                                )
                            }
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ChannelCard(
    modifier: Modifier,
    channel: Channel,
    basefinURL: String,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        border = if (isHighlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 8.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            GlideImage(
                model = "$basefinURL/jtvimage/${channel.logoUrl}",
                contentDescription = channel.channel_name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.channel_name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyCategoryCard(
    message: String,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    requestInitialFocus: Boolean,
    onFocusGained: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(requestInitialFocus, initialFocusRequested) {
        if (requestInitialFocus && !initialFocusRequested) {
            delay(120)
            focusRequester.requestFocus()
            initialFocusRequested = true
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocusGained()
        }
    }

    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
            },
        shape = RoundedCornerShape(28.dp),
        border = if (isFocused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun handleChannelPlay(
    context: Context,
    channel: Channel,
    basefinURL: String,
    allChannels: List<Channel>,
    preferenceManager: SkySharedPref
) {
    val intent = Intent(context, ExoPlayJet::class.java).apply {
        putExtra("video_url", channel.channel_url)
        putExtra("zone", "TV")
        val allChannelsData = ArrayList(allChannels.map { ch ->
            ChannelInfo(
                ch.channel_url,
                "$basefinURL/jtvimage/${ch.logoUrl}",
                ch.channel_name
            )
        })
        putParcelableArrayListExtra("channel_list_data", allChannelsData)
        val currentChannelIndex = allChannels.indexOfFirst { it.channel_id == channel.channel_id }
        putExtra("current_channel_index", currentChannelIndex.coerceAtLeast(0))
        putExtra("logo_url", "$basefinURL/jtvimage/${channel.logoUrl}")
        putExtra("ch_name", channel.channel_name)
    }
    startActivity(context, intent, null)

    val recentChannelsJson = preferenceManager.myPrefs.recentChannels
    val type = object : TypeToken<List<Channel>>() {}.type
    val recentChannels: MutableList<Channel> =
        if (!recentChannelsJson.isNullOrEmpty())
            Gson().fromJson(recentChannelsJson, type) ?: mutableListOf()
        else
            mutableListOf()
    val existingIndex = recentChannels.indexOfFirst { it.channel_id == channel.channel_id }
    if (existingIndex != -1) {
        val existingChannel = recentChannels[existingIndex]
        recentChannels.removeAt(existingIndex)
        recentChannels.add(0, existingChannel)
    } else {
        recentChannels.add(0, channel)
        if (recentChannels.size > 25) recentChannels.removeAt(recentChannels.size - 1)
    }
    preferenceManager.myPrefs.recentChannels = Gson().toJson(recentChannels)
    preferenceManager.savePreferences()
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CardChannelLayoutM3U(
    context: Context,
    channels: List<M3UChannelExp>,
    selectedChannelSetter: (M3UChannelExp) -> Unit
) {
    val groupedCategories = remember(channels) {
        channels.groupBy { channel ->
            channel.category?.takeIf { it.isNotBlank() } ?: "Other"
        }
    }
    val orderedCategories = remember(groupedCategories) {
        groupedCategories.keys.sorted()
    }
    val firstCategory = remember(orderedCategories, groupedCategories) {
        orderedCategories.firstOrNull { groupedCategories[it]?.isNotEmpty() == true } ?: orderedCategories.firstOrNull()
    }
    var lastFocusedCategoryName by rememberSaveable { mutableStateOf(firstCategory) }
    var selectedChannelUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(orderedCategories, firstCategory) {
        if (lastFocusedCategoryName != null && lastFocusedCategoryName !in orderedCategories) {
            lastFocusedCategoryName = null
        }
        if (lastFocusedCategoryName == null) {
            lastFocusedCategoryName = firstCategory
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp)
            .focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        itemsIndexed(orderedCategories, key = { _, category -> category }) { index, categoryName ->
            val categoryChannels = groupedCategories[categoryName].orEmpty()
            val requestInitialFocus = when {
                lastFocusedCategoryName != null -> lastFocusedCategoryName == categoryName
                firstCategory == null -> index == 0
                else -> firstCategory == categoryName
            }

            CategoryCarouselM3U(
                context = context,
                categoryName = categoryName,
                channels = categoryChannels,
                allChannels = channels,
                selectedChannelSetter = selectedChannelSetter,
                requestInitialFocus = requestInitialFocus,
                onCategoryFocused = { focusedName -> lastFocusedCategoryName = focusedName },
                selectedChannelUrl = selectedChannelUrl,
                onChannelSelected = { url -> selectedChannelUrl = url }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CategoryCarouselM3U(
    context: Context,
    categoryName: String,
    channels: List<M3UChannelExp>,
    allChannels: List<M3UChannelExp>,
    selectedChannelSetter: (M3UChannelExp) -> Unit,
    requestInitialFocus: Boolean,
    onCategoryFocused: (String) -> Unit,
    selectedChannelUrl: String?,
    onChannelSelected: (String) -> Unit
) {
    val cardWidth = 180.dp
    val cardHeight = 140.dp
    val cardSpacing = 20.dp
    val rowPadding = 48.dp
    val lazyRowState = rememberLazyListState()
    val density = LocalDensity.current
    val isTvDevice = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    // Hide empty categories
    if (channels.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
    ) {
        Text(
            text = categoryName,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val cardWidthPx = with(density) { cardWidth.toPx() }
            val spacingPx = with(density) { cardSpacing.toPx() }
            val baseRowPaddingPx = with(density) { rowPadding.toPx() }

            LazyRow(
                state = lazyRowState,
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                contentPadding = PaddingValues(horizontal = rowPadding),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.url }) { index, channel ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        var initialFocusRequested by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (isFocused) 1.15f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "cardFocusScaleM3U"
                        )
                        val isFirstItem = index == 0
                        val isLastItem = index == channels.lastIndex

                        LaunchedEffect(requestInitialFocus, initialFocusRequested) {
                            if (requestInitialFocus && isFirstItem && !initialFocusRequested) {
                                delay(120)
                                focusRequester.requestFocus()
                                initialFocusRequested = true
                            }
                        }

                        LaunchedEffect(isFocused) {
                            if (isFocused) {
                                onCategoryFocused(categoryName)
                                delay(50) // Small delay to prevent competing scrolls
                                // Disney+ style centering: scroll so item is at center of viewport
                                // LazyRow scrolls by moving content, where scrollOffset is pixels from item start
                                val viewportCenterPx = containerWidthPx / 2f
                                val cardCenterOffsetPx = cardWidthPx / 2f
                                // Target: align card center with viewport center
                                val targetScrollOffset = -viewportCenterPx + cardCenterOffsetPx
                                lazyRowState.animateScrollToItem(
                                    index = index,
                                    scrollOffset = targetScrollOffset.roundToInt()
                                )
                            }
                        }

                        M3UChannelCard(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .width(cardWidth)
                                .height(cardHeight)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                }
                                .scale(scale)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                if (isFirstItem) {
                                                    true // Consume the event to prevent navigation
                                                } else {
                                                    false // Allow normal navigation
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if (isLastItem) {
                                                    true // Consume the event to prevent navigation
                                                } else {
                                                    false // Allow normal navigation
                                                }
                                            }
                                            Key.DirectionCenter, Key.Enter -> {
                                                selectedChannelSetter(channel)
                                                handleM3UChannelPlay(
                                                    context = context,
                                                    channel = channel,
                                                    allChannels = allChannels
                                                )
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusable()
                                .focusProperties {
                                    if (isFirstItem) left = FocusRequester.Cancel
                                    if (isLastItem) right = FocusRequester.Cancel
                                },
                            channel = channel,
                            isHighlighted = if (isTvDevice) isFocused else false,
                            onClick = {
                                selectedChannelSetter(channel)
                                handleM3UChannelPlay(
                                    context = context,
                                    channel = channel,
                                    allChannels = allChannels
                                )
                            }
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun M3UChannelCard(
    modifier: Modifier,
    channel: M3UChannelExp,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        border = if (isHighlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 8.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            GlideImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun handleM3UChannelPlay(
    context: Context,
    channel: M3UChannelExp,
    allChannels: List<M3UChannelExp>
) {
    val channelInfoList = ArrayList(allChannels.map {
        ChannelInfo(it.url, it.logo ?: "", it.name)
    })
    val currentIndex = allChannels.indexOfFirst { it.url == channel.url }.coerceAtLeast(0)

    val intent = Intent(context, ExoPlayJet::class.java).apply {
        putParcelableArrayListExtra("channel_list_data", channelInfoList)
        putExtra("current_channel_index", currentIndex)
    }
    startActivity(context, intent, null)
}
