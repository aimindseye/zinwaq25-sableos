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
            stateView.text = "Sensor service unavailable"
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
                    if (level) "Level" else "Lay the phone on the surface"
            }

            Capability.PICTURE_HANGING -> Tilt.display(rotation) to
                if (Tilt.isLevel(
                        rotation
                    )
                ) {
                    "Straight"
                } else {
                    "Stand the phone on its edge along the frame"
                }

            Capability.PLUMB_BOB -> Tilt.display(rotation) to
                if (Tilt.isLevel(
                        rotation
                    )
                ) {
                    "Vertical"
                } else {
                    "Hold the phone's side edge against the surface"
                }

            else -> Tilt.display(org.sableos.tools.core.signedDeg(rotation - protractorZero)) to
                "R sets zero here; rotate to measure"
        }
        hold.update(main)
        readingView.text = hold.shown
        stateView.text = (if (hold.isHeld) "HELD · " else "") + state
        detailView.text =
            "zero point: " +
            if (cal ==
                TiltCalibration()
            ) {
                "factory (press C on a known level surface)"
            } else {
                "user calibrated"
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
                host.toast("Calibrated: this position is now zero")
            }
            true
        }

        else -> false
    }

    override fun info() =
        "Angles are rounded to 0.1°; a phone accelerometer is good to about ±0.5° after " +
            "calibration. C sets the current position as zero (do it on a surface you know is" +
            " level). R clears it. H holds the reading."

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
        stateView.text = (if (hold.isHeld) "HELD · " else "") +
            if (Compass.needsCalibration(
                    accuracy
                )
            ) {
                "Needs calibration: press C"
            } else {
                "Hold the phone flat"
            }
        detailView.text = "magnetic field %.0f µT · accuracy %d/3".format(mm.norm, accuracy)
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
                "Calibrate: move the phone in a slow figure-8 for a few seconds, away from metal and magnets."
            true
        }

        else -> false
    }

    override fun info() =
        "Heading of the phone's top edge from magnetic north (true north would need " +
            "location, which this tool does not use). Magnet cases and nearby metal disturb " +
            "it."

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
            b == null -> "Aim the top edge at the BASE of the object and press H"

            t == null -> "Now aim at the TOP and press H"

            else -> HeightEstimate.estimate(eyeHeightM, b, t)?.let {
                "Estimate: " +
                    HeightEstimate.display(it)
            }
                ?: "Cannot estimate from these angles; press R and try again further away"
        }
        detailView.text = "eye height %.2f m (C cycles 1.40–1.90 m)".format(eyeHeightM)
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

    override fun info() =
        "Estimate only: ±${HeightEstimate.ANGLE_ERROR_DEG.toInt()}° of aiming error gives" +
            " the range shown. Stand on level ground; C sets your eye height."

    private companion object {
        const val DEFAULT_EYE_M = 1.60
        const val MIN_EYE_M = 1.40
        const val MAX_EYE_M = 1.90
        const val EYE_STEP_M = 0.05
    }
}
