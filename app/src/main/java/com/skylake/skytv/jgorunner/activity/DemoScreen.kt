package com.skylake.skytv.jgorunner.activity

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.sharp.Adb
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

@Composable
fun DemoScreen(context: Context) {


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // First row with 2 buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button1(context)
            Button2(context)
        }

        Spacer(modifier = Modifier.height(8.dp)) // Space between rows

        // Second row with 4 buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button3(context)
            Button4(context)
            Button5(context)
            Button6(context)
        }
    }
}


// Define individual functions for button actions
@Composable
fun RowScope.Button1(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    Button(
        onClick = { handleButton1Click(context) },
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
        ButtonContent("Demo 11", Icons.Default.Favorite) // Different icon
    }
}

@Composable
fun RowScope.Button2(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = colorPRIME.copy(alpha = 0.5f) //MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    val borderColor = remember { mutableStateOf(Color.Transparent) } // Initialize border color

    Button(
        onClick = { handleButton2Click(context) },
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
        ButtonContent("Demo 12", Icons.Sharp.Adb) // Different icon
    }
}





@Composable
fun RowScope.Button3(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = Color(
        red = (colorPRIME.red * 0.5f + 0.5f).coerceIn(0f, 1f),
        green = (colorPRIME.green * 0.5f + 0.5f).coerceIn(0f, 1f),
        blue = (colorPRIME.blue * 0.5f + 0.5f).coerceIn(0f, 1f),
        alpha = colorPRIME.alpha
    )
    //MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    Button(
        onClick = { handleButton3Click(context) },
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
        ButtonContent("Demo 21", Icons.TwoTone.Settings) // Different icon
    }
}

@Composable
fun RowScope.Button4(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = Color(
        red = (colorPRIME.red * 0.5f + 0.5f).coerceIn(0f, 1f),
        green = (colorPRIME.green * 0.5f + 0.5f).coerceIn(0f, 1f),
        blue = (colorPRIME.blue * 0.5f + 0.5f).coerceIn(0f, 1f),
        alpha = colorPRIME.alpha
    )
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    val borderColor = remember { mutableStateOf(Color.Transparent) }

    Button(
        onClick = { handleButton4Click(context) },
        modifier = Modifier
            .weight(1f)
            .padding(8.dp)
            .border(
                width = 2.dp,
                color = borderColor.value, // Use border color state
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focusState ->
                buttonColor.value = if (focusState.isFocused) {
                    borderColor.value = Color.Yellow // Set border color to white when focused
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
        ButtonContent("Demo 22", Icons.TwoTone.Build) // Different icon
    }
}

@Composable
fun RowScope.Button5(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    val borderColor = remember { mutableStateOf(Color.Transparent) } // Initialize border color

    Button(
        onClick = { handleButton5Click(context) },
        modifier = Modifier
            .weight(1f)
            .padding(8.dp)
            .border(
                width = 2.dp,
                color = borderColor.value, // Use border color state
                shape = RoundedCornerShape(8.dp)
            )
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
        ButtonContent("Demo 23", Icons.Filled.Email) // Different icon
    }
}

@Composable
fun RowScope.Button6(context: Context) {
    val colorPRIME = MaterialTheme.colorScheme.primary
    val colorSECOND = MaterialTheme.colorScheme.secondary
    val buttonColor = remember { mutableStateOf(colorPRIME) }
    OutlinedButton(
        onClick = { handleButton6Click(context) },
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
        ButtonContent("Demo 24", Icons.Rounded.Share) // Different icon
    }
}

//@Composable
//fun ButtonContent(text: String, icon: ImageVector) {
//    Column(
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Icon(
//            imageVector = icon,
//            contentDescription = "Icon",
//            tint = Color.White,
//            modifier = Modifier.size(32.dp)
//        )
//        Spacer(modifier = Modifier.height(4.dp))
//        Text(text = text, fontSize = 12.sp)
//    }
//}

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

fun handleButton3Click(context: Context) {
    Toast.makeText(context, "Demo 21 - Code-21", Toast.LENGTH_SHORT).show()
}

fun handleButton4Click(context: Context) {
    Toast.makeText(context, "Demo 22 - Code-22", Toast.LENGTH_SHORT).show()
}

fun handleButton5Click(context: Context) {
    Toast.makeText(context, "Demo 23 - Code-23", Toast.LENGTH_SHORT).show()
}

fun handleButton6Click(context: Context) {
    Toast.makeText(context, "Demo 24 - Code-24", Toast.LENGTH_SHORT).show()
}
