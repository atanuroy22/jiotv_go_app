package com.skylake.skytv.jgorunner.core.update

import org.cthing.versionparser.semver.SemanticVersion

data class DownloadAsset(
    val name: String,
    val version: SemanticVersion,
    val downloadUrl: String,
    val downloadSize: Long
)
