#import <React/RCTBridgeModule.h>
#import "React/RCTViewManager.h"

@interface RCT_EXTERN_MODULE(RNCameraManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(onCameraReady, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onTouch, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onAudioConnected, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onAudioInterrupted, RCTDirectEventBlock);

@end
