import Foundation

@objc(RNCameraManager)
class RNCameraManager : RCTViewManager {
  override class func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func view() -> UIView! {
    return RNCamera()
  }
}
