package com.skylake.skytv.jgorunner.widgets

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService


class StartServerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, BinaryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[booleanPreferencesKey("is_running")] = true
        }
        ServerGlanceWidget().update(context, glanceId)
    }
}


class StopServerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val stopIntent = Intent(context, BinaryService::class.java).apply {
            action = BinaryService.ACTION_STOP_BINARY
        }
        context.startService(stopIntent)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[booleanPreferencesKey("is_running")] = false
        }
        ServerGlanceWidget().update(context, glanceId)
    }
}

class StartAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[stringPreferencesKey("refresh")] = ""
        }
        ServerGlanceWidget().update(context, glanceId)
    }
}

class StartChromeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val skyprefs = SkySharedPref.getInstance(context).myPrefs
        val serveLocal = skyprefs.serveLocal
        val port = skyprefs.jtvGoServerPort.toString()
        val fullUrl = if (serveLocal) "http://localhost:$port" else getFullServerUrl(context)

        val urlUri = fullUrl.toUri()
        val chromeIntent = Intent(Intent.ACTION_VIEW, urlUri).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chromeIntent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, urlUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[stringPreferencesKey("refresh")] = ""
        }
        ServerGlanceWidget().update(context, glanceId)
    }
}


class ToggleLogsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = SkySharedPref.getInstance(context)
        prefs.myPrefs.widgetShowLogs = !prefs.myPrefs.widgetShowLogs
        prefs.savePreferences()
        ServerGlanceWidget().updateAll(context)
    }
}


fun getFullServerUrl(context: Context): String {
    val prefs = SkySharedPref.getInstance(context).myPrefs
    val port = prefs.jtvGoServerPort
    val isPublic = !prefs.serveLocal

    // Reuse logic similar to MainActivity.getPublicJTVServerURL()
    if (!isPublic) return "http://localhost:$port/"

    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        connectivityManager.activeNetwork
    } else {
        @Suppress("deprecation")
        val networks = connectivityManager.allNetworks
        if (networks.isNotEmpty()) networks[0] else null
    }

    if (activeNetwork != null) {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (networkCapabilities != null &&
            (networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        ) {
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            val ipAddress = linkProperties?.linkAddresses
                ?.filter { it.address is java.net.Inet4Address }
                ?.map { it.address.hostAddress }
                ?.firstOrNull()
            if (ipAddress != null) return "http://$ipAddress:$port/"
        }
        // If cellular, fallback to localhost
        if (networkCapabilities != null && networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "http://localhost:$port/"
        }
    }
    return "http://localhost:$port/"
}