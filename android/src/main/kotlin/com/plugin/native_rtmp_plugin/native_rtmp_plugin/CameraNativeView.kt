package com.plugin.native_rtmp_plugin.native_rtmp_plugin

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.CameraHelper.Facing.BACK
import com.pedro.encoder.input.video.CameraHelper.Facing.FRONT
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.LightOpenGlView
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import vn.tpos.stream_with_rtmp.DartMessenger
import java.io.IOException


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class CameraNativeView(
    private var activity: Activity? = null,
    private var enableAudio: Boolean = false,
    private val preset: ResolutionPreset,
    private var cameraName: String,
    private var dartMessenger: DartMessenger? = null
) :
    PlatformView,
    SurfaceHolder.Callback,
    ConnectCheckerRtmp {

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private val glView = LightOpenGlView(activity)
    private val rtmpCamera: RtmpCameraManager

    private var isSurfaceCreated = false
    private var fps = 0

    init {
        glView.isKeepAspectRatio = true
        glView.holder.addCallback(this)
        rtmpCamera = RtmpCameraManager(glView, activity!!)
        rtmpCamera.setReTries(10)
        rtmpCamera.setFpsListener { fps = it }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("CameraNativeView", "surfaceCreated")
        isSurfaceCreated = true
        startPreview(cameraName)
    }


    override fun onAuthSuccessRtmp() {
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
    }

    override fun onConnectionSuccessRtmp() {
    }

    override fun onConnectionFailedRtmp(reason: String) {
        activity?.runOnUiThread { //Wait 5s and retry connect stream
            if (rtmpCamera.reTry(5000, reason)) {
                dartMessenger?.send(DartMessenger.EventType.RTMP_RETRY, reason)
            } else {
                dartMessenger?.send(DartMessenger.EventType.RTMP_STOPPED, "Failed retry")
                rtmpCamera.stopStream()
            }
        }
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {

    }

    override fun onAuthErrorRtmp() {
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.EventType.ERROR, "Auth error")
        }
    }

    override fun onDisconnectRtmp() {
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.EventType.RTMP_STOPPED, "Disconnected")
        }
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {

    }

    fun close() {
        Log.d("CameraNativeView", "close")
    }


    private fun startVideoStreaming(url: String?, result: MethodChannel.Result) {
        Log.d("CameraNativeView", "startVideoStreaming url: $url")
        if (url == null) {
            result.error("startVideoStreaming", "Must specify a url.", null)
            return
        }

        try {
            if (!rtmpCamera.isStreaming) {
                val streamingSize = CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(
                    cameraName,
                    preset
                )
                if (rtmpCamera.isRecording || rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                        streamingSize.videoFrameWidth,
                        streamingSize.videoFrameHeight,
                        streamingSize.videoBitRate
                    )
                ) {
                    // ready to start streaming
                    rtmpCamera.startStream(url)
                } else {
                    result.error(
                        "videoStreamingFailed",
                        "Error preparing stream, This device cant do it",
                        null
                    )
                    return
                }
            } else {
                rtmpCamera.stopStream()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoStreamingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoStreamingFailed", e.message, null)
        }
    }


    fun startPreview(cameraNameArg: String? = null) {
        val targetCamera = if (cameraNameArg.isNullOrEmpty()) {
            cameraName
        } else {
            cameraNameArg
        }
        cameraName = targetCamera
        val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraNames = cameraManager.cameraIdList
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)

        val previewSize =
            CameraUtils.computeBestPreviewSize(cameraName, ResolutionPreset.ultraHigh)
        val streamConfigurationMap: StreamConfigurationMap? =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val sizes =
            streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)!!.toMutableList()
        Log.d("CameraNativeView", "startPreview: $preset")
        if (isSurfaceCreated) {
            try {
                if (rtmpCamera.isOnPreview) {
                    rtmpCamera.stopPreview()
                }

                rtmpCamera.startPreview(
                    if (isFrontFacing(targetCamera)) FRONT else BACK,
                    sizes[0].width,
                    sizes[0].height
                )
            } catch (e: CameraAccessException) {
                activity?.runOnUiThread {
                    dartMessenger?.send(
                        DartMessenger.EventType.ERROR,
                        "CameraAccessException"
                    )
                }
                return
            }
        }
    }


    override fun getView(): View {
        return glView
    }

    override fun dispose() {
        isSurfaceCreated = false
        activity = null
    }

    private fun isFrontFacing(cameraName: String): Boolean {
        val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    }
}
