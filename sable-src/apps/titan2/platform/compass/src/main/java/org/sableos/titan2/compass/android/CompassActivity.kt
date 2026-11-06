package org.sableos.titan2.compass.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import org.sableos.titan2.compass.core.Accuracy
import org.sableos.titan2.compass.core.CompassText.t
import org.sableos.titan2.compass.core.Heading
import org.sableos.titan2.theme.SableFonts
import org.sableos.titan2.theme.SablePalette

/**
 * Sable Compass: the heading in degrees and words over a card that tilts with the phone. T turns true north on
 * or off (it asks for a rough location the first time), C opens the calibration guide, H holds the reading,
 * Back leaves. The screen stays on while it is open.
 */
class CompassActivity :
    Activity(),
    SensorEventListener {
    private lateinit var palette: SablePalette
    private lateinit var sensors: SensorManager
    private lateinit var dial: DialView
    private lateinit var number: TextView
    private lateinit var word: TextView
    private lateinit var mode: TextView
    private lateinit var status: TextView
    private lateinit var calibration: View
    private lateinit var calibrationStatus: TextView

    private val rotation = FloatArray(MATRIX)
    private var gravity: FloatArray? = null
    private var field: FloatArray? = null
    private var shown: Double? = null
    private var accuracy = Accuracy.UNRELIABLE
    private var held = false
    private var trueNorth = false
    private var declination: Double? = null
    private var locationDenied = false
    private var usesVector = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = SablePalette.read(this).also { it.applyTo(this) }
        sensors = getSystemService(SensorManager::class.java)
        trueNorth = prefs().getBoolean(KEY_TRUE_NORTH, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pad = dp(PAD)
        val body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(palette.window)
            }
        number = text(TITLE_SP, palette.text, bold = true).also(body::addView)
        word = text(BODY_SP, palette.text).also(body::addView)
        mode = text(SMALL_SP, palette.muted).also(body::addView)
        dial = DialView(this, palette)
        body.addView(dial, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        status = text(SMALL_SP, palette.muted).also(body::addView)
        body.addView(text(SMALL_SP, palette.muted).apply { text = t("hint") })
        calibration = calibrationScreen()
        setContentView(
            FrameLayout(this).apply {
                addView(body)
                addView(calibration)
            }
        )
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            back()
        }
        if (trueNorth) locate()
        show()
    }

    private fun calibrationScreen(): View {
        val pad = dp(PAD)
        val box =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(palette.window)
                visibility = View.GONE
            }
        box.addView(text(HEADING_SP, palette.text, bold = true).apply { text = t("calibrate") })
        box.addView(text(BODY_SP, palette.text).apply { text = t("calibrate_body") })
        box.addView(
            Figure8View(this, palette),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        calibrationStatus = text(BODY_SP, palette.text, bold = true).also(box::addView)
        box.addView(text(SMALL_SP, palette.muted).apply { text = t("calibrate_hint") })
        return box
    }

    override fun onResume() {
        super.onResume()
        val vector = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magnet = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (magnet == null) {
            status.text = t("no_sensor")
            return
        }
        // The magnetometer is always listened to, for its accuracy; the fused rotation vector gives the
        // steadier heading where the phone has one, otherwise gravity and the field are combined here.
        sensors.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI)
        if (vector != null) {
            sensors.registerListener(this, vector, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        usesVector = vector != null
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (updateRotation(event) && !held) turn()
    }

    /** Takes in one reading; true when the rotation is new and the card should follow it. */
    private fun updateRotation(event: SensorEvent): Boolean = when (event.sensor.type) {
        Sensor.TYPE_ROTATION_VECTOR -> {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            true
        }

        Sensor.TYPE_ACCELEROMETER -> {
            gravity = event.values.clone()
            fromParts()
        }

        Sensor.TYPE_MAGNETIC_FIELD -> {
            setAccuracy(event.accuracy)
            field = event.values.clone()
            !usesVector && fromParts()
        }

        else -> false
    }

    private fun turn() {
        val facing = Heading.facing(rotation)
        dial.squash = Heading.squash(Heading.tilt(rotation))
        if (facing == null) {
            status.text = t("flat_or_upright")
        } else {
            shown = Heading.smooth(shown, facing, SMOOTHING)
            show()
        }
    }

    /** Without a rotation vector: the rotation from gravity and the magnetic field, when both are in. */
    private fun fromParts(): Boolean {
        val g = gravity
        val m = field
        return g != null && m != null && SensorManager.getRotationMatrix(rotation, null, g, m)
    }

    override fun onAccuracyChanged(sensor: Sensor, status: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) setAccuracy(status)
    }

    private fun setAccuracy(status: Int) {
        val next = Accuracy.of(status)
        if (next == accuracy) return
        accuracy = next
        show()
    }

    private fun show() {
        val trueOffset = declination.takeIf { trueNorth }
        val heading = shown?.let { if (trueOffset != null) Heading.toTrue(it, trueOffset) else it }
        number.text = heading?.let { t("degrees", Heading.degrees(it)) } ?: "--"
        number.setTextColor(if (accuracy.needsCalibration) palette.warn else palette.text)
        word.text = heading?.let { t(Heading.word(it)) }.orEmpty()
        val north = if (trueOffset != null) t("true_north") else t("magnetic")
        mode.text = north + " · " + t(accuracy.key)
        dial.trueNorth = trueOffset != null
        if (heading != null) dial.heading = heading
        status.text = statusLine(trueOffset != null)
        calibrationStatus.text =
            if (accuracy == Accuracy.HIGH) t("calibrate_done") else t(accuracy.key)
    }

    private fun statusLine(trueKnown: Boolean): String = when {
        held -> t("held")
        trueNorth && locationDenied -> t("true_denied")
        trueNorth && !trueKnown -> t("true_waiting")
        else -> ""
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_T -> toggleTrueNorth()

            KeyEvent.KEYCODE_C ->
                calibration.visibility =
                    if (calibration.visibility == View.VISIBLE) View.GONE else View.VISIBLE

            KeyEvent.KEYCODE_H -> {
                held = !held
                show()
            }

            KeyEvent.KEYCODE_ESCAPE -> back()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun back() {
        if (calibration.visibility == View.VISIBLE) calibration.visibility = View.GONE else finish()
    }

    private fun toggleTrueNorth() {
        trueNorth = !trueNorth
        prefs().edit().putBoolean(KEY_TRUE_NORTH, trueNorth).apply()
        if (trueNorth) locate()
        show()
    }

    /** A rough location, only to work out the declination on the phone. Asked for the first time it is needed. */
    private fun locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
        } else {
            locationDenied = false
            getSystemService(LocationManager::class.java)?.let(::locateWith)
        }
    }

    private fun locateWith(manager: LocationManager) {
        try {
            val known =
                manager.getProviders(true)
                    .mapNotNull { manager.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
            val provider =
                listOf(
                    LocationManager.FUSED_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER
                )
                    .firstOrNull { manager.isProviderEnabled(it) }
            when {
                known != null -> useLocation(known)

                provider != null -> manager.getCurrentLocation(provider, null, mainExecutor) {
                    it?.let(::useLocation)
                }
            }
        } catch (_: SecurityException) {
            locationDenied = true
        }
    }

    private fun useLocation(location: Location) {
        declination =
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            ).declination.toDouble()
        show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        if (requestCode != REQUEST_LOCATION) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            locate()
        } else {
            locationDenied = true
            show()
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER_HORIZONTAL
        typeface = SableFonts.of(bold)
    }

    private companion object {
        const val MATRIX = 9
        const val SMOOTHING = 0.2
        const val REQUEST_LOCATION = 1
        const val PREFS = "compass"
        const val KEY_TRUE_NORTH = "true_north"
        const val PAD = 12
        const val TITLE_SP = 40f
        const val HEADING_SP = 20f
        const val BODY_SP = 16f
        const val SMALL_SP = 12f
    }
}
