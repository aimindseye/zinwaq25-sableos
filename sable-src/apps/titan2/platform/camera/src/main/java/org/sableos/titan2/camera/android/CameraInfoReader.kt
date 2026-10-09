package org.sableos.titan2.camera.android

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import org.sableos.titan2.camera.core.CameraInfo
import org.sableos.titan2.camera.core.Facing
import org.sableos.titan2.camera.core.FloatRange
import org.sableos.titan2.camera.core.IntBounds
import org.sableos.titan2.camera.core.LongBounds
import org.sableos.titan2.camera.core.Size

/**
 * Reads only what an ordinary app can see: `cameraIdList` never contains hidden SYSTEM_CAMERA ids,
 * and this reader never tries to open them.
 */
class CameraInfoReader(context: Context) {
    private val mgr = context.getSystemService(CameraManager::class.java)

    fun characteristics(id: String): CameraCharacteristics = mgr.getCameraCharacteristics(id)

    fun readAll(): List<CameraInfo> = mgr.cameraIdList.mapNotNull { id ->
        runCatching { read(id) }.getOrNull()
    }

    fun read(id: String): CameraInfo {
        val c = mgr.getCameraCharacteristics(id)
        val caps =
            c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
        fun sizes(a: Array<android.util.Size>?) = a?.map { Size(it.width, it.height) }.orEmpty()
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraMetadata.LENS_FACING_FRONT -> Facing.Front
            CameraMetadata.LENS_FACING_EXTERNAL -> Facing.External
            else -> Facing.Back
        }
        val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exp = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val zoom = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        val ae = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        return CameraInfo(
            id = id, facing = facing,
            activeArray = active?.let { Size(it.width(), it.height()) },
            jpegSizes = sizes(map?.getOutputSizes(ImageFormat.JPEG)),
            jpegHighResSizes = sizes(map?.getHighResolutionOutputSizes(ImageFormat.JPEG)),
            jpegMaxResSizes = sizes(maxMap?.getOutputSizes(ImageFormat.JPEG)),
            rawSizes = sizes(map?.getOutputSizes(ImageFormat.RAW_SENSOR)),
            hasRaw = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps,
            manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps,
            manualPostProcessing =
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps,
            isoRange = iso?.let { IntBounds(it.lower, it.upper) },
            exposureNsRange = exp?.let { LongBounds(it.lower, it.upper) },
            minFocusDistance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            zoomRatio = zoom?.let { FloatRange(it.lower, it.upper) },
            aeCompensationSteps = ae?.let { IntBounds(it.lower, it.upper) },
            aeCompensationStep =
                c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f,
            flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            physicalIdCount = c.physicalCameraIds.size,
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet().orEmpty(),
            videoSizes = sizes(map?.getOutputSizes(MediaRecorder::class.java))
        )
    }
}
