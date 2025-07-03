package org.futo.inputmethod.latin.uix.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream


// TODO: Move this to a config file or similar
private const val DATABASE_URL = "http://192.168.1.221:8000/literatim.zip"

@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    inner()
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))
    }
}


@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier
        .padding(16.dp)
        .clearAndSetSemantics {
            this.text = AnnotatedString(text)
        }
    ) {
        Text(text, style = Typography.SmallMl)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}


@Composable
@Preview
fun SetupDownloadDatabase() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State management for download process
    var isDownloading by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Suspend function to handle the download process
    suspend fun downloadFile() {
        withContext(Dispatchers.IO) {
            // All network and file operations happen on IO thread
            val url = URL(DATABASE_URL)
            val connection = url.openConnection()
            val totalSize = connection.contentLength
            val input = connection.getInputStream()

            // Download to cache
            val zipFile = File(context.cacheDir, "literatim.zip")
            val output = FileOutputStream(zipFile)
            val buffer = ByteArray(8192)
            var downloaded = 0
            var read: Int
            
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                val newProgress = if (totalSize > 0) downloaded.toFloat() / totalSize else 0f
                
                // Update progress on main thread
                withContext(Dispatchers.Main) {
                    progress = newProgress
                }
            }
            output.close()
            input.close()
            // Unzip
            withContext(Dispatchers.Main) {
                isExtracting = true
                isDownloading = false
            }
            val zip = ZipInputStream(zipFile.inputStream())
            var entry = zip.nextEntry
            while (entry != null) {
                // NOTE: .zip file should only contain one .sqlite file. Will fail otherwise
                if (!entry.isDirectory && entry.name.endsWith(".sqlite")) {
                    // Write to a temporary file first - avoids the existence check false positive whilst extracting
                    val tempFile = File(context.filesDir, "literatim.sqlite.tmp")
                    val outStream = FileOutputStream(tempFile)
                    var len: Int
                    while (zip.read(buffer).also { len = it } > 0) {
                        outStream.write(buffer, 0, len)
                    }
                    outStream.close()
                    // After extraction, rename to final file
                    val finalFile = File(context.filesDir, "literatim.sqlite")
                    if (finalFile.exists()) {
                        finalFile.delete()
                    }
                    tempFile.renameTo(finalFile)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
            // Clean up zip file
            zipFile.delete()
        }
    }

    // Clean click handler that launches the download
    val startDownload = {
        isDownloading = true
        isExtracting = false
        errorMessage = null
        progress = 0f
        
        coroutineScope.launch {
            try {
                downloadFile()
                // Update UI state on main thread
                isDownloading = false
                isExtracting = false
            } catch (e: Exception) {
                // Update error state on main thread
                errorMessage = e.localizedMessage ?: "Download failed"
                isDownloading = false
                isExtracting = false
            }
        }
        Unit // Return Unit to match expected lambda type
    }

    SetupContainer {
        Column {
            Step(fraction = 1.0f/4.0f, text = stringResource(R.string.setup_step_1))

            Text(
                stringResource(R.string.setup_welcome_text),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Show different UI based on state
            when {
                isDownloading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Downloading dictionary...")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Text("${(progress * 100).toInt()}%")
                    }
                }
                isExtracting -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Extracting dictionary...")
                        // don't pass progress -> indeterminate
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = startDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Retry Download")
                        }
                    }
                }
                
                else -> {
                    Button(
                        onClick = startDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.download_dictionary_file))
                    }
                }
            }
        }
    }
}

// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME() {
    val context = LocalContext.current

    val launchImeOptions = {
        // TODO: look into direct boot to get rid of direct boot warning?
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        context.startActivity(intent)
    }

    SetupContainer {
        Column {
            Step(fraction = 2.0f/4.0f, text = stringResource(R.string.setup_step_2))

            Text(
                stringResource(R.string.setup_open_input_settings_text),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_open_input_settings))
            }
        }
    }
}


@Composable
@Preview
fun SetupChangeDefaultIME(doublePackage: Boolean = true) {
    val context = LocalContext.current

    val launchImeOptions = {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.showInputMethodPicker()

        (context as SettingsActivity).updateSystemState()
    }

    SetupContainer {
        Column {
            if(doublePackage) {
                Tip(stringResource(R.string.setup_warning_multiple_versions))
            }

            Step(fraction = 3.0f/4.0f, text = stringResource(R.string.setup_step_3))

            Text(
                stringResource(R.string.setup_active_input_method),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_switch_input_methods))
            }
        }
    }
}