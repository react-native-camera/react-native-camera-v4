import Foundation
import AVFoundation

struct RecordOptions {
  let orientation: AVCaptureVideoOrientation?
  let quality: AVCaptureSession.Preset
  let mute: Bool
  let maxDuration: Double?
  let maxFileSize: Int64?
  let fps: Int?
  let codec: AVVideoCodecType?
  let videoBitrate: String?
  let path: URL?
  let mirrorVideo: Bool
}

struct PartialRecordResult {
  let viewTag: NSNumber
  let videoOrientation: AVCaptureVideoOrientation
  let deviceOrientation: UIInterfaceOrientation
}

struct RecordResult {
  var uri: String
  let videoOrientation: AVCaptureVideoOrientation
  let deviceOrientation: UIInterfaceOrientation
  let isRecordingInterrupted: Bool
  let codec: AVVideoCodecType?
}

enum RecordError: Error {
  case runtimeError(String)
}

enum RecordInvalidOptionsError: Error {
  case runtimeError(String)
}

func parseRecordOptions(_ options: NSDictionary) throws -> RecordOptions {
  var orientation: AVCaptureVideoOrientation? = nil
  
  if let orientationOption = options["orientation"] as? Int {
    orientation = AVCaptureVideoOrientation.init(rawValue: orientationOption)
    if (orientation == nil) {
      throw RecordInvalidOptionsError.runtimeError("record orientation option is wrong, unrecognized AVCaptureDeviceOrientation \(orientationOption)")
    }
  }
  
  var quality: AVCaptureSession.Preset? = nil
  
  if let qualityOption = options["quality"] as? String {
    quality = AVCaptureSession.Preset(rawValue: qualityOption)
    if (quality == nil) {
      throw RecordInvalidOptionsError.runtimeError("record quality option is wrong, unrecognized AVCaptureSession.Preset \(qualityOption)")
    }
  }
  
  var codec: AVVideoCodecType? = nil
  
  if let codecOption = options["codec"] as? String {
    codec = AVVideoCodecType(rawValue: codecOption)
    if (codec == nil) {
      throw RecordInvalidOptionsError.runtimeError("record codec option is wrong, unrecognized AVVideoCodecType \(codecOption)")
    }
  }
  
  var path: URL?
  if let pathOption = options["path"] as? String {
    path = parsePath(pathOption)
    if (path == nil) {
      throw RecordInvalidOptionsError.runtimeError("Could not parse \(pathOption) as an URL. You must provide an already valid URL or a file system path")
    }
  }
  
  return RecordOptions(
    orientation: orientation,
    quality: quality ?? .high,
    mute: options["mute"] as? Bool ?? false,
    maxDuration: options["maxDuration"] as? Double,
    maxFileSize: options["maxFileSize"] as? Int64,
    fps: options["fps"] as? Int,
    codec: codec,
    videoBitrate: options["videoBitrate"] as? String,
    path: path,
    mirrorVideo: options["mirrorVideo"] as? Bool ?? false
  )
}

func recordResultToNSDictionary(_ result: RecordResult) -> NSDictionary {
  let dictionary = NSMutableDictionary()
  
  dictionary["uri"] = result.uri
  dictionary["videoOrientation"] = result.videoOrientation.rawValue
  dictionary["deviceOrientation"] = result.deviceOrientation.rawValue
  dictionary["isRecordingInterrupted"] = result.isRecordingInterrupted
  
  if let codec = result.codec {
    dictionary["codec"] = codec
  }
  
  return dictionary
}
