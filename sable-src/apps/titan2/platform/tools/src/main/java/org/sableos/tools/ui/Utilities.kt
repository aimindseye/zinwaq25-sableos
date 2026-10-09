package org.sableos.tools.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executor
import kotlin.concurrent.thread
import org.sableos.tools.core.AccelStepDetector
import org.sableos.tools.core.Command
import org.sableos.tools.core.HoldableReading
import org.sableos.tools.core.Noise
import org.sableos.tools.core.Perm
import org.sableos.tools.core.SpeedTrip
import org.sableos.tools.core.StepCounterSession
import org.sableos.tools.core.Vec3

private fun readingColumn(ctx: android.content.Context, vararg views: View) =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

class FlashlightController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val cm = ctx.getSystemService(CameraManager::class.java)
    private val state: TextView = Ui.reading(ctx)
    private var on = false
    private val id: String? = try {
        cm?.cameraIdList?.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ==
                true
        }
    } catch (_: android.hardware.camera2.CameraAccessException) {
        null
    }
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == id) {
                on = enabled
                state.text = if (enabled) "ON" else "OFF"
            }
        }
    }

    override fun view(): View = readingColumn(
        ctx,
        state.apply { text = "OFF" },
        Ui.row(ctx, "Turn on / off", null) { toggle() }.also { it.post { it.requestFocus() } },
        Ui.body(
            ctx,
            "Turns off when you leave this screen. Quick Settings has a persistent flashlight."
        )
    )

    private fun toggle() {
        val i = id ?: return host.toast("No flash unit found")
        try {
            cm?.setTorchMode(i, !on)
        } catch (_: android.hardware.camera2.CameraAccessException) {
            host.toast("Flash busy (another app is using the camera)")
        }
    }

    override fun start() {
        cm?.registerTorchCallback(callback, Handler(ctx.mainLooper))
    }

    override fun stop() {
        if (on) toggle()
        cm?.unregisterTorchCallback(callback)
    }

    override fun onCommand(c: Command) = if (c == Command.Toggle) {
        toggle()
        true
    } else {
        false
    }

    override fun info() =
        "Uses the camera flash in torch mode. No camera permission is needed and no image is taken."
}

/** Camera preview with digital zoom. Up/Down zoom. Nothing is captured or saved; the system privacy dot shows. */
class MagnifierController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val cm = ctx.getSystemService(CameraManager::class.java)
    private val texture = TextureView(ctx)
    private val label = Ui.subtitle(ctx, "")
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var request: CaptureRequest.Builder? = null
    private var zoom = 2.0f
    private var zoomRange = 1.0f..1.0f
    private var flash = false
    private var bg: HandlerThread? = null
    private val executor = Executor { r -> bg?.let { Handler(it.looper).post(r) } }

    override fun view(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(label)
        addView(texture, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun backCamera(): String? = cm?.cameraIdList?.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    }

    override fun start() {
        bg = HandlerThread("tools-magnifier").also { it.start() }
        if (texture.isAvailable) {
            open()
        } else {
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = open()
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    // preview keeps its buffer size
                }
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                    // nothing to do per frame
                }
            }
        }
    }

    // ToolActivity only installs this controller after CAMERA is granted; re-checked below.
    @SuppressLint("MissingPermission")
    private fun open() {
        val granted =
            ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val manager = cm
        val id = if (granted && manager != null) backCameraOrNull() else null
        if (manager == null || id == null) {
            label.text = if (granted) "No back camera" else "Camera permission not granted"
            return
        }
        manager.getCameraCharacteristics(
            id
        ).get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
            zoomRange = it.lower..it.upper
        }
        zoom = zoom.coerceIn(zoomRange)
        try {
            manager.openCamera(id, executor, deviceCallback)
        } catch (e: android.hardware.camera2.CameraAccessException) {
            label.text = "Camera unavailable (${e.reason})"
        }
    }

    private fun backCameraOrNull(): String? = try {
        backCamera()
    } catch (_: android.hardware.camera2.CameraAccessException) {
        null
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(d: CameraDevice) {
            device = d
            startPreview(d)
        }

        override fun onDisconnected(d: CameraDevice) = d.close()

        override fun onError(d: CameraDevice, error: Int) {
            d.close()
            ctx.runOnUiThread { label.text = "Camera error $error" }
        }
    }

    private fun startPreview(d: CameraDevice) {
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(texture.width.coerceAtLeast(1), texture.height.coerceAtLeast(1))
        val surface = Surface(st)
        val rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
        request = rb
        d.createCaptureSession(
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(surface)),
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        apply()
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        ctx.runOnUiThread { label.text = "Preview not supported" }
                    }
                }
            )
        )
    }

    private fun apply() {
        val rb = request ?: return
        rb.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        rb.set(
            CaptureRequest.FLASH_MODE,
            if (flash) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
        val ok = try {
            session?.setRepeatingRequest(rb.build(), null, null)
            true
        } catch (_: android.hardware.camera2.CameraAccessException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
        if (ok) {
            ctx.runOnUiThread {
                label.text =
                    "%.1fx (Up/Down zoom)%s".format(zoom, if (flash) " · light on" else "")
            }
        }
    }

    override fun stop() {
        session?.close()
        session = null
        device?.close()
        device = null
        bg?.quitSafely()
        bg = null
    }

    override fun onCommand(c: Command): Boolean {
        if (c !is Command.Move || c.dy == 0) return false
        zoom = (zoom - c.dy * ZOOM_STEP).coerceIn(zoomRange)
        apply()
        return true
    }

    override fun actions() = listOf(
        "Light on / off" to {
            flash = !flash
            apply()
        }
    )

    override fun info() = "Live camera preview only. Nothing is captured, recorded or saved."

    private companion object {
        const val ZOOM_STEP = 0.5f
    }
}

/** Microphone level while the screen is open. No audio is stored (NOISE_TEST_NO_AUDIO_STORAGE_BY_DEFAULT). */
class NoiseController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val reading = Ui.reading(ctx)
    private val state = Ui.body(ctx)
    private val hold = HoldableReading<String>()
    private var maxDb = 0.0

    @Volatile private var running = false

    override fun view(): View = readingColumn(
        ctx,
        reading,
        state,
        Ui.body(ctx, "Approximate: phone microphones are not calibrated sound meters.")
    )

    // Installed only after RECORD_AUDIO is granted; re-checked here.
    @SuppressLint("MissingPermission")
    private fun openRecorder(size: Int): AudioRecord? {
        val granted =
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val rec = if (granted) {
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
        if (rec != null && rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        return rec
    }

    override fun start() {
        val size = AudioRecord.getMinBufferSize(
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
            .coerceAtLeast(MIN_BUF)
        val rec = openRecorder(size)
        if (rec == null) {
            state.text = "Microphone unavailable"
            return
        }
        running = true
        thread(name = "tools-noise") {
            val buf = ShortArray(size / 2)
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    val db = Noise.approxDb(Noise.rmsDbfs(buf, n))
                    ctx.runOnUiThread { show(db) }
                }
            }
            rec.stop()
            rec.release()
        }
    }

    private fun show(db: Double) {
        maxDb = maxOf(maxDb, db)
        hold.update("%.0f dB".format(db))
        reading.text = hold.shown
        state.text =
            (if (hold.isHeld) "HELD · " else "") + "${Noise.label(db)} · max %.0f dB".format(maxDb)
    }

    override fun stop() {
        running = false
    }

    override fun onCommand(c: Command): Boolean = when (c) {
        Command.Hold -> {
            hold.toggleHold()
            true
        }

        Command.Reset -> {
            maxDb = 0.0
            hold.reset()
            true
        }

        else -> false
    }

    override fun info() =
        "Reads the microphone level only while open. Audio is never recorded, kept or sent."
    override fun copyText() = hold.shown

    private companion object {
        const val RATE = 44_100
        const val MIN_BUF = 4096
    }
}

/** GPS speed while open; local only, forgotten when you leave. */
class SpeedController(private val host: ToolHost) :
    ToolController,
    LocationListener {
    private val ctx = host.activity
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private val reading = Ui.reading(ctx)
    private val state = Ui.body(ctx)
    private val trip = SpeedTrip()
    private val hold = HoldableReading<String>()
    private var mph = false

    override fun view(): View = readingColumn(
        ctx,
        reading.apply { text = "--" },
        state.apply {
            text =
                "Waiting for GPS…"
        }
    )

    @SuppressLint("MissingPermission") // installed only after location is granted; re-checked here.
    override fun start() {
        if (ctx.checkSelfPermission(Perm.FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) {
            state.text = "Location is off. Turn it on in Quick Settings."
            return
        }
        lm.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            INTERVAL_MS,
            0f,
            ctx.mainExecutor,
            this
        )
    }

    override fun stop() {
        lm?.removeUpdates(this)
    }

    override fun onLocationChanged(l: Location) {
        val ok = trip.push(
            SpeedTrip.Fix(
                l.latitude,
                l.longitude,
                l.time,
                l.accuracy.toDouble(),
                if (l.hasSpeed()) l.speed.toDouble() else null
            )
        )
        fun fmt(mps: Double) = if (mph) {
            "%.1f mph".format(
                SpeedTrip.mph(mps)
            )
        } else {
            "%.1f km/h".format(SpeedTrip.kmh(mps))
        }
        hold.update(fmt(trip.currentMps))
        reading.text = hold.shown
        state.text =
            (if (hold.isHeld) "HELD · " else "") + (if (ok) "" else "weak fix ignored · ") +
            "distance %.2f km · max %s · avg %s · %d s".format(
                trip.distanceM / M_PER_KM,
                fmt(trip.maxSpeedMps),
                fmt(trip.averageMps),
                trip.elapsedMs / MS_PER_S
            )
    }

    override fun onCommand(c: Command): Boolean = when (c) {
        Command.Hold -> {
            hold.toggleHold()
            true
        }

        Command.Reset -> {
            trip.reset()
            hold.reset()
            true
        }

        else -> false
    }

    override fun actions() = listOf("Switch km/h / mph" to { mph = !mph })
    override fun info() =
        "GPS speed and trip distance while this screen is open. Nothing is stored or shared."
    override fun copyText() = hold.shown

    private companion object {
        const val INTERVAL_MS = 1000L
        const val M_PER_KM = 1000.0
        const val MS_PER_S = 1000L
    }
}

/** Step counter when allowed, accelerometer estimate otherwise. No health claims. */
class PedometerController(private val host: ToolHost) :
    ToolController,
    SensorEventListener {
    private val ctx = host.activity
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val reading = Ui.reading(ctx)
    private val state = Ui.body(ctx)
    private val counter = StepCounterSession()
    private val accel = AccelStepDetector()
    private var source = ""

    override fun view(): View = readingColumn(ctx, reading.apply { text = "0" }, state)

    override fun start() {
        val m = sm ?: return
        val stepSensor = m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null &&
            ctx.checkSelfPermission(Perm.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        ) {
            source = "step counter"
            m.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            source = "accelerometer estimate"
            m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        state.text = "source: $source · counts only while open"
    }

    override fun stop() {
        sm?.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val steps = if (e.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            counter.push(e.values[0].toLong())
        } else {
            accel.push(
                Vec3(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble()).norm,
                SystemClock.elapsedRealtime()
            )
            accel.steps
        }
        reading.text = steps.toString()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // step counts do not depend on accuracy
    }

    override fun onCommand(c: Command): Boolean = if (c == Command.Reset) {
        counter.reset()
        accel.reset()
        reading.text = "0"
        true
    } else {
        false
    }

    override fun info() =
        "Counts steps while this screen is open. An estimate, not a health measurement."
}
