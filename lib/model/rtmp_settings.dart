import 'package:native_rtmp_plugin/model.dart';

class RtmpSettings {
  RtmpSettings({
    required this.description,
    this.streamingPreset = ResolutionPreset.ultraHigh,
    this.androidUseOpenGL = true,
    this.enableAudio = true,
    this.resolutionPreset = ResolutionPreset.ultraHigh,
  });

  RtmpSettings.fromJson(Map<String, dynamic> json) {
    description = json['description'];
    resolutionPreset = json['resolutionPreset'].toString().resolutionPreset;
    streamingPreset = json['streamingPreset'].toString().resolutionPreset;
    androidUseOpenGL = json['androidUseOpenGL'];
    enableAudio = json['enableAudio'];
  }

  late CameraDescription description;
  late  ResolutionPreset resolutionPreset;
  late  ResolutionPreset streamingPreset;
  late  bool enableAudio;
  late bool androidUseOpenGL;

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = {};
    data['description'] = description.toJson();
    data['resolutionPreset'] = resolutionPreset.name;
    data['streamingPreset'] = streamingPreset.name;
    data['enableAudio'] = enableAudio;
    data['androidUseOpenGL'] = androidUseOpenGL;
    return data;
  }
}
