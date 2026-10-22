@file:Suppress("FunctionNaming")

package org.sableos.titan2.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.sableos.titan2.camera.android.CameraController
import org.sableos.titan2.camera.android.CameraInfoReader
import org.sableos.titan2.camera.android.CaptureResult
import org.sableos.titan2.camera.android.MediaStoreSaver
import org.sableos.titan2.camera.android.RecordingSpec
import org.sableos.titan2.camera.android.ReviewActions
import org.sableos.titan2.camera.android.Saved
import org.sableos.titan2.camera.android.StillCallbacks
import org.sableos.titan2.camera.android.SysProps
import org.sableos.titan2.camera.core.ActionRouter
import org.sableos.titan2.camera.core.ArrowGuard
import org.sableos.titan2.camera.core.BackResult
import org.sableos.titan2.camera.core.CameraAction
import org.sableos.titan2.camera.core.CameraDeviceProfile
import org.sableos.titan2.camera.core.CapabilityInterpreter
import org.sableos.titan2.camera.core.CapabilityReport
import org.sableos.titan2.camera.core.CaptureMode
import org.sableos.titan2.camera.core.CapturePlanner
import org.sableos.titan2.camera.core.Cmd
import org.sableos.titan2.camera.core.ControlDeck
import org.sableos.titan2.camera.core.ControlGate
import org.sableos.titan2.camera.core.ControlLegend
import org.sableos.titan2.camera.core.DeckContext
import org.sableos.titan2.camera.core.DeckOrientation
import org.sableos.titan2.camera.core.DeckPresentation
import org.sableos.titan2.camera.core.DeckState
import org.sableos.titan2.camera.core.Gate
import org.sableos.titan2.camera.core.Hint
import org.sableos.titan2.camera.core.HintDock
import org.sableos.titan2.camera.core.LaunchIntent
import org.sableos.titan2.camera.core.LaunchKind
import org.sableos.titan2.camera.core.LegendEntry
import org.sableos.titan2.camera.core.ParameterStrip
import org.sableos.titan2.camera.core.ParameterStrips
import org.sableos.titan2.camera.core.PhotoPath
import org.sableos.titan2.camera.core.ProControls
import org.sableos.titan2.camera.core.ProField
import org.sableos.titan2.camera.core.ProLimits
import org.sableos.titan2.camera.core.ProState
import org.sableos.titan2.camera.core.RecEvent
import org.sableos.titan2.camera.core.RecState
import org.sableos.titan2.camera.core.RecStateMachine
import org.sableos.titan2.camera.core.StripContext
import org.sableos.titan2.camera.core.TouchControl
import org.sableos.titan2.camera.core.TouchFallback
import org.sableos.titan2.camera.core.UiMode
import org.sableos.titan2.camera.core.VideoOption
import org.sableos.titan2.camera.core.VideoPlanner

private const val RECORDING_POLL_MS = 250L

/** Sable camera accent: the cyan of `sable-camera-capture-titan2.svg` (#6EE7F9). */
private val sableCyan = Color(0xFF6EE7F9)

/**
 * Keyboard-first camera with three modes: Video, Photo, Pro (stock order, minus Timelapse/More
 * which are not built).
 * Left/Right change mode. Space/Enter/volume = shutter (record in Video). Photo: Up/Down zoom, M
 * capture mode, E/Alt+E exposure, B compare, hold F/E lock focus/exposure. G reviews this
 * session's captures (Left/Right, Del with confirm, S share, F Files, Esc back). R toggles RAW
 * (or high-res).
 * Pro: W/I/E/S/F pick WB/ISO/EV/shutter/focus, Up/Down change it, A returns it to Auto. Video: P
 * pause, Q quality. Z/X (or +/-) zoom everywhere; H (or /) shows the keys. On a device whose
 * trackpad sends arrows (Q25) the plain arrows only step a strip that has the focus; Alt+arrows
 * change mode and zoom. Started for IMAGE_CAPTURE it takes one Auto JPEG for the calling app.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: CameraController
    private lateinit var profile: CameraDeviceProfile
    private var report by mutableStateOf<CapabilityReport?>(null)
    private var camId by mutableStateOf("")
    private var mode by mutableStateOf(CaptureMode.Auto) // Photo sub-mode: Auto / HighRes / Raw
    private var uiMode by mutableStateOf(UiMode.Photo)
    private var proState by mutableStateOf(ProState())
    private var proField by mutableStateOf(ProField.Iso)
    private var deck by mutableStateOf(DeckState())
    private var recState by mutableStateOf(RecState.Idle)
    private var recMs by mutableStateOf(0L)
    private var videoOption by mutableStateOf<VideoOption?>(null)
    private var micGranted = false
    private var compare by mutableStateOf(false)
    private var status by mutableStateOf("Starting")
    private var hud by mutableStateOf("")
    private var previewAspect by mutableStateOf(3f / 4f)
    private var rotation by mutableStateOf(Surface.ROTATION_0)
    private var texture: SurfaceTexture? = null
    private var textureView: TextureView? = null
    private var busy = false
    private lateinit var review: QuickReviewHost
    private lateinit var keys: CameraKeys
    private lateinit var captureIntent: CaptureIntentHost
    private val launch get() = captureIntent.launch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        profile = CameraDeviceProfile.byId(SysProps.get("ro.sable.profile.id").ifBlank { null })
        val reader = CameraInfoReader(this)
        controller = CameraController(this, reader, MediaStoreSaver(this))
        review = QuickReviewHost(ReviewActions(this)) { status = it }
        onBackPressedDispatcher.addCallback(this, review.back)
        captureIntent = CaptureIntentHost(this)
        keys = CameraKeys(
            profile,
            review,
            ::deckContext,
            ::act,
            stripFocused = { deck.strip != null },
            onGuarded = { status = ArrowGuard.HINT }
        )
        rotation = display?.rotation ?: Surface.ROTATION_0
        setContent { Screen() }
        micGranted =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadCapabilities()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val camIdx = permissions.indexOf(Manifest.permission.CAMERA)
        val micIdx = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        micGranted =
            micIdx >= 0 && grantResults.getOrNull(micIdx) == PackageManager.PERMISSION_GRANTED
        if (camIdx >= 0 &&
            grantResults.getOrNull(camIdx) == PackageManager.PERMISSION_GRANTED
        ) {
            loadCapabilities()
        } else {
            status =
                "Camera permission is required."
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rotation = display?.rotation ?: rotation
        applyTransform()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }

    private fun loadCapabilities() {
        val infos = CameraInfoReader(this).readAll()
        val rep = CapabilityInterpreter.interpret(infos, profile)
        report = rep
        camId =
            rep.defaultCamera()?.camera?.id
                ?: run {
                    status = "No camera visible to this app."
                    return
                }
        resetForCamera()
        restart()
    }

    private fun degrees() = rotationDegrees(rotation)

    /**
     * Per-camera defaults: Photo mode, auto Pro state, the default video size; falls back when a
     * mode is unavailable on this camera.
     */
    private fun resetForCamera() {
        val rep = report?.camera(camId) ?: return
        mode = CaptureMode.Auto
        compare = false
        proState = ProState()
        videoOption = VideoPlanner.default(VideoPlanner.options(rep.camera.videoSizes))
        val avail = ActionRouter.available(rep)
        if (launch == LaunchKind.ImageCapture && UiMode.Photo in avail) uiMode = UiMode.Photo
        if (uiMode !in avail) {
            uiMode = avail.firstOrNull { it == UiMode.Photo } ?: avail.firstOrNull() ?: UiMode.Photo
        }
    }

    private fun proLimits(): ProLimits? = report?.camera(camId)?.camera?.let { ProLimits.of(it) }

    private fun restart() {
        val rep = report?.camera(camId) ?: return
        val tex = texture ?: return
        recState = RecState.Idle
        val photoMode = if (uiMode == UiMode.Photo) mode else CaptureMode.Auto
        val plan = CapturePlanner.single(rep, photoMode)
        val video = uiMode == UiMode.Video
        val label = if (video) {
            "video ${videoOption?.label ?: "?"}"
        } else {
            "${plan.kind.suffix} ${plan.size}"
        }
        status = "Opening camera $camId ($label)"
        controller.setPro(if (uiMode == UiMode.Pro) proState else null, proLimits())
        controller.configure(
            rep,
            plan,
            tex,
            stillCallbacks(rep),
            previewFor = if (video) videoOption?.size ?: plan.size else plan.size
        )
    }

    private fun stillCallbacks(rep: org.sableos.titan2.camera.core.CameraReport) = StillCallbacks(
        onReady = {
            runOnUiThread {
                busy = false
                status = "Ready"
                controller.previewSize?.let { updateAspect(it, rep.camera.sensorOrientation) }
                refreshHud()
            }
        },
        onSaved = { saved ->
            runOnUiThread {
                busy = false
                status = "Saved ${saved.name}  ${saved.detail}"
                val returned = captureIntent.returnCapture(saved) {
                    busy = true
                    status = "Returning the photo…"
                }
                if (!returned) review.remember(saved)
            }
        },
        onError = { msg ->
            runOnUiThread {
                busy = false
                status = msg
            }
        }
    )

    /** Upright preview aspect for the current display rotation ([uprightAspect]). */
    private fun updateAspect(size: org.sableos.titan2.camera.core.Size, sensor: Int) {
        previewAspect = uprightAspect(size, sensor, degrees())
        applyTransform()
    }

    private fun applyTransform() {
        val tv = textureView
        val ps = controller.previewSize
        if (tv != null && ps != null) tv.fitPreview(ps, rotation)
    }

    private fun refreshHud() {
        hud = viewfinder().hud(controller)
    }

    private fun viewfinder() = ViewfinderInfo(
        camId,
        uiMode,
        mode,
        compare,
        proField,
        proLimits()?.let { ProControls.valueLabel(proState, proField, it) },
        videoOption,
        micGranted,
        forApp = launch == LaunchKind.ImageCapture
    )

    private fun setPro(s: ProState) {
        proState = s
        controller.setPro(s, proLimits())
        refreshHud()
    }

    private fun deckContext(): DeckContext? = report?.let { rep ->
        rep.camera(camId)?.let {
            DeckContext(uiMode, it, rep.cameras.size, recState != RecState.Idle, proField)
        }
    }

    private fun stripContext(cam: org.sableos.titan2.camera.core.CameraReport) = StripContext(
        cam,
        proState,
        proField,
        controller.autoSeed(),
        controller.zoom,
        controller.aeSteps
    )

    /**
     * The one entry point for every control. Physical keys and touch buttons both land here, so
     * they share the same legend/Back handling, capability gate, routing and strip.
     */
    private fun act(raw: CameraAction) {
        val rep = report
        val cam = rep?.camera(camId)
        val dc = deckContext()
        when {
            rep == null || cam == null || dc == null -> Unit
            !LaunchIntent.allows(launch, raw) -> status = LaunchIntent.CAPTURE_REFUSED
            else -> dispatch(raw, rep, cam, dc)
        }
    }

    /** Legend/Back handling first; what the deck lets through is routed and may show a strip. */
    private fun dispatch(
        raw: CameraAction,
        rep: org.sableos.titan2.camera.core.CapabilityReport,
        cam: org.sableos.titan2.camera.core.CameraReport,
        dc: DeckContext
    ) {
        val d = ControlDeck.dispatch(deck, raw, dc)
        deck = d.deck
        d.status?.let { status = it }
        if (!d.route) return
        val cmd = ActionRouter.route(uiMode, raw)
        execute(cmd, rep, cam)
        deck = ControlDeck.showStrip(
            deck,
            ParameterStrips.after(cmd, stripContext(cam)),
            SystemClock.elapsedRealtime()
        )
    }

    private fun execute(
        cmd: Cmd,
        rep: org.sableos.titan2.camera.core.CapabilityReport,
        cam: org.sableos.titan2.camera.core.CameraReport
    ) {
        when (cmd) {
            // A key with no camera action: deliberately ignored.
            is Cmd.Nop -> Unit

            is Cmd.SetUi -> if (recState == RecState.Idle) switchUi(cmd.mode, rep)

            is Cmd.UiStep -> if (recState == RecState.Idle) {
                switchUi(ActionRouter.step(uiMode, cmd.dir, ActionRouter.available(cam)), rep)
            }

            is Cmd.ProSelect -> {
                proField = cmd.field
                status = "${cmd.field.label}: Up/Down change, A = Auto"
            }

            is Cmd.ProStep -> stepPro(cmd.dir)

            is Cmd.ProAuto -> setPro(ProControls.auto(proState, proField))

            is Cmd.Act -> doAction(cmd.action, rep, cam)
        }
    }

    private fun stepPro(dir: Int) {
        val l = proLimits() ?: return
        if (l.offers(proField)) {
            setPro(ProControls.adjust(proState, proField, dir, l, controller.autoSeed()))
        } else {
            status = "${proField.label} is not adjustable on camera $camId"
        }
    }

    private fun switchUi(m: UiMode, rep: org.sableos.titan2.camera.core.CapabilityReport) {
        if (m == uiMode ||
            m !in ActionRouter.available(rep.camera(camId) ?: return)
        ) {
            if (m !=
                uiMode
            ) {
                status = "${m.label} is not available on camera $camId"
            }
            return
        }
        uiMode = m
        compare = false
        restart()
    }

    private fun doAction(
        a: CameraAction,
        rep: org.sableos.titan2.camera.core.CapabilityReport,
        cam: org.sableos.titan2.camera.core.CameraReport
    ) {
        when (a) {
            CameraAction.Shutter,
            CameraAction.VideoToggle,
            CameraAction.PauseToggle,
            CameraAction.QualityNext,
            CameraAction.NextCaptureMode,
            CameraAction.RawToggle,
            CameraAction.CompareToggle -> doCaptureAction(a, cam)

            CameraAction.Autofocus,
            CameraAction.AfLockToggle,
            CameraAction.AeLockToggle,
            CameraAction.ZoomIn,
            CameraAction.ZoomOut,
            CameraAction.ExposureUp,
            CameraAction.ExposureDown -> doCameraAction(a)

            CameraAction.SwitchCamera -> if (recState == RecState.Idle) {
                val ids = rep.cameras.map { it.camera.id }
                camId = ids[(ids.indexOf(camId) + 1) % ids.size]
                resetForCamera()
                restart()
            }

            CameraAction.OpenLast -> {
                keys.clear()
                review.open()
            }

            CameraAction.Back -> if (recState == RecState.Idle) finish() else toggleRecording()

            // Router-only actions never reach here; nothing to do for them.
            else -> Unit
        }
    }

    private fun doCaptureAction(a: CameraAction, cam: org.sableos.titan2.camera.core.CameraReport) {
        when (a) {
            CameraAction.Shutter -> if (!busy) shoot()

            CameraAction.VideoToggle -> toggleRecording()

            CameraAction.PauseToggle -> if (recState != RecState.Idle) togglePause()

            CameraAction.QualityNext -> if (recState == RecState.Idle) {
                videoOption =
                    VideoPlanner.next(VideoPlanner.options(cam.camera.videoSizes), videoOption, +1)
                restart()
            }

            CameraAction.NextCaptureMode -> {
                mode = nextPhotoMode(mode, cam)
                restart()
            }

            CameraAction.RawToggle -> PhotoPath.rawToggle(mode, cam)?.let {
                mode = it
                restart()
            }

            CameraAction.CompareToggle -> {
                compare = !compare
                refreshHud()
                status = if (compare) {
                    "Compare: Space saves JPEG, high-res and RAW of the same scene"
                } else {
                    "Compare off"
                }
            }

            else -> Unit
        }
    }

    /** One still, or the compare set when compare is on. */
    private fun shoot() {
        busy = true
        if (compare) {
            runBracket()
        } else {
            status = "Capturing…"
            controller.takePicture(degrees())
        }
    }

    private fun togglePause() {
        recState = RecStateMachine.reduce(recState, RecEvent.TogglePause)
        if (recState == RecState.Paused) {
            controller.video.pause()
        } else {
            controller.video.resume()
        }
    }

    private fun doCameraAction(a: CameraAction) {
        when (a) {
            CameraAction.Autofocus -> {
                controller.triggerFocus()
                status = "Focus"
                refreshHud()
            }

            CameraAction.AfLockToggle, CameraAction.AeLockToggle -> {
                status = toggleLock(a, controller.locks)
                refreshHud()
            }

            CameraAction.ZoomIn -> {
                controller.stepZoom(1)
                refreshHud()
            }

            CameraAction.ZoomOut -> {
                controller.stepZoom(-1)
                refreshHud()
            }

            CameraAction.ExposureUp -> {
                controller.stepExposure(1)
                refreshHud()
            }

            CameraAction.ExposureDown -> {
                controller.stepExposure(-1)
                refreshHud()
            }

            else -> Unit
        }
    }

    private fun toggleRecording() {
        val opt =
            videoOption ?: run {
                status = "No recordable video size on camera $camId"
                return
            }
        val tex = texture ?: return
        if (recState == RecState.Idle) {
            status = "Starting…"
            controller.video.start(
                RecordingSpec(opt, micGranted, degrees()),
                tex,
                onStarted = {
                    runOnUiThread {
                        recState =
                            RecStateMachine.reduce(RecState.Idle, RecEvent.ToggleRecord)
                        status =
                            if (micGranted) "Recording" else "Recording without sound"
                    }
                },
                onErrorCb = { msg ->
                    runOnUiThread {
                        recState =
                            RecStateMachine.reduce(recState, RecEvent.Failed)
                        status = msg
                        restart()
                    }
                }
            )
        } else {
            controller.video.stop(
                onSavedCb = { saved ->
                    runOnUiThread {
                        recState = RecState.Idle
                        status =
                            "Saved ${saved.name}"
                        review.remember(saved)
                        restart()
                    }
                },
                onErrorCb = { msg ->
                    runOnUiThread {
                        recState = RecState.Idle
                        status = msg
                        restart()
                    }
                }
            )
        }
    }

    private fun runBracket() {
        val cam = report?.camera(camId) ?: return
        val tex = texture ?: return
        val plans = CapturePlanner.bracket(cam)
        var i = 0
        fun next() {
            if (i >= plans.size) {
                runOnUiThread {
                    busy = false
                    status =
                        "Compare set saved (${plans.size} files)"
                    restart()
                }
                return
            }
            val p = plans[i++]
            runOnUiThread { status = "Compare $i/${plans.size}: ${p.kind.suffix}" }
            controller.configure(
                cam,
                p,
                tex,
                StillCallbacks(
                    onReady = { controller.takePicture(degrees()) },
                    onSaved = { saved ->
                        runOnUiThread { review.remember(saved) }
                        next()
                    },
                    onError = { msg ->
                        runOnUiThread {
                            busy = false
                            status = msg
                        }
                    }
                )
            )
        }
        next()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        keys.down(keyCode, event.isAltPressed, event.repeatCount) ||
            super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keys.up(keyCode) || super.onKeyUp(keyCode, event)

    override fun onPause() {
        if (recState != RecState.Idle) toggleRecording()
        super.onPause()
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        LaunchedEffect(recState) {
            while (recState != RecState.Idle) {
                recMs = controller.video.elapsedMs()
                delay(RECORDING_POLL_MS)
            }
        }
        StripExpiry(deck) { deck = ControlDeck.tick(deck, SystemClock.elapsedRealtime()) }
        val cfg = LocalConfiguration.current
        val presentation = DeckOrientation.presentation(cfg.screenWidthDp, cfg.screenHeightDp)
        val dc = deckContext()
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            // A tap on the viewfinder reveals touch controls in the landscape deck. Portrait
            // always shows them, so this is a no-op there.
            PreviewSurface(
                Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(previewAspect)
                    .clickable(enabled = presentation == DeckPresentation.LandscapeDeck) {
                        deck = ControlDeck.toggleTouchChrome(deck)
                    }
            )
            if (dc != null) {
                FocusReticle(uiMode, controller.locks.afLocked, Modifier.align(Alignment.Center))
            }
            ModeTitle(viewfinder().title(), Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
            StatusOverlay(
                hud,
                status,
                report?.mismatches?.firstOrNull {
                    it.kind != org.sableos.titan2.camera.core.MismatchKind.Note
                }?.message,
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 26.dp, end = 8.dp)
            )
            if (recState != RecState.Idle) {
                RecordingBadge(recState, recMs, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            BottomChrome(presentation, dc, Modifier.align(Alignment.BottomCenter))
            DeckOverlays(
                deck,
                dc,
                profile,
                { deck = ControlDeck.dismissStrip(deck) },
                { deck = ControlDeck.dismissLegend(deck) }
            )
            if (review.isOpen) QuickReviewOverlay(review.state, status, review::load, review::act)
        }
    }

    /** Chips, touch chrome when it shows, the hint dock and (normal launch) the last capture. */
    @androidx.compose.runtime.Composable
    private fun BottomChrome(presentation: DeckPresentation, dc: DeckContext?, modifier: Modifier) {
        val avail = report?.camera(camId)?.let { ActionRouter.available(it) }.orEmpty()
        Column(
            modifier.padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusChips(viewfinder().chips(controller), Modifier.padding(vertical = 2.dp))
            if (DeckOrientation.touchChromeVisible(presentation, deck.touchChromeRevealed)) {
                ModeSelector(
                    avail,
                    uiMode,
                    enabled = recState == RecState.Idle && launch != LaunchKind.ImageCapture,
                    onSelect = { m -> report?.let { switchUi(m, it) } },
                    Modifier.align(Alignment.CenterHorizontally).padding(vertical = 2.dp)
                )
                if (dc != null) {
                    TouchControlBar(TouchFallback.controls(dc), ::act, Modifier)
                }
            }
            if (dc != null) {
                HintDockBar(
                    HintDock.hints(dc, profile),
                    uiMode,
                    recState != RecState.Idle,
                    ::act,
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (launch != LaunchKind.ImageCapture) {
                LastCaptureRow(review.state.latest, review::load) { act(CameraAction.OpenLast) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun PreviewSurface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureView = this
                    surfaceTextureListener = PreviewSurfaceListener(
                        onAvailable = { st ->
                            texture = st
                            if (report != null) restart()
                        },
                        onResized = ::applyTransform,
                        onDestroyed = {
                            texture = null
                            controller.closeSession()
                        }
                    )
                }
            }
        )
    }
}

/** Mode chips: Video, Photo, Pro; the current one in the accent. Alt+Left/Right step them. */
@Composable
fun ModeSelector(
    avail: List<UiMode>,
    current: UiMode,
    enabled: Boolean,
    onSelect: (UiMode) -> Unit,
    modifier: Modifier
) {
    Row(modifier) {
        avail.forEach { m ->
            val selected = m == current
            Text(
                m.label,
                fontSize = 13.sp,
                color = if (selected) sableCyan else Color.White,
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .border(
                        1.dp,
                        if (selected) sableCyan else chipBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(m) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private val chipBorder = Color(0xFF475569)
private val dockSurface = Color(0xCC111827)
private const val RETICLE_DIM = 0.7f
private const val GRID_ALPHA = 0.4f

@Composable
fun ModeTitle(text: String, modifier: Modifier) {
    Text(
        text,
        color = sableCyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/**
 * Focus reticle: a cyan square in Photo and Pro (solid while AF is locked), a framing rectangle
 * with a centre cross in Video. It is drawn only; taps still reach the preview.
 */
@Composable
fun FocusReticle(ui: UiMode, afLocked: Boolean, modifier: Modifier) {
    val color = if (afLocked) sableCyan else sableCyan.copy(alpha = RETICLE_DIM)
    val shape = RoundedCornerShape(10.dp)
    if (ui == UiMode.Video) {
        Box(modifier.size(width = 220.dp, height = 124.dp).border(2.dp, color, shape)) {
            val grid = color.copy(alpha = GRID_ALPHA)
            Box(Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp).background(grid))
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(grid))
        }
    } else {
        Box(modifier.size(112.dp).border(2.dp, color, shape))
    }
}

@Composable
fun StatusChips(chips: List<String>, modifier: Modifier) {
    Row(modifier) {
        chips.forEach {
            Text(
                it,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .background(deckCell, RoundedCornerShape(12.dp))
                    .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Hint dock: "[Space] Shutter (O) [Z] Zoom [R] RAW/HiRes [H] Keys". The hints come from
 * [HintDock], so each names a key that works; each is also a touch target for its action.
 */
@Composable
fun HintDockBar(
    hints: List<Hint>,
    ui: UiMode,
    recording: Boolean,
    onAction: (CameraAction) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier
            .background(dockSurface, RoundedCornerShape(12.dp))
            .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEachIndexed { i, h ->
            if (i == 1) ShutterGlyph(ui, recording) { onAction(CameraAction.Shutter) }
            val open = h.gate.isOpen
            Text(
                "[${h.keyText}] ${h.label}",
                color = if (open) Color.White else deckMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(enabled = open) { onAction(h.action) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** White ring for a still shutter; red dot (square while recording) for video. */
@Composable
fun ShutterGlyph(ui: UiMode, recording: Boolean, onClick: () -> Unit) {
    val m = Modifier.padding(horizontal = 6.dp).clickable { onClick() }
    when {
        ui != UiMode.Video -> Box(m.size(28.dp).border(3.dp, Color.White, CircleShape))
        recording -> Box(m.size(18.dp).background(statusRed, RoundedCornerShape(3.dp)))
        else -> Box(m.size(22.dp).background(statusRed, CircleShape))
    }
}

private val deckAccent = sableCyan
private val deckMuted = Color(0xFF8BA6B8)
private val deckScrim = Color(0xCC000000)
private val deckCell = Color(0x66000000)
private val deckCellSelected = Color(0xFF123B44)

/** Transient parameter strip. It only exists while the model says so; nothing here owns state. */
@Composable
fun ParameterStripOverlay(strip: ParameterStrip, onDismiss: () -> Unit, modifier: Modifier) {
    val (cells, selected) = strip.window()
    Column(
        modifier.background(deckScrim, RoundedCornerShape(8.dp)).clickable { onDismiss() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strip.title, color = deckMuted, fontSize = 11.sp)
        Row {
            cells.forEachIndexed { i, label ->
                Text(
                    label,
                    color = if (i == selected) Color.White else deckMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(
                            if (i == selected) deckCellSelected else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Translucent legend over the preview. Tap anywhere to dismiss; keys are handled by the model. */
@Composable
fun ControlLegendOverlay(
    entries: List<LegendEntry>,
    note: String?,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    Box(modifier.fillMaxSize().background(deckScrim).clickable { onDismiss() }) {
        Column(
            Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Controls", color = deckAccent, fontSize = 16.sp)
            note?.let { Text(it, color = deckMuted, fontSize = 12.sp) }
            entries.forEach { e ->
                val closed = e.gate as? Gate.Closed
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        e.keys,
                        color = if (closed == null) Color.White else deckMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        e.label + (closed?.let { " (${it.reason})" } ?: ""),
                        color = if (closed == null) Color.White else deckMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Quick review (G or the thumbnail)",
                color = deckAccent,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            ControlLegend.reviewEntries().forEach { (keys, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        keys,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Esc, H or a tap closes this. Touch controls do everything the keys do.",
                color = deckMuted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Touch controls for every open semantic action. Each button calls the same handler a key does,
 * so touch is a complete fallback. Closed controls are hidden here; the legend explains why.
 */
@Composable
fun TouchControlBar(
    controls: List<TouchControl>,
    onAction: (CameraAction) -> Unit,
    modifier: Modifier
) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
        controls.filter { it.gate.isOpen }.forEach { c ->
            val shutter = c.action == CameraAction.Shutter
            Text(
                c.label,
                color = if (shutter) Color.Black else Color.White,
                fontSize = if (shutter) 15.sp else 13.sp,
                modifier = Modifier
                    .padding(2.dp)
                    .background(if (shutter) Color.White else deckCell, RoundedCornerShape(10.dp))
                    .clickable { onAction(c.action) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private val stripBottomInset = 96.dp

/** Hold F / hold E: flip the lock and say what it is now. */
private fun toggleLock(a: CameraAction, locks: CameraController.LockControls): String =
    if (a == CameraAction.AfLockToggle) {
        if (locks.toggleAf()) "AF locked: hold F again to release" else "AF unlocked"
    } else {
        if (locks.toggleAe()) "AE locked: hold E again to release" else "AE unlocked"
    }

/**
 * The transient layer over the preview: the parameter strip (bottom, above touch chrome) and the
 * legend. Both are dismissed through the callbacks, which only touch [DeckState].
 */
@Composable
fun BoxScope.DeckOverlays(
    deck: DeckState,
    context: DeckContext?,
    profile: CameraDeviceProfile,
    onDismissStrip: () -> Unit,
    onDismissLegend: () -> Unit
) {
    deck.strip?.let {
        ParameterStripOverlay(
            it,
            onDismissStrip,
            Modifier.align(Alignment.BottomCenter).padding(bottom = stripBottomInset)
        )
    }
    if (deck.legendVisible && context != null) {
        ControlLegendOverlay(
            ControlLegend.entries(context, profile),
            ArrowGuard.HINT.takeIf { profile.trackpadArrows },
            onDismissLegend,
            Modifier
        )
    }
}

private val statusWarn = Color(0xFFFFB74D)
private val statusRed = Color(0xFFFF5252)
private const val STATUS_TEXT_SP = 13

@Composable
fun StatusOverlay(hud: String, status: String, profileWarning: String?, modifier: Modifier) {
    Column(modifier) {
        Text(hud, color = Color.White, fontSize = STATUS_TEXT_SP.sp)
        Text(status, color = deckMuted, fontSize = 12.sp)
        profileWarning?.let {
            Text("Profile check: $it", color = statusWarn, fontSize = 11.sp)
        }
    }
}

@Composable
fun RecordingBadge(state: RecState, elapsedMs: Long, modifier: Modifier) {
    Text(
        (if (state == RecState.Paused) "❚❚ " else "● ") + RecStateMachine.clock(elapsedMs),
        color = if (state == RecState.Paused) statusWarn else statusRed,
        fontSize = 16.sp,
        modifier = modifier
    )
}

private const val STRIP_POLL_MS = 100L

/** Calls [onTick] while a strip is showing so it can expire; every new strip restarts this. */
@Composable
fun StripExpiry(deck: DeckState, onTick: () -> Unit) {
    LaunchedEffect(deck.stripShownAtMs, deck.strip != null) {
        while (deck.strip != null) {
            delay(STRIP_POLL_MS)
            onTick()
        }
    }
}
