import Foundation
import AVFoundation

struct TakePictureOptions {
  var pauseAfterCapture: Bool = false
  var fastMode: Bool = false
  var mirrorImage: Bool = false
  var forceUpOrientation: Bool = false
  var doNotSave: Bool = false
  var base64: Bool = false
  var exif: Bool = true
  var writeExif: Bool = true
  var exifValues: Dictionary<String, Any>?
  let width: Int?
  let path: URL?
  let orientation: AVCaptureVideoOrientation?
  let imageType: String?
  let quality: Float?
  let id: String?
}

enum InvalidTakePictureOptionsError: Error {
  case runtimeError(String)
}

func parseTakePictureOptions(_ options: NSDictionary) throws -> TakePictureOptions {
  var orientation: AVCaptureVideoOrientation?
  
  if let orientationOption = options["orientation"] as? Int {
    orientation = AVCaptureVideoOrientation.init(rawValue: orientationOption)
    if (orientation == nil) {
      throw InvalidTakePictureOptionsError.runtimeError("takePciture orientation option is wrong, unrecognized AVCaptureDeviceOrientation \(orientationOption)")
    }
  }
  
  var path: URL?
  if let pathOption = options["path"] as? String {
    // Handle string URLs
    path = URL(string: pathOption)
    if (path == nil) {
      // Handle file system paths
      path = URL(fileURLWithPath: pathOption)
    }
    if (path == nil) {
      throw InvalidTakePictureOptionsError.runtimeError("Could not parse \(pathOption) as an URL. You must provide an already valid URL or a file system path")
    }
  }
  
  var result = TakePictureOptions(
    width: options["width"] as? Int,
    path: path,
    orientation: orientation,
    imageType: options["imageType"] as? String,
    quality: options["quality"] as? Float,
    id: options["id"] as? String
  )
  
  if let pauseAfterCapture = options["pauseAfterCapture"] as? Bool {
    result.pauseAfterCapture = pauseAfterCapture
  }
  
  if let fastMode = options["fastMode"] as? Bool {
    result.fastMode = fastMode
  }
  
  if let mirrorImage = options["mirrorImage"] as? Bool {
    result.mirrorImage = mirrorImage
  }
  
  if let forceUpOrientation = options["forceUpOrientation"] as? Bool {
    result.forceUpOrientation = forceUpOrientation
  }
  
  if let doNotSave = options["doNotSave"] as? Bool {
    result.doNotSave = doNotSave
  }
  
  if let base64 = options["base64"] as? Bool {
    result.base64 = base64
  }
  
  if let exif = options["exif"] as? Bool {
    result.exif = exif
  }
  
  if let writeExifDictionary = options["writeExif"] as? NSDictionary {
    var dict = Dictionary<String, Any>()
    writeExifDictionary.forEach { key, value in
      if let keyString = key as? String {
        dict[keyString] = value
      }
    }
    result.exifValues = dict
  } else if let writeExif = options["writeExif"] as? Bool {
    result.writeExif = writeExif
  }
  
  return result
}
