package com.skylake.skytv.jgorunner.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.skylake.skytv.jgorunner.R

import androidx.compose.runtime.LaunchedEffect

import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.Shadow

import androidx.compose.ui.text.TextStyle


import android.util.Log
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.sharp.Info
import androidx.compose.material.icons.sharp.Support
import androidx.compose.runtime.*
import com.skylake.skytv.jgorunner.data.SkySharedPref

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DebugScreen(context: Context, onNavigate: (String) -> Unit) {
    // Load custom font
    val customFontFamily = FontFamily(Font(R.font.chakrapetch_bold))

    // State for the glow color
    val glowColor = remember { mutableStateOf(Color.Green) }

    // State for the flag status
    var isGlowing by remember { mutableStateOf(false) }

    val preferenceManager = remember { SkySharedPref(context) }

    // Launch a coroutine to check the status every 3 seconds on another thread
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            while (true) {
                // Simulate checking the preference status
                val savedSwitchState = preferenceManager.getKey("isFlagSetForLOCAL")

                // Log the status
                Log.d("PreferenceCheck", "isFlagSetForLOCAL: $savedSwitchState")

                // Update the glowing state based on the preference
                isGlowing = savedSwitchState == "Yes"

                // Delay for 3 seconds before the next check
                delay(3000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "JTV-GO SERVER",
            fontSize = 24.sp,
            fontFamily = customFontFamily,
            color = MaterialTheme.colorScheme.onBackground,
            style = if (isGlowing) {
                TextStyle(
                    shadow = Shadow(
                        color = glowColor.value,
                        blurRadius = 30f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 0f)
                    )
                )
            } else {
                TextStyle.Default
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button1(context, onNavigate)
            Button2(context, onNavigate)
        }

        Spacer(modifier = Modifier.height(8.dp)) // Space between rows

        // Second row with 2 new buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button3(context)
            Button4(context)
        }
    }
}






// Define individual functions for button actions
@Composable
fun RowScope.Button1(context: Context, onNavigate: (String) -> Unit) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    Button(
        onClick = { handleButton1Click(context)
            onNavigate("Runner")},
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
        ButtonContent("Binary Runner", Icons.AutoMirrored.Filled.DirectionsRun) // Different icon
    }
}

@Composable
fun RowScope.Button2(context: Context, onNavigate: (String) -> Unit) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = colorPRIME.copy(alpha = 0.5f) //MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    val borderColor = remember { mutableStateOf(Color.Transparent) } // Initialize border color

    Button(
        onClick = { handleButton2Click(context)
            onNavigate("Info")},
        modifier = Modifier
            .weight(1f)
            .padding(8.dp)
            .onFocusChanged { focusState ->
                buttonColor.value = if (focusState.isFocused) {
                    borderColor.value = Color.White // Set border color to white when focused
                    colorSECOND
                } else {
                    borderColor.value = Color.Transparent // Reset border color when not focused
                    colorPRIME
                }
            },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor.value),
        contentPadding = PaddingValues(2.dp)
    ) {
        ButtonContent("System Info", Icons.Sharp.Info) // Different icon
    }
}


// Define individual functions for the new buttons
@Composable
fun RowScope.Button3(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bit.ly/3Uc1usW"))
            context.startActivity(intent)
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
        ButtonContent("Support", Icons.Sharp.Support) // Different icon
    }
}


@Composable
fun RowScope.Button4(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = colorPRIME.copy(alpha = 0.5f)
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    Button(
        onClick = { handleButton4Click(context) },
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
        ButtonContent("Demo 14", Icons.Default.Settings) // Different icon
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
            tint = MaterialTheme.colorScheme.onPrimary, // Use theme color that contrasts the button background
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimary // Text color matching the icon color
        )
    }
}

// Separate onClick functions for each button
fun handleButton1Click(context: Context) {
    Toast.makeText(context, "Demo 11 - Code-11", Toast.LENGTH_SHORT).show()
}

fun handleButton2Click(context: Context) {
    Toast.makeText(context, "Demo 12 - Code-12", Toast.LENGTH_SHORT).show()
}

// Separate onClick functions for each new button
fun handleButton3Click(context: Context) {
    Toast.makeText(context, "Demo 13 - Code-13", Toast.LENGTH_SHORT).show()
}

fun handleButton4Click(context: Context) {
    Toast.makeText(context, "Demo 14 - Code-14", Toast.LENGTH_SHORT).show()
}