package com.plugin.native_rtmp_plugin.native_rtmp_plugin


import com.pedro.rtplibrary.util.BitrateAdapter

interface RtmpReconnectCallBack {
    fun OnRtmpRetry(reason: String, rtmpConnector: RtmpConnector)

    fun onAuthSuccessRtmp(rtmpConnector: RtmpConnector)

    fun onNewBitrateRtmp(bitrate: Long, rtmpConnector: RtmpConnector)

    fun onConnectionSuccessRtmp(rtmpConnector: RtmpConnector)

    fun onConnectionFailedRtmp(reason: String, rtmpConnector: RtmpConnector)

    fun onAuthErrorRtmp(rtmpConnector: RtmpConnector)

    fun onDisconnectRtmp(rtmpConnector: RtmpConnector)
}