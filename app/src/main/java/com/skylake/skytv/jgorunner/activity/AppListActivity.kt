package com.skylake.skytv.jgorunner.activity

import android.R
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.launch

class AppListActivity : ComponentActivity() {

    private lateinit var preferenceManager: SkySharedPref

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = SkySharedPref(this)

        setContent {
            AppListScreen(
                onAppSelected = { selectedApp ->
                    preferenceManager.setKey("app_name", selectedApp.appName)
                    preferenceManager.setKey("app_packagename", selectedApp.packageName)
                    preferenceManager.setKey("app_launch_activity", selectedApp.launchActivity)
                    finish()
                }
            )
        }
    }
}

@Composable
fun AppListScreen(onAppSelected: (AppInfo) -> Unit) {
    val context = LocalContext.current
    val apps = remember { mutableStateListOf<AppInfo>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            apps.addAll(getInstalledApps(context))
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(apps) { app ->
            AppListItem(appInfo = app, onAppSelected = onAppSelected)
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
        }
    }
}

@Composable
fun AppListItem(appInfo: AppInfo, onAppSelected: (AppInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onAppSelected(appInfo) }
    ) {
        val drawable = appInfo.icon
        val bitmap = drawable.toBitmap().asImageBitmap()

        Image(
            bitmap = bitmap,
            contentDescription = "App Icon",
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(text = appInfo.appName, style = MaterialTheme.typography.bodyMedium)
    }
}

data class AppInfo(
    val appName: String,
    val icon: Drawable,
    val packageName: String,
    val launchActivity: String
)

fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager: PackageManager = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(mainIntent, 0)
    val apps = mutableListOf<AppInfo>()

    val noneOption = AppInfo(
        appName = "No IPTV",
        icon = context.getDrawable(R.drawable.ic_menu_close_clear_cancel)!!,
        packageName = "",
        launchActivity = ""
    )
    apps.add(noneOption)

    val webOption = AppInfo(
        appName = "WEB TV",
        icon = context.getDrawable(com.skylake.skytv.jgorunner.R.mipmap.ic_launcher_neo)!!,
        packageName = "webtv",
        launchActivity = ""
    )
    apps.add(webOption)

    for (info in resolveInfos) {
        val appInfo: ApplicationInfo = info.activityInfo.applicationInfo
        val appName = appInfo.loadLabel(packageManager).toString()
        val appIcon = appInfo.loadIcon(packageManager)
        val packageName = appInfo.packageName
        val launchActivity = info.activityInfo.name

        apps.add(AppInfo(appName, appIcon, packageName, launchActivity))
    }
    return apps
}
