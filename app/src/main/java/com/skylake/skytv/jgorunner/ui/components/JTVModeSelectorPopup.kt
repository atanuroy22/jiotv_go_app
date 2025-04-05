package com.skylake.skytv.jgorunner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.data.SkySharedPref
import androidx.compose.ui.platform.LocalContext
import com.skylake.skytv.jgorunner.ui.dev.Helper


@Composable
fun ModeSelectorPopup(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onModeSelected: (Int) -> Unit
) {
    if (isVisible) {
        var selectedIndex by remember { mutableIntStateOf(0) }
        val context = LocalContext.current
        val preferenceManager = remember { SkySharedPref(context) }

        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(text = "Select Operation Mode")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "JTV-Go Operation Mode",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val options = listOf(
                        "Easy",
                        "Expert"
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                onClick = { selectedIndex = index },
                                selected = index == selectedIndex,
                                label = { Text(label) },
                                modifier = Modifier.width(IntrinsicSize.Min)
                            )
                        }
                    }

                    // Display info based on selected option
                    when (selectedIndex) {
                        0 -> {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("• Simple and direct operation")
                                Text("• Best for beginners")
                                Text("• Default parameters")
                            }

                            Helper.setEasyMode(context)
                        }
                        1 -> {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("• Full control over all settings")
                                Text("• Suited for advanced users")
                            }

                            Helper.setExpertMode(context)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onModeSelected(selectedIndex)
                        onDismiss()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onDismiss() }) {
                    Text("Cancel")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}


