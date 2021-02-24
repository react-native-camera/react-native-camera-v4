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

func convertToAVCaptureVideoOrientation(_ orientationOrNil: UIInterfaceOrientation?) -> AVCaptureVideoOrientation? {
  
  guard let orientation = orientationOrNil else { return nil }

  switch (orientation) {
    case .portrait:
      return .portrait;
    case .portraitUpsideDown:
      return .portraitUpsideDown;
    case .landscapeLeft:
      return .landscapeLeft;
    case .landscapeRight:
      return .landscapeRight;
    default:
      return nil
  }
}

func cropImage(_ image: UIImage, toRect: CGRect) -> UIImage? {
  guard let takenCGIImage = image.cgImage else {
    return nil
  }
  
  guard let cropCGImage = takenCGIImage.cropping(to: toRect) else {
    return nil
  }
  
  return UIImage.init(cgImage: cropCGImage, scale: image.scale, orientation: image.imageOrientation)
}


func mirrorImage(_ image: UIImage) -> UIImage? {
  guard let cgImage = image.cgImage else {
    return nil
  }

  var flippedOrientation = UIImage.Orientation.upMirrored
  switch (image.imageOrientation) {
    case .down:
      flippedOrientation = .downMirrored
      break
    case .left:
      flippedOrientation = .leftMirrored
      break
    case .up:
      flippedOrientation = .upMirrored
      break
    case .right:
      flippedOrientation = .rightMirrored
      break
    default:
      break
  }

  return UIImage.init(cgImage: cgImage, scale: image.scale, orientation: flippedOrientation)
}

func forceUpOrientation(_ image: UIImage) -> UIImage? {
  if (image.imageOrientation == .up) {
    return image
  }
  
  UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
  image.draw(in: CGRect(x: 0, y: 0, width: image.size.width, height: image.size.height))
  let result = UIGraphicsGetImageFromCurrentImageContext()
  UIGraphicsEndImageContext()
  return result
}

func scaleImage(_ image: UIImage, toWidth: Int) -> UIImage? {
  let width = UIScreen.main.scale / CGFloat(toWidth)
  let scaleRatio = width / image.size.width
  let size = CGSize(width: width, height: CGFloat(roundf(Float(image.size.height * scaleRatio))))
  UIGraphicsBeginImageContextWithOptions(size, false, 0.0);
  image.draw(in: CGRect(x: 0, y: 0, width: size.width, height: size.height))
  guard let newImage = UIGraphicsGetImageFromCurrentImageContext() else {
    UIGraphicsEndImageContext();
    return nil
  }
  UIGraphicsEndImageContext();
  guard let newCgImage = newImage.cgImage else {
    return nil
  }
  
  return UIImage(cgImage: newCgImage, scale: 1.0, orientation: newImage.imageOrientation)
}

func mirrorVideo (_ url: URL, completion: (URL?, Error?) -> Void) {
  let videoAsset = AVAsset(url: url)
  guard let videoTrack = videoAsset.tracks(withMediaType: .video).first else {
    completion(nil, RecordError.runtimeError("Could not find valid video track while mirroring video"))
    return
  }
  
  let composition = AVMutableComposition()
  composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
  let videoComposition = AVMutableVideoComposition()
  videoComposition.renderSize = CGSize(width: videoTrack.naturalSize.width, height: videoTrack.naturalSize.height)
  videoComposition.frameDuration = videoTrack.minFrameDuration
  
  let transformer = AVMutableVideoCompositionLayerInstruction(assetTrack: videoTrack)
  let instruction = AVMutableVideoCompositionInstruction()
  instruction.timeRange = CMTimeRange(start: .zero, duration: CMTime(seconds: 60, preferredTimescale: 30))
  
  let transform = CGAffineTransform(scaleX: -1.0, y: -1.0)
    .translatedBy(x: -videoTrack.naturalSize.width, y: 0)
    .rotated(by: CGFloat(.pi / 2.0))
    .translatedBy(x: 0.0, y: -videoTrack.naturalSize.height)
  
  transformer.setTransform(transform, at: .zero)
  instruction.layerInstructions = [transformer]
  videoComposition.instructions = [instruction]
  
  //Export
  guard let outputUrl = generatePathInDirectory(cacheDirectoryPath.appending("CameraFlip"), withExtension: ".mp4") else {
    completion(nil, RecordError.runtimeError("Could not find path to save mirror of recorded video"))
    return
  }
  
  guard let exportSession = AVAssetExportSession(asset: videoAsset, presetName: AVAssetExportPreset640x480) else {
    completion(nil, RecordError.runtimeError("Could not initialize export session for mirror of recorded video"))
    return
  }
  exportSession.outputURL = outputUrl
  exportSession.outputFileType = .mp4

  exportSession.exportAsynchronously {
    if let error = exportSession.error {
      completion(nil, error)
      return
    }

    completion(outputUrl, nil)
  }
}
