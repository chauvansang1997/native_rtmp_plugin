package com.plugin.native_rtmp_plugin.native_rtmp_plugin

import android.app.Activity
import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.rtplibrary.base.Camera2Base
import com.pedro.rtplibrary.util.BitrateAdapter
import com.pedro.rtplibrary.view.LightOpenGlView
import com.pedro.rtplibrary.view.OpenGlView
import io.flutter.plugin.common.MethodChannel
import vn.tpos.stream_with_rtmp.DartMessenger
import java.nio.ByteBuffer


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtmpCameraManager : Camera2Base {
    //    private val rtmpClient: RtmpClient
    private lateinit var activity: Activity
    private lateinit var dartMessenger: DartMessenger
    private var rtmpClientMap: MutableMap<String, RtmpConnector>? = null
    private val lock = Any()

    constructor(openGlView: OpenGlView?, activity: Activity, dartMessenger: DartMessenger) : super(
        openGlView
    ) {
        this.activity = activity
        this.dartMessenger = dartMessenger
    }

    constructor(
        lightOpenGlView: LightOpenGlView?,
        activity: Activity,
        dartMessenger: DartMessenger
    ) : super(
        lightOpenGlView
    ) {
        this.activity = activity
        this.dartMessenger = dartMessenger
    }

    constructor(
        context: Context?,
        useOpengl: Boolean,
        activity: Activity,
        dartMessenger: DartMessenger
    ) : super(
        context,
        useOpengl
    ) {
        this.activity = activity
        this.dartMessenger = dartMessenger
    }

    var bitrateAdapter: BitrateAdapter? = null
    var maxBitrate: Int = 0


    /**
     * Start live stream with list [urls]
     */
    fun startListConnection(
        urls: List<String>,
        bitrate: Int = 1200 * 1024,
        mute: Boolean,
        result: MethodChannel.Result,
        width: Int, height: Int
    ) {
        maxBitrate = bitrate
        bitrateAdapter = BitrateAdapter(BitrateAdapter.Listener { adaptiveBitrate ->
            run {
                synchronized(lock) {
                    setVideoBitrateOnFly(adaptiveBitrate)
                }


                if (adaptiveBitrate * 1.2 < maxBitrate) {
                    activity.runOnUiThread {
                        dartMessenger.send(
                            DartMessenger.EventType.CONNECTION_SLOW,
                            "Connection is slow $adaptiveBitrate",
                            null
                        )
                    }
                }
            }
        })

        bitrateAdapter!!.setMaxBitrate(bitrate)

        if (isRecording || prepareAudio() && prepareVideo(
                width,
                height,
                bitrate
            )
        ) {
            startStream("")
        }


        for (url in urls) {
            val cameraRtmp = RtmpConnector(rtmpReconnectCallBack = object : RtmpReconnectCallBack {
                override fun OnRtmpRetry(reason: String, rtmpConnector: RtmpConnector) {
                    activity.runOnUiThread {
                        dartMessenger.send(
                            DartMessenger.EventType.RTMP_RETRY,
                            reason,
                            rtmpConnector.getUrl()
                        )
                    }
                }

                override fun onAuthSuccessRtmp(rtmpConnector: RtmpConnector) {}
                override fun onNewBitrateRtmp(bitrate: Long, rtmpConnector: RtmpConnector) {
                    if (bitrateAdapter != null) {
                        bitrateAdapter!!.adaptBitrate(bitrate)
                    }

                }

                override fun onConnectionSuccessRtmp(rtmpConnector: RtmpConnector) {
                    rtmpConnector.connected = true
                    rtmpConnector.currentRetries = 0

                    synchronized(lock) {

                    }

                    if (rtmpConnector.restart) {
                        activity.runOnUiThread {
                            dartMessenger.send(
                                DartMessenger.EventType.RTMP_RETRY_SUCCESS,
                                "",
                                rtmpConnector.getUrl()
                            )
                        }
                    } else {
                        activity.runOnUiThread {
                            dartMessenger.send(
                                DartMessenger.EventType.RTMP_CONNECTED,
                                "",
                                rtmpConnector.getUrl()
                            )
                        }

                        for (value in rtmpClientMap!!.values) {
                            value.resumeStream()
                        }
                    }
                }

                override fun onConnectionFailedRtmp(reason: String, rtmpConnector: RtmpConnector) {
                    activity.runOnUiThread(Runnable {
                        dartMessenger.send(
                            DartMessenger.EventType.RTMP_FAILED,
                            reason,
                            rtmpConnector.getUrl()
                        )
                        rtmpConnector.reconnect(reason)
                    })
                }

                override fun onAuthErrorRtmp(rtmpConnector: RtmpConnector) {}
                override fun onDisconnectRtmp(rtmpConnector: RtmpConnector) {}

            })

            rtmpClientMap!![url] = cameraRtmp
        }

        if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
            rtmpClient.setVideoResolution(videoEncoder.height, videoEncoder.width)
        } else {
            rtmpClient.setVideoResolution(videoEncoder.width, videoEncoder.height)
        }
        rtmpClient.connect(url)




        /**
         * prepare encoder before reopen camera
         */
        cameraEncoderManager.prepareEncoder()
        Thread.sleep(2000)
        cameraEncoderManager.encoderManager!!.setAudioDataCallBack(this)
        cameraEncoderManager.encoderManager!!.setVideoDataCallBack(this)
        cameraEncoderManager.encoderManager!!.startEncoders()
        Thread.sleep(2000)
        if (mute) {
            cameraEncoderManager.encoderManager!!.disableAudio()
        }

        /**
         * reopen camera
         */
        cameraEncoderManager.open(result = result, onSuccessCallback = Runnable {
            for ((url, connector) in cameraRtmpControllerMap!!) {
                connector.startStream(
                    url = url, width = cameraEncoderManager.encoderManager!!.getStreamWidth(),
                    height = cameraEncoderManager.encoderManager!!.getStreamHeight(),
                    rotation = cameraEncoderManager.encoderManager!!.getRotation()
                )
            }
            cameraEncoderManager.encoderManager!!.setBeauty()
            start = true
//            result.success(null)
        }, cameraName = null)

    }


    @Throws(RuntimeException::class)
    override fun resizeCache(newSize: Int) {
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.resizeCache(newSize)
        }
    }

    override fun getCacheSize(): Int {
        return 0
    }

    override fun getSentAudioFrames(): Long {
        return 0
    }

    override fun getSentVideoFrames(): Long {
        return 0
    }

    override fun getDroppedAudioFrames(): Long {
        return 0
    }

    override fun getDroppedVideoFrames(): Long {
//        return rtmpClient.droppedVideoFrames
        return 0
    }

    override fun resetSentAudioFrames() {
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.resetSentAudioFrames()
        }
    }

    override fun resetSentVideoFrames() {
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.resetSentVideoFrames()
        }
    }

    override fun resetDroppedAudioFrames() {
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.resetDroppedAudioFrames()
        }
    }

    override fun resetDroppedVideoFrames() {
//        rtmpClient.resetDroppedVideoFrames()
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.resetDroppedVideoFrames()
        }
    }

    override fun setAuthorization(user: String, password: String) {
//        rtmpClient.setAuthorization(user, password)
        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.setAuthorization(user, password)
        }
    }

    /**
     * Some Livestream hosts use Akamai auth that requires RTMP packets to be sent with increasing
     * timestamp order regardless of packet type.
     * Necessary with Servers like Dacast.
     * More info here:
     * https://learn.akamai.com/en-us/webhelp/media-services-live/media-services-live-encoder-compatibility-testing-and-qualification-guide-v4.0/GUID-F941C88B-9128-4BF4-A81B-C2E5CFD35BBF.html
     */
    fun forceAkamaiTs(enabled: Boolean) {
        for (value in rtmpClientMap!!.values) {
            value.forceAkamaiTs(enabled)
        }
    }

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        for (value in rtmpClientMap!!.values) {
            value.setAudioInfo(sampleRate, isStereo)
        }
    }

    override fun startStreamRtp(url: String) {
        for (value in rtmpClientMap!!.values) {
            value.startStream(
                url = url,
                width = videoEncoder.width,
                height = videoEncoder.height,
                rotation = videoEncoder.rotation
            )
        }
    }

    override fun stopStreamRtp() {
        for (value in rtmpClientMap!!.values) {
            value.stopStream()
        }
    }

    override fun setReTries(reTries: Int) {
//        rtmpClient.setReTries(reTries)
//        for (value in rtmpClientMap!!.values) {
//            value.onSpsPpsVpsRtp(sps, pps, vps)
//        }
    }

    override fun shouldRetry(reason: String): Boolean {
//        return rtmpClient.shouldRetry(reason)
        return false
    }

    public override fun reConnect(delay: Long, backupUrl: String?) {
//        rtmpClient.reConnect(delay, backupUrl)
    }

    override fun hasCongestion(): Boolean {
        return false
    }

    override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        for (value in rtmpClientMap!!.values) {
            value.sendAudio(aacBuffer, info)
        }
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
        for (value in rtmpClientMap!!.values) {
            value.onSpsPpsVpsRtp(sps, pps, vps)
        }
    }

    override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        for (value in rtmpClientMap!!.values) {
            value.sendVideo(h264Buffer, info)
        }
    }

    override fun setLogs(enable: Boolean) {

        for (value in rtmpClientMap!!.values) {
            value.rtmpClient.setLogs(enable)
        }
    }
}