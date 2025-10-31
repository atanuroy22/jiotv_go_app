package com.skylake.skytv.jgorunner.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.ui.screens.AppListScreen
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme

class AppListActivity : ComponentActivity() {

    private lateinit var preferenceManager: SkySharedPref

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferenceManager = SkySharedPref.getInstance(this)

        setContent {
            val isDarkMode = preferenceManager.myPrefs.darkMODE
            JGOTheme(themeOverride = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = { AppListTopBar(onBack = { finish() }) }
                    ) { innerPadding ->
                        AppListScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .padding(horizontal = 16.dp),
                            onAppSelected = { selectedApp ->
                                with(preferenceManager.myPrefs) {
                                    iptvAppName = selectedApp.appName
                                    iptvAppPackageName = selectedApp.packageName
                                    iptvAppLaunchActivity = selectedApp.launchActivity
                                }
                                preferenceManager.savePreferences()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Select IPTV Player 📲") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
