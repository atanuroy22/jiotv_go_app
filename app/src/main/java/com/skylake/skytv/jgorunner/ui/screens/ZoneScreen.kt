package com.skylake.skytv.jgorunner.ui.screens

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.ui.components.ModeSelectionDialog2
import com.skylake.skytv.jgorunner.ui.dev.RecentTabLayout
import com.skylake.skytv.jgorunner.ui.dev.RecentTabLayoutTV
import com.skylake.skytv.jgorunner.ui.dev.SearchTabLayout
import com.skylake.skytv.jgorunner.ui.dev.SearchTabLayoutTV
import com.skylake.skytv.jgorunner.ui.dev.TVTabLayout
import com.skylake.skytv.jgorunner.ui.dev.TVTabLayoutTV
import kotlin.random.Random

@SuppressLint("NewApi")
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ZoneScreen(context: Context, onNavigate: (String) -> Unit) {

//    val activity = context as? androidx.activity.ComponentActivity
//    LaunchedEffect(Unit) {
//        activity?.window?.let { window ->
//            WindowCompat.setDecorFitsSystemWindows(window, false)
//            val controller = window.insetsController
//            controller?.hide(WindowInsets.Type.systemBars())
//            controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        }
//    }

    data class TabItem(val text: String, val icon: ImageVector)
    var showModeDialog by remember { mutableStateOf(false) }
    val tabs = listOf(
        TabItem("TV", Icons.Default.Tv),
        TabItem("Recent", Icons.Default.Star),
        TabItem("Search", Icons.Default.Search)
    )

    val isRemoteNavigation = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    Log.d("ZoneScreen", "Running in TV Mode: $isRemoteNavigation")


    var selectedTabIndex by remember { mutableIntStateOf(1) }
    val tabFocusRequester = remember { FocusRequester() }

    var backPressHandled by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (backPressHandled) {
            onNavigate("Home")
        } else {
            tabFocusRequester.requestFocus()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            backPressHandled = true

            Handler(Looper.getMainLooper()).postDelayed({
                backPressHandled = false
            }, 2000)
        }
    }

    val glowColors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta
    )
    val glowColor = remember { Animatable(glowColors[Random.nextInt(glowColors.size)]) }
    val customFontFamily = FontFamily(Font(R.font.chakrapetch_bold))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { showModeDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = "Filter Icon",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "JTV-GO",
                fontSize = 12.sp,
                fontFamily = customFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
                style = TextStyle(
                    shadow = Shadow(
                        color = glowColor.value,
                        blurRadius = 30f,
                        offset = Offset(0f, 0f)
                    )
                )
            )

            IconButton(
                onClick = {
                    onNavigate("SettingsTV")
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings Icon",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRestorer()
                .focusRequester(tabFocusRequester),
            verticalAlignment = Alignment.CenterVertically
        ) {


            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp) // Add spacing between icon and text
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = "Tab Icon",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(text = tab.text)
                            }
                        }
                    )
                }
            }

        }

        when (selectedTabIndex) {
            0 -> if (isRemoteNavigation) TVTabLayoutTV(context) else TVTabLayout(context)
            1 -> if (isRemoteNavigation) RecentTabLayoutTV(context) else RecentTabLayout(context)
            2 -> if (isRemoteNavigation) SearchTabLayoutTV(context, tabFocusRequester) else SearchTabLayout(context, tabFocusRequester)
        }
    }

    ModeSelectionDialog2(
        showDialog = showModeDialog,
        onDismiss = { showModeDialog = false },
        onReset = {
            showModeDialog = false
            Handler(Looper.getMainLooper()).postDelayed({
                selectedTabIndex = 0
            }, 100)
            selectedTabIndex = 1
            Toast.makeText(context, "Refreshing Channels", Toast.LENGTH_LONG).show()
                  },
        onSelectionsMade = { selectedQualities, selectedCategories, _, selectedLanguages, _ ->

            Log.d("ZoneScreen","Qualities: $selectedQualities, Categories: $selectedCategories, Languages: $selectedLanguages")
            Toast.makeText(context, "Refreshing Channels", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({
                selectedTabIndex = 0
            }, 100)
            selectedTabIndex = 1
        },
        context = context
    )


    LaunchedEffect(Unit) {
        tabFocusRequester.requestFocus()
        Handler(Looper.getMainLooper()).postDelayed({
            selectedTabIndex = 0
        }, 100)
    }
}

