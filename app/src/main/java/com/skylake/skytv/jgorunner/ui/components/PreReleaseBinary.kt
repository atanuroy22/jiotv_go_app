package com.skylake.skytv.jgorunner.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.core.update.DownloadModelNew
import com.skylake.skytv.jgorunner.core.update.Status
import com.skylake.skytv.jgorunner.data.SkySharedPref
import com.skylake.skytv.jgorunner.utils.CustButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreReleaseBinary(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Context, selectedRelease: String) -> Unit,
    onResetToStable: (Context) -> Unit
) {
    if (!isVisible) return
    val context = LocalContext.current

    val confirmFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    var releases by remember { mutableStateOf<List<String>?>(null) }
    var selectedRelease by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(isVisible, expanded) {
        if (isVisible && !expanded) {
            confirmFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            try {
                releases = fetchGithubReleases()
                selectedRelease = releases?.firstOrNull() ?: ""
                errorMessage = ""
            } catch (e: Exception) {
                releases = emptyList()
                errorMessage = "Failed to fetch releases. Check your connection."
            }
        } else {
            releases = null
            selectedRelease = ""
            expanded = false
            errorMessage = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).padding(end = 12.dp)
                )
                Text("Select Pre-Release Binary", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column {
                when {
                    releases == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    releases!!.isEmpty() -> Text(
                        "No releases found",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusable(true)
                                .onKeyEvent { event ->
                                    if (event.nativeKeyEvent.keyCode.toLong() == Key.DirectionCenter.keyCode
                                        || event.nativeKeyEvent.keyCode.toLong() == Key.Enter.keyCode
                                    ) {
                                        expanded = !expanded
                                        true
                                    } else false
                                }
                        ) {
                            TextField(
                                value = selectedRelease,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Choose Release") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                colors = TextFieldDefaults.colors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                releases!!.forEach { release ->
                                    DropdownMenuItem(
                                        text = { Text(release) },
                                        onClick = {
                                            selectedRelease = release
                                            expanded = false
                                            errorMessage = ""
                                            confirmFocusRequester.requestFocus()
                                        },
                                        modifier = Modifier.focusable(true)
                                    )
                                }
                            }
                        }
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CustButton(
                modifier = Modifier.focusRequester(confirmFocusRequester),
                onClick = {
                    if (selectedRelease.isNotBlank()) onConfirm(context, selectedRelease)
                    else errorMessage = "Please select a release!"
                },
            ) {
                Text("✨ Install")
            }
        },
        dismissButton = {
            Row {
                CustButton(
                    modifier = Modifier.focusRequester(resetFocusRequester),
                    onClick = { onResetToStable(context) }
                ) {
                    Text("↩️ Reset")
                }

                Spacer(modifier = Modifier.width(8.dp))

                CustButton(
                    modifier = Modifier.focusRequester(cancelFocusRequester),
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    )
}


// Use suspend for blocking network calls and optimize ABI/matching
suspend fun fetchGithubReleases(): List<String> = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/atanuroy22/jiotv_go/releases")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(responseBody)
            val list = mutableListOf<String>()
            for (i in 0 until minOf(10, jsonArray.length())) {
                val release = jsonArray.getJSONObject(i)
                val tag = release.optString("tag_name", "Unknown")
                val pre = release.optBoolean("prerelease", false)
                list.add(if (pre) "$tag (Pre-release)" else tag)
            }
            list
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// Updates handling ABI, downloading, context preference atomically
suspend fun performSelectedBinaryUpdate(
    context: Context,
    selectedTag: String,
    onDownloadStatusUpdate: (DownloadModelNew) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val releasesResponse = URL("https://api.github.com/repos/atanuroy22/jiotv_go/releases").readText()
        val releasesArray = JSONArray(releasesResponse)
        var selectedRelease: JSONObject? = null
        var assetFileName = ""
        var assetDownloadUrl = ""

        val selectedTagPure = selectedTag.replace(" (Pre-release)", "")

        loop@ for (i in 0 until releasesArray.length()) {
            val release = releasesArray.getJSONObject(i)
            val tagName = release.getString("tag_name")
            if (tagName.replace(" (Pre-release)", "") == selectedTagPure) {
                val assetsArray = release.getJSONArray("assets")
                val abis = Build.SUPPORTED_ABIS
                val suffix = when {
                    abis.contains("arm64-v8a") -> "-arm64"
                    abis.contains("armeabi-v7a") -> "5-armv7"
                    abis.contains("x86_64") -> "-amd64"
                    abis.contains("armeabi") -> "-arm"
                    else -> "-armv7"
                }
                for (j in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(j)
                    if (asset.getString("name").contains("jiotv_go-android$suffix")) {
                        selectedRelease = release
                        assetFileName = asset.getString("name")
                        assetDownloadUrl = asset.getString("browser_download_url")
                        break@loop
                    }
                }
            }
        }

        if (assetFileName.isEmpty() || assetDownloadUrl.isEmpty()) {
            onDownloadStatusUpdate(DownloadModelNew(Status.FAILED, assetFileName, 0, "No matching binary for ABI or tag"))
            return@withContext false
        }

        val pref = SkySharedPref.getInstance(context)
        val previousBinaryName = pref.myPrefs.jtvGoBinaryName
        previousBinaryName?.let {
            val previousFile = context.filesDir.resolve(it)
            if (previousFile.exists()) previousFile.delete()
        }
        pref.myPrefs.jtvGoBinaryName = null
        pref.myPrefs.jtvGoBinaryVersion = "v0.0.0"
        pref.savePreferences()

        val downloadResult = CompletableDeferred<Boolean>()

        downloadBinFile(
            url = assetDownloadUrl,
            fileName = assetFileName,
            path = context.filesDir.absolutePath,
            onDownloadStatusUpdate = { modelNew ->
                when (modelNew.status) {
                    Status.SUCCESS -> {
                        val tag = selectedRelease?.getString("tag_name") ?: "v0.0.0"
                        pref.myPrefs.jtvGoBinaryVersion = tag
                        pref.myPrefs.jtvGoBinaryName = assetFileName
                        pref.savePreferences()
                        downloadResult.complete(true)
                    }

                    Status.FAILED -> downloadResult.complete(false)
                    else -> { /* IN_PROGRESS */ }
                }
                onDownloadStatusUpdate(modelNew)
            }
        )

        return@withContext downloadResult.await()
    } catch (ex: Exception) {
        onDownloadStatusUpdate(DownloadModelNew(Status.FAILED, "", 0, ex.message ?: "Unknown error"))
        false
    }
}

fun downloadBinFile(
    url: String,
    fileName: String,
    path: String,
    onDownloadStatusUpdate: (DownloadModelNew) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onDownloadStatusUpdate(
                        DownloadModelNew(Status.FAILED, fileName, 0, "HTTP ${response.code}")
                    )
                    return@use
                }

                val file = File(path, fileName)
                response.body?.byteStream()?.use { inputStream ->
                    file.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        val contentLength = response.body?.contentLength() ?: -1L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = if (contentLength > 0) {
                                (totalBytesRead * 100 / contentLength).toInt()
                            } else -1
                            onDownloadStatusUpdate(
                                DownloadModelNew(Status.IN_PROGRESS, fileName, progress, "")
                            )
                        }
                    }
                }
                onDownloadStatusUpdate(DownloadModelNew(Status.SUCCESS, fileName, 100, ""))
            }
        } catch (e: Exception) {
            onDownloadStatusUpdate(
                DownloadModelNew(Status.FAILED, fileName, 0, e.message ?: "Unknown error")
            )
        }
    }
}
