package com.skylake.skytv.jgorunner.ui.tvhome

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import android.util.Log
import android.app.Activity
import com.skylake.skytv.jgorunner.ui.screens.AppStartTracker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit

/**
 * M3U Favourite Layout
 * Mirrors the JioTV FavouriteLayout but operates on parsed M3U playlist channels (M3UChannelExp).
 * Stores favourites separately under key favouriteChannelsM3U inside favourites_store prefs.
 * Categories derive from group-title values. No additional filtering on main grid—shows saved favourites directly.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun M3UFavouriteLayout(context: Context, onChangeZone: (String) -> Unit) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    val headerGap = if (isCompact) 4.dp else 8.dp
    val toggleFont = if (isCompact) 11.sp else 14.sp
    val actionFont = if (isCompact) 11.sp else 14.sp
    val titleFont = if (isCompact) 12.sp else 16.sp
    val btnPadding = if (isCompact) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val minBtnHeight = if (isCompact) 30.dp else 40.dp
    val rowHPad = if (isCompact) 12.dp else 16.dp
    val rowVPad = if (isCompact) 6.dp else 8.dp
    val preferenceManager = SkySharedPref.getInstance(context)
    val gson = remember { Gson() }

    val favKey = "favouriteChannelsM3U"
    val shared = context.getSharedPreferences("favourites_store", Context.MODE_PRIVATE)

    // Parse persisted favourites list
    var favouriteChannels by remember(shared.getString(favKey, "")) {
        val json = shared.getString(favKey, "")
        mutableStateOf(safeM3UChannelParse(json))
    }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showModifyDialog by rememberSaveable { mutableStateOf(false) }

    // Load full M3U channel list from preference manager (channelListJson); if absent remains empty
    var allChannels by remember { mutableStateOf<List<M3UChannelExp>>(emptyList()) }
    var loadingAllChannels by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preferenceManager.myPrefs.channelListJson) {
        loadingAllChannels = true
        loadError = null
        try {
            val json = preferenceManager.myPrefs.channelListJson
            allChannels = safeM3UChannelParse(json)
            if (allChannels.isEmpty()) loadError = "No playlist channels loaded"
        } catch (e: Exception) {
            loadError = e.message
            Log.e("M3UFavouriteLayout", "Load failed: ${e.message}")
        } finally {
            loadingAllChannels = false
        }
    }

    val visibleFavourites = favouriteChannels

    // Autoplay first M3U favourite channel when favourite tab is default
    LaunchedEffect(visibleFavourites) {
        if (preferenceManager.myPrefs.startTvAutomatically &&
            !AppStartTracker.shouldPlayChannel &&
            visibleFavourites.isNotEmpty()
        ) {
            val firstChannel = visibleFavourites.first()

            val intent = Intent(context, ExoPlayJet::class.java).apply {
                putExtra("zone", "TV")
                putParcelableArrayListExtra(
                    "channel_list_data",
                    ArrayList(visibleFavourites.map { ch ->
                        ChannelInfo(ch.url, ch.logo ?: "", ch.name)
                    })
                )
                putExtra("current_channel_index", 0)
                putExtra("video_url", firstChannel.url)
                putExtra("logo_url", firstChannel.logo ?: "")
                putExtra("ch_name", firstChannel.name)
            }

            kotlinx.coroutines.delay(1000)

            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            AppStartTracker.shouldPlayChannel = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rowHPad, vertical = rowVPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(headerGap), verticalAlignment = Alignment.CenterVertically) {
                // Zone toggle (left)
                val selectedM3U = true
                OutlinedButton(
                    onClick = { onChangeZone("JIO") },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Unspecified
                    ),
                    contentPadding = btnPadding,
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Text("JioTV", fontSize = toggleFont, maxLines = 1) }
                OutlinedButton(
                    onClick = { /* already M3U */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2962FF).copy(alpha = 0.18f),
                        contentColor = Color(0xFF2962FF)
                    ),
                    contentPadding = btnPadding,
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Text("M3U", fontSize = toggleFont, maxLines = 1) }
                OutlinedButton(
                    onClick = { onChangeZone("MIX") },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Unspecified
                    ),
                    contentPadding = btnPadding,
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Text("Mix", fontSize = toggleFont, maxLines = 1) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(headerGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title placed near buttons per UX request
                val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
                if (isLandscape) {
                    Text(
                        text = "M3U FAV",
                        fontSize = titleFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (isCompact) 56.dp else 96.dp)
                    )
                }
                IconButton(
                    onClick = {
                    if (loadingAllChannels) {
                        Toast.makeText(context, "Loading channels...", Toast.LENGTH_SHORT).show()
                    } else if (allChannels.isEmpty()) {
                        Toast.makeText(context, loadError ?: "No channels available", Toast.LENGTH_SHORT).show()
                    } else {
                        showAddDialog = true
                    }
                },
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Icon(Icons.Filled.Add, contentDescription = "Add favourites") }
                IconButton(
                    onClick = {
                    if (favouriteChannels.isEmpty()) {
                        Toast.makeText(context, "No favourites", Toast.LENGTH_SHORT).show()
                    } else {
                        showModifyDialog = true
                    }
                },
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Icon(Icons.Filled.Edit, contentDescription = "Modify favourites") }
            }
        }

        if (visibleFavourites.isEmpty()) {
            Text("No favourite channels", fontSize = 14.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(visibleFavourites, key = { it.url }) { channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        border = if (isFocused) BorderStroke(4.dp, Color(0xFFFFA500)) else null,
                        elevation = CardDefaults.cardElevation(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(120.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { playM3UChannel(context, channel, visibleFavourites) }
                    ) {
                        Column {
                            GlideImage(
                                model = channel.logo ?: "",
                                contentDescription = channel.name,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Fit
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                                Text(channel.name, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddM3UFavouriteChannelsDialog(
            context = context,
            allChannels = allChannels,
            alreadySelected = favouriteChannels.map { it.url }.toSet(),
            onDismiss = { showAddDialog = false },
            onSelectionChanged = { updated ->
                val distinct = updated.distinctBy { it.url }
                favouriteChannels = distinct
                shared.edit().putString(favKey, try { gson.toJson(distinct) } catch (e: Exception) { "" }).apply()
            }
        )
    }
    if (showModifyDialog) {
        ModifyM3UFavouriteChannelsDialog(
            context = context,
            favourites = favouriteChannels,
            onDismiss = { showModifyDialog = false },
            onSelectionChanged = { updated ->
                val distinct = updated.distinctBy { it.url }
                favouriteChannels = distinct
                shared.edit().putString(favKey, try { gson.toJson(distinct) } catch (e: Exception) { "" }).apply()
            }
        )
    }
}

private fun safeM3UChannelParse(json: String?): List<M3UChannelExp> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<M3UChannelExp>>() {}.type
        gson.fromJson<List<M3UChannelExp>>(json, type) ?: emptyList()
    } catch (e1: JsonSyntaxException) {
        emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ModifyM3UFavouriteChannelsDialog(
    context: Context,
    favourites: List<M3UChannelExp>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<M3UChannelExp>) -> Unit
) {
    val selected = rememberSaveable(favourites) { mutableStateOf(favourites.map { it.url }.toMutableSet()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(0.90f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modify M3U Fav Channels", fontSize = 16.sp)
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 28.dp)
                    ) { Text("Close") }
                }
                // Tighten vertical gap between header and the red action button
                Spacer(Modifier.height(0.dp))
                Row(
                    // Reduce side padding so the button sits closer to the header
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSelectionChanged(emptyList()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = 0.dp, minHeight = 26.dp)
                    ) { Text("Remove all Favourite channels", fontSize = 12.sp) }
                }
                if (favourites.isEmpty()) {
                    Text("No favourite", fontSize = 12.sp)
                } else {
                    val cfg = LocalConfiguration.current
                    val isCompact = cfg.screenWidthDp < 600
                    val gridMaxH = if (isCompact) (cfg.screenHeightDp.dp * 0.8f) else 340.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isCompact) 96.dp else 110.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxH)
                    ) {
                        items(favourites, key = { it.url }) { channel ->
                            val isChecked = selected.value.contains(channel.url)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(118.dp)
                                    .clickable {
                                        selected.value = selected.value.toMutableSet().apply {
                                            if (isChecked) remove(channel.url) else add(channel.url)
                                        }
                                        val finalList = favourites.filter { url -> selected.value.contains(url.url) }
                                        onSelectionChanged(finalList)
                                    },
                                border = if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GlideImage(
                                        model = channel.logo ?: "",
                                        contentDescription = channel.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selected.value = selected.value.toMutableSet().apply {
                                                    if (checked) add(channel.url) else remove(channel.url)
                                                }
                                                val finalList = favourites.filter { url -> selected.value.contains(url.url) }
                                                onSelectionChanged(finalList)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .focusable()
                                        )
                                        Text(
                                            channel.name,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .weight(1f, fill = false)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AddM3UFavouriteChannelsDialog(
    context: Context,
    allChannels: List<M3UChannelExp>,
    alreadySelected: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<M3UChannelExp>) -> Unit
) {
    // Build categories from group-title values
    val categories = remember(allChannels) {
        allChannels.mapNotNull { it.category }.distinct().sorted()
    }
    var activeCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredChannels = allChannels.filter { (activeCategory == null || it.category == activeCategory) && !it.url.isNullOrBlank() }
    val selectedUrls = rememberSaveable { mutableStateOf(alreadySelected.toMutableSet()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(0.90f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select M3U Fav Channels", fontSize = 16.sp)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            selectedUrls.value = mutableSetOf()
                            onSelectionChanged(emptyList())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = 0.dp, minHeight = 24.dp)
                    ) { Text("Unselect all", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item {
                        val selected = activeCategory == null
                        OutlinedButton(
                            onClick = { activeCategory = null },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("All") }
                    }
                    items(categories) { cat ->
                        val selected = activeCategory == cat
                        OutlinedButton(
                            onClick = { activeCategory = if (selected) null else cat },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(cat) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filteredChannels.isEmpty()) {
                    Text(
                        text = if (allChannels.isEmpty()) "No channels loaded" else "No channels in this category",
                        fontSize = 12.sp
                    )
                } else {
                    val cfg = LocalConfiguration.current
                    val isCompact = cfg.screenWidthDp < 600
                    val gridMaxH = if (isCompact) (cfg.screenHeightDp.dp * 0.8f) else 340.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isCompact) 96.dp else 110.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = gridMaxH)
                    ) {
                        items(filteredChannels, key = { it.url!! }) { channel ->
                            val isChecked = selectedUrls.value.contains(channel.url)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(118.dp)
                                    .clickable {
                                        selectedUrls.value = selectedUrls.value.toMutableSet().apply {
                                            if (isChecked) remove(channel.url) else add(channel.url)
                                        }
                                        val finalList = allChannels.filter { selectedUrls.value.contains(it.url) }
                                        onSelectionChanged(finalList)
                                    },
                                border = if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GlideImage(
                                        model = channel.logo ?: "",
                                        contentDescription = channel.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedUrls.value = selectedUrls.value.toMutableSet().apply {
                                                    if (checked) add(channel.url) else remove(channel.url)
                                                }
                                                val finalList = allChannels.filter { selectedUrls.value.contains(it.url) }
                                                onSelectionChanged(finalList)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .focusable()
                                        )
                                        Text(
                                            channel.name,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .weight(1f, fill = false)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun playM3UChannel(context: Context, channel: M3UChannelExp, channels: List<M3UChannelExp>) {
    try {
        val intent = Intent(context, ExoPlayJet::class.java).apply {
            putExtra("video_url", channel.url)
            putExtra("zone", "TV")
            val allChannelsData = ArrayList(channels.map { ch ->
                ChannelInfo(ch.url, ch.logo ?: "", ch.name)
            })
            putParcelableArrayListExtra("channel_list_data", allChannelsData)
            putExtra("current_channel_index", channels.indexOf(channel))
            putExtra("logo_url", channel.logo ?: "")
            putExtra("ch_name", channel.name)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to play channel", Toast.LENGTH_SHORT).show()
    }
}
