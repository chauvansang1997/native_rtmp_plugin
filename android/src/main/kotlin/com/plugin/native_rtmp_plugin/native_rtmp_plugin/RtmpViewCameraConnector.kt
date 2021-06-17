package com.plugin.native_rtmp_plugin.native_rtmp_plugin

import android.app.Activity
import android.util.Size
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtmp.utils.ConnectCheckerRtmp


class RtmpViewCameraConnector(val activity: Activity,
                              val connectChecker: ConnectCheckerRtmp
) {

    private val rtmpClient: RtmpClient = RtmpClient(connectChecker)

    var isStreaming = false
        private set
    var isRecording = false
        private set

    companion object {
        private val TAG: String? = "RtmpCameraConnector"
    }


}