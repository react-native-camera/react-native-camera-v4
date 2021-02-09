import Foundation
import AVFoundation

func videoOrientationForInterfaceOrientation(orientation: UIInterfaceOrientation) -> AVCaptureVideoOrientation? {
  switch (orientation) {
    case UIInterfaceOrientation.portrait:
      return AVCaptureVideoOrientation.portrait;
    case UIInterfaceOrientation.portraitUpsideDown:
      return AVCaptureVideoOrientation.portraitUpsideDown;
    case UIInterfaceOrientation.landscapeRight:
      return AVCaptureVideoOrientation.landscapeRight;
    case UIInterfaceOrientation.landscapeLeft:
      return AVCaptureVideoOrientation.landscapeLeft;
    default:
      return nil;
  }
}
