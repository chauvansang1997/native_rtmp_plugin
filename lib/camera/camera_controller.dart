import 'package:flutter/widgets.dart';
import 'package:native_rtmp_plugin/camera/camera_value.dart';

class CameraController extends ValueNotifier<CameraValue?> {
  CameraController() : super(null) {
    _cameraValue = null;
  }

  bool get isInitialized => _isInitialized;
  CameraValue? _cameraValue;
  bool _isInitialized = false;

  void setCameraSize(Size value) {
    _cameraValue?.previewSize = value;
    notifyListeners();
  }

  void init(CameraValue value) {
    _cameraValue = value;
  }
}
