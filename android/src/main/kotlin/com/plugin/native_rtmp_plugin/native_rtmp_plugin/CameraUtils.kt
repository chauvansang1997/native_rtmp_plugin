package com.plugin.native_rtmp_plugin.native_rtmp_plugin


import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.collections.ArrayList


/** Provides various utilities for camera.  */
object CameraUtils {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun computeBestPreviewSize(cameraName: String, preset: ResolutionPreset): Size {
//        var preset = preset
//        if (preset.ordinal > ResolutionPreset.high.ordinal) {
//            preset = ResolutionPreset.high
//        }
        val profile = getBestAvailableCamcorderProfileForResolutionPreset(cameraName, preset)
        return Size(profile.videoFrameWidth, profile.videoFrameHeight)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun computeBestCaptureSize(streamConfigurationMap: StreamConfigurationMap): Size {
        // For still image captures, we use the largest available size.
        return Collections.max(
            listOf(*streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
            CompareSizesByArea()
        )
    }

    fun getSupportedResolutions(activity: Activity): Map<String, List<Size>> {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraNames = cameraManager.cameraIdList

        val camerasMap: MutableMap<String, MutableList<Size>> = HashMap()
        val resolutions4x3 = mutableListOf<Size>()
        val resolutions16x9 = mutableListOf<Size>()
        var firstInit = false

        for (cameraName in cameraNames) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraName)

            val streamConfigurationMap: StreamConfigurationMap? =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val sizes = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)!!.toMutableList()

            for (size in sizes) {
                if (size.width / 4 == size.height / 3) {
                    if (!resolutions4x3.contains(size)) {
                        if (firstInit && resolutions4x3.maxBy { it.width }?.width!! < size.width) {
                            resolutions4x3.add(size)
                        } else if (!firstInit) {
                            resolutions4x3.add(size)
                        }
                    }

                } else if (size.width / 16 == size.height / 9) {
                    if (firstInit && resolutions16x9.maxBy { it.width }?.width!! < size.width) {
                        resolutions16x9.add(size)
                    } else if (!firstInit) {
                        resolutions16x9.add(size)
                    }
                }
            }
            firstInit = true
        }
        camerasMap["resolutions4x3"] = resolutions4x3
        camerasMap["resolutions16x9"] = resolutions16x9

        return camerasMap
    }


    @Throws(CameraAccessException::class)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getAvailableCameras(activity: Activity): List<Map<String, Any?>> {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraNames = cameraManager.cameraIdList
        val cameras: MutableList<Map<String, Any?>> = ArrayList()
        for (cameraName in cameraNames) {
            val details = HashMap<String, Any?>()
            val characteristics = cameraManager.getCameraCharacteristics(cameraName)
            details["name"] = cameraName
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            details["sensorOrientation"] = sensorOrientation
            val streamConfigurationMap: StreamConfigurationMap? =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val sizes = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)!!.toList()
            val resolution4x3 = mutableListOf<HashMap<String, Any>>()
            val resolution16x9 = mutableListOf<HashMap<String, Any>>()

            for (size in sizes) {
                if (size.width / 4 == size.height / 3) {
                    val map = HashMap<String, Any>()
                    map["width"] = size.width
                    map["height"] = size.height
                    resolution4x3.add(map)
                } else if (size.width / 16 == size.height / 9) {
                    val map = HashMap<String, Any>()
                    map["width"] = size.width
                    map["height"] = size.height
                    resolution16x9.add(map)
                }
            }

            details["resolution4x3"] = resolution4x3
            details["resolution16x9"] = resolution16x9
            when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraMetadata.LENS_FACING_FRONT -> details["lensFacing"] = "front"
                CameraMetadata.LENS_FACING_BACK -> details["lensFacing"] = "back"
                CameraMetadata.LENS_FACING_EXTERNAL -> details["lensFacing"] = "external"
            }
            cameras.add(details)
        }

        return cameras
    }

    fun getAllResolutionPreset(cameraId: Int): List<String> {
        val cameraProfiles: MutableList<String> = ArrayList()

        //   low, medium, high, veryHigh, ultraHigh, max
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
            cameraProfiles.add("ultraHigh")

        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
            cameraProfiles.add("max")
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
            cameraProfiles.add("veryHigh")
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
            cameraProfiles.add("high")
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
            cameraProfiles.add("medium")
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
            cameraProfiles.add("low")
        }

        return cameraProfiles
    }


    fun getBestAvailableCamcorderProfileForResolutionPreset(
        cameraName: String, preset: ResolutionPreset?): CamcorderProfile {
        val cameraId = cameraName.toInt()
        when (preset) {
            ResolutionPreset.max -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            ResolutionPreset.ultraHigh -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            ResolutionPreset.veryHigh -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            ResolutionPreset.high -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            ResolutionPreset.medium -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            ResolutionPreset.low -> {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA)
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                } else {
                    throw IllegalArgumentException(
                        "No capture session available for current capture session.")
                }
            }
            else -> if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
            } else {
                throw IllegalArgumentException(
                    "No capture session available for current capture session.")
            }
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow.
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }
}