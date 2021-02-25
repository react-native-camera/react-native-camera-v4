#import <React/RCTBridgeModule.h>
#import "React/RCTViewManager.h"

@interface RCT_EXTERN_MODULE(RNCameraManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(type, NSInteger);
RCT_EXPORT_VIEW_PROPERTY(cameraId, NSString);
RCT_EXPORT_VIEW_PROPERTY(zoom, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(maxZoom, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(autoFocus, NSInteger);
RCT_EXPORT_VIEW_PROPERTY(flashMode, NSInteger);
RCT_EXPORT_VIEW_PROPERTY(exposure, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(autoFocusPointOfInterest, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(whiteBalance, NSInteger);
RCT_EXPORT_VIEW_PROPERTY(customWhiteBalance, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(nativeZoom, BOOL);
RCT_EXPORT_VIEW_PROPERTY(focusDepth, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(videoStabilizationMode, NSInteger);
RCT_EXPORT_VIEW_PROPERTY(keepAudioSession, BOOL);

RCT_EXPORT_VIEW_PROPERTY(onCameraReady, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onMountError, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onTouch, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onAudioConnected, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onAudioInterrupted, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onSubjectAreaChanged, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPictureTaken, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPictureSaved, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onRecordingEnd, RCTDirectEventBlock);

RCT_EXTERN_METHOD(takePicture: (nonnull NSNumber *)node
                  options: (NSDictionary)options
                  resolve: (RCTPromiseResolveBlock)resolve
                  reject: (RCTPromiseRejectBlock));
RCT_EXTERN_METHOD(record: (nonnull NSNumber *)node
                  options: (NSDictionary)options
                  resolve: (RCTPromiseResolveBlock)resolve
                  reject: (RCTPromiseRejectBlock));
RCT_EXTERN_METHOD(stopRecording: (nonnull NSNumber *)node);
RCT_EXTERN_METHOD(pausePreview: (nonnull NSNumber *)node);
RCT_EXTERN_METHOD(resumePreview: (nonnull NSNumber *)node);
RCT_EXTERN_METHOD(checkDeviceAuthorizationStatus:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(checkVideoAuthorizationStatus:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(checkRecordAudioAuthorizationStatus:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject);

@end
