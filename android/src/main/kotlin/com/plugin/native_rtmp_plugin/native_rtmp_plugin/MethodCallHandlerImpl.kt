package com.plugin.native_rtmp_plugin.native_rtmp_plugin

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.OrientationEventListener
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.embedding.engine.FlutterEngine
import java.util.HashMap

class MethodCallHandlerImpl(
        private val activity: Activity,
        private val messenger: BinaryMessenger,
        private val cameraPermissions: CameraPermissions,
        private val permissionsRegistry: PermissionStuff,
        flutterEngine: FlutterEngine) : MethodCallHandler {

    private val methodChannel: MethodChannel

    private var currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN
    private var dartMessenger: DartMessenger? = null
    private var nativeViewFactory: NativeViewFactory? = null
    private val handler = Handler()

    private val textureId = 0L

    init {
        Log.d("TAG", "init $flutterEngine")
        methodChannel = MethodChannel(messenger, "plugins.flutter.io/rtmp_publisher")
        methodChannel.setMethodCallHandler(this)
        nativeViewFactory = NativeViewFactory(activity)

        flutterEngine
                .platformViewsController
                .registry
                .registerViewFactory("hybrid-view-type", nativeViewFactory)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "availableCameras" -> try {
                Log.i("Stuff", "availableCameras")
                result.success(CameraUtils.getAvailableCameras(activity))
            } catch (e: Exception) {
                handleException(e, result)
            }
            "initialize" -> {
                Log.i("Stuff", "initialize")
                cameraPermissions.requestPermissions(
                        activity,
                        permissionsRegistry,
                        call.argument("enableAudio")!!,
                        object : CameraPermissions.ResultCallback {
                            override fun onResult(errorCode: String?, errorDescription: String?) {
                                if (errorCode == null) {
                                    try {
                                        instantiateCamera(call, result)
                                    } catch (e: Exception) {
                                        handleException(e, result)
                                    }
                                } else {
                                    result.error(errorCode, errorDescription, null)
                                }
                            }
                        })
            }
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
        handler.postDelayed({
            val cameraName = call.argument<String>("cameraName") ?: "0"
            val resolutionPreset = call.argument<String>("resolutionPreset")
                    ?: "low"
            val enableAudio = call.argument<Boolean>("enableAudio")!!
            dartMessenger = DartMessenger(messenger, textureId)

            val preset = ResolutionPreset.valueOf(resolutionPreset)
            val previewSize = CameraUtils.computeBestPreviewSize(cameraName, preset)
            val reply: MutableMap<String, Any> = HashMap()
            reply["textureId"] = textureId
            reply["previewWidth"] = previewSize.width
            reply["previewHeight"] = previewSize.height
            reply["previewQuarterTurns"] = currentOrientation / 90
            Log.i("TAG", "open: width: " + reply["previewWidth"] + " height: " + reply["previewHeight"] + " currentOrientation: " + currentOrientation + " quarterTurns: " + reply["previewQuarterTurns"])
            // TODO Refactor cameraView initialisation
            nativeViewFactory?.cameraName = cameraName
            nativeViewFactory?.preset = preset
            nativeViewFactory?.enableAudio = enableAudio
            nativeViewFactory?.dartMessenger = dartMessenger
            getCameraView()?.startPreview(cameraName)
            result.success(reply)
        }, 100)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isFrontFacing(cameraName: String): Boolean {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleException(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
        }
        throw (exception as RuntimeException)
    }

    private fun getCameraView(): CameraNativeView? = nativeViewFactory?.cameraNativeView
}