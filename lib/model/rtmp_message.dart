import 'package:native_rtmp_plugin/model.dart';
class RtmpMessage {
  RtmpMessage(
      {required this.message, required this.status, required this.streamUrl, this.data});

  String message;
  dynamic data;
  String? streamUrl;
  RtmpStatus status;

  @override
  String toString() {
    return '$streamUrl $status $message';
  }
}
