import 'package:flutter/painting.dart';
import 'package:native_rtmp_plugin/model.dart';



///object hold all resolutions in specific aspect of camera (example: with 19x6 has resolutions [1920x1080, 1280x720])
class AspectResolution {
  AspectResolution({required this.aspect, required this.resolutions});

  AspectResolution.fromJson(Map<String, dynamic> json) {
    aspect = json['aspect'] != null
        ? json['aspect'].toString().toStreamAspect
        : StreamAspect.Aspect4x3;
    resolutions = [];
    if (json['resolutions'] != null) {
      resolutions = (json['resolutions'] as List)
          .map((e) => Size(e['width'].toDouble(), e['height'].toDouble()))
          .toList();
    }
  }

  late StreamAspect aspect;

  late List<Size> resolutions;
}
