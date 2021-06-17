package com.plugin.native_rtmp_plugin.native_rtmp_plugin


import android.media.MediaCodec
import android.util.Log
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtmp.utils.ConnectCheckerRtmp

import java.nio.ByteBuffer


class RtmpConnector(
    private val rtmpReconnectCallBack: RtmpReconnectCallBack
) : ConnectCheckerRtmp {

    var rtmpClient: RtmpClient = RtmpClient(this)
    var currentRetries = 0
    var connected: Boolean? = false

    var restart: Boolean = false

    private var disconnectTime: Long? = null

    var enableAudio: Boolean? = false

    private var url: String? = ""


    fun getUrl(): String {
        return url!!
    }

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    var isStreaming = false
        private set


    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *
     * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */
    fun startStream(url: String, width: Int, height: Int, rotation: Int) {
        if (isStreaming) {
            return
        }
        this.url = url
        isStreaming = true
        rtmpClient.setLogs(true)
        rtmpClient.setReTries(10)
        rtmpClient.forceAkamaiTs(true)

        disconnectTime = System.nanoTime() / 1000
        if (rotation == 90 || rotation == 270) {
            rtmpClient.setVideoResolution(height, width)
        } else {
            rtmpClient.setVideoResolution(width, height)
        }

        enableAudio = true
        rtmpClient.connect(url, isRetry = true)

    }

    fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (isStreaming && enableAudio!!) {
            rtmpClient.sendAudio(aacBuffer.duplicate(), info)

        }
    }

    fun forceAkamaiTs(enabled: Boolean) {
        rtmpClient.forceAkamaiTs(enabled)
    }

    fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
        rtmpClient.setAudioInfo(sampleRate, isStereo)
    }

    var firstSps: ByteBuffer? = null
    var firstPps: ByteBuffer? = null
    var firstVps: ByteBuffer? = null

    fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        firstSps = sps.duplicate()
        firstPps = pps.duplicate()
        firstVps = vps?.duplicate()
        rtmpClient.setVideoInfo(sps.duplicate(), pps.duplicate(), firstVps?.duplicate())
    }

    fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (isStreaming) {
            rtmpClient.sendVideo(h264Buffer.duplicate(), info)
        }
    }

    override fun onAuthSuccessRtmp() {
        rtmpReconnectCallBack.onAuthSuccessRtmp(this)
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        rtmpReconnectCallBack.onNewBitrateRtmp(bitrate, this)
    }

    override fun onConnectionSuccessRtmp() {
        currentRetries = 0
        rtmpReconnectCallBack.onConnectionSuccessRtmp(this)
    }

    override fun onConnectionFailedRtmp(reason: String) {
        pauseStream()
        rtmpReconnectCallBack.onConnectionFailedRtmp(reason, this)
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {

    }

    fun reconnect(reason: String, delay: Long = 5000) {
        restart = true
        Log.i("RtmpConnector", "reconnecting to rtmp")
        disconnectTime = System.nanoTime() / 1000
        if (reTry(5000, reason)) {
            resumeStream()
            disconnectTime = System.nanoTime() / 1000
            // Success!
            Log.i("RtmpConnector", "connect to rtmp success")
        } else {
            stopStream()
        }
    }

    override fun onAuthErrorRtmp() {
        rtmpReconnectCallBack.onAuthErrorRtmp(this)
    }

    override fun onDisconnectRtmp() {
        rtmpReconnectCallBack.onDisconnectRtmp(this)
    }

    private fun shouldRetry(reason: String): Boolean {
        return rtmpClient.shouldRetry(reason)
    }

    private fun reConnect(delay: Long) {
        rtmpClient.reConnect(delay)
    }

    /**
     * Stop stream started with @startStream.
     */
    fun pauseStream() {
        isStreaming = false
    }

    /**
     * Stop stream started with @startStream.
     */
    fun resumeStream() {
        isStreaming = true
    }


    /**
     * Stop stream started with @startStream.
     */
    fun stopStream() {
        isStreaming = false
        rtmpClient.disconnect()
    }

    private fun reTry(delay: Long, reason: String): Boolean {
        val result = shouldRetry(reason)
        if (result) {
            reConnect(delay)
        }
        return result
    }
}