// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:flutter/widgets.dart';

enum CameraLensDirection { front, back, external }

/// Affect the quality of video recording and image capture:
///
/// If a preset is not available on the camera being used a preset of lower quality will be selected automatically.
enum ResolutionPreset {
  /// 352x288 on iOS, 240p (320x240) on Android
  low,

  /// 480p (640x480 on iOS, 720x480 on Android)
  medium,

  /// 720p (1280x720)
  high,

  /// 1080p (1920x1080)
  veryHigh,

  /// 2160p (3840x2160)
  ultraHigh,

  /// The highest resolution available.
  max,
}

extension CameraLensDirectionExtension on CameraLensDirection {
  String get name {
    List<String> values = this.toString().split('.');
    return values.last;
  }
}

extension ResolutionPresetExtension on ResolutionPreset {
  String get name {
    List<String> values = this.toString().split('.');
    return values.last;
  }
}

extension ResolutionPresetString on String {
  ResolutionPreset get resolutionPreset {
    ResolutionPreset preset =
        ResolutionPreset.values.firstWhere((element) => element.name == this);
    return preset;
  }

  CameraLensDirection get cameraLensDirection {
    CameraLensDirection lensDirection = CameraLensDirection.values
        .firstWhere((element) => element.name == this);
    return lensDirection;
  }
}

class CameraDescription {
  CameraDescription({
    required this.name,
    required this.lensDirection,
    required this.sensorOrientation,
  });

  CameraDescription.fromJson(Map<String, dynamic> json) {
    name = json['name'];
    lensDirection = json['lensDirection'].toString().cameraLensDirection;
    sensorOrientation = json['sensorOrientation'];
  }

  late String name;
  late CameraLensDirection lensDirection;

  /// Clockwise angle through which the output image needs to be rotated to be upright on the device screen in its native orientation.
  ///
  /// **Range of valid values:**
  /// 0, 90, 180, 270
  ///
  /// On Android, also defines the direction of rolling shutter readout, which
  /// is from top to bottom in the sensor's coordinate system.
  late int sensorOrientation;

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = {};
    data['name'] = name;
    data['lensDirection'] = lensDirection.name;
    data['sensorOrientation'] = sensorOrientation;
    return data;
  }

  @override
  bool operator ==(Object o) {
    return o is CameraDescription &&
        o.name == name &&
        o.lensDirection == lensDirection;
  }

  @override
  int get hashCode {
    return hashValues(name, lensDirection);
  }

  @override
  String toString() {
    return '$runtimeType($name, $lensDirection, $sensorOrientation)';
  }
}

/// This is thrown when the plugin reports an error.
class CameraException implements Exception {
  CameraException(this.code, this.description);

  String? code;
  String? description;

  @override
  String toString() => '$runtimeType($code, $description)';
}
