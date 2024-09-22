package com.skylake.skytv.jgorunner.activity

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.data.applyConfigurations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: Context) {
    // Initialize SkySharedPref
    val preferenceManager = remember { SkySharedPref(context) }

    // Retrieve saved switch states
    val savedSwitchStateForLOCAL = preferenceManager.getKey("isFlagSetForLOCAL") == "Yes"
    var isSwitchOnForLOCAL by remember { mutableStateOf(savedSwitchStateForLOCAL) }

    val savedSwitchStateForEPG = preferenceManager.getKey("isFlagSetForEPG") == "Yes"
    var isSwitchOnForEPG by remember { mutableStateOf(savedSwitchStateForEPG) }

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

    LaunchedEffect(isSwitchOnForisFlagSetForAutoBootIPTV) {
        preferenceManager.setKey("isFlagSetForAutoBootIPTV", if (isSwitchOnForisFlagSetForAutoBootIPTV) "Yes" else "No")
        applyConfigurations(context, preferenceManager)
    }

    LaunchedEffect(selectedIPTVTime) {
        preferenceManager.setKey("isFlagSetForIPTVtime", selectedIPTVTime.toString())
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
                    icon = Icons.Filled.PlayArrow,
                    title = "Auto Start Server",
                    subtitle = "Automatically start the server on app start",
                    isChecked = isSwitchOnForAutoStartServer,
                    onCheckedChange = { isChecked -> isSwitchOnForAutoStartServer = isChecked }
                )
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Filled.Home,
                    title = "Auto Start on Boot",
                    subtitle = "Automatically start the server when the device boots",
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
                    icon = Icons.Filled.Notifications,
                    title = "Use Server Publicly",
                    subtitle = "Allow access to server from public networks",
                    isChecked = isSwitchOnForLOCAL,
                    onCheckedChange = { isChecked -> isSwitchOnForLOCAL = isChecked }
                )
            }

            if (false) {
                item {
                    SettingSwitchItem(
                        icon = Icons.Filled.Info,
                        title = "Enabel EPG",
                        subtitle = "Electronic program guide generation",
                        isChecked = isSwitchOnForEPG,
                        onCheckedChange = { isChecked -> isSwitchOnForEPG = isChecked }
                    )
                }
            }


            item {
                SettingSwitchItem(
                    icon = Icons.Filled.Info,
                    title = "Auto IPTV",
                    subtitle = "Automatically start IPTV on app start",
                    isChecked = isSwitchOnForAutoIPTV,
                    onCheckedChange = { isChecked -> isSwitchOnForAutoIPTV = isChecked }
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SettingItem(
                    icon = Icons.Filled.Favorite,
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
                ) {
                    Icon(imageVector = Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "IPTV Redirect Time: ${selectedIPTVTime / 1000} sec", fontSize = 18.sp)
                }
                Slider(
                    value = selectedIPTVTime.toFloat(),
                    onValueChange = { selectedIPTVTime = it.toInt() },
                    valueRange = 2000f..10000f,
                    steps = 5, // for 2, 4, 6, 8, 10 seconds
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}


@Composable
fun SettingItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Normal)
                subtitle?.let {
                    Text(text = it, fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp)) // Space between item and next
    }
}

@Composable
fun SettingSwitchItem(icon: ImageVector, title: String, subtitle: String? = null, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Normal)
                subtitle?.let {
                    Text(text = it, fontSize = 14.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
