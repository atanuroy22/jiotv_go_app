package com.skylake.skytv.jgorunner.ui.tvhome

import android.content.Context
import android.content.Intent
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import android.app.Activity
import com.skylake.skytv.jgorunner.ui.screens.AppStartTracker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun FavouriteLayout(context: Context, onChangeZone: (String) -> Unit) {
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
    val scope = rememberCoroutineScope()
    // Only JioTV favourites retained
    val jiotvFavKey = "favouriteChannelsJio"
    val shared = context.getSharedPreferences("favourites_store", Context.MODE_PRIVATE)
    if (!shared.contains(jiotvFavKey)) {
        // migrate legacy one-time
        preferenceManager.myPrefs.favouriteChannels?.takeIf { !it.isNullOrBlank() }?.let { legacy ->
            shared.edit().putString(jiotvFavKey, legacy).apply()
        }
    }
    var favouriteChannels by remember(shared.getString(jiotvFavKey, "")) {
        val json = shared.getString(jiotvFavKey, "")
        mutableStateOf(safeChannelParse(json))
    }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showModifyDialog by rememberSaveable { mutableStateOf(false) }

    // Canonical full channel list (cache -> network)
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loadingAllChannels by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Load canonical channels once (or when port changes)
    LaunchedEffect(preferenceManager.myPrefs.jtvGoServerPort) {
        loadingAllChannels = true
        loadError = null
        val port = preferenceManager.myPrefs.jtvGoServerPort ?: 8080
        try {
            allChannels = ChannelUtils.loadCanonicalChannels(context, port)
            if (allChannels.isEmpty()) loadError = "No channels available"
        } catch (e: Exception) {
            loadError = e.message
            Log.e("FavouriteLayout", "Channel load failed: ${e.message}")
        } finally {
            loadingAllChannels = false
        }
    }

    // No filtering: show favourites exactly as stored
    val visibleFavourites = favouriteChannels

    // Background refresh only
    LaunchedEffect(visibleFavourites) {
        if (visibleFavourites.isEmpty()) {
            try {
                val port = preferenceManager.myPrefs.jtvGoServerPort ?: 8080
                ChannelUtils.fetchChannels("http://localhost:$port/channels")?.let { response ->
                    context.getSharedPreferences("channel_cache", Context.MODE_PRIVATE).edit().apply {
                        putString("channels_json", Gson().toJson(response))
                        apply()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Autoplay first favourite channel when favourite tab is default
    LaunchedEffect(visibleFavourites) {
        if (preferenceManager.myPrefs.startTvAutomatically &&
            !AppStartTracker.shouldPlayChannel &&
            visibleFavourites.isNotEmpty()
        ) {
            val firstChannel = visibleFavourites.first()
            val port = preferenceManager.myPrefs.jtvGoServerPort ?: 8080

            val intent = Intent(context, ExoPlayJet::class.java).apply {
                putExtra("zone", "TV")
                putParcelableArrayListExtra(
                    "channel_list_data",
                    ArrayList(visibleFavourites.map { ch ->
                        ChannelInfo(
                            ch.channel_url,
                            "http://localhost:$port/jtvimage/${ch.logoUrl}",
                            ch.channel_name
                        )
                    })
                )
                putExtra("current_channel_index", 0)
                putExtra("video_url", firstChannel.channel_url)
                putExtra("logo_url", "http://localhost:$port/jtvimage/${firstChannel.logoUrl}")
                putExtra("ch_name", firstChannel.channel_name)
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
                OutlinedButton(
                    onClick = { /* already JIO */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2962FF).copy(alpha = 0.18f),
                        contentColor = Color(0xFF2962FF)
                    ),
                    contentPadding = btnPadding,
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = minBtnHeight)
                ) { Text("JioTV", fontSize = toggleFont, maxLines = 1) }
                OutlinedButton(
                    onClick = { onChangeZone("M3U") },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Unspecified
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
                        text = "Jio FAV",
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
                        // Try a one-time reload if server just started or cache is empty
                        scope.launch {
                            loadingAllChannels = true
                            try {
                                val port = preferenceManager.myPrefs.jtvGoServerPort ?: 8080
                                val reloaded = ChannelUtils.loadCanonicalChannels(context, port)
                                allChannels = reloaded
                                if (reloaded.isNotEmpty()) {
                                    showAddDialog = true
                                } else {
                                    Toast.makeText(context, loadError ?: "No channels available", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, loadError ?: "No channels available", Toast.LENGTH_SHORT).show()
                            } finally {
                                loadingAllChannels = false
                            }
                        }
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

        // Filters removed per user request

        if (visibleFavourites.isEmpty()) {
            Text("No favourite channels", fontSize = 14.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(visibleFavourites, key = { it.channel_id }) { channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        border = if (isFocused) BorderStroke(4.dp, Color(0xFFFFA500)) else null,
                        elevation = CardDefaults.cardElevation(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(120.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable {
                                playChannel(context, channel, visibleFavourites)
                            }
                    ) {
                        Column {
                            val logo = "http://localhost:${preferenceManager.myPrefs.jtvGoServerPort}/jtvimage/${channel.logoUrl}"
                            GlideImage(
                                model = logo,
                                contentDescription = channel.channel_name,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Fit
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                                Text(channel.channel_name, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFavouriteChannelsDialog(
            context = context,
            allChannels = allChannels,
            alreadySelected = favouriteChannels.map { it.channel_id }.toSet(),
            onDismiss = { showAddDialog = false },
            onSelectionChanged = { updated ->
                favouriteChannels = updated
                shared.edit().putString(jiotvFavKey, try { gson.toJson(updated) } catch (e:Exception){""}).apply()
            }
        )
    }
    if (showModifyDialog) {
        ModifyFavouriteChannelsDialog(
            context = context,
            favourites = favouriteChannels,
            onDismiss = { showModifyDialog = false },
            onSelectionChanged = { updated ->
                favouriteChannels = updated
                shared.edit().putString(jiotvFavKey, try { gson.toJson(updated) } catch (e:Exception){""}).apply()
            }
        )
    }
}

private fun safeChannelParse(json: String?): List<Channel> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<Channel>>() {}.type
        gson.fromJson<List<Channel>>(json, type) ?: emptyList()
    } catch (e1: JsonSyntaxException) {
        // Try wrapped key formats
        try {
            val gson = Gson()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val root: Map<String, Any> = gson.fromJson(json, mapType) ?: return emptyList()
            val keys = listOf("data", "result", "channels")
            val rawList = keys.firstNotNullOfOrNull { k -> root[k] as? List<*> } ?: return emptyList()
            rawList.mapNotNull { elem ->
                try {
                    val objJson = gson.toJson(elem)
                    gson.fromJson(objJson, Channel::class.java)
                } catch (e: Exception) { null }
            }.filterNotNull()
        } catch (e2: Exception) {
            Log.e("FavouriteLayout", "Parse fallback failed: ${e2.message}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("FavouriteLayout", "Parse error: ${e.message}")
        emptyList()
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ModifyFavouriteChannelsDialog(
    context: Context,
    favourites: List<Channel>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<Channel>) -> Unit
) {
    val preferenceManager = SkySharedPref.getInstance(context)
    val selected = remember(favourites) { mutableStateOf(favourites.map { it.channel_id }.toMutableSet()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modify Fav Channels", fontSize = 16.sp)
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 28.dp)
                    ) { Text("Close") }
                }
                // Tighten the vertical gap above the red action button
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
                        modifier = Modifier.heightIn(max = gridMaxH)
                    ) {
                        items(favourites, key = { it.channel_id }) { channel ->
                            var isChecked by remember { mutableStateOf(selected.value.contains(channel.channel_id)) }
                            Card(
                                modifier = Modifier.fillMaxWidth().height(118.dp).clickable {
                                    isChecked = !isChecked
                                    if (isChecked) selected.value.add(channel.channel_id) else selected.value.remove(channel.channel_id)
                                    val finalList = favourites.filter { selected.value.contains(it.channel_id) }
                                    onSelectionChanged(finalList)
                                },
                                border = if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val logo = "http://localhost:${preferenceManager.myPrefs.jtvGoServerPort}/jtvimage/${channel.logoUrl}"
                                    GlideImage(
                                        model = logo,
                                        contentDescription = channel.channel_name,
                                        modifier = Modifier.fillMaxWidth().height(64.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isChecked, onCheckedChange = { checked ->
                                            isChecked = checked
                                            if (checked) selected.value.add(channel.channel_id) else selected.value.remove(channel.channel_id)
                                            val finalList = favourites.filter { selected.value.contains(it.channel_id) }
                                            onSelectionChanged(finalList)
                                        }, modifier = Modifier.size(18.dp))
                                        Text(
                                            channel.channel_name,
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AddFavouriteChannelsDialog(
    context: Context,
    allChannels: List<Channel>,
    alreadySelected: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<Channel>) -> Unit
) {
    val preferenceManager = SkySharedPref.getInstance(context)
    // Provided JioTV category mapping (Name -> ID)
    val jioCategoryNameToId: Map<String, Int?> = mapOf(
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
    // Build reverse map for id -> name (excluding null / Reset)
    val categoryIdToName: Map<Int, String> = jioCategoryNameToId.filterValues { it != null }.map { it.value!! to it.key }.toMap()
    // Build list of categories present in current channel list, with readable names.
    data class Cat(val id: Int, val label: String)
    val categories = remember(allChannels) {
        allChannels.map { ch ->
            val label = categoryIdToName[ch.channelCategoryId] ?: "Cat ${ch.channelCategoryId}"
            Cat(ch.channelCategoryId, label)
        }.distinctBy { it.id }
            .sortedBy { it.label }
    }
    var activeCategory by remember { mutableStateOf<Int?>(null) }

    // Language mapping similar to TvScreenMenu
    val languageNameToId: Map<String, Int?> = mapOf(
        "All Languages" to null,
        "Hindi" to 1,
        "Marathi" to 2,
        "Punjabi" to 3,
        "Urdu" to 4,
        "Bengali" to 5,
        "English" to 6,
        "Malayalam" to 7,
        "Tamil" to 8,
        "Gujarati" to 9,
        "Odia" to 10,
        "Telugu" to 11,
        "Bhojpuri" to 12,
        "Kannada" to 13,
        "Assamese" to 14,
        "Nepali" to 15,
        "French" to 16,
        "Other" to 18
    )
    val languageIdToName: Map<Int, String> = languageNameToId
        .filterValues { it != null }
        .map { it.value!! to it.key }
        .toMap()

    data class Lang(val id: Int, val label: String)
    val languages = remember(allChannels) {
        allChannels.map { ch ->
            val label = languageIdToName[ch.channelLanguageId] ?: "Lang ${ch.channelLanguageId}"
            Lang(ch.channelLanguageId, label)
        }.distinctBy { it.id }
            .sortedBy { it.label }
    }
    var selectedLanguageIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val filteredChannels = allChannels.filter {
        (activeCategory == null || it.channelCategoryId == activeCategory) &&
        (selectedLanguageIds.isEmpty() || selectedLanguageIds.contains(it.channelLanguageId))
    }
    val selectedIds = remember { mutableStateOf(alreadySelected.toMutableSet()) }

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
                    Text("Select Fav Channels", fontSize = 16.sp)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(2.dp))
                // Unselect all currently selected favourites from this picker
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            selectedIds.value = mutableSetOf()
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
                // Language selector as dropdown (dialog) with multi-select checkboxes
                var showLanguagePicker by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showLanguagePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Languages")
                }
                if (showLanguagePicker) {
                    Dialog(onDismissRequest = { showLanguagePicker = false }) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Select Languages", fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                                    items(languages, key = { it.id }) { lang ->
                                        val checked = selectedLanguageIds.contains(lang.id)
                                        var focused by remember { mutableStateOf(false) }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                                .onFocusChanged { focused = it.isFocused }
                                                .focusable()
                                                .clickable {
                                                    selectedLanguageIds = if (checked) selectedLanguageIds - lang.id else selectedLanguageIds + lang.id
                                                }
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    selectedLanguageIds = if (isChecked) selectedLanguageIds + lang.id else selectedLanguageIds - lang.id
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(lang.label, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { selectedLanguageIds = emptySet() }) { Text("Clear") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { showLanguagePicker = false }) { Text("Done") }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Category buttons row (includes 'All') with highlight (now below languages)
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
                    items(categories, key = { it.id }) { cat ->
                        val selected = activeCategory == cat.id
                        OutlinedButton(
                            onClick = { activeCategory = if (selected) null else cat.id },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(cat.label) }
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
                        items(filteredChannels, key = { it.channel_id }) { channel ->
                            val isChecked = selectedIds.value.contains(channel.channel_id)
                            Card(
                                modifier = Modifier.fillMaxWidth().height(118.dp).clickable {
                                    val new = selectedIds.value.toMutableSet()
                                    if (isChecked) new.remove(channel.channel_id) else new.add(channel.channel_id)
                                    selectedIds.value = new
                                    val finalList = allChannels.filter { selectedIds.value.contains(it.channel_id) }
                                    onSelectionChanged(finalList)
                                },
                                border = if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val logo = "http://localhost:${preferenceManager.myPrefs.jtvGoServerPort}/jtvimage/${channel.logoUrl}"
                                    GlideImage(
                                        model = logo,
                                        contentDescription = channel.channel_name,
                                        modifier = Modifier.fillMaxWidth().height(64.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                val new = selectedIds.value.toMutableSet()
                                                if (checked) new.add(channel.channel_id) else new.remove(channel.channel_id)
                                                selectedIds.value = new
                                                val finalList = allChannels.filter { selectedIds.value.contains(it.channel_id) }
                                                onSelectionChanged(finalList)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            channel.channel_name,
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

private fun playChannel(context: Context, channel: Channel, channels: List<Channel>) {
    try {
        val intent = Intent(context, ExoPlayJet::class.java).apply {
            putExtra("video_url", channel.channel_url)
            putExtra("zone", "TV")
            val allChannelsData = ArrayList(channels.map { ch ->
                val fullLogoUrl = "http://localhost:${SkySharedPref.getInstance(context).myPrefs.jtvGoServerPort}/jtvimage/${ch.logoUrl}"
                ChannelInfo(ch.channel_url, fullLogoUrl, ch.channel_name)
            })
            putParcelableArrayListExtra("channel_list_data", allChannelsData)
            putExtra("current_channel_index", channels.indexOf(channel))
            putExtra("video_url", channel.channel_url)
            putExtra("logo_url", "http://localhost:${SkySharedPref.getInstance(context).myPrefs.jtvGoServerPort}/jtvimage/${channel.logoUrl}")
            putExtra("ch_name", channel.channel_name)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to play channel", Toast.LENGTH_SHORT).show()
    }
}
