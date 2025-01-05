package com.skylake.skytv.jgorunner.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.data.SkySharedPref
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.*

@Composable
fun LoginScreen(context: Context) {
    val preferenceManager = SkySharedPref.getInstance(context)

    val glowColors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta
    )
    val glowColor = remember { Animatable(glowColors.first()) }
    val customFontFamily = FontFamily(Font(R.font.chakrapetch_bold))
    var isUsingOtp by remember { mutableStateOf(true) }

    var phoneNumber by remember { mutableStateOf(TextFieldValue()) }
    var otpCode by remember { mutableStateOf(TextFieldValue()) }
    var password by remember { mutableStateOf(TextFieldValue()) }

    val focusRequesterList = remember { List(3) { FocusRequester() } }
    val (phoneNumberFocusRequester, otpCodeFocusRequester, passwordFocusRequester) = focusRequesterList

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        phoneNumberFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Login",
                fontSize = 24.sp,
                fontFamily = customFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
                style = if (true) {
                    TextStyle(
                        shadow = Shadow(
                            color = glowColor.value,
                            blurRadius = 30f,
                            offset = Offset(0f, 0f)
                        )
                    )
                } else {
                    TextStyle.Default
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )


            // Tab layout to switch between OTP and Password
            var selectedTabIndex by remember { mutableStateOf(0) }
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        selectedTabIndex = 0
                        isUsingOtp = true
                        phoneNumberFocusRequester.requestFocus()
                    }
                ) {
                    Text("Use OTP", modifier = Modifier.padding(16.dp))
                }
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        isUsingOtp = false
                        phoneNumberFocusRequester.requestFocus()
                    }
                ) {
                    Text("Use Password", modifier = Modifier.padding(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isUsingOtp) {
                // OTP Login UI
                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { otpCodeFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .focusRequester(phoneNumberFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            handleKeyEvent(
                                event = event,
                                focusManager = focusManager,
                                focusRequesterList = focusRequesterList,
                                currentFieldValue = phoneNumber.text,
                                cursorPosition = phoneNumber.selection.end
                            )
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { sendOtp(context, phoneNumber.text) }) {
                    Text("Send OTP")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("OTP Code") },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { verifyOtp(context, phoneNumber.text, otpCode.text) }),
                    modifier = Modifier
                        .focusRequester(otpCodeFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            handleKeyEvent(
                                event = event,
                                focusManager = focusManager,
                                focusRequesterList = focusRequesterList,
                                currentFieldValue = otpCode.text,
                                cursorPosition = otpCode.selection.end
                            )
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { verifyOtp(context, phoneNumber.text, otpCode.text) }) {
                    Text("Verify OTP")
                }

            } else {
                // Password Login UI
                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .focusRequester(phoneNumberFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            handleKeyEvent(
                                event = event,
                                focusManager = focusManager,
                                focusRequesterList = focusRequesterList,
                                currentFieldValue = phoneNumber.text,
                                cursorPosition = phoneNumber.selection.end
                            )
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { login(context, phoneNumber.text, password.text) }),
                    modifier = Modifier
                        .focusRequester(passwordFocusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            handleKeyEvent(
                                event = event,
                                focusManager = focusManager,
                                focusRequesterList = focusRequesterList,
                                currentFieldValue = password.text,
                                cursorPosition = password.selection.end
                            )
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { login(context, phoneNumber.text, password.text) }) {
                    Text("Login")
                }
            }
        }
    }
}

private fun handleKeyEvent(
    event: KeyEvent,
    focusManager: FocusManager,
    focusRequesterList: List<FocusRequester>,
    currentFieldValue: String,
    cursorPosition: Int
): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionDown -> {
                if (cursorPosition == currentFieldValue.length) {
                    // Cursor is at the end of the field, move focus to the next field
                    focusManager.moveFocus(FocusDirection.Down)
                    return true
                }
            }
            Key.DirectionUp -> {
                if (cursorPosition == 0) {
                    // Cursor is at the start of the field, move focus to the previous field
                    focusManager.moveFocus(FocusDirection.Up)
                    return true
                }
            }
        }
    }
    return false
}

private fun sendOtp(context: Context, phoneNumber: String) {
    if (phoneNumber.isNotBlank()) {
        Toast.makeText(context, "Sending OTP to $phoneNumber", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Please enter your phone number", Toast.LENGTH_SHORT).show()
    }
}

private fun verifyOtp(context: Context, phoneNumber: String, otpCode: String) {
    if (otpCode.isNotBlank()) {
        Toast.makeText(context, "Verifying OTP: $otpCode for $phoneNumber", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Please enter the OTP", Toast.LENGTH_SHORT).show()
    }
}

private fun login(context: Context, phoneNumber: String, password: String) {
    if (phoneNumber.isNotBlank() && password.isNotBlank()) {
        Toast.makeText(context, "Logging in with $phoneNumber", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Please enter your phone number and password", Toast.LENGTH_SHORT).show()
    }
}



