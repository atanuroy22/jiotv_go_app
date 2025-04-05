package com.skylake.skytv.jgorunner.ui.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.data.SkySharedPref

@SuppressLint("MutableCollectionMutableState")
@Composable
fun ModeSelectionDialog2(
    showDialog: Boolean,
    context: Context,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSelectionsMade: (
        quality: String?,
        categoryNames: List<String>,
        categoryIds: List<Int>,
        languageNames: List<String>,
        languageIds: List<Int>
    ) -> Unit
) {
    if (!showDialog) return

    val preferenceManager = SkySharedPref.getInstance(context)

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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quality Selection
                var selectedQuality by remember {
                    mutableStateOf(preferenceManager.myPrefs.filterQX ?: "Auto")
                }
                val qualityMap =
                    remember { mapOf("Auto" to "auto", "High" to "high", "Medium" to "medium", "Low" to "low") }

                // Category Selection
                val categoryMap = remember {
                    mapOf(
                        "All Categories" to null,
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
                }
                val categoryOptions = remember(categoryMap) { categoryMap.keys.toList() }

                // Load selected categories from preferences
                val selectedCategoryInts = remember {
                    mutableStateOf(
                        preferenceManager.myPrefs.filterCI?.split(",")
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.toMutableList() ?: mutableListOf()
                    )
                }

                // Convert selected category integers back to names for the UI
                val selectedCategories = remember {
                    mutableStateOf(
                        selectedCategoryInts.value.mapNotNull { categoryId ->
                            categoryMap.entries.find { it.value == categoryId }?.key
                        }.toMutableList()
                    )
                }

                var showCategoryCheckboxes by remember { mutableStateOf(false) }

                Button(onClick = { showCategoryCheckboxes = !showCategoryCheckboxes }) {
                    Text(if (showCategoryCheckboxes) "Hide Categories" else "Show Categories")
                }

                if (showCategoryCheckboxes) {
                    MultiSelectDropdown(
                        title = "Category",
                        options = categoryOptions,
                        selectedOptions = selectedCategories.value,
                        onOptionsSelected = { selectedCategoryNames ->
                            selectedCategories.value = selectedCategoryNames.toMutableList()
                            selectedCategoryInts.value =
                                selectedCategoryNames.mapNotNull { categoryMap[it] }.toMutableList()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection
                val languageMap = remember {
                    mapOf(
                        "All Languages" to null,
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
                }
                val languageOptions = remember(languageMap) { languageMap.keys.toList() }

                // Load selected languages from preferences as integers
                val selectedLanguageInts = remember {
                    mutableStateOf(
                        preferenceManager.myPrefs.filterLI?.split(",")
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.toMutableList() ?: mutableListOf()
                    )
                }
                // Convert selected language integers back to names for the UI
                val selectedLanguages = remember {
                    mutableStateOf(
                        selectedLanguageInts.value.mapNotNull { languageId ->
                            languageMap.entries.find { it.value == languageId }?.key
                        }.toMutableList()
                    )
                }

                var showLanguageCheckboxes by remember { mutableStateOf(false) }

                Button(onClick = { showLanguageCheckboxes = !showLanguageCheckboxes }) {
                    Text(if (showLanguageCheckboxes) "Hide Languages" else "Show Languages")
                }

                if (showLanguageCheckboxes) {
                    MultiSelectDropdown(
                        title = "Language",
                        options = languageOptions,
                        selectedOptions = selectedLanguages.value,
                        onOptionsSelected = { selectedLanguageNames ->
                            selectedLanguages.value = selectedLanguageNames.toMutableList()
                            selectedLanguageInts.value =
                                selectedLanguageNames.mapNotNull { languageMap[it] }.toMutableList()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    // Reset Button
                    Button(
                        onClick = {
                            selectedQuality = "Auto"
                            selectedCategories.value = mutableListOf()
                            selectedCategoryInts.value = mutableListOf()
                            selectedLanguages.value = mutableListOf()
                            selectedLanguageInts.value = mutableListOf()

                            preferenceManager.apply {
                                myPrefs.filterQX = "Auto"
                                myPrefs.filterCI = ""
                                myPrefs.filterLI = ""
                                savePreferences()
                            }

                            onReset()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                    }

                    Button(onClick = {
                        onSelectionsMade(
                            qualityMap[selectedQuality],
                            selectedCategories.value,
                            selectedCategoryInts.value,
                            selectedLanguages.value,
                            selectedLanguageInts.value
                        )

                        preferenceManager.apply {
                            myPrefs.filterQX = selectedQuality
                            myPrefs.filterCI = selectedCategoryInts.value.joinToString(",")
                            myPrefs.filterLI = selectedLanguageInts.value.joinToString(",")
                            savePreferences()
                        }

                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun MultiSelectDropdown(
    title: String,
    options: List<String>,
    selectedOptions: List<String>,
    onOptionsSelected: (List<String>) -> Unit
) {
    Column {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isChecked = remember { mutableStateOf(selectedOptions.contains(option)) }
                    Checkbox(
                        checked = isChecked.value,
                        onCheckedChange = { newValue ->
                            isChecked.value = newValue
                            val mutableSelectedOptions = selectedOptions.toMutableList()
                            if (newValue) {
                                mutableSelectedOptions.add(option)
                            } else {
                                mutableSelectedOptions.remove(option)
                            }
                            onOptionsSelected(mutableSelectedOptions)
                        }
                    )
                    Text(text = option)
                }
            }
        }
    }
}

@Composable
fun DropdownSelection2(
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