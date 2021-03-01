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
  let imageType: ImageType?
  let quality: Float?
  let id: String?
}

struct TakePictureResult {
  var uri: String?
  var base64: String?
  var exif: Dictionary<String, Any>?
  var deviceOrientation: UIInterfaceOrientation?
  let pictureOrientation: AVCaptureVideoOrientation
  let width: CGFloat
  let height: CGFloat
}

enum InvalidTakePictureOptionsError: Error {
  case runtimeError(String)
}

enum TakePictureError: Error {
  case runtimeError(String)
}

func parseTakePictureOptions(_ options: NSDictionary) throws -> TakePictureOptions {
  var orientation: AVCaptureVideoOrientation?
  if let orientationOption = options["orientation"] as? Int {
    orientation = AVCaptureVideoOrientation.init(rawValue: orientationOption)
    if (orientation == nil) {
      throw InvalidTakePictureOptionsError.runtimeError("takePicture orientation option is wrong, unrecognized AVCaptureDeviceOrientation \(orientationOption)")
    }
  }
  
  var path: URL?
  if let pathOption = options["path"] as? String {
    path = parsePath(pathOption)
    if (path == nil) {
      throw InvalidTakePictureOptionsError.runtimeError("Could not parse \(pathOption) as an URL. You must provide an already valid URL or a file system path")
    }
  }
  
  var imageType: ImageType?
  if let imageTypeOption = options["imageType"] as? Int {
    imageType = ImageType(rawValue: imageTypeOption)
    if (imageType == nil) {
      throw InvalidTakePictureOptionsError.runtimeError("takePicture imageType option is wrong, unrecognized ImageType \(imageTypeOption)")
    }
  }
  
  var result = TakePictureOptions(
    width: options["width"] as? Int,
    path: path,
    orientation: orientation,
    imageType: imageType,
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

func takePictureResultToNSDictionary(_ result: TakePictureResult) -> NSMutableDictionary {
  let dictionary = NSMutableDictionary()
  
  dictionary["width"] = result.width
  dictionary["height"] = result.height
  dictionary["pictureOrientation"] = result.pictureOrientation.rawValue
  
  if let uri = result.uri {
    dictionary["uri"] = uri
  }
  
  if let base64 = result.base64 {
    dictionary["base64"] = base64
  }
  
  if let exif = result.exif {
    dictionary["exif"] = exif
  }
  
  if let deviceOrientation = result.deviceOrientation {
    dictionary["deviceOrientation"] = deviceOrientation.rawValue
  }
  
  return dictionary
}

func takePictureOutputPath(pathOption: URL?, imageExtension: String) -> URL? {
  return pathOption ?? generatePathInDirectory(cacheDirectoryPath.appending("Camera"), withExtension: imageExtension)
}

func generateMockPhoto(_ size: CGSize) -> UIImage? {
  let rect = CGRect(x: 0, y: 0, width: size.width, height: size.height)
  UIGraphicsBeginImageContextWithOptions(size, true, 0)
  let color = UIColor.black
  color.setFill()
  UIRectFill(rect)
  let dateFormatter = DateFormatter()
  dateFormatter.dateFormat = "dd.MM.YY HH:mm:ss"
  let text = dateFormatter.string(from: Date()) as NSString
  text.draw(
    at: CGPoint(x: size.width * 0.1, y: size.height * 0.9),
    withAttributes: [
      .font: UIFont.systemFont(ofSize: 18),
      .foregroundColor: UIColor.orange
    ]
  )
  let image = UIGraphicsGetImageFromCurrentImageContext()
  UIGraphicsEndImageContext()
  return image
}
