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

func deviceWithCameraId(_ cameraId: String) -> AVCaptureDevice? {
  return AVCaptureDevice(uniqueID: cameraId)
}

func deviceWithMediaType(
  mediaType: AVMediaType,
  position: AVCaptureDevice.Position
) -> AVCaptureDevice? {
  let devices = AVCaptureDevice.devices(for: mediaType)
  
  for device in devices {
    if (device.position == position) {
      return device
    }
  }
  
  return devices.first
}
