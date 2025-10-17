package com.skylake.skytv.jgorunner.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.app.PendingIntent
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService

class ServerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_START_SERVER = "com.skylake.skytv.jgorunner.widgets.action.START_SERVER"
        const val ACTION_STOP_SERVER = "com.skylake.skytv.jgorunner.widgets.action.STOP_SERVER"
        const val ACTION_REFRESH = "com.skylake.skytv.jgorunner.widgets.action.REFRESH"
        const val ACTION_TOGGLE_LOGS = "com.skylake.skytv.jgorunner.widgets.action.TOGGLE_LOGS"

        private fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ServerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        private fun getFullServerUrl(context: Context): String {
            val prefs = SkySharedPref.getInstance(context).myPrefs
            val port = prefs.jtvGoServerPort
            val isPublic = !prefs.serveLocal

            // Reuse logic similar to MainActivity.getPublicJTVServerURL()
            if (!isPublic) return "http://localhost:$port/"

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
                            networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) {
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

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = SkySharedPref.getInstance(context).myPrefs
            val isRunning = BinaryService.isRunning
            val port = prefs.jtvGoServerPort
            val modeText = if (prefs.serveLocal) "Local" else "Public"
            val statusText = if (isRunning) "Running" else "Stopped"
            val fullUrl = getFullServerUrl(context)
            val showLogs = prefs.widgetShowLogs
            val logs = prefs.widgetLogs ?: ""

            val views = RemoteViews(context.packageName, R.layout.widget_server_control)

            views.setTextViewText(R.id.widget_status, "Status: $statusText")
            views.setTextViewText(R.id.widget_port, "Port: $port")
            views.setTextViewText(R.id.widget_mode, "Mode: $modeText")
            views.setTextViewText(R.id.widget_url, "URL: $fullUrl")
            views.setTextViewText(R.id.widget_logs, if (showLogs) logs else "")
            views.setViewVisibility(R.id.widget_logs, if (showLogs) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.widget_toggle_logs_button, if (showLogs) "Hide Logs" else "Show Logs")

            // Start server pending intent
            val startIntent = Intent(context, ServerWidgetProvider::class.java).apply { action = ACTION_START_SERVER }
            val startPending = PendingIntent.getBroadcast(
                context,
                0,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            views.setOnClickPendingIntent(R.id.widget_start_button, startPending)

            // Stop server pending intent
            val stopIntent = Intent(context, ServerWidgetProvider::class.java).apply { action = ACTION_STOP_SERVER }
            val stopPending = PendingIntent.getBroadcast(
                context,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            views.setOnClickPendingIntent(R.id.widget_stop_button, stopPending)

            // Refresh pending intent
            val refreshIntent = Intent(context, ServerWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                2,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending)

            // Toggle logs pending intent
            val toggleIntent = Intent(context, ServerWidgetProvider::class.java).apply { action = ACTION_TOGGLE_LOGS }
            val togglePending = PendingIntent.getBroadcast(
                context,
                3,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_logs_button, togglePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_START_SERVER -> {
                val serviceIntent = Intent(context, BinaryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                updateAllWidgets(context)
            }
            ACTION_STOP_SERVER -> {
                val stopIntent = Intent(context, BinaryService::class.java).apply {
                    action = BinaryService.ACTION_STOP_BINARY
                }
                context.startService(stopIntent)
                updateAllWidgets(context)
            }
            ACTION_TOGGLE_LOGS -> {
                val pref = SkySharedPref.getInstance(context)
                pref.myPrefs.widgetShowLogs = !pref.myPrefs.widgetShowLogs
                pref.savePreferences()
                updateAllWidgets(context)
            }
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            BinaryService.ACTION_BINARY_STOPPED,
            BinaryService.ACTION_BINARY_STARTED -> {
                updateAllWidgets(context)
            }
        }
    }
}