package com.skylake.skytv.jgorunner;

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.skylake.sky7t.jgo.ui.theme.JGOTheme
import java.io.FileInputStream

class MainActivity : ComponentActivity() {

    private var selectedBinaryUri: Uri? = null
    private var isBinaryRunning by mutableStateOf(false)
    private var selectedBinaryName by mutableStateOf("JGO Runner v3.9.4")

    // SharedPreferences for saving binary selection
    private lateinit var preferenceManager: SkySharedPref

    // Receiver to handle binary stop action
    private val binaryStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BinaryService.ACTION_BINARY_STOPPED) {
                // Update output text and UI state when binary is stopped
                isBinaryRunning = false
                outputText = "Server stopped"
            }
        }
    }

    private var outputText by mutableStateOf("✨")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferenceManager = SkySharedPref(this)

        // Restore binary selection from SharedPreferences
        val savedUriString = preferenceManager.getKey("selectedBinaryUri")
        val savedName = preferenceManager.getKey("selectedBinaryName")

        selectedBinaryUri = savedUriString?.let { Uri.parse(it) }
        selectedBinaryName = savedName ?: "JGO Runner v3.9.4"

        // Register for file selection result
        val selectBinaryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedBinaryUri = result.data?.data
                selectedBinaryName = selectedBinaryUri?.let {
                    val fileName = it.path?.substringAfterLast('/')
                    fileName ?: "Unknown file"
                } ?: "JGO Runner v3.9.4"

                // Save binary selection to SharedPreferences
                preferenceManager.setKey("selectedBinaryUri", selectedBinaryUri.toString())
                preferenceManager.setKey("selectedBinaryName", selectedBinaryName)
            }
        }

        // Register the receiver to listen for binary stop broadcasts
        registerReceiver(binaryStoppedReceiver, IntentFilter(BinaryService.ACTION_BINARY_STOPPED))

        setContent {
            JGOTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var argumentsText by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
//                        Text(text = outputText)

//                        Spacer(modifier = Modifier.height(16.dp))
//
//                        OutlinedTextField(
//                            value = argumentsText,
//                            onValueChange = { argumentsText = it },
//                            label = { Text("Arguments (e.g., run -port 5322)") },
//                            modifier = Modifier.fillMaxWidth()
//                        )


//                        Text(text = "Selected binary: $selectedBinaryName")
                        Text(text = selectedBinaryName)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = outputText)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = {
                                val uriToUse = selectedBinaryUri ?: Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin")
                                val arguments = argumentsText.trim().split("\\s+".toRegex()).toTypedArray()

                                val intent = Intent(this@MainActivity, BinaryService::class.java).apply {
                                    putExtra("binaryUri", uriToUse.toString())
                                    putExtra("arguments", arguments)
                                }
                                // Check if the device is running Android O (API level 26) or higher
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    // For devices below API level 26, use startService
                                    startService(intent)
                                }

                                outputText = "Server is running in the background"
                                isBinaryRunning = true
                            }) {
                                Text(
                                    text = if (selectedBinaryUri == null) "Run Server" else "Run Custom Binary"
                                )
                            }

                            // Vector icon for settings
//                            IconButton(onClick = {
//                                // Start file chooser for selecting binary file
//                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//                                    type = "*/*" // Adjust the MIME type as necessary
//                                    addCategory(Intent.CATEGORY_OPENABLE)
//                                }
//                                selectBinaryLauncher.launch(intent)
//                            }) {
//                                Icon(
//                                    painter = painterResource(id = R.drawable.manufacturing_24px),
//                                    contentDescription = "Settings"
//                                )
//                            }

                            Button(
                                onClick = {
                                    BinaryExecutor.stopBinary()
                                    outputText = "Server stopped"
                                    isBinaryRunning = false
                                },
                                enabled = isBinaryRunning
                            ) {
                                Text("Stop Server")
                            }
                        }

                        //Spacer(modifier = Modifier.height(16.dp))



                        Spacer(modifier = Modifier.height(16.dp))

                        if (selectedBinaryUri != null) {
                            Button(
                                onClick = {
                                    selectedBinaryUri = null
                                    selectedBinaryName = "JGO Runner v3.9.4"
                                    preferenceManager.setKey("selectedBinaryUri", null)
                                    preferenceManager.setKey("selectedBinaryName", selectedBinaryName)
                                    Toast.makeText(this@MainActivity, "Binary selection cleared", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear Selection")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver when activity is destroyed
        unregisterReceiver(binaryStoppedReceiver)
    }

    private fun readBinaryFromUri(uri: Uri): ByteArray? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { inputStream ->
                    inputStream.readBytes()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to read binary from Uri", e)
            null
        }
    }
}
