package org.sableos.tools.ui

import org.sableos.tools.core.Capability
import org.sableos.tools.core.ReportCategory
import org.sableos.tools.core.Section
import org.sableos.tools.core.Tool

/** Maps each catalog tool to its screen. Pure data decides visibility; this only builds the view. */
object Controllers {
    private fun info(category: ReportCategory): (ToolHost) -> ToolController =
        { h -> InfoController(h) { h.collectors.collect(category) } }

    private val FACTORIES: Map<Tool, (ToolHost) -> ToolController> = mapOf(
        Tool.COMPASS to ::CompassController,
        Tool.BUBBLE_LEVEL to { h -> TiltController(h, Capability.BUBBLE_LEVEL) },
        Tool.PLUMB_BOB to { h -> TiltController(h, Capability.PLUMB_BOB) },
        Tool.PROTRACTOR to { h -> TiltController(h, Capability.PROTRACTOR) },
        Tool.PICTURE_HANGING to { h -> TiltController(h, Capability.PICTURE_HANGING) },
        Tool.HEIGHT_ESTIMATE to ::HeightController,
        Tool.FLASHLIGHT to ::FlashlightController,
        Tool.MAGNIFIER to ::MagnifierController,
        Tool.NOISE_METER to ::NoiseController,
        Tool.SPEEDOMETER to ::SpeedController,
        Tool.PEDOMETER to ::PedometerController,
        Tool.IR_REMOTE to ::IrRemoteController,
        Tool.DEVICE_IDENTITY to info(ReportCategory.DEVICE),
        Tool.KEY_VIEWER to ::KeyViewerController,
        Tool.KEYBOARD_PROFILE to info(ReportCategory.INPUT),
        Tool.POINTER to ::PointerController,
        Tool.DISPLAY_INPUT to info(ReportCategory.DISPLAY),
        Tool.CAMERA_REPORT to info(ReportCategory.CAMERA),
        Tool.SENSORS to info(ReportCategory.SENSORS),
        Tool.RADIO to info(ReportCategory.RADIO),
        Tool.NETWORK to info(ReportCategory.NETWORK),
        Tool.STORAGE to info(ReportCategory.STORAGE),
        Tool.APPS to info(ReportCategory.APPS),
        Tool.TEXT_ENTRY to ::TextEntryController,
        Tool.ATTENTION to ::AttentionController,
        Tool.HARDWARE_TESTS to ::HardwareTestsController,
        Tool.CAPABILITIES to info(ReportCategory.CAPABILITIES),
        Tool.FACTORY_BRIDGE to ::FactoryBridgeController
    )

    fun create(host: ToolHost): ToolController = if (host.tool.section == Section.REPORTS) {
        ReportController(host)
    } else {
        FACTORIES.getValue(host.tool)(host)
    }
}
