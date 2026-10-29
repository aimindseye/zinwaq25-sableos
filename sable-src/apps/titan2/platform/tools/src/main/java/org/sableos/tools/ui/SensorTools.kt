package org.sableos.tools.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.tools.core.Capability
import org.sableos.tools.core.Command
import org.sableos.tools.core.Compass
import org.sableos.tools.core.HeightEstimate
import org.sableos.tools.core.HoldableReading
import org.sableos.tools.core.LowPass
import org.sableos.tools.core.Tilt
import org.sableos.tools.core.TiltCalibration
import org.sableos.tools.core.ToolsText.t
import org.sableos.tools.core.Vec3

private fun SensorEvent.vec() =
    Vec3(values[0].toDouble(), values[1].toDouble(), values[2].toDouble())

/** Base for accelerometer/magnetometer tools: registers only while started, shows reading + state lines. */
abstract class SensorController(protected val host: ToolHost) :
    ToolController,
    SensorEventListener {
    protected val ctx = host.activity
    protected val sm: SensorManager? = ctx.getSystemService(SensorManager::class.java)
    protected val readingView: TextView = Ui.reading(ctx)
    protected val stateView: TextView = Ui.body(ctx)
    protected val detailView: TextView = Ui.mono(ctx)
    protected val accel = LowPass()

    override fun view(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(readingView)
        addView(stateView)
        addView(detailView)
    }

    protected open val sensorTypes = listOf(Sensor.TYPE_ACCELEROMETER)

    override fun start() {
        val m = sm ?: run {
            stateView.text = t("sensor_service_unavailable")
            return
        }
        sensorTypes.forEach { t ->
            m.getDefaultSensor(t)?.let {
                m.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun stop() {
        sm?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // subclasses that care (compass) override this
    }
}

/** Bubble level, picture hanging, plumb bob and protractor share the tilt math; only the presentation differs. */
class TiltController(host: ToolHost, private val capability: Capability) : SensorController(host) {
    private var cal = host.env.prefs.calibration(capability) ?: TiltCalibration()
    private val hold = HoldableReading<String>()
    private var last: Vec3? = null

    /** Protractor: R sets a new zero at the current angle (relative measurement). */
    private var protractorZero = 0.0

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val g = accel.push(e.vec())
        last = g
        val surface = cal.apply(Tilt.surface(g))
        val rotation = cal.applyRotation(Tilt.screenRotation(g))
        val (main, state) = when (capability) {
            Capability.BUBBLE_LEVEL -> {
                val level = Tilt.isLevel(surface.xDeg) && Tilt.isLevel(surface.yDeg)
                "${Tilt.display(surface.xDeg)}  ${Tilt.display(surface.yDeg)}" to
                    if (level) t("level") else t("lay_the_phone_on_the")
            }

            Capability.PICTURE_HANGING -> Tilt.display(rotation) to
                if (Tilt.isLevel(
                        rotation
                    )
                ) {
                    t("straight")
                } else {
                    t("stand_the_phone_on_its")
                }

            Capability.PLUMB_BOB -> Tilt.display(rotation) to
                if (Tilt.isLevel(
                        rotation
                    )
                ) {
                    t("vertical")
                } else {
                    t("hold_the_phone_s_side")
                }

            else -> Tilt.display(org.sableos.tools.core.signedDeg(rotation - protractorZero)) to
                t("r_sets_zero_here_rotate")
        }
        hold.update(main)
        readingView.text = hold.shown
        stateView.text = (if (hold.isHeld) t("held") else "") + state
        detailView.text =
            t("zero_point") +
            if (cal ==
                TiltCalibration()
            ) {
                t("factory_press_c_on_a")
            } else {
                t("user_calibrated")
            }
    }

    override fun onCommand(c: Command): Boolean = when (c) {
        Command.Hold -> {
            hold.toggleHold()
            true
        }

        Command.Reset -> {
            if (capability == Capability.PROTRACTOR) {
                protractorZero = last?.let { cal.applyRotation(Tilt.screenRotation(it)) } ?: 0.0
            } else {
                cal = TiltCalibration()
                host.env.prefs.setCalibration(capability, null)
            }
            hold.reset()
            accel.reset()
            true
        }

        Command.Calibrate -> {
            last?.let {
                cal = TiltCalibration.from(Tilt.surface(it), Tilt.screenRotation(it))
                host.env.prefs.setCalibration(capability, cal)
                host.toast(t("calibrated_this_position_is_now"))
            }
            true
        }

        else -> false
    }

    override fun info() = t("angles_are_rounded_to_0")

    override fun copyText() = hold.shown
}

class CompassController(host: ToolHost) : SensorController(host) {
    override val sensorTypes = listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)
    private val mag = LowPass()
    private var g: Vec3? = null
    private var m: Vec3? = null
    private var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private val hold = HoldableReading<String>()

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> g = accel.push(e.vec())

            Sensor.TYPE_MAGNETIC_FIELD -> {
                m = mag.push(e.vec())
                accuracy = e.accuracy
            }
        }
        val gg = g ?: return
        val mm = m ?: return
        val az = Compass.azimuth(gg, mm)
        hold.update(az?.let { Compass.display(it) } ?: "--")
        readingView.text = hold.shown
        stateView.text = (if (hold.isHeld) t("held") else "") +
            if (Compass.needsCalibration(
                    accuracy
                )
            ) {
                t("needs_calibration_press_c")
            } else {
                t("hold_the_phone_flat")
            }
        detailView.text = t("magnetic_field_0f_t_accuracy").format(mm.norm, accuracy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) this.accuracy = accuracy
    }

    override fun onCommand(c: Command): Boolean = when (c) {
        Command.Hold -> {
            hold.toggleHold()
            true
        }

        Command.Reset -> {
            hold.reset()
            accel.reset()
            mag.reset()
            true
        }

        Command.Calibrate -> {
            stateView.text =
                t("calibrate_move_the_phone_in")
            true
        }

        else -> false
    }

    override fun info() = t("heading_of_the_phone_s")

    override fun copyText() = hold.shown
}

/** Two sightings along the top edge (base, then top) from the user's eye height. Always an estimate with a range. */
class HeightController(host: ToolHost) : SensorController(host) {
    private var eyeHeightM = DEFAULT_EYE_M
    private var base: Double? = null
    private var top: Double? = null
    private var elevation = 0.0

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        elevation = Tilt.topEdgeElevation(accel.push(e.vec()))
        val b = base
        val t = top
        readingView.text = when {
            b == null -> Tilt.display(elevation)

            t == null -> Tilt.display(elevation)

            else -> HeightEstimate.estimate(eyeHeightM, b, t)?.let { "%.1f m".format(it.heightM) }
                ?: "--"
        }
        stateView.text = when {
            b == null -> t("aim_the_top_edge_at")

            t == null -> t("now_aim_at_the_top")

            else -> HeightEstimate.estimate(eyeHeightM, b, t)?.let {
                t("estimate") +
                    HeightEstimate.display(it)
            }
                ?: t("cannot_estimate_from_these_angles")
        }
        detailView.text = t("eye_height_2f_m_c").format(eyeHeightM)
    }

    override fun onCommand(c: Command): Boolean = when (c) {
        Command.Hold -> {
            if (base == null) {
                base = elevation
            } else if (top == null) {
                top = elevation
            }
            true
        }

        Command.Reset -> {
            base = null
            top = null
            true
        }

        Command.Calibrate -> {
            eyeHeightM = if (eyeHeightM >= MAX_EYE_M) MIN_EYE_M else eyeHeightM + EYE_STEP_M
            true
        }

        else -> false
    }

    override fun info() = t("estimate_only_of_aiming_error", HeightEstimate.ANGLE_ERROR_DEG.toInt())

    private companion object {
        const val DEFAULT_EYE_M = 1.60
        const val MIN_EYE_M = 1.40
        const val MAX_EYE_M = 1.90
        const val EYE_STEP_M = 0.05
    }
}
