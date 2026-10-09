package org.sableos.start.visualreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import org.sableos.design.AccentPreset
import org.sableos.design.AppearanceMode
import org.sableos.design.SableAppearance
import org.sableos.design.SableTheme
import android.graphics.Paint as AndroidPaint

/**
 * Design-only R9 daily-driver review.
 *
 * This activity is intentionally static-fixture driven. It is not production
 * launcher/app plumbing and must never be added to the Panther product graph.
 *
 * Icon glyph geometry is derived from Phosphor Icons regular SVGs at pinned
 * upstream commit 2b75f3ad12b420c9504ef05df8d2564a28f8500e (MIT).
 */
class R9DailyDriverDesignReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val frame = ReviewFrame.fromStableValue(intent.getStringExtra(EXTRA_FRAME))
        setContent {
            val light = frame == ReviewFrame.StartLight
            SableTheme(
                appearance =
                    SableAppearance(
                        mode = if (light) AppearanceMode.Light else AppearanceMode.Dark,
                        accent = AccentPreset.Blue,
                    ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ReviewRoot(frame)
                }
            }
        }
    }

    companion object {
        const val EXTRA_FRAME = "frame"
    }
}

private enum class ReviewFrame(
    val stableValue: String,
    val title: String,
) {
    Icons("icons", "icon system"),
    StartDark("start-dark", "start · dark"),
    StartLight("start-light", "start · light"),
    Live("live", "live"),
    Calendar("calendar", "calendar"),
    Clock("clock", "clock"),
    Camera("camera", "camera"),
    Files("files", "files"),
    Photos("photos", "photos"),
    Browser("browser", "vanadium"),
    Messages("messages", "messages"),
    ;

    companion object {
        fun fromStableValue(value: String?): ReviewFrame = entries.firstOrNull { it.stableValue == value } ?: StartDark
    }
}

private data class ReviewGlyph(
    val name: String,
    val pathData: String,
)

private data class ReviewApp(
    val label: String,
    val glyph: ReviewGlyph,
    val accent: Color,
)

private val SableBlue = Color(0xFF4D9CFF)
private val SableGreen = Color(0xFF35C66B)
private val SablePurple = Color(0xFF7A42E8)
private val SableOrange = Color(0xFFF28C45)
private val SableSlate = Color(0xFF64748B)
private val SableRed = Color(0xFFE45B66)

private object PhosphorReviewGlyphs {
    val Phone =
        ReviewGlyph(
            "phone-call",
            "M144.27,45.93a8,8,0,0,1,9.8-5.66,86.22,86.22,0,0,1,61.66,61.66,8,8,0,0,1-5.66,9.8A8.23,8.23,0,0,1,208,112a8,8,0,0,1-7.73-5.94,70.35,70.35,0,0,0-50.33-50.33A8,8,0,0,1,144.27,45.93Zm-2.33,41.8c13.79,3.68,22.65,12.54,26.33,26.33A8,8,0,0,0,176,120a8.23,8.23,0,0,0,2.07-.27,8,8,0,0,0,5.66-9.8c-5.12-19.16-18.5-32.54-37.66-37.66a8,8,0,1,0-4.13,15.46Zm81.94,95.35A56.26,56.26,0,0,1,168,232C88.6,232,24,167.4,24,88A56.26,56.26,0,0,1,72.92,32.12a16,16,0,0,1,16.62,9.52l21.12,47.15,0,.12A16,16,0,0,1,109.39,104c-.18.27-.37.52-.57.77L88,129.45c7.49,15.22,23.41,31,38.83,38.51l24.34-20.71a8.12,8.12,0,0,1,.75-.56,16,16,0,0,1,15.17-1.4l.13.06,47.11,21.11A16,16,0,0,1,223.88,183.08Zm-15.88-2s-.07,0-.11,0h0l-47-21.05-24.35,20.71a8.44,8.44,0,0,1-.74.56,16,16,0,0,1-15.75,1.14c-18.73-9.05-37.4-27.58-46.46-46.11a16,16,0,0,1,1-15.7,6.13,6.13,0,0,1,.57-.77L96,95.15l-21-47a.61.61,0,0,1,0-.12A40.2,40.2,0,0,0,40,88,128.14,128.14,0,0,0,168,216,40.21,40.21,0,0,0,208,181.07Z",
        )
    val Messages =
        ReviewGlyph(
            "chat-circle-dots",
            "M140,128a12,12,0,1,1-12-12A12,12,0,0,1,140,128ZM84,116a12,12,0,1,0,12,12A12,12,0,0,0,84,116Zm88,0a12,12,0,1,0,12,12A12,12,0,0,0,172,116Zm60,12A104,104,0,0,1,79.12,219.82L45.07,231.17a16,16,0,0,1-20.24-20.24l11.35-34.05A104,104,0,1,1,232,128Zm-16,0A88,88,0,1,0,51.81,172.06a8,8,0,0,1,.66,6.54L40,216,77.4,203.53a7.85,7.85,0,0,1,2.53-.42,8,8,0,0,1,4,1.08A88,88,0,0,0,216,128Z",
        )
    val Mail =
        ReviewGlyph(
            "envelope-simple",
            "M224,48H32a8,8,0,0,0-8,8V192a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A8,8,0,0,0,224,48ZM203.43,64,128,133.15,52.57,64ZM216,192H40V74.19l82.59,75.71a8,8,0,0,0,10.82,0L216,74.19V192Z",
        )
    val Calendar =
        ReviewGlyph(
            "calendar-dots",
            "M208,32H184V24a8,8,0,0,0-16,0v8H88V24a8,8,0,0,0-16,0v8H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32ZM72,48v8a8,8,0,0,0,16,0V48h80v8a8,8,0,0,0,16,0V48h24V80H48V48ZM208,208H48V96H208V208Zm-68-76a12,12,0,1,1-12-12A12,12,0,0,1,140,132Zm44,0a12,12,0,1,1-12-12A12,12,0,0,1,184,132ZM96,172a12,12,0,1,1-12-12A12,12,0,0,1,96,172Zm44,0a12,12,0,1,1-12-12A12,12,0,0,1,140,172Zm44,0a12,12,0,1,1-12-12A12,12,0,0,1,184,172Z",
        )
    val Weather =
        ReviewGlyph(
            "cloud-sun",
            "M164,72a76.2,76.2,0,0,0-20.26,2.73,55.63,55.63,0,0,0-9.41-11.54l9.51-13.57a8,8,0,1,0-13.11-9.18L121.22,54A55.9,55.9,0,0,0,96,48c-.58,0-1.16,0-1.74,0L91.37,31.71a8,8,0,1,0-15.75,2.77L78.5,50.82A56.1,56.1,0,0,0,55.23,65.67L41.61,56.14a8,8,0,1,0-9.17,13.11L46,78.77A55.55,55.55,0,0,0,40,104c0,.57,0,1.15,0,1.72L23.71,108.6a8,8,0,0,0,1.38,15.88,8.24,8.24,0,0,0,1.39-.12l16.32-2.88a55.74,55.74,0,0,0,5.86,12.42A52,52,0,0,0,84,224h80a76,76,0,0,0,0-152ZM56,104a40,40,0,0,1,72.54-23.24,76.26,76.26,0,0,0-35.62,40,52.14,52.14,0,0,0-31,4.17A40,40,0,0,1,56,104ZM164,208H84a36,36,0,1,1,4.78-71.69c-.37,2.37-.63,4.79-.77,7.23a8,8,0,0,0,16,.92,58.91,58.91,0,0,1,1.88-11.81c0-.16.09-.32.12-.48A60.06,60.06,0,1,1,164,208Z",
        )
    val Photos =
        ReviewGlyph(
            "image-square",
            "M208,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V48A16,16,0,0,0,208,32ZM48,48H208v77.38l-24.69-24.7a16,16,0,0,0-22.62,0L53.37,208H48ZM208,208H76l96-96,36,36v60ZM96,120A24,24,0,1,0,72,96,24,24,0,0,0,96,120Zm0-32a8,8,0,1,1-8,8A8,8,0,0,1,96,88Z",
        )
    val Media =
        ReviewGlyph(
            "music-notes",
            "M212.92,17.69a8,8,0,0,0-6.86-1.45l-128,32A8,8,0,0,0,72,56V166.08A36,36,0,1,0,88,196V110.25l112-28v51.83A36,36,0,1,0,216,164V24A8,8,0,0,0,212.92,17.69ZM52,216a20,20,0,1,1,20-20A20,20,0,0,1,52,216ZM88,93.75V62.25l112-28v31.5ZM180,184a20,20,0,1,1,20-20A20,20,0,0,1,180,184Z",
        )
    val Camera =
        ReviewGlyph(
            "camera",
            "M208,56H180.28L166.65,35.56A8,8,0,0,0,160,32H96a8,8,0,0,0-6.65,3.56L75.71,56H48A24,24,0,0,0,24,80V192a24,24,0,0,0,24,24H208a24,24,0,0,0,24-24V80A24,24,0,0,0,208,56Zm8,136a8,8,0,0,1-8,8H48a8,8,0,0,1-8-8V80a8,8,0,0,1,8-8H80a8,8,0,0,0,6.66-3.56L100.28,48h55.43l13.63,20.44A8,8,0,0,0,176,72h32a8,8,0,0,1,8,8ZM128,88a44,44,0,1,0,44,44A44.05,44.05,0,0,0,128,88Zm0,72a28,28,0,1,1,28-28A28,28,0,0,1,128,160Z",
        )
    val Calculator =
        ReviewGlyph(
            "calculator",
            "M80,120h96a8,8,0,0,0,8-8V64a8,8,0,0,0-8-8H80a8,8,0,0,0-8,8v48A8,8,0,0,0,80,120Zm8-48h80v32H88ZM200,24H56A16,16,0,0,0,40,40V216a16,16,0,0,0,16,16H200a16,16,0,0,0,16-16V40A16,16,0,0,0,200,24Zm0,192H56V40H200ZM100,148a12,12,0,1,1-12-12A12,12,0,0,1,100,148Zm40,0a12,12,0,1,1-12-12A12,12,0,0,1,140,148Zm40,0a12,12,0,1,1-12-12A12,12,0,0,1,180,148Zm-80,40a12,12,0,1,1-12-12A12,12,0,0,1,100,188Zm40,0a12,12,0,1,1-12-12A12,12,0,0,1,140,188Zm40,0a12,12,0,1,1-12-12A12,12,0,0,1,180,188Z",
        )
    val Files =
        ReviewGlyph(
            "folder-simple",
            "M216,72H130.67L102.93,51.2a16.12,16.12,0,0,0-9.6-3.2H40A16,16,0,0,0,24,64V200a16,16,0,0,0,16,16H216.89A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72Zm0,128H40V64H93.33L123.2,86.4A8,8,0,0,0,128,88h88Z",
        )
    val Reader =
        ReviewGlyph(
            "book-open-text",
            "M232,48H160a40,40,0,0,0-32,16A40,40,0,0,0,96,48H24a8,8,0,0,0-8,8V200a8,8,0,0,0,8,8H96a24,24,0,0,1,24,24,8,8,0,0,0,16,0,24,24,0,0,1,24-24h72a8,8,0,0,0,8-8V56A8,8,0,0,0,232,48ZM96,192H32V64H96a24,24,0,0,1,24,24V200A39.81,39.81,0,0,0,96,192Zm128,0H160a39.81,39.81,0,0,0-24,8V88a24,24,0,0,1,24-24h64ZM160,88h40a8,8,0,0,1,0,16H160a8,8,0,0,1,0-16Zm48,40a8,8,0,0,1-8,8H160a8,8,0,0,1,0-16h40A8,8,0,0,1,208,128Zm0,32a8,8,0,0,1-8,8H160a8,8,0,0,1,0-16h40A8,8,0,0,1,208,160Z",
        )
    val Clock =
        ReviewGlyph(
            "clock",
            "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm64-88a8,8,0,0,1-8,8H128a8,8,0,0,1-8-8V72a8,8,0,0,1,16,0v48h48A8,8,0,0,1,192,128Z",
        )
    val Browser =
        ReviewGlyph(
            "browser",
            "M216,40H40A16,16,0,0,0,24,56V200a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A16,16,0,0,0,216,40Zm0,16V88H40V56Zm0,144H40V104H216v96Z",
        )
    val Grid =
        ReviewGlyph(
            "grid-nine",
            "M216,48H40A16,16,0,0,0,24,64V192a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V64A16,16,0,0,0,216,48ZM104,144V112h48v32Zm48,16v32H104V160ZM40,112H88v32H40Zm64-16V64h48V96Zm64,16h48v32H168Zm48-16H168V64h48ZM88,64V96H40V64ZM40,160H88v32H40Zm176,32H168V160h48v32Z",
        )
    val Bomb =
        ReviewGlyph(
            "bomb",
            "M248,32h0a8,8,0,0,0-8,8,52.66,52.66,0,0,1-3.57,17.39C232.38,67.22,225.7,72,216,72c-11.06,0-18.85-9.76-29.49-24.65C176,32.66,164.12,16,144,16c-16.39,0-29,8.89-35.43,25a66.07,66.07,0,0,0-3.9,15H88A16,16,0,0,0,72,72v9.59A88,88,0,0,0,112,248h1.59A88,88,0,0,0,152,81.59V72a16,16,0,0,0-16-16H120.88a46.76,46.76,0,0,1,2.69-9.37C127.62,36.78,134.3,32,144,32c11.06,0,18.85,9.76,29.49,24.65C184,71.34,195.88,88,216,88c16.39,0,29-8.89,35.43-25A68.69,68.69,0,0,0,256,40,8,8,0,0,0,248,32ZM140.8,94a72,72,0,1,1-57.6,0A8,8,0,0,0,88,86.66V72h48V86.66A8,8,0,0,0,140.8,94ZM111.89,209.32A8,8,0,0,1,104,216a8.52,8.52,0,0,1-1.33-.11,57.5,57.5,0,0,1-46.57-46.57,8,8,0,1,1,15.78-2.64,41.29,41.29,0,0,0,33.43,33.43A8,8,0,0,1,111.89,209.32Z",
        )
    val Settings =
        ReviewGlyph(
            "gear-six",
            "M128,80a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Zm109.94-52.79a8,8,0,0,0-3.89-5.4l-29.83-17-.12-33.62a8,8,0,0,0-2.83-6.08,111.91,111.91,0,0,0-36.72-20.67,8,8,0,0,0-6.46.59L128,41.85,97.88,25a8,8,0,0,0-6.47-.6A112.1,112.1,0,0,0,54.73,45.15a8,8,0,0,0-2.83,6.07l-.15,33.65-29.83,17a8,8,0,0,0-3.89,5.4,106.47,106.47,0,0,0,0,41.56,8,8,0,0,0,3.89,5.4l29.83,17,.12,33.62a8,8,0,0,0,2.83,6.08,111.91,111.91,0,0,0,36.72,20.67,8,8,0,0,0,6.46-.59L128,214.15,158.12,231a7.91,7.91,0,0,0,3.9,1,8.09,8.09,0,0,0,2.57-.42,112.1,112.1,0,0,0,36.68-20.73,8,8,0,0,0,2.83-6.07l.15-33.65,29.83-17a8,8,0,0,0,3.89-5.4A106.47,106.47,0,0,0,237.94,107.21Zm-15,34.91-28.57,16.25a8,8,0,0,0-3,3c-.58,1-1.19,2.06-1.81,3.06a7.94,7.94,0,0,0-1.22,4.21l-.15,32.25a95.89,95.89,0,0,1-25.37,14.3L134,199.13a8,8,0,0,0-3.91-1h-.19c-1.21,0-2.43,0-3.64,0a8.08,8.08,0,0,0-4.1,1l-28.84,16.1A96,96,0,0,1,67.88,201l-.11-32.2a8,8,0,0,0-1.22-4.22c-.62-1-1.23-2-1.8-3.06a8.09,8.09,0,0,0-3-3.06l-28.6-16.29a90.49,90.49,0,0,1,0-28.26L61.67,97.63a8,8,0,0,0,3-3c.58-1,1.19-2.06,1.81-3.06a7.94,7.94,0,0,0,1.22-4.21l.15-32.25a95.89,95.89,0,0,1,25.37-14.3L122,56.87a8,8,0,0,0,4.1,1c1.21,0,2.43,0,3.64,0a8.08,8.08,0,0,0,4.1-1l28.84-16.1A96,96,0,0,1,188.12,55l.11,32.2a8,8,0,0,0,1.22,4.22c.62,1,1.23,2,1.8,3.06a8.09,8.09,0,0,0,3,3.06l28.6,16.29A90.49,90.49,0,0,1,222.9,142.12Z",
        )
}

private val ReviewApps =
    listOf(
        ReviewApp("Phone", PhosphorReviewGlyphs.Phone, SableBlue),
        ReviewApp("Sable Messages", PhosphorReviewGlyphs.Messages, SableGreen),
        ReviewApp("Sable Mail", PhosphorReviewGlyphs.Mail, SableSlate),
        ReviewApp("Calendar", PhosphorReviewGlyphs.Calendar, SableOrange),
        ReviewApp("Sable Weather", PhosphorReviewGlyphs.Weather, SableBlue),
        ReviewApp("Photos", PhosphorReviewGlyphs.Photos, SablePurple),
        ReviewApp("Sable Media", PhosphorReviewGlyphs.Media, SablePurple),
        ReviewApp("Camera", PhosphorReviewGlyphs.Camera, SableSlate),
        ReviewApp("Sable Calculator", PhosphorReviewGlyphs.Calculator, SableBlue),
        ReviewApp("Files", PhosphorReviewGlyphs.Files, SableOrange),
        ReviewApp("Sable Reader", PhosphorReviewGlyphs.Reader, SableGreen),
        ReviewApp("Clock", PhosphorReviewGlyphs.Clock, SablePurple),
        ReviewApp("Vanadium", PhosphorReviewGlyphs.Browser, SableBlue),
        ReviewApp("Settings", PhosphorReviewGlyphs.Settings, SableSlate),
        ReviewApp("Sudoku", PhosphorReviewGlyphs.Grid, SableBlue),
        ReviewApp("Minesweeper", PhosphorReviewGlyphs.Bomb, SableOrange),
        ReviewApp("2048", PhosphorReviewGlyphs.Grid, SableSlate),
    )

@Composable
private fun ReviewRoot(frame: ReviewFrame) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        ReviewStamp(frame.title)
        Spacer(Modifier.height(10.dp))
        when (frame) {
            ReviewFrame.Icons -> IconSystemReview()

            ReviewFrame.StartDark,
            ReviewFrame.StartLight,
            -> StartReview()

            ReviewFrame.Live -> LiveReview()

            ReviewFrame.Calendar -> CalendarReview()

            ReviewFrame.Clock -> ClockReview()

            ReviewFrame.Camera -> CameraReview()

            ReviewFrame.Files -> FilesReview()

            ReviewFrame.Photos -> PhotosReview()

            ReviewFrame.Browser -> BrowserReview()

            ReviewFrame.Messages -> MessagesReview()
        }
    }
}

@Composable
private fun ReviewStamp(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "R9 DESIGN REVIEW",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "static fixture · visual approval only · no product plumbing",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IconSystemReview() {
    ReviewHeader("app identity", "Phosphor-derived glyphs inside a Sable adaptive-icon language.")
    Spacer(Modifier.height(14.dp))
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReviewApps.chunked(4).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowApps.forEach { app ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SableAppIcon(
                            app = app,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - rowApps.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StartReview() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "13:58",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "Sunday · September 21",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            SableAppIcon(
                app = ReviewApps.first { it.label == "Settings" },
                modifier = Modifier.size(46.dp),
                compact = true,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "good afternoon",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(14.dp))

        DimensionalWideTile(
            app = ReviewApps.first { it.label == "Calendar" },
            eyebrow = "NEXT",
            title = "2:30 PM · Design review",
            detail = "Calendar",
        )

        Spacer(Modifier.height(10.dp))
        TilePair(
            left = {
                DimensionalLiveTile(
                    app = ReviewApps.first { it.label == "Sable Messages" },
                    metric = "3",
                    detail = "unread",
                )
            },
            right = {
                DimensionalLiveTile(
                    app = ReviewApps.first { it.label == "Sable Weather" },
                    metric = "68°",
                    detail = "partly cloudy",
                )
            },
        )

        Spacer(Modifier.height(10.dp))
        DimensionalWideTile(
            app = ReviewApps.first { it.label == "Sable Media" },
            eyebrow = "NOW PLAYING",
            title = "Teardrop",
            detail = "Massive Attack · ▶",
        )

        Spacer(Modifier.height(10.dp))
        TilePair(
            left = {
                DimensionalLiveTile(
                    app = ReviewApps.first { it.label == "Sable Mail" },
                    metric = "4",
                    detail = "unread",
                )
            },
            right = {
                DimensionalCompactTile(
                    app = ReviewApps.first { it.label == "Phone" },
                    detail = "2 missed",
                )
            },
        )

        Spacer(Modifier.height(10.dp))
        TilePair(
            left = {
                DimensionalCompactTile(
                    app = ReviewApps.first { it.label == "Camera" },
                )
            },
            right = {
                DimensionalCompactTile(
                    app = ReviewApps.first { it.label == "Photos" },
                    detail = "284 local",
                )
            },
        )

        Spacer(Modifier.height(10.dp))
        TilePair(
            left = {
                DimensionalCompactTile(
                    app = ReviewApps.first { it.label == "Sable Calculator" },
                )
            },
            right = {
                DimensionalCompactTile(
                    app = ReviewApps.first { it.label == "Files" },
                )
            },
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "all apps   ·   search   ·   live",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LiveReview() {
    ReviewHeader("live", "Useful state only. No fake feeds and no ambient polling.")
    Spacer(Modifier.height(14.dp))
    Text("13:58", style = MaterialTheme.typography.displayLarge)
    Text(
        "Sunday · September 21",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(18.dp))

    LiveReviewRow(
        app = ReviewApps.first { it.label == "Sable Weather" },
        primary = "68° · partly cloudy",
        secondary = "Jersey City · updated 8 min ago",
        state = "LIVE",
    )
    LiveReviewRow(
        app = ReviewApps.first { it.label == "Calendar" },
        primary = "2:30 PM · Design review",
        secondary = "next event",
        state = "NEXT",
    )
    LiveReviewRow(
        app = ReviewApps.first { it.label == "Sable Messages" },
        primary = "3 unread",
        secondary = "message content stays private on Home",
        state = "PRIVATE",
    )
    LiveReviewRow(
        app = ReviewApps.first { it.label == "Sable Mail" },
        primary = "4 unread",
        secondary = "subject previews off",
        state = "PRIVATE",
    )
    LiveReviewRow(
        app = ReviewApps.first { it.label == "Sable Media" },
        primary = "Teardrop",
        secondary = "Massive Attack · playing",
        state = "PLAYING",
    )
    LiveReviewRow(
        app = ReviewApps.first { it.label == "Photos" },
        primary = "284 local photos",
        secondary = "device media only",
        state = "LOCAL",
    )
}

@Composable
private fun CalendarReview() {
    ReviewHeader("calendar", "Local CalendarProvider UI · account sync remains separate.")
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SableAppIcon(ReviewApps.first { it.label == "Calendar" }, Modifier.size(58.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("september", style = MaterialTheme.typography.headlineLarge)
            Text(
                "2026",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    WeekStrip()
    Spacer(Modifier.height(18.dp))
    SectionEyebrow("TODAY · 21")
    AgendaRow("09:00", "Product sync", "SableOS")
    AgendaRow("14:30", "Design review", "R9 daily-driver UI", SableBlue)
    AgendaRow("18:00", "Gym", "Personal", SableGreen)
    Spacer(Modifier.height(14.dp))
    OutlineAction("+  new event")
}

@Composable
private fun ClockReview() {
    ReviewHeader("clock", "Alarms, world clock, timer and stopwatch with shared Sable hierarchy.")
    Spacer(Modifier.height(18.dp))
    Text("13:58", style = MaterialTheme.typography.displayLarge)
    Text(
        "Sunday · September 21",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(18.dp))
    PivotRow(listOf("ALARMS", "CLOCK", "TIMER", "STOPWATCH"), selected = 0)
    Spacer(Modifier.height(18.dp))
    AlarmRow("06:30", "Weekdays", true)
    AlarmRow("08:00", "Saturday", false)
    Spacer(Modifier.height(14.dp))
    OutlineAction("+  new alarm")
}

@Composable
private fun CameraReview() {
    ReviewHeader("camera", "Bounded Sable presentation over the mature camera capability.")
    Spacer(Modifier.height(12.dp))
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF17222E),
                            Color(0xFF344B61),
                            Color(0xFF0D1117),
                        ),
                    ),
                ),
    ) {
        CameraGrid()
        Text(
            text = "HDR",
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
        Text(
            text = "1×",
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.46f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
    Spacer(Modifier.height(12.dp))
    PivotRow(listOf("VIDEO", "PHOTO", "PORTRAIT"), selected = 1)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PhotoThumbnail()
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .border(4.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .padding(7.dp)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape),
        )
        SableAppIcon(
            app = ReviewApps.first { it.label == "Camera" },
            modifier = Modifier.size(46.dp),
            compact = true,
        )
    }
}

@Composable
private fun FilesReview() {
    ReviewHeader("files", "Local-first file browsing with calm hierarchy and visible storage context.")
    Spacer(Modifier.height(14.dp))
    DimensionalPanel(accent = SableOrange) {
        Text("device storage", style = MaterialTheme.typography.labelLarge, color = SableOrange)
        Text("83 GB free", style = MaterialTheme.typography.headlineMedium)
        Text(
            "45 GB used of 128 GB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ProgressLine(0.35f, SableOrange)
    }
    Spacer(Modifier.height(16.dp))
    SectionEyebrow("BROWSE")
    FileCategoryRow(PhosphorReviewGlyphs.Photos, "Images", "284 items", SablePurple)
    FileCategoryRow(PhosphorReviewGlyphs.Media, "Audio", "1,248 items", SablePurple)
    FileCategoryRow(PhosphorReviewGlyphs.Files, "Downloads", "16 items", SableOrange)
    FileCategoryRow(PhosphorReviewGlyphs.Reader, "Documents", "43 items", SableGreen)
    Spacer(Modifier.height(12.dp))
    SectionEyebrow("RECENT")
    SimpleListRow("R9_UI_review.pdf", "PDF · 2.4 MB")
    SimpleListRow("launcher-capture.png", "PNG · 1.8 MB")
}

@Composable
private fun PhotosReview() {
    ReviewHeader("photos", "Device media first; visual content gets room without turning the shell into a gallery clone.")
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("today", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.weight(1f))
        Text("284 local", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    PhotoGrid()
    Spacer(Modifier.height(14.dp))
    PivotRow(listOf("PHOTOS", "ALBUMS", "FAVORITES"), selected = 0)
}

@Composable
private fun BrowserReview() {
    ReviewHeader("vanadium", "Preserve Vanadium security behavior; refresh only the bounded Sable presentation.")
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SableAppIcon(ReviewApps.first { it.label == "Vanadium" }, Modifier.size(58.dp))
        Spacer(Modifier.width(14.dp))
        Text("new tab", style = MaterialTheme.typography.headlineLarge)
    }
    Spacer(Modifier.height(20.dp))
    DimensionalPanel(accent = SableBlue) {
        Text(
            "Search or enter address",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        QuickLink("docs", PhosphorReviewGlyphs.Reader, Modifier.weight(1f))
        QuickLink("news", PhosphorReviewGlyphs.Messages, Modifier.weight(1f))
        QuickLink("local", PhosphorReviewGlyphs.Files, Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    SectionEyebrow("PRIVACY")
    SimpleListRow("Site permissions", "review camera, microphone and location")
    SimpleListRow("Tracking protection", "Vanadium defaults preserved")
    SimpleListRow("Clear browsing data", "history, cookies and site data")
}

@Composable
private fun MessagesReview() {
    ReviewHeader("messages", "One visible Sable communication surface; transport detail stays secondary.")
    Spacer(Modifier.height(14.dp))
    PivotRow(listOf("ALL", "MESSAGES", "PEOPLE", "SERVICES"), selected = 1)
    Spacer(Modifier.height(18.dp))
    OutlineAction("+  new message")
    Spacer(Modifier.height(14.dp))
    ConversationRow("Maya", "Lunch at 12:30 works.", "12:08", unread = true)
    ConversationRow("Alex", "Sent the R9 screenshots.", "11:41", unread = true)
    ConversationRow("Delivery", "Package is out for delivery.", "09:12", unread = false)
    Spacer(Modifier.height(16.dp))
    DimensionalPanel(accent = SableGreen) {
        Text("Messaging status", style = MaterialTheme.typography.titleMedium)
        Text(
            "SMS ready · MMS/RCS can open the system messaging service when needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewHeader(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.displayLarge,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SableAppIcon(
    app: ReviewApp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 11.dp else 15.dp)
    Box(
        modifier =
            modifier
                .shadow(if (compact) 2.dp else 4.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            app.accent.copy(alpha = 0.30f),
                        ),
                    ),
                ).border(1.dp, app.accent.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center,
    ) {
        PhosphorGlyph(
            glyph = app.glyph,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize().padding(if (compact) 11.dp else 15.dp),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(app.accent),
        )
    }
}

@Composable
private fun PhosphorGlyph(
    glyph: ReviewGlyph,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val androidPath: android.graphics.Path =
        remember(glyph.pathData) {
            PathParser.createPathFromPathData(glyph.pathData)
        }
    Canvas(modifier = modifier) {
        val iconSize = size.minDimension * 0.86f
        val scale = iconSize / 256f
        val dx = (size.width - iconSize) / 2f
        val dy = (size.height - iconSize) / 2f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val save = native.save()
            native.translate(dx, dy)
            native.scale(scale, scale)
            val paint =
                AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.FILL
                    this.color = color.toArgb()
                }
            native.drawPath(androidPath, paint)
            native.restoreToCount(save)
        }
    }
}

@Composable
private fun DimensionalCompactTile(
    app: ReviewApp,
    detail: String? = null,
) {
    DimensionalTileSurface(app.accent, Modifier.fillMaxWidth().height(118.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SableAppIcon(app, Modifier.size(42.dp), compact = true)
            Column {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DimensionalLiveTile(
    app: ReviewApp,
    metric: String,
    detail: String,
) {
    DimensionalTileSurface(app.accent, Modifier.fillMaxWidth().height(118.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    app.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = app.accent,
                )
                Spacer(Modifier.weight(1f))
                PhosphorGlyph(
                    glyph = app.glyph,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(27.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    metric,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun DimensionalWideTile(
    app: ReviewApp,
    eyebrow: String,
    title: String,
    detail: String,
) {
    DimensionalTileSurface(app.accent, Modifier.fillMaxWidth().height(106.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SableAppIcon(app, Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = app.accent,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DimensionalTileSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            modifier
                .shadow(3.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant,
                                accent.copy(alpha = 0.11f),
                            ),
                    ),
                ).border(1.dp, accent.copy(alpha = 0.30f), shape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accent),
        )
        content()
    }
}

@Composable
private fun TilePair(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

@Composable
private fun LiveReviewRow(
    app: ReviewApp,
    primary: String,
    secondary: String,
    state: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SableAppIcon(app, Modifier.size(48.dp), compact = true)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text(primary, style = MaterialTheme.typography.bodyLarge)
            Text(
                secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            state,
            style = MaterialTheme.typography.labelLarge,
            color = app.accent,
        )
    }
    ThinDivider()
}

@Composable
private fun WeekStrip() {
    val days = listOf("M" to "16", "T" to "17", "W" to "18", "T" to "19", "F" to "20", "S" to "21", "S" to "22")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { (day, date) ->
            val selected = date == "21"
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) {
                                SableBlue.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).border(
                            1.dp,
                            if (selected) SableBlue else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp),
                        ).padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day, style = MaterialTheme.typography.labelLarge)
                Text(date, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AgendaRow(
    time: String,
    title: String,
    detail: String,
    accent: Color = SableSlate,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
    ) {
        Text(
            time,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        Box(Modifier.width(3.dp).height(48.dp).background(accent))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmRow(
    time: String,
    repeat: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(repeat, style = MaterialTheme.typography.titleMedium)
            Text(
                if (active) "next · tomorrow" else "off",
                color = if (active) SableBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TogglePreview(active)
    }
    ThinDivider()
}

@Composable
private fun TogglePreview(active: Boolean) {
    Box(
        modifier =
            Modifier
                .width(48.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (active) SableBlue else MaterialTheme.colorScheme.surfaceVariant,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(if (active) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(
                        if (active) Color(0xFF06111F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape,
                    ),
        )
    }
}

@Composable
private fun CameraGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.18f)
        drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1f)
        drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1f)
        drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1f)
        drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1f)
    }
}

@Composable
private fun PhotoThumbnail() {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(SablePurple.copy(alpha = 0.8f), SableBlue.copy(alpha = 0.6f)),
                    ),
                ),
    )
}

@Composable
private fun DimensionalPanel(
    accent: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, accent.copy(alpha = 0.28f), shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun ProgressLine(
    progress: Float,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(accent),
        )
    }
}

@Composable
private fun FileCategoryRow(
    glyph: ReviewGlyph,
    title: String,
    detail: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            PhosphorGlyph(glyph, accent, Modifier.size(28.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SimpleListRow(
    title: String,
    detail: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ThinDivider()
}

@Composable
private fun PhotoGrid() {
    val colors =
        listOf(
            SableBlue to SablePurple,
            SableOrange to SableRed,
            SableGreen to SableBlue,
            SablePurple to SableSlate,
            SableSlate to SableBlue,
            SableOrange to SablePurple,
        )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { (start, end) ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(126.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(start.copy(alpha = 0.86f), end.copy(alpha = 0.70f)),
                                    ),
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLink(
    label: String,
    glyph: ReviewGlyph,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhosphorGlyph(glyph, MaterialTheme.colorScheme.primary, Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ConversationRow(
    name: String,
    message: String,
    time: String,
    unread: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .background(
                        if (unread) {
                            SableGreen.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            time,
            color = if (unread) SableGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ThinDivider()
}

@Composable
private fun PivotRow(
    labels: List<String>,
    selected: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (index == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (index == selected) 3.dp else 1.dp)
                        .background(
                            if (index == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun OutlineAction(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
