package com.skylake.skytv.jgorunner.core.data

import com.google.gson.annotations.SerializedName

data class JTVConfiguration(
    @SerializedName("epg")
    var epg: Boolean = true,

    @SerializedName("debug")
    var debug: Boolean = false,

    @SerializedName("disable_ts_handler")
    var disableTsHandler: Boolean = false,

    @SerializedName("disable_logout")
    var disableLogout: Boolean = false,

    @SerializedName("drm")
    var drm: Boolean = false,

    @SerializedName("title")
    var title: String = "Jio+",

    @SerializedName("disable_url_encryption")
    var disableUrlEncryption: Boolean = false,

    @SerializedName("path_prefix")
    var pathPrefix: String = "",

    @SerializedName("proxy")
    var proxy: String = "",

    @SerializedName("log_path")
    var logPath: String = "",

    @SerializedName("log_to_stdout")
    var logToStdout: Boolean = false,

    @SerializedName("custom_channels_file")
    var customChannelsFile: String = "custom_channels.json",

    @SerializedName("default_categories")
    var defaultCategories: List<String> = emptyList(),

    @SerializedName("default_languages")
    var defaultLanguages: List<String> = emptyList(),

    @SerializedName("custom_channels_url")
    var customChannelsUrl: String = "",

    @SerializedName("epg_url")
    var epgUrl: String = "https://avkb.short.gy/jioepg.xml.gz"
)
