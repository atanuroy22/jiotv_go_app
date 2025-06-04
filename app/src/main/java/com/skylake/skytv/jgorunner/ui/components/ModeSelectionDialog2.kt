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
    var showMultiSelectDialog by remember { mutableStateOf(false) }


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
                // --- Quality Selection ---
                var selectedQuality by remember {
                    mutableStateOf(preferenceManager.myPrefs.filterQX ?: "auto")
                }
                val qualityMap = mapOf(
//                    "Not yet implemented" to "auto",
                    "Auto" to "auto",
                    "High" to "high",
                    "Medium" to "medium",
                    "Low" to "low"
                )
                val qualityOptions = qualityMap.keys.toList()
                var qualityDropdownExpanded by remember { mutableStateOf(false) }
                val selectedQualityLabel = qualityMap.entries.find { it.value == selectedQuality }?.key ?: selectedQuality[0]
                DropdownSelection2(
                    title = "Quality",
                    options = qualityOptions,
                    selectedOption = selectedQualityLabel.toString(),
                    onOptionSelected = { label ->
                        selectedQuality = (qualityMap[label] ?: "auto")
                    },
                    expanded = qualityDropdownExpanded,
                    onExpandChange = { qualityDropdownExpanded = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- TV Start Page Selection  ---
                val startScreenTV = mapOf(
                    "All Channels" to 0,
                    "Recent Channels" to 1,
//                    "Search page" to 2
                )
                val startOptionsTV = startScreenTV.keys.toList()
                // Load as Int, fallback to 0
                var selectedScreenTV by remember {
                    mutableIntStateOf(preferenceManager.myPrefs.selectedScreenTV?.toIntOrNull() ?: 0)
                }
                var screenDropdownExpanded by remember { mutableStateOf(false) }
                // Find the label for the selectedScreenTV int value
                val selectedScreenLabel = startScreenTV.entries.find { it.value == selectedScreenTV }?.key ?: startOptionsTV[0]
                DropdownSelection2(
                    title = "Select TV start page",
                    options = startOptionsTV,
                    selectedOption = selectedScreenLabel,
                    onOptionSelected = { label ->
                        selectedScreenTV = startScreenTV[label] ?: 0
                    },
                    expanded = screenDropdownExpanded,
                    onExpandChange = { screenDropdownExpanded = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Category Selection ---
                val categoryMap = mapOf(
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
                val categoryOptions = categoryMap.keys.toList()
                val selectedCategoryInts = remember {
                    mutableStateOf(
                        preferenceManager.myPrefs.filterCI?.split(",")
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.toMutableList() ?: mutableListOf()
                    )
                }
                val selectedCategories = remember {
                    mutableStateOf(
                        selectedCategoryInts.value.mapNotNull { id ->
                            categoryMap.entries.find { it.value == id }?.key
                        }.toMutableList()
                    )
                }
                var showCategoryCheckboxes by remember { mutableStateOf(false) }
                Column {
                    Text(text = "Select Categories", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showCategoryCheckboxes = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {

                            Text("Categories")
                        }
                    }
                }
                if (showCategoryCheckboxes) {
                    Dialog(onDismissRequest = { showCategoryCheckboxes = false }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MultiSelectDropdown(
                                    title = "Category",
                                    options = categoryOptions,
                                    selectedOptions = selectedCategories.value,
                                    onOptionsSelected = { names ->
                                        selectedCategories.value = names.toMutableList()
                                        selectedCategoryInts.value = names.mapNotNull { categoryMap[it] }.toMutableList()
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                               Button(
                                    onClick = { showCategoryCheckboxes = false },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }

                if (false) {
                    MultiSelectDropdown(
                        title = "Category",
                        options = categoryOptions,
                        selectedOptions = selectedCategories.value,
                        onOptionsSelected = { names ->
                            selectedCategories.value = names.toMutableList()
                            selectedCategoryInts.value = names.mapNotNull { categoryMap[it] }.toMutableList()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Language Selection ---
                val languageMap = mapOf(
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
                val languageOptions = languageMap.keys.toList()
                val selectedLanguageInts = remember {
                    mutableStateOf(
                        preferenceManager.myPrefs.filterLI?.split(",")
                            ?.mapNotNull { it.toIntOrNull() }
                            ?.toMutableList() ?: mutableListOf()
                    )
                }
                val selectedLanguages = remember {
                    mutableStateOf(
                        selectedLanguageInts.value.mapNotNull { id ->
                            languageMap.entries.find { it.value == id }?.key
                        }.toMutableList()
                    )
                }
                var showLanguageCheckboxes by remember { mutableStateOf(false) }
                Column {
                    Text(text = "Select Languages", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showLanguageCheckboxes = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {

                            Text("Languages")
                        }
                    }
                }

                if (showLanguageCheckboxes) {
                    Dialog(onDismissRequest = { showLanguageCheckboxes = false }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MultiSelectDropdown(
                                    title = "Language",
                                    options = languageOptions,
                                    selectedOptions = selectedLanguages.value,
                                    onOptionsSelected = { names ->
                                        selectedLanguages.value = names.toMutableList()
                                        selectedLanguageInts.value = names.mapNotNull { languageMap[it] }.toMutableList()
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showLanguageCheckboxes = false },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }

                if (false) {
                    MultiSelectDropdown(
                        title = "Language",
                        options = languageOptions,
                        selectedOptions = selectedLanguages.value,
                        onOptionsSelected = { names ->
                            selectedLanguages.value = names.toMutableList()
                            selectedLanguageInts.value = names.mapNotNull { languageMap[it] }.toMutableList()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Action Buttons ---
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.medium) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            selectedQuality = "auto"
                            selectedScreenTV = 0
                            selectedCategories.value.clear()
                            selectedCategoryInts.value.clear()
                            selectedLanguages.value.clear()
                            selectedLanguageInts.value.clear()
                            preferenceManager.apply {
                                myPrefs.selectedScreenTV = "0"
                                myPrefs.filterQX = "auto"
                                myPrefs.filterCI = ""
                                myPrefs.filterLI = ""
                                savePreferences()
                            }
                            onReset()
                        },
                        shape = MaterialTheme.shapes.medium
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
                            myPrefs.selectedScreenTV = selectedScreenTV.toString()
                            myPrefs.filterQX = selectedQuality
                            myPrefs.filterCI = selectedCategoryInts.value.joinToString(",")
                            myPrefs.filterLI = selectedLanguageInts.value.joinToString(",")
                            savePreferences()
                        }
                        onDismiss()
                    },
                        shape = MaterialTheme.shapes.medium) {
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
                val isChecked = selectedOptions.contains(option)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            val mutableSelected = selectedOptions.toMutableList()
                            if (checked) {
                                if (!mutableSelected.contains(option)) mutableSelected.add(option)
                            } else {
                                mutableSelected.remove(option)
                            }
                            onOptionsSelected(mutableSelected)
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
                shape = MaterialTheme.shapes.medium,
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
