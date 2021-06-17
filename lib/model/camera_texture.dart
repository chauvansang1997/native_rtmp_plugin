import 'package:flutter/material.dart';

class CameraTexture {
  CameraTexture(
      {required this.id,
      required this.previewQuarterTurns,
      required this.previewSize});

  int id;
  int previewQuarterTurns;
  Size previewSize;

  double get aspectRatio => previewSize.height / previewSize.width;
}
