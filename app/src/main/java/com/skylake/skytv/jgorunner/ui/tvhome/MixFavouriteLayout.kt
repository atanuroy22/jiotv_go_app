package com.skylake.skytv.jgorunner.ui.tvhome

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet

/**
 * Mix Favourite Layout
 * Combines JioTV and M3U favourites into a single grid.
 * No separate JSON persisted: computed live from respective favourite stores.
 */
data class MixedChannel(
    val name: String,
    val url: String,
    val logoRaw: String?, // raw logo value; for Jio, this is logoUrl (needs server prefix)
    val source: String // "JIO" or "M3U"
)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MixFavouriteLayout(context: Context, onChangeZone: (String) -> Unit) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    val headerGap = if (isCompact) 4.dp else 8.dp
    val toggleFont = if (isCompact) 11.sp else 14.sp
    val titleFont = if (isCompact) 12.sp else 16.sp
    val btnPadding = if (isCompact) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val minBtnHeight = if (isCompact) 30.dp else 40.dp
    val rowHPad = if (isCompact) 12.dp else 16.dp
    val rowVPad = if (isCompact) 6.dp else 8.dp
    val preferenceManager = SkySharedPref.getInstance(context)

    val shared = context.getSharedPreferences("favourites_store", Context.MODE_PRIVATE)
    val jioKey = "favouriteChannelsJio"
    val m3uKey = "favouriteChannelsM3U"

    // Load favourites from both stores
    var jioFavs by remember(shared.getString(jioKey, "")) {
        val json = shared.getString(jioKey, "")
        mutableStateOf(safeChannelParse(json))
    }
    var m3uFavs by remember(shared.getString(m3uKey, "")) {
        val json = shared.getString(m3uKey, "")
        mutableStateOf(safeM3UChannelParse(json))
    }

    val mixedList: List<MixedChannel> = remember(jioFavs, m3uFavs, preferenceManager.myPrefs.jtvGoServerPort) {
        val jio = jioFavs.map { ch -> MixedChannel(ch.channel_name, ch.channel_url, ch.logoUrl, "JIO") }
        val m3u = m3uFavs.map { ch -> MixedChannel(ch.name, ch.url, ch.logo, "M3U") }
        // Distinct by URL to avoid duplicates if same URL exists in both
        (jio + m3u).distinctBy { it.url }
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
                    onClick = { onChangeZone("JIO") },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Unspecified
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
                    onClick = { /* already MIX */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2962FF).copy(alpha = 0.18f),
                        contentColor = Color(0xFF2962FF)
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
                        text = "Mix FAV",
                        fontSize = titleFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (isCompact) 56.dp else 96.dp)
                    )
                }
            }
        }

        if (mixedList.isEmpty()) {
            Text("No favourite channels", fontSize = 14.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(mixedList, key = { it.url }) { item ->
                    Card(
                        border = null,
                        elevation = CardDefaults.cardElevation(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .height(120.dp)
                            .clickable { playMixedChannel(context, item, mixedList) }
                    ) {
                        Column {
                            val logo = when (item.source) {
                                "JIO" -> {
                                    val port = preferenceManager.myPrefs.jtvGoServerPort ?: 8080
                                    "http://localhost:$port/jtvimage/${item.logoRaw}"
                                }
                                else -> item.logoRaw ?: ""
                            }
                            GlideImage(
                                model = logo,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Fit
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                                Text(item.name, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun safeChannelParse(json: String?): List<Channel> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<Channel>>() {}.type
        gson.fromJson<List<Channel>>(json, type) ?: emptyList()
    } catch (e1: JsonSyntaxException) {
        // Fallback for wrapped formats
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
                } catch (_: Exception) { null }
            }.filterNotNull()
        } catch (_: Exception) { emptyList() }
    } catch (_: Exception) { emptyList() }
}

private fun safeM3UChannelParse(json: String?): List<M3UChannelExp> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<M3UChannelExp>>() {}.type
        gson.fromJson<List<M3UChannelExp>>(json, type) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun playMixedChannel(context: Context, current: MixedChannel, list: List<MixedChannel>) {
    try {
        val port = SkySharedPref.getInstance(context).myPrefs.jtvGoServerPort ?: 8080
        val infoList = ArrayList(list.map { item ->
            val logo = if (item.source == "JIO") "http://localhost:$port/jtvimage/${item.logoRaw}" else (item.logoRaw ?: "")
            ChannelInfo(item.url, logo, item.name)
        })
        val intent = Intent(context, ExoPlayJet::class.java).apply {
            putExtra("zone", "TV")
            putParcelableArrayListExtra("channel_list_data", infoList)
            putExtra("current_channel_index", list.indexOf(current))
            putExtra("video_url", current.url)
            val logo = if (current.source == "JIO") "http://localhost:$port/jtvimage/${current.logoRaw}" else (current.logoRaw ?: "")
            putExtra("logo_url", logo)
            putExtra("ch_name", current.name)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to play channel", Toast.LENGTH_SHORT).show()
    }
}
