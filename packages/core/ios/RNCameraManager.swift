import Foundation

@objc(RNCameraManager)
class RNCameraManager : RCTViewManager, RNCameraDelegate {
  static let VIEW_NOT_FOUND_ERROR_CODE = "E_RNCAMERA_VIEW_NOT_FOUND"
  static let TAKE_PICTURE_FAILED_CODE = "E_IMAGE_CAPTURE_FAILED"
  static let RECORD_FAILED_CODE = "E_RECORDING_FAILED"
  
  
  override class func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func view() -> UIView! {
    let view = RNCamera()
    view.delegate = self
    return view
  }
  
  
  @objc(takePicture:options:resolve:reject:)
  func takePicture(
    _ node: NSNumber,
    options: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard let view = findView(node, reject: reject) else {
      return
    }
    
    let takePictureOptions: TakePictureOptions
    do {
      takePictureOptions = try parseTakePictureOptions(options)
    } catch {
      reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, error.localizedDescription, error)
      return
    }
    
    #if TARGET_IPHONE_SIMULATOR
    mockedSimulatorPhoto(takePictureOptions: takePictureOptions, view: view, resolve: resolve, reject: reject)
    
    #else
    view.takePicture(takePictureOptions) { resultOrNil, errorOrNil in
      
      if let error = errorOrNil {
        reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, error.localizedDescription, error)
        return
      }
      
      if let result = resultOrNil {
        resolve(takePictureResultToNSDictionary(result))
      } else {
        resolve(nil)
      }
    }
    #endif
  }
  
  @objc(record:options:resolve:reject:)
  func record(
    _ node: NSNumber,
    options: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    #if TARGET_IPHONE_SIMULATOR
    reject(RNCameraManager.RECORD_FAILED_CODE, "Video recording is not supported on a simulator.", nil)
    return
    #endif
    
    guard let view = findView(node, reject: reject) else { return }

    if recordPromises.keys.contains(node) {
      reject(RNCameraManager.RECORD_FAILED_CODE, "View \(node) already has an ongoing recording", nil)
      return
    }
    
    let recordOptions: RecordOptions
    do {
      try recordOptions = parseRecordOptions(options)
    } catch {
      reject(RNCameraManager.RECORD_FAILED_CODE, "Invalid record options: \(error.localizedDescription)", error)
      return
    }
    
    view.record(recordOptions, viewTag: node) { [self] errorOrNil in
      if let error = errorOrNil {
        reject(RNCameraManager.RECORD_FAILED_CODE, error.localizedDescription, error)
        return
      }
      
      recordPromises[node] = (resolve, reject)
    }
  }
  
  
  func recorded(viewTag: NSNumber, resultOrNil: RecordResult?, errorOrNil: Error?) {
    guard let (resolve, reject) = recordPromises[viewTag] else {
      rctLogWarn("View with tag \(viewTag) reported a record result but its promise was not found")
      return
    }
    
    recordPromises.removeValue(forKey: viewTag)
    
    if let error = errorOrNil {
      reject(RNCameraManager.RECORD_FAILED_CODE, error.localizedDescription, error)
    } else if let result = resultOrNil {
      resolve(recordResultToNSDictionary(result))
    } else {
      reject(RNCameraManager.RECORD_FAILED_CODE, "Empty record result with no error", nil)
    }
  }
  
  
  private var recordPromises = Dictionary<NSNumber, (RCTPromiseResolveBlock, RCTPromiseRejectBlock)>()
  
  
  private func findView(_ reactTag: NSNumber, reject: RCTPromiseRejectBlock) -> RNCamera? {
    guard let view = bridge.uiManager.view(forReactTag: reactTag) else {
      reject(RNCameraManager.VIEW_NOT_FOUND_ERROR_CODE, "Could not find view for tag \(reactTag)", nil)
      return nil
    }
    
    guard let camera = view as? RNCamera else {
      reject(RNCameraManager.VIEW_NOT_FOUND_ERROR_CODE, "View for tag \(reactTag) is not a RNCamera view", nil)
      return nil
    }
    
    return camera
  }
  
  private func mockedSimulatorPhoto(
    takePictureOptions: TakePictureOptions,
    view: RNCamera,
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    guard let path = takePictureOutputPath(pathOption: takePictureOptions.path, imageExtension: ".jpg") else {
      reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, "Unable to get path to save photo to", nil)
      return
    }
    guard let mock = generateMockPhoto(CGSize(width: 200, height: 200)) else {
      reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, "Unable to create mocked photo for iOS simulator", nil)
      return
    }
    if (takePictureOptions.fastMode) {
      resolve(nil)
    }
    if let handler = view.onPictureTaken {
      handler(nil)
    }
    let cgQuality: CGFloat
    if let quality = takePictureOptions.quality {
      cgQuality = CGFloat(quality)
    } else {
      cgQuality = 1
    }
    guard let photoData = mock.jpegData(compressionQuality: cgQuality) else {
      reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, "Unable to get JPEG data from mocked photo for iOS simulator", nil)
      return
    }

    let result = takePictureResultToNSDictionary(TakePictureResult(
      uri: nil,
      base64: nil,
      exif: nil,
      deviceOrientation: .portrait,
      pictureOrientation: .portrait,
      width: mock.size.width,
      height: mock.size.height
    ))

    if (!takePictureOptions.doNotSave) {
      do {
        try photoData.write(to: path)
      } catch {
        reject(RNCameraManager.TAKE_PICTURE_FAILED_CODE, "Unable to save mock photo for iOS simulator: \(error.localizedDescription)", error)
        return
      }
      result["uri"] = path.absoluteString
    }
    if (takePictureOptions.base64) {
      result["base64"] = photoData.base64EncodedString()
    }

    if (takePictureOptions.fastMode) {
      if let handler = view.onPictureSaved{
        var dict = [String: Any]()
        dict["data"] = result
        if let id = takePictureOptions.id {
          dict["id"] = id
        }
        handler(dict)
      }
    } else {
      resolve(result)
    }
  }
}
