package com.skylake.skytv.jgorunner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ModeSelectionDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onSelectionsMade: (String?, String?, String?) -> Unit // Quality, Category, Language
) {
    if (showDialog) {
        Dialog(
            onDismissRequest = onDismiss,
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
                    Text(text = "Select Options", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality Selection
                    var qualityExpanded by remember { mutableStateOf(false) }
                    var selectedQuality by remember { mutableStateOf("Auto") }
                    val qualityMap = mapOf( "Auto" to "auto", "High" to "high", "Medium" to "medium", "Low" to "low")

                    DropdownSelection(
                        title = "Quality",
                        options = qualityMap.keys.toList(),
                        selectedOption = selectedQuality,
                        onOptionSelected = { selectedQuality = it },
                        expanded = qualityExpanded,
                        onExpandChange = { qualityExpanded = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category Selection
                    var categoryExpanded by remember { mutableStateOf(false) }
                    var selectedCategory by remember { mutableStateOf("All Categories") }
                    val categoryMap = mapOf(
                        "All Categories" to 0, // Default case
                        "Entertainment" to 5,
                        "Movies" to 6,
                        "Kids" to 7,
                        "Sports" to 8,
                        "Lifestyle" to 9,
                        "Infotainment" to 10,
                        "News" to 12,
                        "Music" to 13,
                        "Devotional" to 15,
                        "Business" to 16,
                        "Educational" to 17,
                        "Shopping" to 18,
                        "JioDarshan" to 19
                    )

                    DropdownSelection(
                        title = "Category",
                        options = categoryMap.keys.toList(),
                        selectedOption = selectedCategory,
                        onOptionSelected = { selectedCategory = it },
                        expanded = categoryExpanded,
                        onExpandChange = { categoryExpanded = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Language Selection
                    var languageExpanded by remember { mutableStateOf(false) }
                    var selectedLanguage by remember { mutableStateOf("All Languages") }
                    val languageMap = mapOf(
                        "All Languages" to 0, // Default case
                        "Hindi" to 1,
                        "Marathi" to 2,
                        "Punjabi" to 3,
                        "Urdu" to 4,
                        "Bengali" to 5,
                        "English" to 6,
                        "Malayalam" to 7,
                        "Tamil" to 8,
                        "Gujarati" to 9,
                        "Odia" to 10,
                        "Telugu" to 11,
                        "Bhojpuri" to 12,
                        "Kannada" to 13,
                        "Assamese" to 14,
                        "Nepali" to 15,
                        "French" to 16,
                        "Other" to 18
                    )

                    DropdownSelection(
                        title = "Language",
                        options = languageMap.keys.toList(),
                        selectedOption = selectedLanguage,
                        onOptionSelected = { selectedLanguage = it },
                        expanded = languageExpanded,
                        onExpandChange = { languageExpanded = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onSelectionsMade(
                                qualityMap[selectedQuality],
                                categoryMap[selectedCategory].toString(),
                                languageMap[selectedLanguage].toString()
                            )
                            onDismiss()
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownSelection(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    Column {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onExpandChange(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = selectedOption)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandChange(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option) },
                        onClick = {
                            onOptionSelected(option)
                            onExpandChange(false)
                        }
                    )
                }
            }
        }
    }
}
