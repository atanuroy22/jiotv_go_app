package com.skylake.skytv.jgorunner.ui.tvhome

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skylake.skytv.jgorunner.services.player.PlayerCommandBus

/**
 * Wrapper layout exposing zone UI for switching between JioTV and M3U favourites.
 * Keeps states inside each underlying layout; switching does not reset their internal state
 * because they each manage their own remember() scopes.
 */
@Composable
fun MultiZoneFavouriteLayout(
    context: Context,
    startWithM3U: Boolean
) {
    // Start zone aligns with playlist selection so favourites open on the active source
    var activeZone by remember(startWithM3U) {
        mutableStateOf(if (startWithM3U) "M3U" else "JIO")
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.weight(1f)) {
            when (activeZone) {
                "M3U" -> M3UFavouriteLayout(context) { zone ->
                    if (zone != activeZone) {
                        try {
                            PlayerCommandBus.requestStopPlayback()
                            PlayerCommandBus.requestClosePip()
                        } catch (_: Exception) {}
                    }
                    activeZone = zone
                }
                "MIX" -> MixFavouriteLayout(context) { zone ->
                    if (zone != activeZone) {
                        try {
                            PlayerCommandBus.requestStopPlayback()
                            PlayerCommandBus.requestClosePip()
                        } catch (_: Exception) {}
                    }
                    activeZone = zone
                }
                else -> FavouriteLayout(context) { zone ->
                    if (zone != activeZone) {
                        try {
                            PlayerCommandBus.requestStopPlayback()
                            PlayerCommandBus.requestClosePip()
                        } catch (_: Exception) {}
                    }
                    activeZone = zone
                }
            }
        }
    }
}
