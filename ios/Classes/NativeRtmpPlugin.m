#import "NativeRtmpPlugin.h"
#if __has_include(<native_rtmp_plugin/native_rtmp_plugin-Swift.h>)
#import <native_rtmp_plugin/native_rtmp_plugin-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "native_rtmp_plugin-Swift.h"
#endif

@implementation NativeRtmpPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftNativeRtmpPlugin registerWithRegistrar:registrar];
}
@end
