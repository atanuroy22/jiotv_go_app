package com.skylake.skytv.jgorunner

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ExitToApp
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.twotone.Deblur
import androidx.compose.material.icons.twotone.LiveTv
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.ResetTv
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.skylake.skytv.jgorunner.activity.DebugScreen
import com.skylake.skytv.jgorunner.activity.InfoScreen
import com.skylake.skytv.jgorunner.activity.LoginScreen
import com.skylake.skytv.jgorunner.activity.RunnerScreen
import com.skylake.skytv.jgorunner.activity.SettingsScreen
import com.skylake.skytv.jgorunner.activity.WebPlayerActivity
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.data.applyConfigurations
import com.skylake.skytv.jgorunner.data.jsonmaker
import com.skylake.skytv.jgorunner.services.BinaryExecutor
import com.skylake.skytv.jgorunner.services.BinaryService
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme
import com.skylake.skytv.jgorunner.utils.RemoteBinaryFetcher
import com.skylake.skytv.jgorunner.utils.ConfigHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    private var selectedBinaryUri: Uri? = null
    private var isBinaryRunning by mutableStateOf(false)
    private var isOutputShowing by mutableStateOf(false)
    private var selectedBinaryName by mutableStateOf("JTV-GO SERVER")

    // SharedPreferences for saving binary selection
    private lateinit var preferenceManager: SkySharedPref

    private val REQUEST_STORAGE_PERMISSION = 100

    private val customFontFamily = FontFamily(Font(R.font.chakrapetch_bold))

    // Receiver to handle binary stop action
    private val binaryStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BinaryService.ACTION_BINARY_STOPPED) {
                isBinaryRunning = false
                outputText = "Server stopped"
            }
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

        if (readGranted) {
            Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission is required to run the binary", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false

        if (notificationGranted) {
            //Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission is required to show alerts", Toast.LENGTH_SHORT).show()
        }
    }

    // To request the permission, you can call this function
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermissions() {
        notificationPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }



    private var outputText by mutableStateOf("ℹ️ Output logs")
    private var currentScreen by mutableStateOf("Home") // Manage current screen
    private lateinit var backPressedCallback: OnBackPressedCallback

    private val executor = Executors.newSingleThreadExecutor()

    private var showCustUpdatePopup by mutableStateOf(false)
    private var showCustENDPopup by mutableStateOf(false)
    private var showAutoUpdatePopup by mutableStateOf(false)
    private var showLoginPopup by mutableStateOf(false)
    private var isServerLoginRunning by mutableStateOf(false)
    private var isServerRunning by mutableStateOf(false)

    private var isGlowBox by mutableStateOf(true)


    private var showPARAM by mutableStateOf(false)

    private var showRedirectPopup by mutableStateOf(false)
    private var shouldLaunchIPTV by mutableStateOf(false)
    private var countdownJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferenceManager = SkySharedPref(this)

        checkStoragePermission()

        val overlayRequestCounter: Int = preferenceManager.getKey("REQcounter")?.toIntOrNull() ?: 0
        fun checkOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (overlayRequestCounter < 2) {
                    requestOverlayPermission()
                    preferenceManager.setKey("REQcounter", (overlayRequestCounter + 1).toString())
                } else {
                    Log.d("MainActivity", "Overlay permission request skipped")
                }
            }
        }

        checkOverlayPermission()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissions()
        }


        val preferenceManager = SkySharedPref(this)
        applyConfigurations(this, preferenceManager)

        val savedUriString = preferenceManager.getKey("selectedBinaryUri")
        val savedName = preferenceManager.getKey("selectedBinaryName")

        selectedBinaryUri = savedUriString?.let { Uri.parse(it) }
        selectedBinaryName = savedName ?: "JTV-GO SERVER"

        val selectBinaryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedBinaryUri = result.data?.data
                selectedBinaryName =
                    selectedBinaryUri?.path?.substringAfterLast('/') ?: "Unknown file"

                preferenceManager.setKey("selectedBinaryUri", selectedBinaryUri.toString())
                preferenceManager.setKey("selectedBinaryName", selectedBinaryName)
            }
        }

//        registerReceiver(binaryStoppedReceiver, IntentFilter(BinaryService.ACTION_BINARY_STOPPED))


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(binaryStoppedReceiver, IntentFilter(BinaryService.ACTION_BINARY_STOPPED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(binaryStoppedReceiver, IntentFilter(BinaryService.ACTION_BINARY_STOPPED))
        }


        // Register the OnBackPressedCallback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    "Settings" -> {
                        currentScreen = "Home"
                    }
                    "Debug" -> {
                        currentScreen = "Home"
                    }
                    "Info" -> {
                        currentScreen = "Debug"
                    }
                    "Runner" -> {
                        currentScreen = "Debug"
                    }
                    "Login" -> {
                        currentScreen = "Debug"
                    }
                    else -> {
                        // Let the system handle the back press
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }

            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)


        fun updateSizeIfExist(context: Context, fileName: String) {
            val fileDir = context.filesDir
            val file = File(fileDir, fileName)

            if (file.exists()) {
                val fileSize = file.length()
                Log.d("DIX", "File size of $fileName: $fileSize bytes")
                preferenceManager.setKey("expectedFileSize", fileSize.toString())

            } else {
                Log.e("DIX", "File not found: $fileName")
                preferenceManager.setKey("expectedFileSize", "69")
//                try {
//                    context.resources.openRawResource(R.raw.majorbin).use { `in` ->
//                        FileOutputStream(file).use { out ->
//                            val buffer = ByteArray(1024)
//                            var bytesRead: Int
//                            while ((`in`.read(buffer).also { bytesRead = it }) != -1) {
//                                out.write(buffer, 0, bytesRead)
//                            }
//                        }
//                    }
//                } catch (e: java.lang.Exception) {
//                    Log.e("DIX", "Error copying binary from resources: ", e)
//                }
            }
        }

        val isCheckForUpdate = preferenceManager.getKey("isCheckForUpdate")

        if (isCheckForUpdate.isNullOrEmpty() || isCheckForUpdate == "Yes") {
            updateSizeIfExist(this@MainActivity,"majorbin")


            val expectedFileSize: Int? = preferenceManager.getKey("expectedFileSize")?.toIntOrNull()
            RemoteBinaryFetcher.isFileSizeSame(expectedFileSize) { result ->
                if (result) {
                    if (expectedFileSize == 69){
                        isOutputShowing = true
                        RemoteBinaryFetcher.startDownloadAndSave(this@MainActivity) { output ->
                            Handler(Looper.getMainLooper()).post {
                                outputText += "\n$output"
                            }
                        }
                    } else {
                        showAutoUpdatePopup = true
                    }

                }
            }
        } else {
           Log.d("DIX","Auto Update OFF")
        }




        setContent {
            JGOTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavigationBar(selectBinaryLauncher) },
//                    floatingActionButton = {
//                        FloatingActionButton(onClick = {  }) {
//                            Icon(Icons.Default.Add, contentDescription = "Add")
//                        }
//                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            "Home" -> HomeScreen(
                                selectedBinaryName = selectedBinaryName,
                                selectedBinaryUri = selectedBinaryUri,
                                isBinaryRunning = isBinaryRunning,
                                outputText = outputText,
                                selectBinaryLauncher = selectBinaryLauncher,
                                onRunBinary = { uri, arguments ->
                                    val intent = Intent(this@MainActivity, BinaryService::class.java).apply {
                                        putExtra("binaryUri", uri.toString())
                                        putExtra("arguments", arguments)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(intent)
                                    } else {
                                        startService(intent)
                                    }

                                    outputText = "Server is running in the background"
                                    isBinaryRunning = true

                                    BinaryExecutor.executeBinary(
                                        this@MainActivity,
                                        arguments
                                    ) { output ->
                                        outputText += "\n$output"
                                    }
                                },
                                onStopBinary = {
                                    //BinaryExecutor.stopBinary()
                                    val intent = Intent(this@MainActivity, BinaryService::class.java)
                                    intent.action = BinaryService.ACTION_STOP_BINARY
                                    startService(intent)
                                    outputText = "Server stopped"
                                    isBinaryRunning = false
                                }
                            )
                            "Settings" -> SettingsScreen(context = this@MainActivity)
                            "Info" -> InfoScreen(context = this@MainActivity)
                            "Debug" -> DebugScreen(context = this@MainActivity, onNavigate = { title -> currentScreen = title })
                            "Runner" -> RunnerScreen(context = this@MainActivity)
                            "Login" -> LoginScreen(context = this@MainActivity)
                        }


                        val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 5000
                        val countdownTimeF = savedIPTVRedirectTime / 1000

                        // Show the redirect popup
                        RedirectPopup(
                            isVisible = showRedirectPopup,
                            countdownTime = countdownTimeF,
                            context = this@MainActivity,
                            onDismiss = {
                                showRedirectPopup = false
                                shouldLaunchIPTV = false // Cancel IPTV launch if dismissed
                            }
                        )

//                        CustPopup(
//                            isVisible = showCustUpdatePopup,
//                            xtitle ="Update Available",
//                            xsubtitle="A new version of the app is available. Please update to the latest version for improved features and performance.",
//                            xokbtn="Update!",
//                            xendbtn="Dismiss",
//                            onOk = { showCustUpdatePopup = false },
//                            onDismiss = { showCustUpdatePopup = false }
//                        )

                        val isUpdate_xtitle = preferenceManager.getKey("isUpdate_xtitle")?.takeIf { it.isNotEmpty() } ?: "WARNING"
                        val isUpdate_xsubtitle = preferenceManager.getKey("isUpdate_xsubtitle")?.takeIf { it.isNotEmpty() } ?: "If you are seeing this message, it indicates an error has occurred."
                        val isUpdate_xokbtn = preferenceManager.getKey("isUpdate_xokbtn")?.takeIf { it.isNotEmpty() } ?: "OK"

                        val isEngine_xtitle = preferenceManager.getKey("isEngine_xtitle")?.takeIf { it.isNotEmpty() } ?: "WARNING"
                        val isEngine_xsubtitle = preferenceManager.getKey("isEngine_xsubtitle")?.takeIf { it.isNotEmpty() } ?: "If you are seeing this message, it indicates an error has occurred."
                        val isEngine_xokbtn = preferenceManager.getKey("isEngine_xokbtn")?.takeIf { it.isNotEmpty() } ?: "OK"



                        CustPopup(
                            isVisible = showCustUpdatePopup,
                            xtitle = isUpdate_xtitle,
                            xsubtitle=isUpdate_xsubtitle,
                            xokbtn=isUpdate_xokbtn,
                            xendbtn="Dismiss",
                            onOk = { showCustUpdatePopup = false },
                            onDismiss = { showCustUpdatePopup = false }
                        )

                        CustPopup(
                            isVisible = showCustENDPopup,
                            xtitle = isEngine_xtitle,
                            xsubtitle= isEngine_xsubtitle,
                            xokbtn= isEngine_xokbtn,
                            xendbtn = "Dismiss",
                            onOk = {
                                showCustENDPopup = false
                                finishAffinity()
                                return@CustPopup
                                   },
                            onDismiss = {
                                showCustENDPopup = false
                                finishAffinity()
                                return@CustPopup
                            }
                        )

                        CustPopup(
                            isVisible = showLoginPopup,
                            xtitle = "Login Required",
                            xsubtitle = "Please log in using WebTV, to access the server.",
                            xokbtn = "Login",
                            xendbtn = "Cancel",
                            onOk = {
                                showLoginPopup = false
                                val intent = Intent(this@MainActivity, WebPlayerActivity::class.java)
                                startActivity(intent)
                                Toast.makeText(this@MainActivity, "Opening WEBTV", Toast.LENGTH_SHORT).show()
                                Log.d("DIX", "Opening WEBTV")
                                return@CustPopup
                            },
                            onDismiss = {
                                showLoginPopup = false
                                return@CustPopup
                            }
                        )


                        CustPopup(
                            isVisible = showAutoUpdatePopup,
                            xtitle = "Binary Update Available",
                            xsubtitle = "A new version of the Binary is available.",
                            xokbtn = "Update!",
                            xendbtn = "Dismiss",
                            onOk = {
                                showAutoUpdatePopup = false
                                preferenceManager.setKey("expectedFileSize", "0")
                                isOutputShowing = true
                                RemoteBinaryFetcher.startDownloadAndSave(this@MainActivity
                                ) { output ->
                                    runOnUiThread {
                                            outputText += "\n$output"
                                    }
                                }
                            },
                            onDismiss = {
                                showAutoUpdatePopup = false
                                return@CustPopup
                            }
                        )

                        CustPopup(
                            isVisible = showPARAM,
                            xtitle = "Request Permission",
                            xsubtitle= "Draw over other apps permission is required for the app to function properly.",
                            xokbtn= "Grant",
                            xendbtn = "Dismiss",
                            onOk = {
                                showPARAM = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                }
                            },
                            onDismiss = {
                                showPARAM = false
                                Toast.makeText(this@MainActivity, "Permission is required to continue", Toast.LENGTH_SHORT).show()
                                return@CustPopup
                            }
                        )


                    }
                }

//                // Handle delayed IPTV launch
//                LaunchedEffect(shouldLaunchIPTV) {
//                    if (shouldLaunchIPTV) {
//                        val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 5000
//                        delay(savedIPTVRedirectTime.toLong())
//                        startIPTV()
//                    }
//                }

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backPressedCallback.remove()
        unregisterReceiver(binaryStoppedReceiver)
    }



    override fun onStart() {
        super.onStart()

        val wifiIpAddress = getWifiIpAddress(this)

        val nekoFirstTime = preferenceManager.getKey("nekoFirstTime")

        if (nekoFirstTime.isNullOrEmpty()) {
            preferenceManager.setKey("nekoFirstTime", "Done")
            Log.d("MainActivity", "nekoFirstTime was empty or not available, set to Done.")

            val arch = System.getProperty("os.arch")
            preferenceManager.setKey("ARCHx", arch)

            // Set default values
            preferenceManager.setKey("isFlagSetForLOCAL", "Yes") // Reverse
            preferenceManager.setKey("isFlagSetForAutoStartServer", "No")
            preferenceManager.setKey("isFlagSetForAutoStartOnBoot", "No")
            preferenceManager.setKey("isFlagSetForAutoBootIPTV", "Yes")
            preferenceManager.setKey("app_packagename", "")
            preferenceManager.setKey("isFlagSetForEPG", "No")
            preferenceManager.setKey("app_name", "")
            preferenceManager.setKey("isFlagSetForAutoIPTV", "No")
            preferenceManager.setKey("app_launch_activity", "")
            preferenceManager.setKey("isCustomSetForPORT", "5350")
            preferenceManager.setKey("__Port", " --port 5350")
            preferenceManager.setKey("__Public", " --public")
            preferenceManager.setKey("__EPG", " --config configtv.json")
            preferenceManager.setKey("BinaryFileName", "majorbin")
            preferenceManager.setKey("isAutoUpdate", "No")
            preferenceManager.setKey("VersionNumber", "v3.11")
            preferenceManager.setKey("expectedFileSize", "69")
            preferenceManager.setKey("isCheckForUpdate", "Yes")

        }

        // Use the utility function to fetch and save config
        ConfigHandler.fetchAndSaveConfig(this)

        val isUpdate = preferenceManager.getKey("isUpdate")
        val isEngine = preferenceManager.getKey("isEngine")


//        Debug Values
//        val isUpdate = "Yes"
//        val isEngine = "2"


        if (isUpdate == "Yes") {
            showCustUpdatePopup = true
            Toast.makeText(this@MainActivity, "Update Available please update", Toast.LENGTH_SHORT).show()
        }

        if (isEngine?.toIntOrNull()?.let { it > 1 } == true) {
            showCustENDPopup = true
            Toast.makeText(this@MainActivity, "App support period ended.", Toast.LENGTH_SHORT).show()
        }


        fun checkAndRun() {
            val externalStoragePath = Environment.getExternalStorageDirectory().path
            val folder = File(externalStoragePath, ".jiotv_go")
            val jsonFile = File(folder, "jiotv-config.json")

            if (jsonFile.exists()) {
                Log.d("DIX","Config exist")
            } else {
                jsonmaker(this@MainActivity);
            }
        }

        checkAndRun()


        // Check if server should start automatically
        val isFlagSetForAutoStartServer = preferenceManager.getKey("isFlagSetForAutoStartServer")
        if (isFlagSetForAutoStartServer == "Yes") {
            Log.d("DIX-AutoStartServer", "Starting server automatically")

            val uriToUse = selectedBinaryUri
                ?: Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin")
            val arguments = emptyArray<String>()

            startServer(uriToUse, arguments)
        }

        val isFlagSetForAutoIPTV = preferenceManager.getKey("isFlagSetForAutoIPTV")

//        val isServerRunning = checkServerStatus()

        Log.i("DIX::","UP")
        checkServerStatusInCoroutine()
        Log.i("DIX::","DOWN")




//        if (isFlagSetForAutoIPTV == "Yes" && isServerLoginRunning) {
//            showRedirectPopup = true
//            shouldLaunchIPTV = true
//
//            countdownJob?.cancel() // Cancel any existing countdown job
//            val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 4000
//            var countdownTime = savedIPTVRedirectTime / 1000
//            countdownJob = CoroutineScope(Dispatchers.Main).launch {
//                while (countdownTime > 0) {
//                    delay(1000)
//                    countdownTime--
//                }
//                if (shouldLaunchIPTV) {
//                    startIPTV()
//                }
//                showRedirectPopup = false
//            }
//        }
    }


    private fun startServer(uri: Uri, arguments: Array<String>) {
        val intent = Intent(this, BinaryService::class.java).apply {
            putExtra("binaryUri", uri.toString())
            putExtra("arguments", arguments)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        outputText = "Server is running in the background"
        isBinaryRunning = true

        BinaryExecutor.executeBinary(this, arguments) { output ->
            outputText += "\n$output"
        }
    }

    private fun checkServerStatusInCoroutine() {
        CoroutineScope(Dispatchers.IO).launch {
            val isServerRunning = checkServerStatus()
            if (isServerRunning) {
                checkLoginStatusInCoroutine()
            } else {
                Log.w("DIXxx", "Server is not expected to be running.")
            }
            withContext(Dispatchers.Main) {
                Log.d("DIXx", "Is server running: $isServerRunning")
            }
        }
    }

    private fun checkLoginStatusInCoroutine() {
        CoroutineScope(Dispatchers.IO).launch {
            val statusCode = checkServerLoginStatus()
            withContext(Dispatchers.Main) {
                Log.d("DIX", "Server login status code: $statusCode")
                when (statusCode) {
                    200 -> {
                        Log.i("DIX", "Server is online, login successful.")
                        isGlowBox = false
                        isServerLoginRunning = true
                        val isFlagSetForAutoIPTV = preferenceManager.getKey("isFlagSetForAutoIPTV")
                        if (isFlagSetForAutoIPTV == "Yes" && isServerLoginRunning) {
                            showRedirectPopup = true
                            shouldLaunchIPTV = true

                            countdownJob?.cancel() // Cancel any existing countdown job
                            val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 4000
                            var countdownTime = savedIPTVRedirectTime / 1000
                            countdownJob = CoroutineScope(Dispatchers.Main).launch {
                                while (countdownTime > 0) {
                                    delay(1000)
                                    countdownTime--
                                }
                                if (shouldLaunchIPTV) {
                                    startIPTV()
                                }
                                showRedirectPopup = false
                            }
                        }
                    }
                    500 -> {
                        Log.e("DIX", "Server returned an internal error (500).")
                        showLoginPopup = true
                    }
                    0 -> {
                        Log.e("DIX", "Error checking server status or server is down.")
                    }
                    else -> {
                        Log.e("DIX", "Unexpected return value: $statusCode")
                    }
                }
            }
        }
    }

    private fun startIPTV() {
        executor.execute {
            try {
                runOnUiThread {
                        val appPackageName = preferenceManager.getKey("app_packagename")
                        if (appPackageName.isNotEmpty()) {
                            Log.d("DIX", appPackageName)
                            val appName = preferenceManager.getKey("app_name")

                            if (appPackageName == "webtv") {
                                val intent = Intent(this@MainActivity, WebPlayerActivity::class.java)
                                startActivity(intent)
                                Toast.makeText(this@MainActivity, "Opening WEBTV", Toast.LENGTH_SHORT).show()
                                Log.d("DIX", "Opening WEBTV")
                            } else if (appPackageName.isNotEmpty() && appName.isNotEmpty()) {
                                val launchIntent = packageManager.getLaunchIntentForPackage(appPackageName)
                                launchIntent?.let {
                                    startActivity(it)
                                    Toast.makeText(this@MainActivity, "Opening $appName", Toast.LENGTH_SHORT).show()
                                    Log.d("DIX", "Opening $appName")
                                } ?: run {
                                    Toast.makeText(this@MainActivity, "Cannot find the specified application", Toast.LENGTH_SHORT).show()
                                    Log.d("DIX", "Cannot find the specified application")
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "No application details found", Toast.LENGTH_SHORT).show()
                                Log.d("DIX", "No application details found")
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "IPTV not set.", Toast.LENGTH_SHORT).show()
                            Log.d("DIX", "IPTV not set")
                        }
                }
            } catch (e: Exception) {
                Log.e("DIX", "Error starting IPTV", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error starting IPTV", Toast.LENGTH_SHORT).show()
                    Log.d("DIX", "Error starting IPTV: ${e.message}")
                }
            }
        }

    }


    private fun getWifiIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // CODE TWISTER XD
        val isPublic = preferenceManager.getKey("isFlagSetForLOCAL")
        if (isPublic != "Yes") {
            val savedPortNumber = preferenceManager.getKey("isCustomSetForPORT")?.toIntOrNull() ?: 5350
            return "http://localhost:$savedPortNumber/playlist.m3u"
        }


        val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork
        } else {
            val networks = connectivityManager.allNetworks
            if (networks.isNotEmpty()) networks[0] else null
        }

        if (activeNetwork != null) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
                val ipAddresses = linkProperties?.linkAddresses
                    ?.filter { it.address is Inet4Address } // Filter for IPv4 addresses
                    ?.map { it.address.hostAddress }
                val ipAddress = ipAddresses?.firstOrNull() // Get the first IPv4 address

                if (ipAddress != null) {
                    val savedPortNumber = preferenceManager.getKey("isCustomSetForPORT")?.toIntOrNull() ?: 5350
                    return "http://$ipAddress:$savedPortNumber/playlist.m3u"
                }
            }
        }
        return null // Return null if not connected to Wi-Fi or no valid IP is found
    }





    @Composable
    fun RedirectPopup(
        isVisible: Boolean,
        countdownTime: Int,
        onDismiss: () -> Unit,
        context: Context // Add context as a parameter to access PackageManager
    ) {
        var currentTime by remember { mutableIntStateOf(countdownTime) }
        val appIPTV = preferenceManager.getKey("app_name") ?: "IPTV app"
        val appIPTVpkg = preferenceManager.getKey("app_packagename")

        // Retrieve the app icon from the package name
        val appIcon = remember {
            try {
                val pm = context.packageManager
                pm.getApplicationIcon(appIPTVpkg ?: "")
            } catch (e: Exception) {
                null
            }
        }

        if (isVisible) {
            Log.d("RedirectPopup", "Popup is visible.")

            // Countdown logic
            LaunchedEffect(isVisible) {
                currentTime = countdownTime
                while (currentTime > 0) {
                    delay(1000)
                    currentTime -= 1
                }
                onDismiss()
            }

            // Log current time
            Log.d("RedirectPopup", "Current countdown: $currentTime")

            AlertDialog(
                onDismissRequest = { onDismiss() },
//                title = {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Icon(
//                            imageVector = Icons.Filled.Flare,
//                            contentDescription = "Timer Icon",
//                            modifier = Modifier.padding(end = 8.dp)
//                        )
//                        Text("Redirecting")
//                    }
//                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        appIcon?.let {
                            Image(
                                bitmap = it.toBitmap().asImageBitmap(),
                                contentDescription = "App Icon",
                                modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                            )
                        }

                        Text(
                            text = "Redirecting to $appIPTV",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = "$currentTime seconds remaining",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.alpha(if (currentTime % 2 == 0) 1f else 0.7f) // subtle flicker effect
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Dismiss")
                    }
                },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            )
        } else {
            Log.d("RedirectPopup", "Popup is NOT visible.")
        }
    }






    @Composable
    fun CustPopup(isVisible: Boolean, xtitle: String, xsubtitle: String, xokbtn: String, xendbtn: String, onOk: () -> Unit, onDismiss: () -> Unit) {
        if (isVisible) {
            Log.d("CustPopup", "Popup is visible.")

            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text(xtitle) },
                text = { Text(xsubtitle) },
                confirmButton = {
                    Button(onClick = { onOk() }) {
                        Text(xokbtn)
                    }
                },
                dismissButton = {
                    Button(onClick = { onDismiss() }) {
                        Text(xendbtn)
                    }
                },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            )
        } else {
            Log.d("CustPopup", "CustPopup is NOT visible.")
        }
    }



    private suspend fun checkServerStatus(): Boolean {
        val savedPortNumber = preferenceManager.getKey("isCustomSetForPORT")?.toIntOrNull() ?: 5350
        val url = URL("http://localhost:$savedPortNumber")
        Log.d("ServerStatusChecker", "Constructed URL: $url")

        repeat(5) { attempt ->  // Change from 3 to 5 attempts
            try {
                Log.d("ServerStatusChecker", "Attempt ${attempt + 1} to check server status")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000 // 5 seconds timeout

                Log.d("ServerStatusChecker", "Connection established. Requesting server headers...")

                val responseCode = connection.responseCode
                Log.d("ServerStatusChecker", "Received response code: $responseCode")

                connection.disconnect()

                // If we get a successful response, return true
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("ServerStatusChecker", "Server is up and running (HTTP_OK response).")
                    return true
                } else {
                    Log.d("ServerStatusChecker", "Unexpected response code: $responseCode. Server might be down.")
                }
            } catch (e: Exception) {
                Log.d("ServerStatusChecker", "Error occurred during attempt ${attempt + 1}")
                Log.e("ServerStatusChecker", "Error checking server status (attempt ${attempt + 1})", e)
            }

            // Only introduce a delay if an error occurs
            if (attempt < 4) { // Change to 4 to avoid sleeping after the last attempt
                Log.d("ServerStatusChecker", "Retrying after 1 second delay (attempt ${attempt + 1})")
                delay(1000) // Use delay instead of Thread.sleep
            }
        }

        // If all attempts fail, return false
        Log.d("ServerStatusChecker", "All 5 attempts failed. Server is not reachable.")
        return false
    }





    private suspend fun checkServerLoginStatus(): Int {
        val savedPortNumber = preferenceManager.getKey("isCustomSetForPORT")?.toIntOrNull() ?: 5350
        val url = URL("http://localhost:$savedPortNumber/live/144.m3u8")
        var consecutiveFailures = 0 // Counter for consecutive failures
        val maxFailures = 5 // Maximum allowed consecutive failures

        repeat(5) { attempt ->
            try {
                Log.d("ServerLoginCheck", "Attempt #${attempt + 1}: Checking server status...")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000 // 5 seconds timeout
                connection.readTimeout = 5000 // 5 seconds timeout
                val responseCode = connection.responseCode
                connection.disconnect()

                return when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        Log.d("ServerLoginCheck", "Response Code: $responseCode - Server is up!")
                        consecutiveFailures = 0 // Reset on success
                        200
                    }
                    HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                        Log.d("ServerLoginCheck", "Response Code: $responseCode - Server error!")
                        consecutiveFailures = 0 // Reset on success
                        500
                    }
                    else -> {
                        consecutiveFailures++ // Increment failure counter
                        Log.d("ServerLoginCheck", "Unexpected Response Code: $responseCode")
                        if (consecutiveFailures >= maxFailures) {
                            Log.d("ServerLoginCheck", "Reached maximum consecutive failures.")
                            return 0 // Stop checking after 5 failures
                        }
                        0
                    }
                }
            } catch (e: Exception) {
                Log.d("ServerLoginCheck", "Attempt #${attempt + 1}: Error occurred")
                Log.e("ServerLoginCheck", "Error checking server status (attempt ${attempt + 1})", e)
                consecutiveFailures++ // Increment failure counter
                if (consecutiveFailures >= maxFailures) {
                    Log.d("ServerLoginCheck", "Reached maximum consecutive failures.")
                    return 0 // Stop checking after 5 failures
                }
            }

            // Only introduce a delay if an error occurs
            if (attempt < 2) { // Avoid sleeping after the last attempt
                Log.d("ServerLoginCheck", "Delaying before next attempt...")
                delay(1000) // Use delay instead of Thread.sleep
            }
        }

        // If all attempts fail, return 0
        return 0
    }





    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!isPermissionGranted) {
                storagePermissionLauncher.launch(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            }
        }
    }

    @Composable
    fun BottomNavigationBar(selectBinaryLauncher: ActivityResultLauncher<Intent>) {
        val items = listOf(
            BottomNavigationItem(
                title = "Home",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                hasNews = false,
            ),
            BottomNavigationItem(
                title = "Settings",
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                hasNews = false,
            ),
            BottomNavigationItem(
                title = "Debug",
                selectedIcon = Icons.Filled.DeveloperMode,
                unselectedIcon = Icons.Outlined.DeveloperMode,
                hasNews = false,
            ),
        )
        var selectedItemIndex by rememberSaveable { mutableStateOf(0) }

        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedItemIndex == index,
                    onClick = {
                        selectedItemIndex = index
                        currentScreen = item.title
//                        if (index == 1) {
//                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//                                type = "*/*"
//                                addCategory(Intent.CATEGORY_OPENABLE)
//                            }
//                            selectBinaryLauncher.launch(intent)
//                        }
                    },
                    label = {
                        Text(text = item.title)
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (item.hasNews) {
                                    Badge()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedItemIndex == index) {
                                    item.selectedIcon
                                } else {
                                    item.unselectedIcon
                                },
                                contentDescription = item.title
                            )
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun HomeScreen(
        selectedBinaryName: String,
        selectedBinaryUri: Uri?,
        isBinaryRunning: Boolean,
        outputText: String,
        selectBinaryLauncher: ActivityResultLauncher<Intent>,
        onRunBinary: (Uri, Array<String>) -> Unit,
        onStopBinary: () -> Unit
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
//            Text(
//                text = selectedBinaryName,
//                fontSize = 24.sp,
//                fontFamily = customFontFamily,
//                color = MaterialTheme.colorScheme.onBackground
//            )
            Text(
                text = selectedBinaryName,
                fontSize = 24.sp,
                fontFamily = customFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
                style = if (isGlowBox) {
                    TextStyle.Default
                } else {
                    TextStyle(
                        shadow = Shadow(
                            color = Color.Green,
                            blurRadius = 30f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f)
                        )
                    )
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

//            Text(text = "✨")
//
//            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .background(
                        if (isGlowBox) MaterialTheme.colorScheme.errorContainer else  Color(0xFFA5D6A7),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(5.dp)
            ) {
                val wifiIpAddress = getWifiIpAddress(this@MainActivity) ?: "Not connected to Wi-Fi"
                val clipboardManager = LocalClipboardManager.current

                Text(
                    text = wifiIpAddress,
                    fontSize = 16.sp,
                    color = if (isGlowBox) MaterialTheme.colorScheme.onSurface else  Color.Black,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(wifiIpAddress))
                        }
                )
            }



            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button1(selectedBinaryUri)
                Button2()
            }

            Spacer(modifier = Modifier.height(4.dp)) // Space between rows

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button3()
                Button4()
                Button5()
                Button6()
            }


            Spacer(modifier = Modifier.height(16.dp))


            Column (
                modifier = Modifier.alpha(if (isBinaryRunning || isOutputShowing) 1f else 0f)
            ) {
                Text(
                    text = outputText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.Black)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }


    @Composable
    fun RowScope.Button1(selectedBinaryUri: Uri?) {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }

        Button(
            onClick = {
                val uriToUse = selectedBinaryUri ?: Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin")
                val arguments = emptyArray<String>()
                onRunBinary(uriToUse, arguments)
            },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
            contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Run Server", Icons.TwoTone.PlayArrow) // Different icon
        }
    }

    private fun onRunBinary(uriToUse: Uri?, arguments: Array<String>) {
        val intent = Intent(this@MainActivity, BinaryService::class.java).apply {
            putExtra("binaryUri", uriToUse.toString())
            putExtra("arguments", arguments)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        outputText = "Server is running in the background"
        isBinaryRunning = true

        BinaryExecutor.executeBinary(
            this@MainActivity,
            arguments
        ) { output ->
            outputText += "\n$output"
        }
    }

    private fun onStopBinary() {

        // BinaryExecutor.stopBinary()

        val intent = Intent(this@MainActivity, BinaryService::class.java).apply {
            action = BinaryService.ACTION_STOP_BINARY
        }

        startService(intent)
        outputText = "Server stopped"
        isBinaryRunning = false
    }




    @Composable
    fun RowScope.Button2() {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }
        Button(
            onClick = { onStopBinary() },
            enabled = isBinaryRunning,
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
        contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Stop Server", Icons.TwoTone.Stop) // Different icon
        }
    }

    @Composable
    fun RowScope.Button3() {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }
        Button(
            onClick = { iptvRedirectFunc() },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
            contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Run IPTV", Icons.TwoTone.ResetTv) // Different icon
        }
    }

    @Composable
    fun RowScope.Button4() {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }
        Button(
            onClick = {  val intent = Intent(this@MainActivity, WebPlayerActivity::class.java)
                    startActivity(intent) },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
            contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Web TV", Icons.TwoTone.LiveTv) // Different icon
        }
    }

    @Composable
    fun RowScope.Button5() {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }
        Button(
            onClick = {
                currentScreen = "Debug"
                },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
            contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Debug", Icons.TwoTone.Deblur) // Different icon
        }
    }

    @Composable
    fun RowScope.Button6() {
        val colorPRIME = MaterialTheme.colorScheme.primary
        val colorSECOND = MaterialTheme.colorScheme.secondary
        val buttonColor = remember { mutableStateOf(colorPRIME) }
        Button(
            onClick = {
                // Call the function to stop the binary process
                onStopBinary()

                // Exit the app
                (this@MainActivity as? Activity)?.finish()
            },
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    buttonColor.value = if (focusState.isFocused) {
                        colorSECOND
                    } else {
                        colorPRIME
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
            contentPadding = PaddingValues(2.dp)
        ) {
            ButtonContent("Exit", Icons.AutoMirrored.TwoTone.ExitToApp) // Different icon
        }
    }

    @Composable
    fun ButtonContent(text: String, icon: ImageVector) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Icon",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(27.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }



    fun handleButton1Click(context: Context) {
        Toast.makeText(context, "Debug Mode : ON", Toast.LENGTH_SHORT).show()
    }


    private fun iptvRedirectFunc() {
        // Retrieve the app package name from shared preferences
        val appPackageName = preferenceManager.getKey("app_packagename")

        if (appPackageName.isNotEmpty()) {
            Log.d("DIX",appPackageName)
            // Retrieve other details
            val appLaunchActivity = preferenceManager.getKey("app_launch_activity")
            val appName = preferenceManager.getKey("app_name")

            if (appPackageName=="webtv") {
                val intent = Intent(this@MainActivity, WebPlayerActivity::class.java)
                startActivity(intent)
                Toast.makeText(this@MainActivity, "Starting: $appName", Toast.LENGTH_SHORT).show()
            } else {
                if (appLaunchActivity.isNotEmpty()) {
                    Log.d("DIX",appLaunchActivity)
                    // Create an intent to launch the app
                    val launchIntent = Intent().apply {
                        setClassName(appPackageName, appLaunchActivity)
                    }

                    // Check if the app can be resolved
                    val packageManager = this@MainActivity.packageManager
                    if (launchIntent.resolveActivity(packageManager) != null) {
                        // Start the activity
                        startActivity(launchIntent)
                        Toast.makeText(this@MainActivity, "Starting: $appName", Toast.LENGTH_SHORT).show()
                    } else {
                        // Handle the case where the app can't be resolved
                        Toast.makeText(this@MainActivity, "App not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Handle the case where app_launch_activity is not found
                    Toast.makeText(this@MainActivity, "App launch activity not found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Handle the case where app_packagename is null
            Toast.makeText(this@MainActivity, "IPTV app not selected", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle the result of the permission request
        if (Settings.canDrawOverlays(this)) {
            // Permission granted
            Log.d("KOX","granted")
        } else {
            // Permission not granted
            Log.d("KOX","not granted")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
//            val intent = Intent(
//                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                Uri.parse("package:$packageName")
//            )
//            overlayPermissionLauncher.launch(intent)
            showPARAM = true
        }
    }
}




data class BottomNavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val hasNews: Boolean
)



