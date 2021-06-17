package com.plugin.native_rtmp_plugin.native_rtmp_plugin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.CamcorderProfile
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.util.BitrateAdapter
import com.pedro.rtplibrary.view.LightOpenGlView
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera(
    val activity: Activity,
    private val flutterTexture: SurfaceTextureEntry,
    val dartMessenger: DartMessenger,
    val cameraName: String,
    val resolutionPreset: String?,
    private val streamingPreset: String?,
    private val enableAudio: Boolean,
    private val useOpenGL: Boolean
) : ConnectCheckerRtmp, SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback {
    private val cameraManager: CameraManager =
        activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    //    private val orientationEventListener: OrientationEventListener
    private val isFrontFacing: Boolean
    private val sensorOrientation: Int
    private val captureSize: Size
    private val previewSize: Size
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var pictureImageReader: ImageReader? = null
    private var imageStreamReader: ImageReader? = null
    private val recordingProfile: CamcorderProfile
    private val streamingProfile: CamcorderProfile
    private var currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN

    //    private var rtmpCamera: RtmpCameraConnector? = null
    private var bitrateAdapter: BitrateAdapter? = null
    private val maxRetries = 3
    private var currentRetries = 0
    private var publishUrl: String? = null
    private val aspectRatio: Double = 4.0 / 5.0

    //    private val glView: FlutterGLSurfaceView
    private val glView: LightOpenGlView
    private val rtmpCamera: RtmpCamera2

    init {

        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        isFrontFacing =
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        currentOrientation =
            (activity.resources.configuration.orientation / 90.0).roundToInt() * 90
        val preset = ResolutionPreset.valueOf(resolutionPreset!!)
        recordingProfile =
            CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(cameraName, preset)

        captureSize = Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight)
        previewSize = CameraUtils.computeBestPreviewSize(cameraName, preset)

        // Data for streaming, different than the recording data.
        val streamPreset = ResolutionPreset.valueOf(streamingPreset!!)
        streamingProfile = CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(
            cameraName,
            streamPreset
        )

        glView = LightOpenGlView(activity)
        glView.isKeepAspectRatio = true
        glView.holder.addCallback(this)
        rtmpCamera = RtmpCamera2(glView, this)
        updateSurfaceView()
    }

    private val formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical

    @Throws(IOException::class)
    private fun prepareCameraForRecordAndStream(fps: Int, bitrate: Int?) {
        rtmpCamera.stopStream()
        Log.i(
            TAG,
            "prepareCameraForRecordAndStream(opengl=" + useOpenGL + ", portrait: " + isPortrait + ", currentOrientation: " + currentOrientation + ", mediaOrientation: " + mediaOrientation
                    + ", frontfacing: " + isFrontFacing + ")"
        )
        // Turn on audio if it is requested.
        if (enableAudio) {
            rtmpCamera.prepareAudio()
        }

        // Bitrate for the stream/recording.
        var bitrateToUse = bitrate
        if (bitrateToUse == null) {
            bitrateToUse = 1200 * 1024
        }
    }


    @SuppressLint("MissingPermission")
    @Throws(CameraAccessException::class)
    fun open(result: MethodChannel.Result) {
        Handler().postDelayed({
            val rtmpPreviewSize = getSizePairByOrientation()
            rtmpCamera.startPreview(
                CameraHelper.Facing.FRONT,
                rtmpPreviewSize.first,
                rtmpPreviewSize.second
            )
            val reply: MutableMap<String, Any> = HashMap()
            reply["textureId"] = flutterTexture.id()

            if (isPortrait) {
                reply["previewWidth"] = previewSize.width
                reply["previewHeight"] = previewSize.height
            } else {
                reply["previewWidth"] = previewSize.height
                reply["previewHeight"] = previewSize.width
            }
            reply["previewQuarterTurns"] = currentOrientation / 90
            Log.i(
                TAG,
                "open: width: " + reply["previewWidth"] + " height: " + reply["previewHeight"] + " currentOrientation: " + currentOrientation + " quarterTurns: " + reply["previewQuarterTurns"]
            )
            result.success(reply)
        }, 500)

    }

    @Throws(IOException::class)
    private fun writeToFile(buffer: ByteBuffer, file: File) {
        FileOutputStream(file).use { outputStream ->
            while (0 < buffer.remaining()) {
                outputStream.channel.write(buffer)
            }
        }
    }

    fun takePicture(filePath: String, result: MethodChannel.Result) {
        val file = File(filePath)
        if (file.exists()) {
            result.error(
                "fileExists", "File at path '$filePath' already exists. Cannot overwrite.", null
            )
            return
        }

        pictureImageReader!!.setOnImageAvailableListener(
            { reader: ImageReader ->
                try {
                    reader.acquireLatestImage().use { image ->
                        val buffer = image.planes[0].buffer
                        writeToFile(buffer, file)
                        result.success(null)
                    }
                } catch (e: IOException) {
                    result.error("IOError", "Failed saving image", null)
                }
            },
            null
        )
        try {
            // Create a new capture session with all this stuff in it.
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(pictureImageReader!!.surface)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mediaOrientation)
            cameraCaptureSession!!.capture(
                captureBuilder.build(),
                object : CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        val reason: String = when (failure.reason) {
                            CaptureFailure.REASON_ERROR -> "An error happened in the framework"
                            CaptureFailure.REASON_FLUSHED -> "The capture has failed due to an abortCaptures() call"
                            else -> "Unknown reason"
                        }
                        result.error("captureFailure", reason, null)
                    }

                    // Close out the session once we have captured stuff.
                    override fun onCaptureSequenceCompleted(
                        session: CameraCaptureSession,
                        sequenceId: Int,
                        frameNumber: Long
                    ) {
                        session.close()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            result.error("cameraAccess", e.message, null);
        }
    }


    @Throws(CameraAccessException::class)
    private fun createCaptureSession(
        templateType: Int, onSuccessCallback: Runnable
    ) {
        // Close the old session first.
        closeCaptureSession()
        Log.v(
            "Camera",
            "createCaptureSession " + previewSize.width + "x" + previewSize.height + " mediaOrientation: " + mediaOrientation + " currentOrientation: " + currentOrientation + " sensorOrientation: " + sensorOrientation + " porteait: " + isPortrait
        )

        // Create a new capture builder.
        val requestBuilder = cameraDevice!!.createCaptureRequest(templateType)

        // Collect all surfaces we want to render to.
        val surfaceList: MutableList<Surface> = ArrayList()

        // Build Flutter surface to render to
        val surfaceTexture = flutterTexture.surfaceTexture()
        val size = getSizePairByOrientation()
        surfaceTexture.setDefaultBufferSize(size.first, size.second)
        val flutterSurface = Surface(surfaceTexture)

        // The capture request.
        requestBuilder.addTarget(flutterSurface)

        // Create the surface lists for the capture session.
        surfaceList.add(flutterSurface)

        // Prepare the callback
        val callback: CameraCaptureSession.StateCallback =
            object : CameraCaptureSession.StateCallback() {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        if (cameraDevice == null) {
                            dartMessenger.send(
                                DartMessenger.EventType.ERROR,
                                "The camera was closed during configuration."
                            )
                            return
                        }
                        Log.v("Camera", "open successful ")
                        requestBuilder.set(
                            CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO
                        )
                        session.setRepeatingRequest(requestBuilder.build(), null, null)
                        cameraCaptureSession = session
                        onSuccessCallback.run()
                    } catch (e: CameraAccessException) {
                        Log.v("Camera", "Error CameraAccessException", e)
                        dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                    } catch (e: IllegalStateException) {
                        Log.v("Camera", "Error IllegalStateException", e)
                        dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                    } catch (e: IllegalArgumentException) {
                        Log.v("Camera", "Error IllegalArgumentException", e)
                        dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    dartMessenger.send(
                        DartMessenger.EventType.ERROR, "Failed to configure camera session."
                    )
                }
            }

        // Start the session
        cameraDevice!!.createCaptureSession(surfaceList, callback, null)
    }

    fun startVideoRecording(filePath: String, result: MethodChannel.Result) {
        if (File(filePath).exists()) {
            result.error("fileExists", "File at path '$filePath' already exists.", null)
            return
        }
        try {
            if (!rtmpCamera.isStreaming) {
                val rtmpPreviewSize = getSizePairByOrientation()
                if (rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                        rtmpPreviewSize.first,
                        rtmpPreviewSize.second,
                        1024 * 1024
                    )
                ) {
                    rtmpCamera.startRecord(filePath)
                }
            } else {
                rtmpCamera.startRecord(filePath)
            }


            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoRecordingOrStreaming(result: MethodChannel.Result) {
        Log.i("Camera", "stopVideoRecordingOrStreaming ")
        try {
            currentRetries = 0
            publishUrl = null
            rtmpCamera.apply {
                if (isRecording) {
                    stopRecord()
                }
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoRecording(result: MethodChannel.Result) {
        Log.i("Camera", "stopVideoRecording")

        try {
            currentRetries = 0
            publishUrl = null
            rtmpCamera.stopRecord()

            startPreview()
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoStreaming(result: MethodChannel.Result) {
        Log.i("Camera", "stopVideoRecording")

        try {
            currentRetries = 0
            publishUrl = null
            rtmpCamera.stopStream()

            startPreview()
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun pauseVideoRecording(result: MethodChannel.Result) {
        if (!rtmpCamera.isRecording) {
            result.success(null)
            return
        }
        try {
            rtmpCamera.pauseRecord()
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
            return
        }
        result.success(null)
    }

    fun resumeVideoRecording(result: MethodChannel.Result) {
        if (!rtmpCamera.isRecording) {
            result.success(null)
            return
        }
        try {
            rtmpCamera.resumeRecord()
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
            return
        }
        result.success(null)
    }

    @Throws(CameraAccessException::class)
    fun startPreview() {
        createCaptureSession(
            CameraDevice.TEMPLATE_PREVIEW,
            Runnable { }
        )
    }

    @Throws(CameraAccessException::class)
    fun startPreviewWithImageStream(imageStreamChannel: EventChannel) {
        imageStreamReader = ImageReader.newInstance(
            previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2
        )

        createCaptureSession(
            CameraDevice.TEMPLATE_RECORD,
            Runnable {}
        )
        imageStreamChannel.setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(o: Any, imageStreamSink: EventSink) {
                    setImageStreamImageAvailableListener(imageStreamSink)
                }

                override fun onCancel(o: Any) {
                    imageStreamReader!!.setOnImageAvailableListener(null, null)
                }
            })
    }

    private fun setImageStreamImageAvailableListener(imageStreamSink: EventSink) {
        imageStreamReader!!.setOnImageAvailableListener(
            { reader: ImageReader ->
                val img = reader.acquireLatestImage()
                    ?: return@setOnImageAvailableListener
                val planes: MutableList<Map<String, Any>> = ArrayList()
                for (plane in img.planes) {
                    val buffer = plane.buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer[bytes, 0, bytes.size]
                    val planeBuffer: MutableMap<String, Any> = HashMap()
                    planeBuffer["bytesPerRow"] = plane.rowStride
                    planeBuffer["bytesPerPixel"] = plane.pixelStride
                    planeBuffer["bytes"] = bytes
                    planes.add(planeBuffer)
                }
                val imageBuffer: MutableMap<String, Any> = HashMap()
                imageBuffer["width"] = img.width
                imageBuffer["height"] = img.height
                imageBuffer["format"] = img.format
                imageBuffer["planes"] = planes
                imageStreamSink.success(imageBuffer)
                img.close()
            },
            null
        )
    }

    private fun closeCaptureSession() {
        if (cameraCaptureSession != null) {
            Log.v("Camera", "Close recordingCaptureSession")
            try {
                cameraCaptureSession!!.stopRepeating()
                cameraCaptureSession!!.abortCaptures()
                cameraCaptureSession!!.close()
            } catch (e: CameraAccessException) {
                Log.w("RtmpCamera", "Error from camera", e)
            }
            cameraCaptureSession = null
        } else {
            Log.v("Camera", "No recoordingCaptureSession to close")
        }
    }

    fun close() {
        closeCaptureSession()
        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (pictureImageReader != null) {
            pictureImageReader!!.close()
            pictureImageReader = null
        }
        if (imageStreamReader != null) {
            imageStreamReader!!.close()
            imageStreamReader = null
        }
        rtmpCamera.stopStream()
        bitrateAdapter = null
        publishUrl = null
    }

    fun dispose() {
        close()
        flutterTexture.release()
    }

    fun startVideoStreaming(url: String?, bitrate: Int?, result: MethodChannel.Result) {
        if (url == null) {
            result.error("fileExists", "Must specify a url.", null)
            return
        }
        try {
            // Setup the rtmp session
            rtmpCamera.startStream(url)
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoStreamingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoStreamingFailed", e.message, null)
        }
    }

    fun startVideoRecordingAndStreaming(
        filePath: String,
        url: String?,
        bitrate: Int?,
        result: MethodChannel.Result
    ) {
        if (File(filePath).exists()) {
            result.error("fileExists", "File at path '$filePath' already exists.", null)
            return
        }
        if (url == null) {
            result.error("fileExists", "Must specify a url.", null)
            return
        }
        try {
            // Setup the rtmp session
            currentRetries = 0
            prepareCameraForRecordAndStream(streamingProfile.videoFrameRate, bitrate)

            createCaptureSession(
                CameraDevice.TEMPLATE_RECORD,
                Runnable {
                    rtmpCamera.startStream(url)
                    rtmpCamera.startRecord(filePath)
                }
            )
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }


    fun pauseVideoStreaming(result: MethodChannel.Result) {
        if (!rtmpCamera.isStreaming) {
            result.success(null)
            return
        }
        try {
            currentRetries = 0
        } catch (e: IllegalStateException) {
            result.error("videoStreamingFailed", e.message, null)
            return
        }
        result.success(null)
    }

    fun getStreamStatistics(result: MethodChannel.Result) {
        val ret = hashMapOf<String, Any>()
        ret["cacheSize"] = rtmpCamera.cacheSize
        ret["sentAudioFrames"] = rtmpCamera.sentAudioFrames
        ret["sentVideoFrames"] = rtmpCamera.sentVideoFrames
        ret["droppedAudioFrames"] = rtmpCamera.droppedAudioFrames
        ret["droppedVideoFrames"] = rtmpCamera.droppedVideoFrames
        ret["isAudioMuted"] = rtmpCamera.isAudioMuted
        ret["bitrate"] = rtmpCamera.bitrate
        ret["width"] = rtmpCamera.streamWidth
        ret["height"] = rtmpCamera.streamHeight
        result.success(ret)
    }

    fun resumeVideoStreaming(result: MethodChannel.Result) {
        if (!rtmpCamera.isStreaming) {
            result.success(null)
            return
        }
        try {
        } catch (e: IllegalStateException) {
            result.error("videoStreamingFailed", e.message, null)
            return
        }
        result.success(null)
    }


    private val mediaOrientation: Int
        get() {
            val sensorOrientationOffset = if (isFrontFacing)
                -currentOrientation
            else
                currentOrientation
            return (sensorOrientationOffset + sensorOrientation + 360) % 360
        }

    private val isPortrait: Boolean
        get() {
            val getOrient = activity.windowManager.defaultDisplay
            val pt = Point()
            getOrient.getSize(pt)

            if (pt.x == pt.y) {
                return true
            } else {
                return pt.x < pt.y
            }
        }

    override fun onAuthSuccessRtmp() {
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        if (bitrateAdapter != null) {
            bitrateAdapter!!.setMaxBitrate(bitrate.toInt());
        }
    }

    override fun onConnectionSuccessRtmp() {
        bitrateAdapter = BitrateAdapter { bitrate -> rtmpCamera.setVideoBitrateOnFly(bitrate) }
        bitrateAdapter!!.setMaxBitrate(rtmpCamera.bitrate)
    }

    override fun onConnectionFailedRtmp(reason: String) {
        // Retry first.
        for (i in currentRetries..maxRetries) {
            currentRetries = i
            if (rtmpCamera.reTry(5000, reason)) {
                activity.runOnUiThread {
                    dartMessenger.send(DartMessenger.EventType.RTMP_RETRY, reason)
                }
                // Success!
                return
            }
        }

        rtmpCamera.stopStream()
        activity.runOnUiThread {
            dartMessenger.send(DartMessenger.EventType.RTMP_STOPPED, "Failed retry")
        }
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {

    }

    override fun onAuthErrorRtmp() {
        activity.runOnUiThread {
            dartMessenger.send(DartMessenger.EventType.ERROR, "Auth error")
        }
    }

    override fun onDisconnectRtmp() {
        rtmpCamera.stopStream()
        activity.runOnUiThread {
            dartMessenger.send(DartMessenger.EventType.RTMP_STOPPED, "Disconnected")
        }
    }

    companion object {
        private const val TAG: String = "FlutterCamera"
    }


    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
    }

    private fun getSizePairByOrientation(): Pair<Int, Int> {
        return if (isPortrait) {
            Pair((previewSize.width * aspectRatio).toInt(), previewSize.height)
        } else {
            Pair(previewSize.height, (previewSize.width * aspectRatio).toInt())
        }
    }

    private fun updateSurfaceView() {
        resizeSurface()
    }


    private fun resizeSurface() {
        val size = getSizePairByOrientation()
        Log.v(
            TAG,
            "resizeSurface size [${size.first}: ${size.second}] isAttachedToWindow: ${glView.isAttachedToWindow}"
        )
        val layoutParams = LinearLayout.LayoutParams(size.second, size.first)
        if (!glView.isAttachedToWindow) {
            activity.addContentView(glView, layoutParams)
        } else {
            glView.layoutParams = layoutParams
        }
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        val surfaceTexture = flutterTexture.surfaceTexture()
        val size = getSizePairByOrientation()
        surfaceTexture.setDefaultBufferSize(size.first, size.second)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {

    }
}