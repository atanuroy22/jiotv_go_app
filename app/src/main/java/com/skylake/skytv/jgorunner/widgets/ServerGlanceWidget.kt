package com.skylake.skytv.jgorunner.widgets

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.services.BinaryService

class ServerGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            val prefs = currentState<Preferences>()
            val skyprefs = SkySharedPref.getInstance(context).myPrefs
            val isRunning = prefs[booleanPreferencesKey("is_running")] ?: BinaryService.isRunning
            val port = prefs[stringPreferencesKey("jtv_go_server_port")] ?:  skyprefs.jtvGoServerPort.toString()
            val serveLocal = prefs[booleanPreferencesKey("serve_local")] ?: skyprefs.serveLocal
            val refresh = prefs[stringPreferencesKey("refresh")] ?: ""
                ServerWidgetContent(context, isRunning, port, serveLocal)
            }
        }
    }

    @SuppressLint("RestrictedApi", "ResourceType")
    @Composable
    private fun ServerWidgetContent(context: Context, isRunning: Boolean, port: String, serveLocal: Boolean) {

        val mode = if (serveLocal) "Local" else "Public"
        val fullUrl = if (serveLocal) "http://localhost:$port" else getFullServerUrl(context)
        val statusColor = if (isRunning) GlanceTheme.colors.primary else GlanceTheme.colors.error
        val accentColor = GlanceTheme.colors.primary

        Column(
            modifier = GlanceModifier
                .wrapContentHeight()
                .themedBackground(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "JTV-Go Server",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(onClick = actionRunCallback<StartAppAction>()),
                    maxLines = 1

                )

                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher_neo),
                    contentDescription = "Logo",
                    modifier = GlanceModifier
                        .size(30.dp)
                        .clickable(onClick = actionRunCallback<StartAppAction>())
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .themedBackground(GlanceTheme.colors.surfaceVariant)
                    .padding(12.dp)
                    .cornerRadius(12.dp)
            ) {
                Text(
                    if (isRunning) "🟢 Running" else "🔴 Stopped",
                    style = TextStyle(color = statusColor),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2.dp))
                Text("Port: $port", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                Text("Mode: $mode", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "🌍 $fullUrl",
                    style = TextStyle(color = accentColor), //, fontSize = 12.sp)
                    modifier = GlanceModifier.clickable(
                        onClick = actionRunCallback<StartChromeAction>()
                    ),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            Row(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxWidth()
            ) {

                Button(
                    text = if (isRunning) "Restart" else "Start",
                    onClick = actionRunCallback<StartServerAction>(),
                    modifier = GlanceModifier
                        .defaultWeight(),
                    maxLines = 1
                )

                Spacer(GlanceModifier.width(8.dp))

                Button(
                    text = "Stop",
                    onClick = actionRunCallback<StopServerAction>(),
                    modifier = GlanceModifier
                        .defaultWeight(),
                    maxLines = 1
                )

            }
        }

    }

    @Composable
    fun GlanceModifier.themedBackground(colorProvider: ColorProvider): GlanceModifier {
        val context = LocalContext.current
        val resolvedColor = colorProvider.getColor(context)
        return this.background(resolvedColor)
    }