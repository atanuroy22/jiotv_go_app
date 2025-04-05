package com.skylake.skytv.jgorunner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skylake.skytv.jgorunner.data.SkySharedPref
import java.io.File
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.io.FileOutputStream


@Composable
fun InfoScreen(context: Context) {
    val preferenceManager = SkySharedPref.getInstance(context)
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ipAddress = getIpAddress(wifiManager)
    val appVersion = getAppVersion(context)
    val binVersion = preferenceManager.myPrefs.jtvGoBinaryVersion
    val androidVersion = Build.VERSION.RELEASE
    val sdkVersion = Build.VERSION.SDK_INT
    val architecture = System.getProperty("os.arch") ?: "Unknown"
    val totalStorage = getTotalStorageSpace(context)
    val dateTime = getCurrentDateTime()

    // Creating a formatted device information string
    val deviceInfo = """
        JTV-GO Version: $appVersion
        Binary Version: $binVersion
        IP Address: $ipAddress
        Android Version: $androidVersion
        SDK Version: $sdkVersion
        Architecture: $architecture
        Storage Space: $totalStorage GB
        Date and Time: $dateTime
    """.trimIndent()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "### JTV-GO Server ###",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "### Limited Test Version ###",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Device Info Section
        Text(text = deviceInfo, fontSize = 16.sp, modifier = Modifier.padding(16.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

//        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val allInfo = buildString {
                    append(deviceInfo)
                }

                copyToClipboard(context, allInfo)
                Toast.makeText(context, "Information copied to clipboard", Toast.LENGTH_SHORT)
                    .show()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Copy")
        }
    }
}

// Function to get the app version
fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unavailable"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unavailable"
    }
}

// com.skylake.skytv.jgorunner.ui.dev.Helper function to get the IP address from WifiManager
fun getIpAddress(wifiManager: WifiManager): String {
    @Suppress("deprecation")
    val ip = wifiManager.connectionInfo.ipAddress
    return if (ip != 0) {
        InetAddress.getByAddress(
            byteArrayOf(
                (ip and 0xff).toByte(),
                (ip shr 8 and 0xff).toByte(),
                (ip shr 16 and 0xff).toByte(),
                (ip shr 24 and 0xff).toByte()
            )
        ).hostAddress ?: "Unavailable"
    } else {
        "Unavailable"
    }
}

// com.skylake.skytv.jgorunner.ui.dev.Helper function to get the total storage space
fun getTotalStorageSpace(context: Context): Long {
    val stat = android.os.StatFs(context.filesDir.absolutePath)
    val bytesAvailable = stat.blockSizeLong * stat.blockCountLong
    return bytesAvailable / (1024 * 1024 * 1024) // Convert to GB
}

// com.skylake.skytv.jgorunner.ui.dev.Helper function to get the current date and time
fun getCurrentDateTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}

// com.skylake.skytv.jgorunner.ui.dev.Helper function to copy text to clipboard
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Device Info", text)
    clipboard.setPrimaryClip(clip)
}
