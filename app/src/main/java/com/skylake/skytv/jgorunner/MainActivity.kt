package com.skylake.skytv.jgorunner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.activity.SettingsScreen
import com.skylake.skytv.jgorunner.activity.WebPlayerActivity
import com.skylake.skytv.jgorunner.services.BinaryExecutor
import com.skylake.skytv.jgorunner.services.BinaryService
import com.skylake.skytv.jgorunner.utils.ConfigUtil
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.data.applyConfigurations
import com.skylake.skytv.jgorunner.ui.theme.JGOTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    private var selectedBinaryUri: Uri? = null
    private var isBinaryRunning by mutableStateOf(false)
    private var selectedBinaryName by mutableStateOf("JGO Runner")

    // SharedPreferences for saving binary selection
    private lateinit var preferenceManager: SkySharedPref

    private val REQUEST_STORAGE_PERMISSION = 100

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



    private var outputText by mutableStateOf("ℹ️ JioTV GO")
    private var currentScreen by mutableStateOf("Home") // Manage current screen
    private lateinit var backPressedCallback: OnBackPressedCallback

    private val executor = Executors.newSingleThreadExecutor()

    private var showRedirectPopup by mutableStateOf(false)
    private var shouldLaunchIPTV by mutableStateOf(false)
    private var countdownJob: Job? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        checkStoragePermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissions()
        }
        preferenceManager = SkySharedPref(this)

        val preferenceManager = SkySharedPref(this)
        applyConfigurations(this, preferenceManager)

        val savedUriString = preferenceManager.getKey("selectedBinaryUri")
        val savedName = preferenceManager.getKey("selectedBinaryName")

        selectedBinaryUri = savedUriString?.let { Uri.parse(it) }
        selectedBinaryName = savedName ?: "JGO Runner"

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

        registerReceiver(binaryStoppedReceiver, IntentFilter(BinaryService.ACTION_BINARY_STOPPED))

        // Register the OnBackPressedCallback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen == "Settings") {
                    currentScreen = "Home"
                } else {
                    // Let the system handle the back press
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)



        setContent {
            JGOTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavigationBar(selectBinaryLauncher) }
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
                        }

                        val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 5000
                        var countdownTimeF = savedIPTVRedirectTime / 1000

                        // Show the redirect popup
                        RedirectPopup(
                            isVisible = showRedirectPopup,
                            countdownTime = countdownTimeF,
                            onDismiss = {
                                showRedirectPopup = false
                                shouldLaunchIPTV = false // Cancel IPTV launch if dismissed
                            }
                        )

                    }
                }

                // Handle delayed IPTV launch
                LaunchedEffect(shouldLaunchIPTV) {
                    if (shouldLaunchIPTV) {

                        val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 5000
                        delay(savedIPTVRedirectTime.toLong())
                        startIPTV()
                    }
                }
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


        val nekoFirstTime = preferenceManager.getKey("nekoFirstTime")

        if (nekoFirstTime.isNullOrEmpty()) {
            preferenceManager.setKey("nekoFirstTime", "Done")
            Log.d("MainActivity", "nekoFirstTime was empty or not available, set to Done.")

            // Set default values
            preferenceManager.setKey("isFlagSetForLOCAL", "Yes") // Reverse
            preferenceManager.setKey("isFlagSetForAutoStartServer", "No")
            preferenceManager.setKey("isFlagSetForAutoStartOnBoot", "No")
            preferenceManager.setKey("isFlagSetForAutoBootIPTV", "No")
            preferenceManager.setKey("app_packagename", "")
            preferenceManager.setKey("isFlagSetForEPG", "No")
            preferenceManager.setKey("app_name", "")
            preferenceManager.setKey("isFlagSetForAutoIPTV", "No")
            preferenceManager.setKey("app_launch_activity", "")
            preferenceManager.setKey("isFlagSetForPORT", "5350")
            preferenceManager.setKey("__Port", " --port 5350")
            preferenceManager.setKey("__Public", " --public")
            preferenceManager.setKey("__EPG", " --config jiotv_go.json")
        }

        // Use the utility function to fetch and save config
        ConfigUtil.fetchAndSaveConfig(this)

        val isUpdate = preferenceManager.getKey("isUpdate")
        val isEngine = preferenceManager.getKey("isEngine")

        if (isUpdate == "Yes") {
            Toast.makeText(this@MainActivity, "Update Available please update", Toast.LENGTH_SHORT).show()
        }

        if (isEngine?.toIntOrNull()?.let { it > 1 } == true) {
            Toast.makeText(this@MainActivity, "App support period ended.", Toast.LENGTH_SHORT).show()
            finishAffinity()
            return
        }







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
        if (isFlagSetForAutoIPTV == "Yes") {
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

    private fun startIPTV() {
        executor.execute {
            try {
                val isServerRunning = checkServerStatus()
                runOnUiThread {
                    if (isServerRunning) {
                        val appPackageName = preferenceManager.getKey("app_packagename")
                        if (appPackageName.isNotEmpty()) {
                            Log.d("DIX", appPackageName)
                            val appLaunchActivity = preferenceManager.getKey("app_launch_activity")
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
                    } else {
                        Toast.makeText(this@MainActivity, "Server issue. If it persists, restart.", Toast.LENGTH_SHORT).show()
                        Log.d("DIX", "Server issue. If it persists, restart")
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

    @Composable
    fun RedirectPopup(isVisible: Boolean, countdownTime: Int, onDismiss: () -> Unit) {
        var currentTime by remember { mutableIntStateOf(countdownTime) }

        if (isVisible) {
            Log.d("RedirectPopup", "Popup is visible.")
            // Countdown logic
            LaunchedEffect(isVisible) {
                currentTime = countdownTime
                while (currentTime > 0) {
                    delay(1000)
                    currentTime -= 1
                }
                onDismiss() // Dismiss when time reaches 0
            }

            // Log current time
            Log.d("RedirectPopup", "Current countdown: $currentTime")

            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text("Redirecting") },
                text = { Text("You will be redirected to the IPTV app in $currentTime seconds.") },
                confirmButton = {
                    Button(onClick = { onDismiss() }) {
                        Text("Dismiss")
                    }
                },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            )
        } else {
            Log.d("RedirectPopup", "Popup is NOT visible.")
        }
    }


    private fun checkServerStatus(): Boolean {
        return try {
            val url = URL("http://localhost:5350")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000 // 5 seconds timeout
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            Log.e("DIX", "Error checking server status", e)
            false
        }
    }


    private fun checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val isPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!isPermissionGranted) {
                storagePermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = selectedBinaryName)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "✨")
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(onClick = {
                    val uriToUse = selectedBinaryUri
                        ?: Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin")
                    val arguments = emptyArray<String>()

                    onRunBinary(uriToUse, arguments)
                }) {
                    Text(if (selectedBinaryUri == null) "Run Server" else "Run Custom Binary")
                }

                Button(onClick = onStopBinary, enabled = isBinaryRunning) {
                    Text("Stop Server")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
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
                }) {
                    Text("Run IPTV")
                }

                Button(onClick = {
                    val intent = Intent(this@MainActivity, WebPlayerActivity::class.java)
                    startActivity(intent)
                }) {
                    Text("WEB TV")
                }

                Button(onClick = {
                    // Call the function to stop the binary process
                    onStopBinary()

                    // Exit the app
                    (this@MainActivity as? Activity)?.finish()
                }) {
                    Text("Exit")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))


            Column {
//                Text(
//                    text = "Output Logs",
//                    color = Color.White,
//                    fontSize = 16.sp,
//                    fontWeight = FontWeight.Normal,
//                    modifier = Modifier.padding(bottom = 8.dp)
//                )

                Text(
                    text = outputText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.Black)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }

//    @Composable
//    fun SettingsScreen() {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = "Settings",
//                fontSize = 24.sp,
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier.padding(bottom = 16.dp)
//            )
//
//            Button(onClick = {
//                // Open Browser
//                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
//                startActivity(intent)
//            }) {
//                Text("Open Browser")
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            Button(onClick = {
//                // Open Calculator
//                val intent = Intent(Intent.ACTION_VIEW)
//                intent.setClassName("com.android.calculator2", "com.android.calculator2.Calculator")
//                startActivity(intent)
//            }) {
//                Text("Open CA")
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            Button(onClick = {
//                // Open Dialer
//                val intent = Intent(Intent.ACTION_DIAL)
//                startActivity(intent)
//            }) {
//                Text("Open DX")
//            }
//        }
//    }
}



data class BottomNavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val hasNews: Boolean
)



