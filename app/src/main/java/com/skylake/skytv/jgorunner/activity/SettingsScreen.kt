package com.skylake.skytv.jgorunner.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.data.applyConfigurations
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.MainActivity
import com.skylake.skytv.jgorunner.utils.Config2DL
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context) {
    // Initialize SkySharedPref
    val preferenceManager = remember { SkySharedPref(context) }

    val focusRequester = remember { FocusRequester() }

    // Retrieve saved switch states
    val savedSwitchStateForLOCAL = preferenceManager.getKey("isFlagSetForLOCAL") == "Yes"
    var isSwitchOnForLOCAL by remember { mutableStateOf(savedSwitchStateForLOCAL) }

    val savedSwitchStateForEPG = preferenceManager.getKey("isFlagSetForEPG") == "Yes"
    var isSwitchOnForEPG by remember { mutableStateOf(savedSwitchStateForEPG) }

    val savedSwitchStateForAutoUpdate = preferenceManager.getKey("isAutoUpdate") == "Yes"
    var isSwitchOnForAutoUpdate by remember { mutableStateOf(savedSwitchStateForAutoUpdate) }

    val savedSwitchStateForAutoStartServer = preferenceManager.getKey("isFlagSetForAutoStartServer") == "Yes"
    var isSwitchOnForAutoStartServer by remember { mutableStateOf(savedSwitchStateForAutoStartServer) }

    val savedSwitchStateForAutoStartOnBoot = preferenceManager.getKey("isFlagSetForAutoStartOnBoot") == "Yes"
    var isSwitchOnForAutoStartOnBoot by remember { mutableStateOf(savedSwitchStateForAutoStartOnBoot) }

    val savedSwitchStateForAutoIPTV = preferenceManager.getKey("isFlagSetForAutoIPTV") == "Yes"
    var isSwitchOnForAutoIPTV by remember { mutableStateOf(savedSwitchStateForAutoIPTV) }

    val savedSwitchStateForAutoStartIPTVOnBoot = preferenceManager.getKey("isFlagSetForAutoBootIPTV") == "Yes"
    var isSwitchOnForisFlagSetForAutoBootIPTV by remember { mutableStateOf(savedSwitchStateForAutoStartIPTVOnBoot) }

    val savedIPTVRedirectTime = preferenceManager.getKey("isFlagSetForIPTVtime")?.toInt() ?: 5000
    var selectedIPTVTime by remember { mutableStateOf(savedIPTVRedirectTime) }

    // Retrieve saved port number
    val savedPortNumber = preferenceManager.getKey("isCustomSetForPORT")?.toIntOrNull() ?: 5350
    var portNumber by remember { mutableStateOf(savedPortNumber.toString()) }

    // Retrieve saved version number
    val savedVersionNumber = preferenceManager.getKey("releaseName")?: "Not Installed"
    var releaseName by remember { mutableStateOf(savedVersionNumber) }

    var showPortDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showRestartAppDialog by remember { mutableStateOf(false) }

    // Update shared preference when switch states change
    LaunchedEffect(isSwitchOnForLOCAL) {
        preferenceManager.setKey("isFlagSetForLOCAL", if (isSwitchOnForLOCAL) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(isSwitchOnForAutoStartServer) {
        preferenceManager.setKey("isFlagSetForAutoStartServer", if (isSwitchOnForAutoStartServer) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(isSwitchOnForAutoStartOnBoot) {
        preferenceManager.setKey("isFlagSetForAutoStartOnBoot", if (isSwitchOnForAutoStartOnBoot) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(isSwitchOnForAutoIPTV) {
        preferenceManager.setKey("isFlagSetForAutoIPTV", if (isSwitchOnForAutoIPTV) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(isSwitchOnForAutoUpdate) {
        preferenceManager.setKey("isAutoUpdate", if (isSwitchOnForAutoUpdate) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(isSwitchOnForisFlagSetForAutoBootIPTV) {
        preferenceManager.setKey("isFlagSetForAutoBootIPTV", if (isSwitchOnForisFlagSetForAutoBootIPTV) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(selectedIPTVTime) {
        preferenceManager.setKey("isFlagSetForIPTVtime", selectedIPTVTime.toString())
    }

    // Update port number when changed
    LaunchedEffect(portNumber) {
        val validPort = portNumber.toIntOrNull()
        if (validPort != null && validPort in 1000..9999) {
            preferenceManager.setKey("isCustomSetForPORT", validPort.toString())
            applyConfigurations(context, preferenceManager)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // TopAppBar
        TopAppBar(
            title = { Text(text = "Settings", fontSize = 30.sp) },
        )

        // Content
        LazyColumn(
            modifier = Modifier.padding(top = 0.dp)
        ) {
            item {
                SettingSwitchItem(
                    icon = Icons.Filled.Pix,
                    title = "Auto Start Server",
                    subtitle = "Automatically start server on app start",
                    isChecked = isSwitchOnForAutoStartServer,
                    onCheckedChange = { isChecked -> isSwitchOnForAutoStartServer = isChecked }
                )
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Filled.Stream,
                    title = "Auto Start on Boot",
                    subtitle = "Automatically start server on boot",
                    isChecked = isSwitchOnForAutoStartOnBoot,
                    onCheckedChange = { isChecked -> isSwitchOnForAutoStartOnBoot = isChecked }
                )
            }

            // Conditionally render the Auto Start IPTV on Boot switch
            if (false) {
                item {
                    SettingSwitchItem(
                        icon = Icons.Filled.PlayArrow,
                        title = "Auto Start IPTV on Boot",
                        subtitle = "Automatically start IPTV on boot",
                        isChecked = isSwitchOnForisFlagSetForAutoBootIPTV,
                        onCheckedChange = { isChecked -> isSwitchOnForisFlagSetForAutoBootIPTV = isChecked }
                    )
                }
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Filled.NetworkWifi,
                    title = "Use Server Publicly",
                    subtitle = "Allow access to server from other devices",
                    isChecked = isSwitchOnForLOCAL,
                    onCheckedChange = { isChecked -> isSwitchOnForLOCAL = isChecked }
                )
            }

            // Port Number Setting
            item {
                SettingItem(
                    icon = Icons.Filled.Settings,
                    title = "Server Port",
                    subtitle = "Current Port: $portNumber",
                    onClick = { showPortDialog = true }
                )
            }

            if (false) {
                item {
                    SettingSwitchItem(
                        icon = Icons.Filled.Info,
                        title = "Enable EPG",
                        subtitle = "Electronic program guide generation",
                        isChecked = isSwitchOnForEPG,
                        onCheckedChange = { isChecked -> isSwitchOnForEPG = isChecked }
                    )
                }
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Filled.LiveTv,
                    title = "Auto IPTV",
                    subtitle = "Automatically start IPTV on app start",
                    isChecked = isSwitchOnForAutoIPTV,
                    onCheckedChange = { isChecked -> isSwitchOnForAutoIPTV = isChecked }
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SettingItem(
                    icon = Icons.Filled.ResetTv,
                    title = "Select Start IPTV",
                    subtitle = "Choose your preferred IPTV to start",
                    onClick = {
                        val intent = Intent(context, AppListActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Timelapse, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "IPTV Redirect Time: ${selectedIPTVTime / 1000} sec", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = selectedIPTVTime.toFloat(),
                    onValueChange = { selectedIPTVTime = it.toInt() },
                    valueRange = 2000f..10000f,
                    steps = 5, // for 2, 4, 6, 8, 10 seconds
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .focusRequester(focusRequester) // Focus for keyboard handling
                        .focusable()
                        .onKeyEvent { event ->
                            when (event.nativeKeyEvent.keyCode) {
                                Key.DirectionRight.nativeKeyCode -> {
                                    selectedIPTVTime = (selectedIPTVTime + 1000).coerceAtMost(10000)
                                    true
                                }
                                Key.DirectionLeft.nativeKeyCode -> {
                                    selectedIPTVTime = (selectedIPTVTime - 1000).coerceAtLeast(2000)
                                    true
                                }
                                else -> false
                            }
                        }
                )

            }

            item {
                SettingItem(
                    icon = Icons.Filled.ArrowCircleUp,
                    title = "Update Binary",
                    subtitle = "Update to the latest binary version.",
                    onClick = {
                        preferenceManager.setKey("expectedFileSize", "0")
                        Config2DL.startDownloadAndSave(context) { output ->
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, output, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            item {
                SettingItem(
                    icon = Icons.Filled.RestartAlt,
                    title = "Reset All Settings",
                    subtitle = "Useful for troubleshooting and resolving issues.",
                    onClick = {
                        resetFunc(context) {
                            showRestartAppDialog = true
                        }

                    }
                )
            }



        }
    }

    // Port Number Dialog
    if (showPortDialog) {
        Dialog(
            onDismissRequest = { showPortDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Set Server Port", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = portNumber,
                        onValueChange = { portNumber = it.take(4) },
                        label = { Text(text = "Port Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), // Numeric keyboard + Done action
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val validPort = portNumber.toIntOrNull()
                                if (validPort != null && validPort in 1000..9999) {
                                    preferenceManager.setKey("isCustomSetForPORT", validPort.toString())
                                    showPortDialog = false
                                    showRestartDialog = true // Show restart dialog
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = { showPortDialog = false }) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            val validPort = portNumber.toIntOrNull()
                            if (validPort != null && validPort in 1000..9999) {
                                preferenceManager.setKey("isCustomSetForPORT", validPort.toString())
                                showPortDialog = false
                                showRestartDialog = true
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }


    // Restart Required Dialog
    if (showRestartDialog) {
        Dialog(
            onDismissRequest = { showRestartDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Restart Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "The server port has been changed. Please restart the device for the changes to take effect.", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = { showRestartDialog = false }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

    // Restart App Dialog
    if (showRestartAppDialog) {
        Dialog(
            onDismissRequest = { showRestartAppDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Restart Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "The app needs to be restarted for the changes to take effect. Please restart the app.", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = {
                            showRestartAppDialog = false
                            restartApp(context)
                        }) {
                            Text("Restart")
                        }
                    }
                }
            }
        }
    }


}

fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    (context as? Activity)?.finish()
}


fun resetFunc(context: Context, onComplete: () -> Unit) {
    Toast.makeText(context, "[#] Clearing files.", Toast.LENGTH_LONG).show()

    // Delete folder in external storage
    val externalStoragePath = Environment.getExternalStorageDirectory().path
    val folder = File("$externalStoragePath/.jiotv_go")

    if (folder.exists() && folder.isDirectory) {
        folder.deleteRecursively()
    } else {
    }

    // Delete binary file in internal storage
    val binaryFile = File(context.filesDir, "majorbin")
    if (binaryFile.exists()) {
        binaryFile.delete()
    } else {
        Toast.makeText(context, "Reset successfully.", Toast.LENGTH_SHORT).show();
    }
    onComplete()
}





@Composable
fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onCheckedChange(!isChecked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = { onCheckedChange(it) })
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
