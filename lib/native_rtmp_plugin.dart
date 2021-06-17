import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:logger/logger.dart';
import 'package:native_rtmp_plugin/camera/camera_value.dart';
import 'package:native_rtmp_plugin/model.dart';
import 'package:rxdart/rxdart.dart';

class NativeRtmpPlugin {
  static final NativeRtmpPlugin _singleton = NativeRtmpPlugin._internal();

  NativeRtmpPlugin._internal() {
    _logger = Logger(
      printer: PrettyPrinter(
        methodCount: 2,
        errorMethodCount: 8,
        lineLength: 120,
        colors: true,
        printEmojis: true,
        printTime: false,
      ),
    );
  }

  factory NativeRtmpPlugin() {
    return _singleton;
  }

  bool _initialized = false;
  final MethodChannel _channel =
      const MethodChannel('plugins.flutter.io/camera_with_rtmp');

  int? _textureId;

  int? get textureId => _textureId;

  late BehaviorSubject<RtmpMessage>? _rtmpSubject;

  late Logger _logger;

  Stream<RtmpMessage> get rtmpStream => _rtmpSubject!.stream;

  Stream<dynamic>? _streamEvent;

  ///Ensure initialization complete first
  Completer<void>? _creatingCompleter;
  StreamSubscription<dynamic>? _streamSubscription;

  Future<CameraTexture> previewWithSettings(Size previewSize) async {
    CameraTexture cameraTexture;
    final Map<String, dynamic>? reply =
        await _channel.invokeMapMethod<String, dynamic>(
      'previewWithSettings',
      <String, dynamic>{
        'width': previewSize.width.toInt(),
        'height': previewSize.height.toInt(),
      },
    );
    if (reply == null) {
      throw Exception('Can not initialized');
    }
    _textureId = reply['textureId'];
    cameraTexture = CameraTexture(
      id: _textureId!,
      previewQuarterTurns: reply['previewQuarterTurns'],
      previewSize: Size(
        reply['previewWidth'].toDouble(),
        reply['previewHeight'].toDouble(),
      ),
    );

    return cameraTexture;
  }

  Future<CameraValue> initialize(RtmpSettings settings) async {
    _rtmpSubject = BehaviorSubject<RtmpMessage>();
    late CameraValue cameraValue;
    try {

      _creatingCompleter = Completer<void>();

      Map<String, dynamic>? reply =
          await _channel.invokeMapMethod<String, dynamic>(
        'initialize',
        <String, dynamic>{
          'cameraName': settings.description.name,
          'resolutionPreset': settings.resolutionPreset.name,
          'streamingPreset': settings.streamingPreset.name,
          'enableAudio': settings.enableAudio,
          'enableAndroidOpenGL': settings.androidUseOpenGL
        },
      );

      if (reply == null) {
        throw Exception('Can not initialized');
      }

      _textureId = reply['textureId'];

      cameraValue = CameraValue(
        textureId: _textureId,
        previewQuarterTurns: reply['previewQuarterTurns'],
        previewSize: Size(
          reply['previewWidth'].toDouble(),
          reply['previewHeight'].toDouble(),
        ),
      );

      _streamEvent =
          EventChannel('plugins.flutter.io/camera_with_rtmp/streamEvents')
              .receiveBroadcastStream();

      _initialized = true;

      if (_streamEvent != null) {
        _streamSubscription = _streamEvent!.listen(_listener);
      }
    } on PlatformException catch (e) {
      throw CameraException(e.code, e.message);
    }

    _creatingCompleter!.complete();

    return cameraValue;
  }

  CameraLensDirection _parseCameraLensDirection(String string) {
    switch (string) {
      case 'front':
        return CameraLensDirection.front;
      case 'back':
        return CameraLensDirection.back;
      case 'external':
        return CameraLensDirection.external;
    }
    throw ArgumentError('Unknown CameraLensDirection value');
  }

  /// Completes with a list of available cameras.
  ///
  /// May throw a [CameraException].
  Future<List<CameraDescription>> availableCameras() async {
    try {
      final List<Map<dynamic, dynamic>>? cameras = await _channel
          .invokeListMethod<Map<dynamic, dynamic>>('availableCameras');
      if (cameras == null) {
        throw Exception('can not get cameras');
      }

      return cameras.map((Map<dynamic, dynamic> camera) {
        // List<Size> resolutions4x3 = [];
        // List<Size> resolutions16x9 = [];
        // if (camera['resolution4x3'] != null) {
        //   resolutions4x3 = (camera['resolution4x3'] as List)
        //       .map((e) => Size(e['width'].toDouble(), e['height'].toDouble()))
        //       .toList();
        // }
        //
        // if (camera['resolutions16x9'] != null) {
        //   resolutions4x3 = (camera['resolutions16x9'] as List)
        //       .map((e) => Size(e['width'], e['height']))
        //       .toList();
        // }
        return CameraDescription(
          name: camera['name'],
          lensDirection: _parseCameraLensDirection(camera['lensFacing']),
          sensorOrientation: camera['sensorOrientation'],
          // resolutions4x3: resolutions4x3,
          // resolutions16x9: resolutions16x9,
        );
      }).toList();
    } on PlatformException catch (e) {
      throw CameraException(e.code, e.message);
    }
  }

  Future<void> setMuteVideoStream(bool mute) async {
    await _channel.invokeMethod<void>('setMuteVideo', {'mute': mute});
  }

  Future<void> setMuteAudioStream(bool mute) async {
    await _channel.invokeMethod<void>('setMuteAudio', {'mute': mute});
  }

  void _listener(dynamic event) {
    if (!_initialized) {
      return;
    }
    final Map<dynamic, dynamic> map = event;

    switch (map['eventType']) {
      case 'error':
        _logger.e(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.Error,
            streamUrl: map['streamUrl'],
            message: ''));
        break;
      case 'camera_closing':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.CameraClosing, streamUrl: null, message: ''));
        break;
      case 'rtmp_connected':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.Connected,
            streamUrl: map['streamUrl'],
            message: ''));
        break;
      case 'rtmp_retry':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.Retry,
            streamUrl: map['streamUrl'],
            message: ''));
        break;
      case 'rtmp_retry_success':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.RetrySuccess,
            streamUrl: map['streamUrl'],
            message: ''));
        break;
      case 'rtmp_stopped':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
          status: RtmpStatus.Stopped,
          streamUrl: map['streamUrl'],
          message: '',
        ));
        break;
      case 'rotation_update':
        _logger.i(event);
        _rtmpSubject?.add(RtmpMessage(
            status: RtmpStatus.CameraRotated,
            message: '',
            streamUrl: null,
            data: event['data']));
        break;
      case 'rtmp_failed':
        _logger.e(event);
        _rtmpSubject?.add(RtmpMessage(
          status: RtmpStatus.Failed,
          message: '',
          streamUrl: map['streamUrl'],
        ));
        break;
      case 'connection_slow':
        _logger.w(event);
        _rtmpSubject?.add(RtmpMessage(
          status: RtmpStatus.ConnectionSlow,
          message: '',
          streamUrl: null,
        ));
        break;
      case 'new_bitrate':
        _logger.w(event);
        _rtmpSubject?.add(RtmpMessage(
          status: RtmpStatus.NewBitrate,
          message: '',
          streamUrl: null,
        ));
        break;
      default:
        _logger.i(event);
        break;
    }
  }

  /// Start a video streaming to the url in [url`].
  ///
  /// This uses rtmp to do the sending the remote side.
  ///
  /// Throws a [CameraException] if the capture fails.
  Future<void> startVideoStreaming(
      {required String url,
      required String streamId,
      int bitrate = 1200 * 1024,
      bool androidUseOpenGL = true}) async {
    try {
      await _channel
          .invokeMethod<void>('startVideoStreaming', <String, dynamic>{
        'textureId': _textureId,
        'url': url,
        'bitrate': bitrate,
        'streamId': streamId,
      });
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  /// Start a video streaming to the urls wuth [urls].
  ///
  /// This uses rtmp to do the sending the remote side.
  ///
  /// Throws a [StreamException] if the capture fails.
  Future<CameraTexture> startListStreaming(
      {required List<String> urls, int bitrate = 1200 * 1024}) async {
    CameraTexture cameraTexture;
    try {
      final Map<String, dynamic>? reply = await _channel
          .invokeMapMethod<String, dynamic>(
              'startListStreaming', <String, dynamic>{
        'bitrate': bitrate,
        'urls': urls,
      });

      if (reply == null) {
        throw Exception('Can not initialized');
      }

      _textureId = reply['textureId'];

      cameraTexture = CameraTexture(
        id: _textureId!,
        previewQuarterTurns: reply['previewQuarterTurns'],
        previewSize: Size(
          reply['previewWidth'].toDouble(),
          reply['previewHeight'].toDouble(),
        ),
      );
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
    return cameraTexture;
  }

  /// Start a video streaming to the urls in [urls].
  /// Call after startListStreaming
  /// This uses rtmp to do the sending the remote side.
  ///
  /// Throws a [StreamException] if the capture fails.
  Future<void> addListStreaming(List<String> urls) async {
    try {
      await _channel.invokeMethod<void>('addListStreaming', <String, dynamic>{
        'urls': urls,
      });
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  /// Remove a video streaming to the urls in [urls].
  ///
  /// This uses rtmp to do the sending the remote side.
  ///
  /// Throws a [StreamException] if the capture fails.
  Future<void> removeListStreaming(List<String> urls) async {
    try {
      await _channel
          .invokeMethod<void>('removeListStreaming', <String, dynamic>{
        'urls': urls,
      });
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  /// Pause video recording.
  ///
  /// This feature is only available on iOS and Android sdk 24+.
  Future<void> pauseChannelStream({required String url}) async {
    try {
      await _channel.invokeMethod<void>(
        'pauseChannelStream',
        <String, dynamic>{
          'streamUrl': url,
        },
      );
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  Future<void> resumeChannelStream({required String url}) async {
    try {
      await _channel.invokeMethod<void>(
        'resumeChannelStream',
        <String, dynamic>{
          'streamUrl': url,
        },
      );
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  Future<void> pauseStream() async {
    try {
      await _channel.invokeMethod<void>('pauseStream');
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  Future<void> resumeStream() async {
    try {
      await _channel.invokeMethod<void>('resumeStream');
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  Future<CameraTexture> setStreamSize(
      {required int width, required int height}) async {
    // try {
    //   await _channel.invokeMethod<void>(
    //     'setStreamSize',
    //     <String, dynamic>{
    //       'width': width,
    //       'height': height,
    //     },
    //   );
    // } on PlatformException catch (e) {
    //   throw StreamException(e.code, e.message);
    // }
    CameraTexture cameraTexture;
    try {
      final Map<String, dynamic>? reply = await _channel
          .invokeMapMethod<String, dynamic>('setStreamSize', <String, dynamic>{
        'width': width,
        'height': height,
      });

      if (reply == null) {
        throw Exception('Can not initialized');
      }

      _textureId = reply['textureId'];

      cameraTexture = CameraTexture(
        id: _textureId!,
        previewQuarterTurns: reply['previewQuarterTurns'],
        previewSize: Size(
          reply['previewWidth'].toDouble(),
          reply['previewHeight'].toDouble(),
        ),
      );
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
    return cameraTexture;
  }

  Future<CameraTexture> switchCamera(int cameraId) async {
    try {
      final Map<String, dynamic>? reply =
          await _channel.invokeMapMethod<String, dynamic>(
        'switchCamera',
        <String, dynamic>{'cameraId': cameraId},
      );

      if (reply == null) {
        throw Exception('Can not switch camera');
      }
      _textureId = reply['textureId'];
      CameraTexture cameraTexture = CameraTexture(
        id: _textureId!,
        previewQuarterTurns: reply['previewQuarterTurns'],
        previewSize: Size(
          reply['previewWidth'].toDouble(),
          reply['previewHeight'].toDouble(),
        ),
      );
      return cameraTexture;
    } on PlatformException catch (e) {
      throw StreamException(e.code, e.message);
    }
  }

  Future<void> setTextFilter(
      {required String text,
      required double scale,
      required double fontSize,
      required double x,
      required double y,
      int color = 0xFF000000,
      int index = 0}) async {
    await _channel.invokeMapMethod<String, dynamic>(
      'setTextFilter',
      <String, dynamic>{
        'text': text,
        'scale': scale,
        'fontSize': fontSize,
        'x': x,
        'y': y,
        'index': index,
        'color': color,
      },
    );
  }

  Future<void> setNumberFilter(int numberFilter) async {
    await _channel.invokeMapMethod<String, dynamic>(
      'setNumberFilter',
      <String, dynamic>{'numberFilter': numberFilter},
    );
  }

  Future<void> setImageFilter(
      {required Uint8List image,
      required double scale,
      required double x,
      required double y,
      int index = 0}) async {
    await _channel.invokeMapMethod<String, dynamic>(
      'setImageFilter',
      <String, dynamic>{
        'image': image,
        'scale': scale,
        'x': x,
        'y': y,
        'index': index,
      },
    );
  }

  /// Stop streaming.
  Future<void> stopStreaming({required String streamUrl}) async {
    try {
      print('Stop streaming call');
      await _channel.invokeMethod<void>(
        'stopStreaming',
        <String, dynamic>{'streamUrl': streamUrl},
      );
    } on PlatformException catch (e) {
      print('Got exception ' + e.toString());
      throw StreamException(e.code, e.message);
    }
  }

  /// Stop streaming.
  Future<void> stopVideoStreaming() async {
    try {
      print('Stop video streaming call');
      await _channel.invokeMethod<void>(
        'stopRecordingOrStreaming',
        <String, dynamic>{'textureId': _textureId},
      );
    } on PlatformException catch (e) {
      print('Got exception ' + e.toString());
      throw StreamException(e.code, e.message);
    }
  }

  Future<List<AspectResolution>> getAllAspectResolutions() async {
    try {
      final List<dynamic>? cameraResolutionMap =
          await _channel.invokeMethod<List<dynamic>>('getAllCameraResolutions');

      if (cameraResolutionMap != null) {
        return cameraResolutionMap
            .map((json) =>
                AspectResolution.fromJson(Map<String, dynamic>.from(json)))
            .toList();
      } else {
        return [];
      }
    } on PlatformException catch (e) {
      print('Got exception ' + e.toString());
      throw StreamException(e.code, e.message);
    }
  }

  Future<List<ResolutionPreset>> getAllResolutions(int cameraId) async {
    try {
      final List<dynamic>? cameraResolutionMap =
          await _channel.invokeMethod<List<dynamic>>(
        'getAllResolutions',
        <String, dynamic>{'cameraId': cameraId},
      );

      if (cameraResolutionMap != null) {
        return cameraResolutionMap
            .map((e) => e.toString().resolutionPreset)
            .toList();
      } else {
        return [];
      }
    } on PlatformException catch (e) {
      print('Got exception ' + e.toString());
      throw StreamException(e.code, e.message);
    }
  }

  Future<void> dispose() async {
    _initialized = false;
    if (_creatingCompleter != null) {
      _rtmpSubject?.close();
      _rtmpSubject = null;
      await _creatingCompleter!.future;
      _streamSubscription?.cancel();

      await _channel.invokeMethod<void>(
        'dispose',
        <String, dynamic>{'textureId': _textureId},
      );
    }
  }
}
