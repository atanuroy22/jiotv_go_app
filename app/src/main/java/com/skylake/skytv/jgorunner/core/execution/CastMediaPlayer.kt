package com.skylake.skytv.jgorunner.core.execution

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.skylake.skytv.jgorunner.data.SkySharedPref

fun castMediaPlayer(context: Context, videoUrl: String) {
    val tag = "MediaPlayer-DIX"
    val castSession: CastSession? =
        CastContext.getSharedInstance(context).sessionManager.currentCastSession
    val remoteMediaClient: RemoteMediaClient? = castSession?.remoteMediaClient
    val prefManager = SkySharedPref.getInstance(context)

    val currentChannelName =
        prefManager.myPrefs.castChannelName?.takeIf { it.isNotEmpty() } ?: "Streaming"
    val currentChannelLogo = prefManager.myPrefs.castChannelLogo?.takeIf { it.isNotEmpty() }

    Log.d(tag, currentChannelName)

    if (remoteMediaClient != null) {
        val isCatchup = isCatchupStream(videoUrl)
        val streamType = if (isCatchup) MediaInfo.STREAM_TYPE_BUFFERED else MediaInfo.STREAM_TYPE_LIVE

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, currentChannelName)
            currentChannelLogo?.let { logo ->
                runCatching { addImage(WebImage(Uri.parse(logo))) }
            }
        }

        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(streamType)
            .setContentType("application/x-mpegURL")
            .setMetadata(metadata)
            .build()


        val loadRequestData = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteMediaClient.load(loadRequestData)
    }
}

private fun isCatchupStream(url: String): Boolean {
    val parsed = runCatching { Uri.parse(url) }.getOrNull()
    val queryNames = runCatching { parsed?.queryParameterNames }.getOrNull().orEmpty()

    if (queryNames.any {
            it.equals("start", true) ||
                    it.equals("end", true) ||
                    it.equals("from", true) ||
                    it.equals("to", true) ||
                    it.equals("begin", true) ||
                    it.equals("starttime", true) ||
                    it.equals("endtime", true) ||
                    it.equals("timestamp", true)
        }) {
        return true
    }

    val combined = (parsed?.path.orEmpty() + "?" + parsed?.query.orEmpty()).lowercase()
    return combined.contains("catchup") || combined.contains("timeshift")
}

