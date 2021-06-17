import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';

class CameraValue {
  CameraValue({required this.previewSize, required this.previewQuarterTurns, this.textureId});

  /// The size of the preview in pixels.
  ///
  /// Is `null` until  [isInitialized] is `true`.
  Size previewSize;

  /// The amount to rotate the preview by in quarter turns.
  ///
  /// Is `null` until  [isInitialized] is `true`.
  int previewQuarterTurns;

  ///This value use for ios
  int? textureId;
}
