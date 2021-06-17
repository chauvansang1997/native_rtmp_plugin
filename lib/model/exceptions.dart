/// This is thrown when the plugin reports an error.
class StreamException implements Exception {
  StreamException(this.code, this.description);

  String? code;
  String? description;

  @override
  String toString() => '$runtimeType($code, $description)';
}
