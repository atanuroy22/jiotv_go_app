package com.skylake.skytv.jgorunner.core.update

data class DownloadAsset(
    val name: String,
    val version: SemanticVersionNew,
    val downloadUrl: String,
    val downloadSize: Long
)
