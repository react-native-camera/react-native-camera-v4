import Foundation
import CoreMotion
import UIKit

protocol SensorOrientationCheckerDelegate {
  func orientationSet(orientation: UIInterfaceOrientation)
}

class SensorOrientationChecker {
  let motionManager = CMMotionManager()
  let operationQueue = OperationQueue()
  
  var delegate: SensorOrientationCheckerDelegate?
  var orientation: UIInterfaceOrientation
  
  init() {
    motionManager.accelerometerUpdateInterval = 0.2
    motionManager.gyroUpdateInterval = 0.2
  }
  
  deinit {
    pause()
  }
  
  func resume() {
    motionManager.startAccelerometerUpdates(
      to: operationQueue
    ) { [self] dataOrNil, error in
      if (error != nil) {
        return
      }
      
      guard let data = dataOrNil else { return }
      
      orientation = getOrientationBy(acceleration: data.acceleration)
      delegate?.orientationSet(orientation: orientation)
    }
  }
  
  func pause() {
    motionManager.stopAccelerometerUpdates()
    operationQueue.cancelAllOperations()
  }
  
  
  private func getOrientationBy(acceleration: CMAcceleration) -> UIInterfaceOrientation {
    if(acceleration.x >= 0.75) {
      return UIInterfaceOrientation.landscapeLeft;
    }
    if(acceleration.x <= -0.75) {
      return UIInterfaceOrientation.landscapeRight;
    }
    if(acceleration.y <= -0.75) {
      return UIInterfaceOrientation.portrait;
    }
    if(acceleration.y >= 0.75) {
      return UIInterfaceOrientation.portraitUpsideDown;
    }
    
    return UIApplication.shared.statusBarOrientation
  }
}
