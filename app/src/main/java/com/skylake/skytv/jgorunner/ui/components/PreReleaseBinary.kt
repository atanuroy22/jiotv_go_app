import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.skylake.skytv.jgorunner.R
import com.skylake.skytv.jgorunner.core.update.BinaryUpdater
import com.skylake.skytv.jgorunner.core.update.DownloadModelNew
import com.skylake.skytv.jgorunner.core.update.Status
import com.skylake.skytv.jgorunner.data.SkySharedPref
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
    onConfirm: (Context, selectedRelease: String) -> Unit
) {
    val context = LocalContext.current
    val confirmButtonFocusRequester = remember { FocusRequester() }
    var releases by remember { mutableStateOf<List<String>?>(null) }
    var selectedRelease by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showErrorToast by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            confirmButtonFocusRequester.requestFocus()
            try {
                val fetchedReleases = fetchGithubReleases()
                releases = fetchedReleases
                selectedRelease = if (fetchedReleases.isNotEmpty()) fetchedReleases[0] else ""
            } catch (e: Exception) {
                releases = emptyList()
                selectedRelease = ""
                showErrorToast = true
            }
        } else {
            releases = null
            selectedRelease = ""
            expanded = false
            showErrorToast = false
        }
    }

    if (showErrorToast) {
        Toast.makeText(context, "Failed to fetch releases. Please check your connection.", Toast.LENGTH_LONG).show()
        showErrorToast = false
    }


    if (!isVisible) return

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon: Painter = painterResource(id = R.drawable.logo)
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 9.dp)
                        .size(40.dp)
                )
                Text("Select Pre-Release Binary Version", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    releases == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    releases!!.isEmpty() -> {
                        Text("No releases found", modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                value = selectedRelease,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Choose Release") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                releases!!.forEach { release ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedRelease = release
                                            expanded = false
                                            Toast.makeText(context, "Selected: $release", Toast.LENGTH_SHORT).show()
                                        },
                                        text = { Text(release) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedRelease.isNotBlank()) {

                        onConfirm(context, selectedRelease)
                    } else {
                        Toast.makeText(context, "No release selected", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = releases?.isNotEmpty() == true,
                modifier = Modifier.focusRequester(confirmButtonFocusRequester)
            ) {
                Text("Download and Install")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}


suspend fun fetchGithubReleases(): List<String> = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/JioTV-Go/jiotv_go/releases")
            .build()
        val response = client.newCall(request).execute()
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
    } catch (e: Exception) {
        emptyList()
    }
}



suspend fun performSelectedBinaryUpdate(
    context: Context,
    selectedTag: String,
    onDownloadStatusUpdate: (DownloadModelNew) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val releasesResponse = URL("https://api.github.com/repos/JioTV-Go/jiotv_go/releases").readText()
        val releasesArray = JSONArray(releasesResponse)
        var binaryAssetInfo: JSONObject? = null
        var releaseVersion: String = "v0.0.0"
        var assetFileName: String = ""
        var assetDownloadUrl: String = ""

        for (i in 0 until releasesArray.length()) {
            val release = releasesArray.getJSONObject(i)
            val tagName = release.getString("tag_name").replace(" (Pre-release)", "")
            if (tagName == selectedTag.replace(" (Pre-release)", "")) {
                releaseVersion = tagName
                val assetsArray = release.getJSONArray("assets")
                val supportedABIs = Build.SUPPORTED_ABIS
                val releaseNameSuffix = when {
                    supportedABIs.contains("arm64-v8a") -> "-arm64"
                    supportedABIs.contains("armeabi-v7a") -> "5-armv7"
                    supportedABIs.contains("armeabi") -> "-arm"
                    supportedABIs.contains("x86_64") -> "-amd64"
                    else -> "-armv7"
                }
                for (j in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(j)
                    if (asset.getString("name").contains("jiotv_go-android$releaseNameSuffix")) {
                        binaryAssetInfo = asset
                        assetFileName = asset.getString("name")
                        assetDownloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                break
            }
        }
        if (binaryAssetInfo == null || assetDownloadUrl.isEmpty()) {
            onDownloadStatusUpdate(
                DownloadModelNew(Status.FAILED, assetFileName, 0, "No matching binary for ABI or tag")
            )
            return@withContext false
        }

        val preferenceManager = SkySharedPref.getInstance(context)
        val previousBinaryName = preferenceManager.myPrefs.jtvGoBinaryName
        if (!previousBinaryName.isNullOrEmpty()) {
            val previousBinaryFile = context.filesDir.resolve(previousBinaryName)
            if (previousBinaryFile.exists()) previousBinaryFile.delete()
        }
        preferenceManager.myPrefs.jtvGoBinaryName = null
        preferenceManager.myPrefs.jtvGoBinaryVersion = "v0.0.0"
        preferenceManager.savePreferences()

        val downloadResult = CompletableDeferred<Boolean>()

        downloadBinFile(
            url = assetDownloadUrl,
            fileName = assetFileName,
            path = context.filesDir.absolutePath,
            onDownloadStatusUpdate = { modelNew ->
                when (modelNew.status) {
                    Status.SUCCESS -> {
                        preferenceManager.myPrefs.jtvGoBinaryVersion = releaseVersion
                        preferenceManager.myPrefs.jtvGoBinaryName = assetFileName
                        preferenceManager.savePreferences()
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
        onDownloadStatusUpdate(
            DownloadModelNew(Status.FAILED, "", 0, ex.message ?: "Unknown error")
        )
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
                            } else {
                                -1
                            }
                            onDownloadStatusUpdate(
                                DownloadModelNew(Status.IN_PROGRESS, fileName, progress, "")
                            )
                        }
                    }
                }

                onDownloadStatusUpdate(
                    DownloadModelNew(Status.SUCCESS, fileName, 100, "")
                )
            }
        } catch (e: Exception) {
            onDownloadStatusUpdate(
                DownloadModelNew(Status.FAILED, fileName, 0, e.message ?: "Unknown error")
            )
        }
    }
}
