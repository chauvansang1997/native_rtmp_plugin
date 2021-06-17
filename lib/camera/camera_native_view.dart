// Build the UI texture view of the video data with textureId.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'camera_controller.dart';

class CameraPreview extends StatelessWidget {
  const CameraPreview(this.controller);

  final CameraController controller;

  @override
  Widget build(BuildContext context) {
    if (controller.value != null) {
      late Widget childView;
      if (Platform.isAndroid) {
        childView = AndroidView(
          viewType: 'hybrid-view-type',
          creationParamsCodec: const StandardMessageCodec(),
        );
      } else if (controller.value?.textureId != null) {
        childView = Texture(textureId: controller.value!.textureId!);
      } else {
        return const SizedBox();
      }

      if (controller.value!.previewSize.width <
          controller.value!.previewSize.height) {
        return RotatedBox(
          quarterTurns: controller.value!.previewQuarterTurns,
          child: childView,
        );
      } else {
        return childView;
      }
    } else {
      return const SizedBox();
    }
  }
}
