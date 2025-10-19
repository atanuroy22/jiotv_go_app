package com.skylake.skytv.jgorunner.widgets

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.skylake.skytv.jgorunner.services.BinaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ServerGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServerGlanceWidget()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val isRunningKey = booleanPreferencesKey("is_running")
        coroutineScope.launch {
            val isRunning = when (intent.action) {
                BinaryService.ACTION_BINARY_STARTED -> true
                BinaryService.ACTION_BINARY_STOPPED -> false
                else -> {
                    return@launch
                }
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(ServerGlanceWidget::class.java)

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[isRunningKey] = isRunning
                }
            }

            glanceAppWidget.updateAll(context)
        }
    }
}