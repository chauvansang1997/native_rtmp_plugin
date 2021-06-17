package vn.tpos.stream_with_rtmp


import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import java.util.*


class DartMessenger(messenger: BinaryMessenger) {
    private var eventSink: EventSink? = null

    enum class EventType {
        ERROR, CAMERA_CLOSING, RTMP_STOPPED, RTMP_RETRY,
        RTMP_CONNECTED, RTMP_RETRY_SUCCESS, ROTATION_UPDATE,
        CAMERA_UPDATE, RTMP_FAILED,
        CONNECTION_SLOW, NEW_BITRATE,FRAME_SLOW
    }

    fun sendCameraClosingEvent() {
        send(EventType.CAMERA_CLOSING, null)
    }

    fun send(eventType: EventType, data: Any?, streamUrl: String? = null) {
        if (eventSink == null) {
            return
        }
        val event: MutableMap<String, Any?> = HashMap()
        event["eventType"] = eventType.toString().toLowerCase(Locale.ROOT)
        // Only errors have a description.
        if (data != null) {
            event["data"] = data
        }
        if (streamUrl != null) {
            event["streamUrl"] = streamUrl
        }
        eventSink!!.success(event)
    }

    init {
        EventChannel(messenger, "plugins.flutter.io/camera_with_rtmp/streamEvents")
            .setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(arguments: Any?, sink: EventSink) {
                        eventSink = sink
                    }

                    override fun onCancel(arguments: Any?) {
                        eventSink = null
                    }
                })
    }
}