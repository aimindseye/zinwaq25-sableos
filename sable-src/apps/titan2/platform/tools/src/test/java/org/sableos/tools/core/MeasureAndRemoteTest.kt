package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureAndRemoteTest {
    private val g = 9.80665

    @Test fun flatPhoneIsLevel() {
        val s = Tilt.surface(Vec3(0.0, 0.0, g))
        assertEquals(0.0, s.xDeg, 1e-9)
        assertEquals(0.0, s.totalDeg, 1e-9)
        assertTrue(Tilt.isLevel(s.xDeg))
        val tilted = Tilt.surface(
            Vec3(
                g * Math.sin(Math.toRadians(10.0)),
                0.0,
                g * Math.cos(Math.toRadians(10.0))
            )
        )
        assertEquals(10.0, tilted.xDeg, 1e-6)
        assertEquals(10.0, tilted.totalDeg, 1e-6)
    }

    @Test fun edgeRotationAndElevation() {
        assertEquals(0.0, Tilt.screenRotation(Vec3(0.0, g, 0.0)), 1e-9)
        assertEquals(90.0, Tilt.screenRotation(Vec3(g, 0.0, 0.0)), 1e-9)
        assertEquals(30.0, Tilt.topEdgeElevation(Vec3(0.0, g * 0.5, g * Math.sqrt(0.75))), 1e-6)
        assertEquals("1.2°", Tilt.display(1.234))
    }

    @Test fun calibrationOffsets() {
        val cal = TiltCalibration.from(Tilt.Surface(1.0, -2.0, 2.2), rotationDeg = 3.0)
        assertEquals(0.0, cal.apply(Tilt.Surface(1.0, -2.0, 2.2)).xDeg, 1e-9)
        assertEquals(-3.0, cal.applyRotation(0.0), 1e-9)
        assertEquals(cal, TiltCalibration.decode(cal.encode()))
        assertNull(TiltCalibration.decode("x"))
    }

    @Test fun compassAzimuth() {
        val flat = Vec3(0.0, 0.0, g)
        assertEquals(0.0, Compass.azimuth(flat, Vec3(0.0, 20.0, -40.0))!!, 1e-6)
        assertEquals(270.0, Compass.azimuth(flat, Vec3(20.0, 0.0, -40.0))!!, 1e-6)
        assertEquals(90.0, Compass.azimuth(flat, Vec3(-20.0, 0.0, -40.0))!!, 1e-6)
        assertNull(Compass.azimuth(flat, Vec3(0.0, 0.0, -40.0)))
        assertEquals("N", Compass.cardinal(359.0))
        assertEquals("SW", Compass.cardinal(225.0))
        assertEquals("90° E (magnetic)", Compass.display(90.2))
        assertTrue(Compass.needsCalibration(1))
        assertFalse(Compass.needsCalibration(3))
    }

    @Test fun holdAndReset() {
        val h = HoldableReading<Int>()
        h.update(1)
        h.toggleHold()
        h.update(2)
        assertEquals(1, h.shown)
        h.toggleHold()
        assertEquals(2, h.shown)
        h.reset()
        assertNull(h.shown)
    }

    @Test fun noiseLevels() {
        assertEquals(-96.0, Noise.rmsDbfs(ShortArray(10)), 1e-9)
        val full = ShortArray(100) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(0.0, Noise.rmsDbfs(full), 0.01)
        assertEquals("Quiet", Noise.label(30.0))
        assertEquals("Loud", Noise.label(75.0))
        assertEquals(30.0, Noise.approxDb(-60.0), 1e-9)
    }

    @Test fun speedTrip() {
        val t = SpeedTrip()
        assertTrue(t.push(SpeedTrip.Fix(0.0, 0.0, 0, 5.0, null)))
        assertFalse(t.push(SpeedTrip.Fix(1.0, 1.0, 500, 500.0, null)))
        assertTrue(t.push(SpeedTrip.Fix(0.0, 0.001, 10_000, 5.0, null)))
        assertEquals(111.2, t.distanceM, 0.5)
        assertEquals(11.12, t.currentMps, 0.05)
        assertEquals(40.0, SpeedTrip.kmh(11.111), 0.01)
        assertEquals(11.12, t.averageMps, 0.05)
        t.reset()
        assertEquals(0.0, t.distanceM, 0.0)
    }

    @Test fun stepCounterBaselineAndReboot() {
        val s = StepCounterSession()
        s.push(100)
        assertEquals(50L, s.push(150))
        assertEquals(60L, s.push(10))
        s.reset()
        assertEquals(5L, s.push(15))
    }

    @Test fun accelStepDetector() {
        val d = AccelStepDetector()
        var t = 0L
        repeat(3) {
            d.push(8.0, t)
            t += 150
            d.push(12.0, t)
            t += 150
        }
        assertEquals(3L, d.steps)
        assertFalse(d.push(12.0, t))
    }

    @Test fun heightEstimate() {
        // Eye 1.5 m, base 45° down: 1.5 m away; top 45° up: 1.5 + 1.5 = 3.0 m.
        val r = HeightEstimate.estimate(1.5, -45.0, 45.0)
        assertNotNull(r)
        assertEquals(3.0, r!!.heightM, 1e-9)
        assertEquals(1.5, r.distanceM, 1e-9)
        assertTrue(r.lowM < 3.0 && r.highM > 3.0)
        assertNull(HeightEstimate.estimate(1.5, 0.5, 20.0))
        assertNull(HeightEstimate.estimate(0.0, -45.0, 45.0))
        assertTrue(HeightEstimate.display(r).startsWith("about 3.0 m"))
    }

    @Test fun necPattern() {
        val p = IrProtocol.NEC.pattern(0x20DF10EFL)
        assertEquals(2 + 64 + 1, p.size)
        assertEquals(9000, p[0])
        assertEquals(4500, p[1])
        // 0x2 = 0010: first two bits are 0, third is 1.
        assertEquals(560, p[3])
        assertEquals(560, p[5])
        assertEquals(1690, p[7])
        assertEquals(560, p.last())
        assertEquals(4500, IrProtocol.SAMSUNG32.pattern(0xE0E040BFL)[0])
        assertTrue(p.sum() < IrProtocol.MAX_PATTERN_US)
    }

    @Test fun necFrameFromAddressCommand() {
        assertEquals(0x20DF10EFL, RemoteLibrary.necFrame(0x04, 0x08))
        val lg = RemoteLibrary.BUILT_IN.first { it.name == "LG TV" }
        assertEquals(RemoteLibrary.necFrame(0x04, 0x08), lg.button("power")!!.code)
    }

    @Test fun remoteCodecRoundTripSkipsBuiltIns() {
        val mine =
            Remote(
                "Living room",
                ApplianceType.PROJECTOR,
                IrProtocol.NEC,
                listOf(IrButton("Power", 0x10EF08F7L), IrButton("In;put=", 0xFFL))
            )
        val text = RemoteCodec.encode(RemoteLibrary.BUILT_IN + mine)
        val back = RemoteCodec.decode(text)
        assertEquals(1, back.size)
        assertEquals("Living room", back[0].name)
        assertEquals(0x10EF08F7L, back[0].buttons[0].code)
        assertEquals("In,put-", back[0].buttons[1].name)
        assertEquals(emptyList<Remote>(), RemoteCodec.decode("garbage\nx\ty"))
        assertEquals(0x20DF10EFL, RemoteCodec.parseCode("0x20df10ef"))
        assertNull(RemoteCodec.parseCode("1FFFFFFFF"))
    }

    @Test fun irTransmitIsCapabilityGated() {
        val all = Hardware.entries.associateWith { Probe.PRESENT }
        val lg = RemoteLibrary.BUILT_IN.first { it.name == "LG TV" }
        val q25 = CapabilityGate.resolve(ToolsDeviceProfile.ZinwaQ25, all, Capability.IR_REMOTE)
        assertTrue(
            IrTransmit.decide(lg, lg.buttons[0], q25, emptyList()) is IrTransmit.Decision.Refuse
        )
        val t2 = CapabilityGate.resolve(ToolsDeviceProfile.Titan2, all, Capability.IR_REMOTE)
        assertTrue(
            IrTransmit.decide(
                lg,
                lg.buttons[0],
                t2,
                listOf(30_000..60_000)
            ) is IrTransmit.Decision.Send
        )
        assertTrue(
            IrTransmit.decide(
                lg,
                lg.buttons[0],
                t2,
                listOf(56_000..56_000)
            ) is IrTransmit.Decision.Refuse
        )
    }

    @Test fun dialerCodeBridge() {
        val all = Hardware.entries.associateWith { Probe.PRESENT }
        val q25 = CapabilityGate.resolve(
            ToolsDeviceProfile.ZinwaQ25,
            all,
            Capability.FACTORY_TEST_BRIDGE
        )
        val closed = DialerCodes.bridge(q25, developerMode = true)
        assertFalse(closed.opensBridge)
        assertTrue(closed.reason.isNotBlank())
        val r = DialerCodes.resolveQuery("*#*# 3377 #*#*", closed)
        assertTrue(r is DialerCodes.QueryResult.Bridge && !r.decision.opensBridge)
        val t2 = CapabilityGate.resolve(
            ToolsDeviceProfile.Titan2,
            all,
            Capability.FACTORY_TEST_BRIDGE
        )
        assertFalse(DialerCodes.bridge(t2, developerMode = false).opensBridge)
        assertTrue(DialerCodes.bridge(t2, developerMode = true).opensBridge)
        assertEquals(
            DialerCodes.QueryResult.Category(Tool.HARDWARE_TESTS),
            DialerCodes.resolveQuery("Factory  Test", closed)
        )
        assertEquals(
            DialerCodes.QueryResult.Category(Tool.KEY_VIEWER),
            DialerCodes.resolveQuery("key test", closed)
        )
        assertEquals(
            DialerCodes.QueryResult.Category(Tool.ATTENTION),
            DialerCodes.resolveQuery("keyboard light", closed)
        )
        assertEquals(DialerCodes.QueryResult.NoMatch, DialerCodes.resolveQuery("weather", closed))
        assertEquals("3377", DialerCodes.secretCodeFromUri("android_secret_code://3377"))
        assertNull(DialerCodes.secretCodeFromUri("tel:3377"))
        assertFalse(DialerCodes.receiverEnabled(vendorHandlerPresent = true))
    }

    @Test fun factoryItemsMapSafely() {
        val byName = DialerCodes.ITEMS.associateBy { it.name }
        assertEquals(DiagTarget.Open(Tool.KEY_VIEWER), byName.getValue("Key").target)
        listOf(
            "Mtklog",
            "Aging Test",
            "Gravity Calibration",
            "Distance Calibration",
            "Smartpa calib"
        ).forEach {
            assertTrue(it, byName.getValue(it).target is DiagTarget.Gated)
        }
        DiagCategory.entries.forEach {
            assertTrue(it.name, DialerCodes.byCategory().getValue(it).isNotEmpty())
        }
    }

    @Test fun subscreenModesNeedProvenSubscreen() {
        val all = Hardware.entries.associateWith { Probe.PRESENT }
        assertTrue(
            SubscreenModes.available(
                CapabilityGate.resolveAll(ToolsDeviceProfile.ZinwaQ25, all)
            ).isEmpty()
        )
        val t2 = SubscreenModes.available(CapabilityGate.resolveAll(ToolsDeviceProfile.Titan2, all))
        assertTrue("Compass" in t2)
        assertTrue("Safe IR quick control" in t2)
    }
}
