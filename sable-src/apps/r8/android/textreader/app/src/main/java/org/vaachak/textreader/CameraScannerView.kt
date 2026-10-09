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

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner // Updated Import
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.annotation.VisibleForTesting

@androidx.annotation.OptIn(ExperimentalGetImage::class) // Acknowledges the experimental CameraX API
@Composable
fun CameraScannerView(
    isDevanagari: Boolean,
    onScriptChange: (Boolean) -> Unit,
    onTextScanned: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val recognizer = remember(isDevanagari) {
        val options = if (isDevanagari) DevanagariTextRecognizerOptions.Builder().build() else TextRecognizerOptions.DEFAULT_OPTIONS
        TextRecognition.getClient(options)
    }

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var detectedText by remember { mutableStateOf("Point camera at text...") }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            isPinchToZoomEnabled = true
        }
    }

    LaunchedEffect(zoomRatio) { cameraController.setZoomRatio(zoomRatio) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    controller = cameraController
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(Unit) {
            // Replaced context.mainExecutor with ContextCompat to support API < 28
            cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                val img = proxy.image
                if (img != null) {
                    recognizer.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { if (it.text.isNotBlank()) detectedText = it.text }
                        .addOnCompleteListener { proxy.close() }
                } else proxy.close()
            }
        }

        ScannerTopBar(
            isDevanagari = isDevanagari,
            onScriptChange = onScriptChange,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        ScannerBottomControls(
            zoomRatio = zoomRatio,
            detectedText = detectedText,
            onZoomChange = { zoomRatio = it },
            onOpenGallery = onOpenGallery,
            onCapture = { onTextScanned(detectedText); onDismiss() },
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ---------------------------------------------------------------------------
// Extracted Helper Composables to reduce Cognitive Complexity
// ---------------------------------------------------------------------------

@Composable
@VisibleForTesting
internal fun ScannerTopBar(
    isDevanagari: Boolean,
    onScriptChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var expanded by remember { mutableStateOf(false) }
        Text("Detect Script: ", color = Color.White, style = MaterialTheme.typography.labelMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(if (isDevanagari) "Hindi/Marathi" else "English/Spanish", color = Color.White)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("English/Spanish") }, onClick = { onScriptChange(false); expanded = false })
                DropdownMenuItem(text = { Text("Hindi/Marathi") }, onClick = { onScriptChange(true); expanded = false })
            }
        }
    }
}

@Composable
@VisibleForTesting
internal fun ScannerBottomControls(
    zoomRatio: Float,
    detectedText: String,
    onZoomChange: (Float) -> Unit,
    onOpenGallery: () -> Unit,
    onCapture: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
            Slider(
                value = zoomRatio,
                onValueChange = onZoomChange,
                valueRange = 1f..4f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                )
            )
            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
        }

        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = detectedText,
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                maxLines = 3
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onOpenGallery,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Color.White)
            }

            FilledIconButton(
                onClick = onCapture,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Done, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }

            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}