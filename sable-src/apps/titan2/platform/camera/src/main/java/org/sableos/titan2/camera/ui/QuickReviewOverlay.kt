@file:Suppress("FunctionNaming")

package org.sableos.titan2.camera.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.titan2.camera.core.CameraText.t
import org.sableos.titan2.camera.core.Capture
import org.sableos.titan2.camera.core.ControlLegend
import org.sableos.titan2.camera.core.QuickReview
import org.sableos.titan2.camera.core.ReviewAction
import org.sableos.titan2.camera.core.ReviewState
import org.sableos.titan2.camera.core.ReviewTouch

/** Loads a preview bitmap off the main thread; null when the file has none (for example RAW). */
typealias CaptureLoader = suspend (Capture<Uri>, Int) -> ImageBitmap?

private val reviewMuted = Color(0xFF8BA6B8)
private val reviewCell = Color(0x66FFFFFF)
private val reviewDanger = Color(0xFFFF5252)
private val reviewScrim = Color(0xE6000000)
private val reviewCard = Color(0xFF1B1F24)
private const val REVIEW_EDGE_PX = 1080
private const val THUMB_EDGE_PX = 160
private val thumbEdge = 44.dp

/** One capture's preview, or a labelled placeholder while it loads or when it has none. */
@Composable
fun CaptureImage(
    c: Capture<Uri>,
    edgePx: Int,
    load: CaptureLoader,
    modifier: Modifier,
    scale: ContentScale = ContentScale.Fit
) {
    val bitmap by produceState<ImageBitmap?>(null, c.ref, edgePx) { value = load(c, edgePx) }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = c.name, modifier = modifier, contentScale = scale)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(c.kind.label, color = reviewMuted, fontSize = 12.sp)
        }
    }
}

/**
 * Bottom row of the Control Deck: the last capture's thumbnail (tap = G) and the help hint. An
 * empty slot of the same size keeps the hint centred before the first capture.
 */
@Composable
fun LastCaptureRow(latest: Capture<Uri>?, load: CaptureLoader, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (latest == null) {
            Box(Modifier.size(thumbEdge))
        } else {
            CaptureImage(
                latest,
                THUMB_EDGE_PX,
                load,
                Modifier.size(thumbEdge).clip(shape).background(reviewCell, shape)
                    .clickable { onOpen() },
                ContentScale.Crop
            )
        }
        Text(
            if (latest ==
                null
            ) {
                t("h_or_shows_the_controls")
            } else {
                t("h_or_shows_the_controls_2")
            },
            color = reviewMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(thumbEdge))
    }
}

/**
 * Quick review over the viewfinder: the shown capture, its place in this session, and the same
 * actions the keys reach as buttons. A delete always asks first in its own card.
 */
@Composable
fun QuickReviewOverlay(
    state: ReviewState<Uri>,
    status: String,
    load: CaptureLoader,
    onAction: (ReviewAction) -> Unit
) {
    val c = state.current ?: return
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CaptureImage(
            c,
            REVIEW_EDGE_PX,
            load,
            Modifier.fillMaxSize().padding(top = 36.dp, bottom = 96.dp)
        )
        Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("${state.position} · ${c.name}", color = Color.White, fontSize = 13.sp)
            Text(status, color = reviewMuted, fontSize = 12.sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReviewButtons(ReviewTouch.browse, onAction)
            Text(
                ControlLegend.reviewEntries().joinToString("  ·  ") { "${it.first} ${it.second}" },
                color = reviewMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (state.confirmingDelete) DeleteConfirm(c, onAction)
    }
}

@Composable
private fun DeleteConfirm(c: Capture<Uri>, onAction: (ReviewAction) -> Unit) {
    Box(Modifier.fillMaxSize().background(reviewScrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).background(reviewCard, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(QuickReview.confirmText(c), color = Color.White, fontSize = 14.sp)
            Text(
                t("the_file_is_removed_from"),
                color = reviewMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            ReviewButtons(ReviewTouch.confirm, onAction)
        }
    }
}

@Composable
private fun ReviewButtons(
    buttons: List<Pair<ReviewAction, String>>,
    onAction: (ReviewAction) -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        buttons.forEach { (a, label) ->
            val danger = a == ReviewAction.Confirm
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(2.dp)
                    .background(if (danger) reviewDanger else reviewCell, RoundedCornerShape(10.dp))
                    .clickable { onAction(a) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
