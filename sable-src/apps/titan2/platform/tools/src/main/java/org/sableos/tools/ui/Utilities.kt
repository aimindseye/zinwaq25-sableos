package org.sableos.tools.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.text.InputType
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.concurrent.thread
import org.sableos.tools.android.StepStore
import org.sableos.tools.core.AccelStepDetector
import org.sableos.tools.core.Command
import org.sableos.tools.core.HoldableReading
import org.sableos.tools.core.Noise
import org.sableos.tools.core.Perm
import org.sableos.tools.core.SirenWave
import org.sableos.tools.core.SosPattern
import org.sableos.tools.core.SpeedTrip
import org.sableos.tools.core.StepCounterSession
import org.sableos.tools.core.StepMath
import org.sableos.tools.core.ToolsText.t
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
        Ui.row(ctx, t("turn_on_off"), null) { toggle() }.also { it.post { it.requestFocus() } },
        Ui.body(
            ctx,
            t("turns_off_when_you_leave")
        )
    )

    private fun toggle() {
        val i = id ?: return host.toast(t("no_flash_unit_found"))
        try {
            cm?.setTorchMode(i, !on)
        } catch (_: android.hardware.camera2.CameraAccessException) {
            host.toast(t("flash_busy_another_app_is"))
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

    override fun info() = t("uses_the_camera_flash_in")
}

/**
 * SOS light, screen flash and siren. Each is its own switch, they stop when the screen is left, and nothing here
 * calls anyone. The siren plays on the alarm stream, so it follows the alarm volume.
 */
class SosController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val handler = Handler(ctx.mainLooper)
    private val cm = ctx.getSystemService(CameraManager::class.java)
    private val torchId: String? = try {
        cm?.cameraIdList?.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: android.hardware.camera2.CameraAccessException) {
        null
    }
    private val state: TextView = Ui.reading(ctx)
    private var light = false
    private var screen = false
    private var siren = false
    private var step = 0
    private var track: AudioTrack? = null
    private var savedBackground: android.graphics.drawable.Drawable? = null

    private val tick = object : Runnable {
        override fun run() {
            val on = SosPattern.isOn(step)
            if (light) setTorch(on)
            if (screen) ctx.window.decorView.setBackgroundColor(if (on) WHITE else BLACK)
            val wait = SosPattern.STEPS[step]
            step = SosPattern.next(step)
            handler.postDelayed(this, wait)
        }
    }

    override fun view(): View = readingColumn(
        ctx,
        state,
        Ui.row(ctx, t("sos_light"), null) { toggleLight() }.also { it.post { it.requestFocus() } },
        Ui.row(ctx, t("sos_screen_flash"), null) { toggleScreen() },
        Ui.row(ctx, t("sos_siren"), null) { toggleSiren() },
        Ui.row(ctx, t("sos_stop_all"), null) { stopAll() },
        Ui.body(ctx, t("sos_info"))
    ).also { refresh() }

    private fun refresh() {
        fun word(on: Boolean) = if (on) "ON" else "OFF"
        state.text = t("sos_status", word(light), word(screen), word(siren))
    }

    private fun toggleLight() {
        if (!light && torchId == null) return host.toast(t("no_flash_unit_found"))
        light = !light
        if (!light) setTorch(false)
        sync()
    }

    private fun toggleScreen() {
        screen = !screen
        if (screen) {
            savedBackground = ctx.window.decorView.background
            setBrightness(1f)
        } else {
            restoreScreen()
        }
        sync()
    }

    private fun toggleSiren() {
        siren = !siren
        if (siren) startSiren() else stopSiren()
        sync()
    }

    private fun stopAll() {
        light = false
        screen = false
        siren = false
        setTorch(false)
        restoreScreen()
        stopSiren()
        sync()
    }

    /** The pattern runs while the light or the screen flash is on. */
    private fun sync() {
        handler.removeCallbacks(tick)
        if (light || screen) {
            step = 0
            handler.post(tick)
        }
        refresh()
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            cm?.setTorchMode(id, on)
        } catch (_: android.hardware.camera2.CameraAccessException) {
            light = false
            host.toast(t("flash_busy_another_app_is"))
        }
    }

    private fun setBrightness(level: Float) {
        val lp = ctx.window.attributes
        lp.screenBrightness = level
        ctx.window.attributes = lp
    }

    private fun restoreScreen() {
        ctx.window.decorView.background = savedBackground
        setBrightness(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun startSiren() {
        val data = SirenWave.samples()
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SirenWave.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        audio.write(data, 0, data.size)
        audio.setLoopPoints(0, data.size, -1)
        audio.play()
        track = audio
    }

    private fun stopSiren() {
        track?.let {
            it.stop()
            it.release()
        }
        track = null
    }

    override fun stop() {
        stopAll()
    }

    override fun onCommand(c: Command) = if (c == Command.Toggle) {
        toggleLight()
        true
    } else {
        false
    }

    override fun info() = t("sos_info")

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
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
            label.text = if (granted) t("no_back_camera") else t("camera_permission_not_granted")
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
            label.text = t("camera_unavailable", e.reason)
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
            ctx.runOnUiThread { label.text = t("camera_error", error) }
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
                        ctx.runOnUiThread { label.text = t("preview_not_supported") }
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
                    t("n1fx_up_down_zoom_s").format(zoom, if (flash) t("light_on") else "")
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
        t("light_on_off") to {
            flash = !flash
            apply()
        }
    )

    override fun info() = t("live_camera_preview_only_nothing")

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
        Ui.body(ctx, t("approximate_phone_microphones_are_not"))
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
            state.text = t("microphone_unavailable")
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
        hold.update(t("n0f_db").format(db))
        reading.text = hold.shown
        state.text =
            (if (hold.isHeld) t("held") else "") + "${Noise.label(db)} · max %.0f dB".format(maxDb)
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

    override fun info() = t("reads_the_microphone_level_only")
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
                t("waiting_for_gps")
        }
    )

    @SuppressLint("MissingPermission") // installed only after location is granted; re-checked here.
    override fun start() {
        if (ctx.checkSelfPermission(Perm.FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) {
            state.text = t("location_is_off_turn_it")
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
            t("n1f_mph").format(
                SpeedTrip.mph(mps)
            )
        } else {
            t("n1f_km_h").format(SpeedTrip.kmh(mps))
        }
        hold.update(fmt(trip.currentMps))
        reading.text = hold.shown
        state.text =
            (if (hold.isHeld) t("held") else "") + (if (ok) "" else t("weak_fix_ignored")) +
            t("distance_2f_km_max_s").format(
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

    override fun actions() = listOf(t("switch_km_h_mph") to { mph = !mph })
    override fun info() = t("gps_speed_and_trip_distance")
    override fun copyText() = hold.shown

    private companion object {
        const val INTERVAL_MS = 1000L
        const val M_PER_KM = 1000.0
        const val MS_PER_S = 1000L
    }
}

/**
 * Step counter when allowed, accelerometer estimate otherwise. No health claims: distance and energy are estimates
 * from the height and weight you enter. With the step counter, the day's total, the daily goal and the last seven
 * days are kept on this phone; the counter keeps running while the tool is closed, so the gain is added on open.
 */
class PedometerController(private val host: ToolHost) :
    ToolController,
    SensorEventListener {
    private val ctx = host.activity
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val reading = Ui.reading(ctx)
    private val state = Ui.body(ctx)
    private val summary = Ui.body(ctx)
    private val counter = StepCounterSession()
    private val accel = AccelStepDetector()
    private val store = StepStore(ctx)
    private val ledger = store.ledger()
    private var profile = store.profile()
    private var source = ""
    private var useLedger = false
    private var lastSaveMs = 0L

    override fun view(): View = readingColumn(ctx, reading.apply { text = "0" }, state, summary)

    override fun start() {
        val m = sm ?: return
        val stepSensor = m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null &&
            ctx.checkSelfPermission(Perm.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        ) {
            source = t("step_counter")
            useLedger = true
            m.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            source = t("accelerometer_estimate")
            useLedger = false
            m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        state.text = t("source_counts_only_while_open", source)
        showSummary()
    }

    override fun stop() {
        sm?.unregisterListener(this)
        if (useLedger) store.save(ledger)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val steps = if (e.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val raw = e.values[0].toLong()
            ledger.sample(raw, today())
            val now = SystemClock.elapsedRealtime()
            if (now - lastSaveMs > SAVE_EVERY_MS) {
                store.save(ledger)
                lastSaveMs = now
            }
            showSummary()
            counter.push(raw)
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

    private fun today(): Long = LocalDate.now().toEpochDay()

    private fun showSummary() {
        if (!useLedger) {
            summary.text = t("pedometer_note")
            return
        }
        val day = today()
        val steps = ledger.steps(day)
        val km = StepMath.distanceKm(steps, profile.heightCm)
        val format = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        summary.text = buildString {
            appendLine(t("pedometer_today", steps, profile.goal))
            appendLine(
                t(
                    "pedometer_estimates",
                    String.format(Locale.getDefault(), "%.1f", km),
                    StepMath.kcal(km, profile.weightKg)
                )
            )
            appendLine(t("pedometer_profile", profile.heightCm, profile.weightKg))
            appendLine()
            appendLine(t("pedometer_last_days"))
            ledger.recent(day, HISTORY_DAYS).forEach { (d, n) ->
                appendLine(t("pedometer_day_steps", LocalDate.ofEpochDay(d).format(format), n))
            }
            appendLine()
            append(t("pedometer_note"))
        }
    }

    override fun actions(): List<Pair<String, () -> Unit>> = listOf(
        t("pedometer_set_height") to {
            ask("pedometer_set_height", profile.heightCm) {
                profile = profile.copy(heightCm = it).safe
            }
        },
        t("pedometer_set_weight") to {
            ask("pedometer_set_weight", profile.weightKg) {
                profile = profile.copy(weightKg = it).safe
            }
        },
        t("pedometer_set_goal") to {
            ask("pedometer_set_goal", profile.goal) {
                profile = profile.copy(goal = it).safe
            }
        }
    )

    private fun ask(titleKey: String, current: Int, apply: (Int) -> Unit) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(ctx)
            .setTitle(t(titleKey))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let {
                    apply(it)
                    store.save(profile)
                    showSummary()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun info() = t("counts_steps_while_this_screen")

    private companion object {
        const val SAVE_EVERY_MS = 5_000L
        const val HISTORY_DAYS = 7
    }
}
