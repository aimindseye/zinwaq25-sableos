package org.sableos.titan2.camera.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import org.sableos.titan2.camera.core.AutoSeed
import org.sableos.titan2.camera.core.CameraReport
import org.sableos.titan2.camera.core.CaptureOrientation
import org.sableos.titan2.camera.core.CapturePlanner
import org.sableos.titan2.camera.core.PreviewChooser
import org.sableos.titan2.camera.core.ProControls
import org.sableos.titan2.camera.core.ProLimits
import org.sableos.titan2.camera.core.ProState
import org.sableos.titan2.camera.core.StillKind
import org.sableos.titan2.camera.core.StillPlan
import org.sableos.titan2.camera.core.VideoOption
import org.sableos.titan2.camera.core.VideoPlanner
import org.sableos.titan2.camera.core.ZoomPlanner

private const val DEFAULT_SENSOR_ORIENTATION = 90
private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val VIDEO_FRAME_RATE = 30
private const val AUDIO_BIT_RATE = 128_000
private const val AUDIO_SAMPLE_RATE = 44_100

/** The three callbacks a still/preview configuration reports through. */
class StillCallbacks(
    val onReady: () -> Unit,
    val onSaved: (String) -> Unit,
    val onError: (String) -> Unit
)

/** What the user asked to record: the size, whether the microphone may be used, and the display rotation. */
class RecordingSpec(val option: VideoOption, val mic: Boolean, val displayRotationDegrees: Int)

private class RecordingContext(
    val device: CameraDevice,
    val chars: CameraCharacteristics,
    val report: CameraReport
)

private class RecordingJob(
    val ctx: RecordingContext,
    val spec: RecordingSpec,
    val texture: SurfaceTexture,
    val onStarted: () -> Unit,
    val onError: (String) -> Unit
)

/**
 * Camera2 controller for ordinary public camera ids. One session per still plan: changing mode or
 * camera reconfigures, which keeps the stream set simple (preview + one JPEG or RAW reader) and
 * lets maximum-resolution streams use their own pixel mode.
 * All callbacks arrive on the camera thread; the UI layer must post to the main thread.
 */
class CameraController(
    private val context: Context,
    private val reader: CameraInfoReader,
    private val saver: MediaStoreSaver
) {
    private val mgr = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("sable-camera").also { it.start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var chars: CameraCharacteristics? = null
    private var report: CameraReport? = null
    private var plan: StillPlan? = null
    private var previewSurface: Surface? = null
    private var savedCb: ((String) -> Unit)? = null
    private var errorCb: ((String) -> Unit)? = null

    // Pro mode: null state = ordinary auto camera. The seed is the camera's own last AE/AF choice
    // so the first manual step starts near it.
    private var proState: ProState? = null
    private var proLimits: ProLimits? = null

    @Volatile private var seed = AutoSeed()
    fun autoSeed(): AutoSeed = seed

    // Video recording state (a recorder exists only between video.start and video.stop).
    private var recorder: MediaRecorder? = null
    private var videoTarget: MediaStoreSaver.VideoTarget? = null
    private var recordStartMs = 0L
    private var pausedAtMs = 0L
    private var pausedTotalMs = 0L
    private var videoName: String? = null
    val isRecording: Boolean get() = recorder != null

    /** Video recording controls (start/pause/resume/stop and the elapsed clock). */
    val video = VideoControls()

    var zoom = 1f
        private set
    var aeSteps = 0
        private set
    var previewSize: org.sableos.titan2.camera.core.Size? = null
        private set

    // RAW needs the Image and the TotalCaptureResult together; they arrive independently.
    private var pendingImage: Image? = null
    private var pendingResult: TotalCaptureResult? = null
    private var pendingName: String? = null

    @SuppressLint("MissingPermission")
    fun configure(
        r: CameraReport,
        p: StillPlan,
        texture: SurfaceTexture,
        callbacks: StillCallbacks,
        previewFor: org.sableos.titan2.camera.core.Size = p.size
    ) {
        this.report = r
        this.plan = p
        this.savedCb = callbacks.onSaved
        this.errorCb = callbacks.onError
        val c = reader.characteristics(p.cameraId)
        chars = c
        val pv = PreviewChooser.choose(previewSizes(c), previewFor)
        if (pv == null) {
            callbacks.onError("No preview size available for camera ${p.cameraId}")
            return
        }
        previewSize = pv
        texture.setDefaultBufferSize(pv.w, pv.h)
        previewSurface?.release()
        previewSurface = Surface(texture)
        zoom = ZoomPlanner.clamp(zoom, r.camera.zoomRatio)
        closeSession()
        val open = device
        if (open != null && open.id == p.cameraId) {
            createSession(open, callbacks.onReady)
        } else {
            open?.close()
            device = null
            openCamera(p.cameraId, callbacks)
        }
    }

    private fun previewSizes(c: CameraCharacteristics): List<org.sableos.titan2.camera.core.Size> =
        c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.map { org.sableos.titan2.camera.core.Size(it.width, it.height) }
            .orEmpty()

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String, callbacks: StillCallbacks) {
        runCatching {
            mgr.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) {
                        device = d
                        createSession(d, callbacks.onReady)
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        d.close()
                        device = null
                        errorCb?.invoke("Camera disconnected")
                    }

                    override fun onError(d: CameraDevice, error: Int) {
                        d.close()
                        device = null
                        errorCb?.invoke("Camera error $error")
                    }
                },
                handler
            )
        }.onFailure { callbacks.onError("Cannot open camera $cameraId: ${it.message}") }
    }

    private fun createSession(d: CameraDevice, onReady: () -> Unit) {
        val p = plan ?: return
        val fmt = if (p.kind == StillKind.RawDng) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
        imageReader?.close()
        val ir = ImageReader.newInstance(p.size.w, p.size.h, fmt, 2).also { imageReader = it }
        ir.setOnImageAvailableListener({ r -> r.acquireNextImage()?.let { onImage(it) } }, handler)
        val pv = previewSurface ?: return
        val outputs = mutableListOf(OutputConfiguration(pv), OutputConfiguration(ir.surface))
        if (p.maxResMode) {
            outputs.forEach {
                it.addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
            }
        }
        val cfg =
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        previewBuilder =
                            d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(pv)
                                applyCommon(this, p)
                            }
                        s.setRepeatingRequest(previewBuilder!!.build(), seedCallback, handler)
                        onReady()
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        errorCb?.invoke(
                            "Could not configure ${p.kind} ${p.size} on camera ${p.cameraId}"
                        )
                    }
                }
            )
        runCatching { d.createCaptureSession(cfg) }
            .onFailure { errorCb?.invoke("Session failed: ${it.message}") }
    }

    private val seedCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            // Only record the camera's own choices while it is in auto; manual values are ours
            // already.
            val iso = result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)
            val ns = result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)
            val fd = result.get(android.hardware.camera2.CaptureResult.LENS_FOCUS_DISTANCE)
            seed = AutoSeed(iso ?: seed.iso, ns ?: seed.exposureNs, fd ?: seed.focusDiopters)
        }
    }

    private fun applyCommon(b: CaptureRequest.Builder, p: StillPlan) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        report?.camera?.zoomRatio?.let { b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom) }
        val ps = proState
        val pl = proLimits
        if (ps == null || pl == null) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeSteps)
        } else {
            val pr = ProControls.toRequest(ps, pl, seed)
            b.set(CaptureRequest.CONTROL_AWB_MODE, pr.awbMode)
            if (pr.aeOn) {
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, pr.evSteps)
            } else {
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                b.set(CaptureRequest.SENSOR_SENSITIVITY, pr.iso)
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, pr.exposureNs)
                b.set(CaptureRequest.SENSOR_FRAME_DURATION, pr.frameDurationNs)
            }
            if (pr.afOn) {
                b.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            } else {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, pr.focusDiopters)
            }
        }
        if (p.maxResMode) {
            b.set(
                CaptureRequest.SENSOR_PIXEL_MODE,
                CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION
            )
        }
    }

    /**
     * Apply Pro state to the live preview and later stills. Pass null to return to the ordinary
     * auto camera.
     */
    fun setPro(state: ProState?, limits: ProLimits?) {
        proState = state
        proLimits = limits
        refreshPreview()
    }

    private fun refreshPreview() {
        val b = previewBuilder
        val s = session
        val p = plan
        if (b == null || s == null || p == null) return
        if (recorder != null) {
            // Recording owns AF/AE mode; only zoom and exposure compensation may change mid-clip.
            report?.camera?.zoomRatio?.let { b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom) }
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeSteps)
        } else {
            applyCommon(b, p)
        }
        runCatching { s.setRepeatingRequest(b.build(), seedCallback, handler) }
    }

    fun stepZoom(dir: Int) {
        zoom = ZoomPlanner.step(zoom, dir, report?.camera?.zoomRatio)
        refreshPreview()
    }

    fun stepExposure(dir: Int) {
        val range = report?.camera?.aeCompensationSteps ?: return
        aeSteps = (aeSteps + dir).coerceIn(range.lo, range.hi)
        refreshPreview()
    }

    /** One-shot autofocus trigger; continuous AF resumes afterwards. */
    fun triggerFocus() {
        val b = previewBuilder ?: return
        val s = session ?: return
        runCatching {
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            s.capture(b.build(), null, handler)
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            s.setRepeatingRequest(b.build(), seedCallback, handler)
        }
    }

    fun takePicture(displayRotationDegrees: Int) {
        val d = device
        val s = session
        val ir = imageReader
        if (d != null && s != null && ir != null) captureStill(d, s, ir, displayRotationDegrees)
    }

    private fun captureStill(
        d: CameraDevice,
        s: CameraCaptureSession,
        ir: ImageReader,
        displayRotationDegrees: Int
    ) {
        val p = plan ?: return
        val c = chars ?: return
        val orient = CaptureOrientation.jpeg(
            sensorOrientationOf(c),
            displayRotationDegrees,
            isFrontFacing(c)
        )
        val (date, time) = timestamps()
        pendingName = CapturePlanner.fileName(p, date, time)
        pendingImage = null
        pendingResult = null
        pendingOrientation = orient
        runCatching {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(ir.surface)
                applyCommon(this, p)
                set(CaptureRequest.JPEG_ORIENTATION, orient)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte()) // 95 as proven in the Titan 2 MVP
            }
            s.capture(b.build(), stillCaptureCallback(p), handler)
        }.onFailure { errorCb?.invoke("Capture error: ${it.message}") }
    }

    private fun stillCaptureCallback(p: StillPlan) =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (p.kind == StillKind.RawDng) {
                    synchronized(this@CameraController) { pendingResult = result }
                    maybeWriteDng()
                }
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: android.hardware.camera2.CaptureFailure
            ) {
                errorCb?.invoke("Capture failed (reason ${failure.reason})")
            }
        }

    private fun isFrontFacing(c: CameraCharacteristics): Boolean =
        c.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT

    private fun sensorOrientationOf(c: CameraCharacteristics): Int =
        c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: DEFAULT_SENSOR_ORIENTATION

    /** File-name timestamp parts: (yyyyMMdd, HHmmss) for the same instant. */
    private fun timestamps(): Pair<String, String> {
        val now = Date()
        return Pair(
            SimpleDateFormat("yyyyMMdd", Locale.US).format(now),
            SimpleDateFormat("HHmmss", Locale.US).format(now)
        )
    }

    private var pendingOrientation = 0

    private fun onImage(img: Image) {
        val p = plan ?: run {
            img.close()
            return
        }
        if (p.kind == StillKind.RawDng) {
            synchronized(this) {
                pendingImage?.close()
                pendingImage = img
            }
            maybeWriteDng()
            return
        }
        val name = pendingName ?: "SBL_unnamed.jpg"
        try {
            val buf = img.planes[0].buffer
            val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
            val uri = saver.save(name, "image/jpeg") { it.write(bytes) }
            if (uri != null) {
                savedCb?.invoke("$name  ${p.size}")
            } else {
                errorCb?.invoke("Could not save $name")
            }
        } finally {
            img.close()
        }
    }

    private fun maybeWriteDng() {
        val p = plan
        val c = chars
        if (p == null || c == null) return
        val pending = synchronized(this) {
            val img = pendingImage
            val res = pendingResult
            if (img != null && res != null) {
                pendingImage = null
                pendingResult = null
                Pair(img, res)
            } else {
                null
            }
        }
        if (pending == null) return
        val image = pending.first
        val result = pending.second
        val name = pendingName ?: "SBL_unnamed.dng"
        try {
            val dng = DngCreator(c, result).apply {
                setOrientation(exifOrientation(pendingOrientation))
            }
            val uri = saver.save(name, "image/x-adobe-dng") { dng.writeImage(it, image) }
            dng.close()
            if (uri != null) {
                savedCb?.invoke("$name  ${p.size}")
            } else {
                errorCb?.invoke("Could not save $name")
            }
        } finally {
            image.close()
        }
    }

    private fun exifOrientation(deg: Int) = when (deg) {
        QUARTER_TURN -> android.media.ExifInterface.ORIENTATION_ROTATE_90
        HALF_TURN -> android.media.ExifInterface.ORIENTATION_ROTATE_180
        THREE_QUARTER_TURN -> android.media.ExifInterface.ORIENTATION_ROTATE_270
        else -> android.media.ExifInterface.ORIENTATION_NORMAL
    }

    // ------------------------------------------------------------------ video

    /** Video recording on the already-open camera; reached through [video]. */
    inner class VideoControls {
        /**
         * Start recording. The recorder needs its output before its surface exists, so a session
         * is rebuilt here (preview + recorder surface) instead of keeping an empty file around
         * while the user is only framing. Audio is optional: without the microphone permission
         * the clip is silent. Orientation uses the same rule as stills.
         */
        fun start(
            spec: RecordingSpec,
            texture: SurfaceTexture,
            onStarted: () -> Unit,
            onErrorCb: (String) -> Unit
        ) {
            val ctx = prepare(onErrorCb)
            if (ctx != null) begin(RecordingJob(ctx, spec, texture, onStarted, onErrorCb))
        }

        private fun prepare(onErrorCb: (String) -> Unit): RecordingContext? {
            val d = device
            val c = chars
            val r = report
            if (d == null || c == null) {
                onErrorCb(if (d == null) "Camera not open" else "No camera characteristics")
                return null
            }
            return if (r == null || recorder != null) null else RecordingContext(d, c, r)
        }

        private fun begin(job: RecordingJob) {
            val (date, time) = timestamps()
            val name = VideoPlanner.fileName(job.ctx.device.id, job.spec.option.size, date, time)
            val target = saver.createVideo(name)
            if (target == null) {
                job.onError("Could not create $name")
            } else {
                val rec = runCatching { newRecorder(job, target) }.getOrElse {
                    saver.discard(target)
                    job.onError("Recorder setup failed: ${it.message}")
                    null
                }
                if (rec != null) attachPreview(job, name, target, rec)
            }
        }

        private fun newRecorder(
            job: RecordingJob,
            target: MediaStoreSaver.VideoTarget
        ): MediaRecorder {
            val opt = job.spec.option
            val mic = job.spec.mic
            val hint = CaptureOrientation.jpeg(
                sensorOrientationOf(job.ctx.chars),
                job.spec.displayRotationDegrees,
                isFrontFacing(job.ctx.chars)
            )
            return MediaRecorder(context).apply {
                if (mic) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(target.pfd.fileDescriptor)
                setVideoEncodingBitRate(VideoPlanner.bitrate(opt.size))
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(opt.size.w, opt.size.h)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (mic) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(AUDIO_BIT_RATE)
                    setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                }
                setOrientationHint(hint)
                prepare()
            }
        }

        private fun attachPreview(
            job: RecordingJob,
            name: String,
            target: MediaStoreSaver.VideoTarget,
            rec: MediaRecorder
        ) {
            val pv = PreviewChooser.choose(previewSizes(job.ctx.chars), job.spec.option.size)
            if (pv == null) {
                rec.release()
                saver.discard(target)
                job.onError("No preview size for video")
            } else {
                previewSize = pv
                job.texture.setDefaultBufferSize(pv.w, pv.h)
                previewSurface?.release()
                val ps = Surface(job.texture)
                previewSurface = ps
                openRecordingSession(job, ps, name, target, rec)
            }
        }

        private fun openRecordingSession(
            job: RecordingJob,
            ps: Surface,
            name: String,
            target: MediaStoreSaver.VideoTarget,
            rec: MediaRecorder
        ) {
            closeSession()
            recorder = rec
            videoTarget = target
            videoName = name
            val outputs = listOf(OutputConfiguration(ps), OutputConfiguration(rec.surface))
            val cfg =
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    executor,
                    recordingStateCallback(job, ps, rec)
                )
            runCatching { job.ctx.device.createCaptureSession(cfg) }.onFailure {
                abortRecording()
                job.onError("Session failed: ${it.message}")
            }
        }

        private fun recordingStateCallback(job: RecordingJob, ps: Surface, rec: MediaRecorder) =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    runCatching {
                        val b = job.ctx.device.createCaptureRequest(
                            CameraDevice.TEMPLATE_RECORD
                        ).apply {
                            addTarget(ps)
                            addTarget(rec.surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            )
                            job.ctx.report.camera.zoomRatio?.let {
                                set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
                            }
                            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeSteps)
                        }
                        previewBuilder = b
                        s.setRepeatingRequest(b.build(), seedCallback, handler)
                        rec.start()
                        recordStartMs = SystemClock.elapsedRealtime()
                        pausedAtMs = 0
                        pausedTotalMs = 0
                        job.onStarted()
                    }.onFailure {
                        abortRecording()
                        job.onError("Recording failed to start: ${it.message}")
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    abortRecording()
                    job.onError("Could not configure ${job.spec.option.label} for recording")
                }
            }

        fun pause() {
            recorder?.let {
                runCatching {
                    it.pause()
                    pausedAtMs = SystemClock.elapsedRealtime()
                }
            }
        }

        fun resume() {
            recorder?.let {
                runCatching {
                    it.resume()
                    if (pausedAtMs != 0L) {
                        pausedTotalMs += SystemClock.elapsedRealtime() - pausedAtMs
                    }
                    pausedAtMs = 0
                }
            }
        }

        /** Elapsed recording time without paused spans. */
        fun elapsedMs(): Long {
            if (recorder == null) return 0
            val end = if (pausedAtMs != 0L) pausedAtMs else SystemClock.elapsedRealtime()
            return (end - recordStartMs - pausedTotalMs).coerceAtLeast(0)
        }

        /**
         * Stop and publish the clip. A recording stopped within moments can fail in the encoder;
         * that clip is discarded and reported.
         */
        fun stop(onSavedCb: (String) -> Unit, onErrorCb: (String) -> Unit) {
            val rec = recorder ?: return
            val target = videoTarget
            val name = videoName ?: "video.mp4"
            recorder = null
            videoTarget = null
            videoName = null
            closeSession()
            val ok = runCatching { rec.stop() }.isSuccess
            runCatching { rec.reset() }
            runCatching { rec.release() }
            if (target == null) return
            if (ok) {
                saver.finishVideo(target)
                onSavedCb(name)
            } else {
                saver.discard(target)
                onErrorCb("Recording was too short to save")
            }
        }
    }

    private fun abortRecording() {
        val rec = recorder
        val target = videoTarget
        recorder = null
        videoTarget = null
        videoName = null
        closeSession()
        rec?.let {
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        target?.let { saver.discard(it) }
    }

    fun closeSession() {
        runCatching { session?.close() }
        session = null
    }

    fun release() {
        if (recorder != null) video.stop({}, {})
        closeSession()
        runCatching { device?.close() }
        device = null
        imageReader?.close()
        imageReader = null
        previewSurface?.release()
        previewSurface = null
        thread.quitSafely()
    }
}
