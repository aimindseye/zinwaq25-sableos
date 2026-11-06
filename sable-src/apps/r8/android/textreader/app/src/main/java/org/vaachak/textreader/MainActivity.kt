/*
 *
 *  *
 *  *  *  Copyright (c) 2026 Piyush Daiya
 *  *  *  *
 *  *  *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  *  *  * of this software and associated documentation files (the "Software"), to deal
 *  *  *  * in the Software without restriction, including without limitation the rights
 *  *  *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  *  *  * copies of the Software, and to permit persons to whom the Software is
 *  *  *  * furnished to do so, subject to the following conditions:
 *  *  *  *
 *  *  *  * The above copyright notice and this permission notice shall be included in all
 *  *  *  * copies or substantial portions of the Software.
 *  *  *  *
 *  *  *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  *  *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  *  *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  *  *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  *  *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  *  *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  *  *  * SOFTWARE.
 *  *
 *
 */

package org.vaachak.textreader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

private const val PLAY_UTTERANCE = "play_text"
private const val EXPORT_UTTERANCE = "export_audio"

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var isTtsReady by mutableStateOf(false)

    // UI State for Playback
    var isPlaying by mutableStateOf(false)

    // Temporary variables for Audio Export tracking
    var pendingAudioFile: File? = null
    var targetExportUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)

        // Process incoming text from Share Menu or Text Selection
        var sharedText = ""
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            sharedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        ttsEngine = tts,
                        isTtsReady = isTtsReady,
                        isPlaying = isPlaying,
                        initialText = sharedText,
                        onSpeakText = { text, locale ->
                            if (isTtsReady && text.isNotBlank()) {
                                tts.language = locale
                                // Pass a unique Utterance ID so our listener can track it
                                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, PLAY_UTTERANCE)
                            }
                        },
                        onStopText = {
                            tts.stop()
                            isPlaying = false // Eagerly update UI on manual stop
                        }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            // Unified Listener for both Playback UI and Audio Export
            tts.setOnUtteranceProgressListener(utteranceListener)
        }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId == PLAY_UTTERANCE) runOnUiThread { isPlaying = true }
        }

        override fun onError(utteranceId: String?) {
            if (utteranceId == PLAY_UTTERANCE) runOnUiThread { isPlaying = false }
            else showToast("Error processing audio.", Toast.LENGTH_SHORT)
        }

        override fun onDone(utteranceId: String?) {
            when (utteranceId) {
                PLAY_UTTERANCE -> runOnUiThread { isPlaying = false } // Reset Play button when reading finishes
                EXPORT_UTTERANCE -> saveExportedAudio()
            }
        }
    }

    /** The temporary file is ready: copy it to the location the user chose, then remove it. */
    private fun saveExportedAudio() {
        val file = pendingAudioFile ?: return
        val uri = targetExportUri ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                file.inputStream().use { input -> input.copyTo(os) }
            }
            if (!file.delete()) file.deleteOnExit() // Clean up the temp file
            showToast("Audio saved successfully!", Toast.LENGTH_LONG)
        } catch (e: Exception) {
            showToast("Failed to save file.", Toast.LENGTH_SHORT)
        }
    }

    private fun showToast(message: String, duration: Int) {
        runOnUiThread { Toast.makeText(this@MainActivity, message, duration).show() }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    ttsEngine: TextToSpeech,
    isTtsReady: Boolean,
    isPlaying: Boolean,
    initialText: String,
    onSpeakText: (String, Locale) -> Unit,
    onStopText: () -> Unit
) {
    val context = LocalContext.current
    val (currentText, setCurrentText) = remember { mutableStateOf(initialText) }
    val (currentTtsLocale, setCurrentTtsLocale) = remember { mutableStateOf(Locale.US) }
    val (showTtsSettings, setShowTtsSettings) = remember { mutableStateOf(false) }
    val (isScannerOpen, setScannerOpen) = remember { mutableStateOf(false) }
    val (useDevanagari, setUseDevanagari) = remember { mutableStateOf(false) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Launcher to select a text file
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> setCurrentText(TextReaderUtil.readTextFromStream(stream)) } }
    }

    // Launcher to let the user choose where to save the audio file
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        uri?.let { targetUri ->
            Toast.makeText(context, "Exporting Audio...", Toast.LENGTH_SHORT).show()

            // Save to temporary cache first
            val tempFile = File(context.cacheDir, "temp_export.wav")

            // Send references back to MainActivity for processing
            (context as? MainActivity)?.let { activity ->
                activity.pendingAudioFile = tempFile
                activity.targetExportUri = targetUri
            }

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, EXPORT_UTTERANCE)

            ttsEngine.language = currentTtsLocale
            ttsEngine.synthesizeToFile(currentText, params, tempFile, EXPORT_UTTERANCE)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { safeUri ->
            recognizeImageText(context, safeUri, useDevanagari) { recognized ->
                recognized?.let(setCurrentText)
                if (isScannerOpen) setScannerOpen(false)
            }
        }
    }

    if (isScannerOpen) {
        CameraScannerView(
            isDevanagari = useDevanagari,
            onScriptChange = setUseDevanagari,
            onTextScanned = { setCurrentText(it) },
            onOpenGallery = { galleryLauncher.launch("image/*") },
            onDismiss = { setScannerOpen(false) }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("V A A C H A K", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp), fontWeight = FontWeight.Light) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 10.dp)) {

                OcrScriptMenu(useDevanagari = useDevanagari, onScriptChange = setUseDevanagari)

                OutlinedTextField(
                    value = currentText,
                    onValueChange = setCurrentText,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    placeholder = { Text("Scan, paste or upload text...") },
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionIcon(Icons.Default.ContentPaste, "Paste") {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.let { setCurrentText(it.toString()) }
                    }
                    ActionIcon(Icons.AutoMirrored.Filled.TextSnippet, "Files") { fileLauncher.launch(arrayOf("text/plain")) }
                    ActionIcon(Icons.Default.CameraAlt, "Scan") {
                        if (cameraPermission.status.isGranted) setScannerOpen(true) else cameraPermission.launchPermissionRequest()
                    }
                    ActionIcon(Icons.Default.PhotoLibrary, "Gallery") {
                        galleryLauncher.launch("image/*")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ProfessionalTranslationRow(currentText) { translated, tag ->
                    setCurrentText(translated)
                    setCurrentTtsLocale(Locale.forLanguageTag(tag))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // DYNAMIC MEDIA CONTROLS
                PlaybackControls(
                    isPlaying = isPlaying,
                    hasText = currentText.isNotBlank(),
                    canSpeak = isTtsReady && currentText.isNotBlank() && !isPlaying,
                    onStop = onStopText,
                    onPlay = { onSpeakText(currentText, currentTtsLocale) },
                    // Triggers the Android File Picker!
                    onExport = { exportLauncher.launch("Vaachak_Audio_${System.currentTimeMillis()}.wav") },
                    onOpenSettings = { setShowTtsSettings(true) },
                )
            }
        }
    }

    if (showTtsSettings) {
        TtsSettingsDialog(ttsEngine, currentTtsLocale) { setShowTtsSettings(false) }
    }
}

/** Runs on-device OCR over a picked image; [onResult] gets the text, or null when there was none or it failed. */
private fun recognizeImageText(context: Context, uri: Uri, useDevanagari: Boolean, onResult: (String?) -> Unit) {
    Toast.makeText(context, "Analyzing image...", Toast.LENGTH_SHORT).show()
    try {
        val image = InputImage.fromFilePath(context, uri)
        val options = if (useDevanagari) DevanagariTextRecognizerOptions.Builder().build() else TextRecognizerOptions.DEFAULT_OPTIONS
        val recognizer = TextRecognition.getClient(options)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val found = visionText.text.takeIf { it.isNotBlank() }
                if (found == null) Toast.makeText(context, "No text found in image.", Toast.LENGTH_SHORT).show()
                onResult(found)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to recognize text.", Toast.LENGTH_SHORT).show()
            }
    } catch (e: Exception) {
        Toast.makeText(context, "Error loading image.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun OcrScriptMenu(useDevanagari: Boolean, onScriptChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = true }) {
            Text(if (useDevanagari) "OCR: Hindi/Marathi" else "OCR: English/Spanish", fontSize = 12.sp)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("English/Spanish") }, onClick = { onScriptChange(false); expanded = false })
            DropdownMenuItem(text = { Text("Hindi/Marathi") }, onClick = { onScriptChange(true); expanded = false })
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    hasText: Boolean,
    canSpeak: Boolean,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onExport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val idleTint = if (hasText && !isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {

        // STOP BUTTON: Enabled & highlighted (Error Red) ONLY when playing
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(56.dp),
            enabled = isPlaying
        ) {
            Icon(
                Icons.Default.StopCircle, "Stop",
                modifier = Modifier.size(36.dp),
                tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
            )
        }

        // PLAY BUTTON: Disabled/Dimmed when already playing or empty text
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(72.dp),
            enabled = canSpeak
        ) {
            Icon(
                Icons.Default.PlayCircleFilled, "Play",
                modifier = Modifier.size(64.dp),
                tint = idleTint
            )
        }

        // EXPORT AUDIO BUTTON (Uses Native File Picker)
        IconButton(
            onClick = { if (canSpeak) onExport() },
            modifier = Modifier.size(56.dp),
            enabled = canSpeak
        ) {
            Icon(
                Icons.Default.SaveAlt, "Export Audio",
                modifier = Modifier.size(36.dp),
                tint = idleTint
            )
        }

        // SETTINGS BUTTON
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Tune, "Settings", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
