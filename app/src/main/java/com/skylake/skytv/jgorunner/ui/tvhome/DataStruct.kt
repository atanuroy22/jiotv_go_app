package com.skylake.skytv.jgorunner.ui.tvhome

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ChannelResponse(
    val code: Int,
    val message: String,
    val result: List<Channel>
)

@Keep
data class Channel(
    @SerializedName(value = "channel_id", alternate = ["id"])
    val channel_id: String,
    @SerializedName(value = "channel_name", alternate = ["name"])
    val channel_name: String,
    @SerializedName(value = "channel_url", alternate = ["url"])
    val channel_url: String,
    @SerializedName(value = "logoUrl", alternate = ["logo"])
    val logoUrl: String,
    @SerializedName(value = "channelCategoryId", alternate = ["category_id"])
    val channelCategoryId: Int = 0,
    @SerializedName(value = "channelLanguageId", alternate = ["language"])
    val channelLanguageId: Int = 0,
    @SerializedName(value = "country", alternate = ["country_code"])
    val country: String? = null,
    val isHD: Boolean = false
)

@Keep
data class EpgResponse(val epg: List<EpgProgram>)

@Keep
data class EpgProgram(
    val srno: Long,
    val showId: String,
    val showtime: String,
    val showname: String,
    val description: String,
    val duration: Int,
    val endtime: String,
    val channel_name: String,
    val episodeThumbnail: String,
    val episodePoster: String,
    val startEpoch: Long,
    val endEpoch: Long
)

// Data class for parsed channels
@Keep
data class M3UChannelExp(
    @SerializedName(value = "name", alternate = ["channel_name", "title"])
    val name: String,
    @SerializedName(value = "url", alternate = ["channel_url", "href", "link"])
    val url: String,
    @SerializedName(value = "logo", alternate = ["tvg_logo", "logoUrl", "image"])
    val logo: String?,
    @SerializedName(value = "category", alternate = ["group", "group_title", "group-title"])
    val category: String?,
    @SerializedName(value = "language", alternate = ["lang", "tvg_language", "tvg-language"])
    val language: String? = null,   // ISO 639-1 code e.g. "ta", "hi", "en"
    @SerializedName(value = "country", alternate = ["country_code", "tvg_country", "tvg-country"])
    val country: String? = null,     // ISO 3166-1 alpha-2 code e.g. "IN", "GB"
    @SerializedName(value = "open_in_browser", alternate = ["browser", "direct_browser", "browser_only"])
    val openInBrowser: Boolean = false,
    @SerializedName(value = "play_mode", alternate = ["mode", "type", "stream_type"])
    val playMode: String? = null
)

@Keep
data class DynamicZoneTabConfig(
    @SerializedName(value = "enable_tab", alternate = ["enabled", "enableTab"])
    val enabled: Boolean = false,
    @SerializedName(value = "tab_name", alternate = ["name", "tabName", "title"])
    val name: String = "",
    @SerializedName(value = "enable_channels", alternate = ["enable_main_channels", "enable_m3u_channels"])
    val enableChannels: Boolean = true,
    @SerializedName(value = "channels", alternate = ["channel_list", "items"])
    val channels: List<M3UChannelExp> = emptyList(),
    @SerializedName(value = "enable_browser_channels", alternate = ["enable_web_channels", "enable_direct_channels"])
    val enableBrowserChannels: Boolean = false,
    @SerializedName(value = "browser_channels", alternate = ["web_channels", "direct_channels"])
    val browserChannels: List<M3UChannelExp> = emptyList()
)