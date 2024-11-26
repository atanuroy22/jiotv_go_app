package com.skylake.skytv.jgorunner.core.update

data class DownloadModelNew(
    val status: Status,
    val fileName: String,
    val progress: Int,
    val failureReason: String
)

enum class Status {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    CANCELLED
}
