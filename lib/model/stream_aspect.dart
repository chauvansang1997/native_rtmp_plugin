enum StreamAspect {
  Aspect4x3,
  Aspect16x9,
}

extension StreamAspectExtention on StreamAspect {
  String get name {
    switch (this) {
      case StreamAspect.Aspect4x3:
        return '4x3';
      case StreamAspect.Aspect16x9:
        return '16x9';
      default:
        return '4x3';
    }
  }
}

extension StreamAspectStringExtension on String {
  StreamAspect get toStreamAspect {
    switch (this) {
      case 'resolution4x3':
        return StreamAspect.Aspect4x3;
      case 'resolution16x9':
        return StreamAspect.Aspect16x9;
      default:
        return StreamAspect.Aspect4x3;
    }
  }
}
