import Foundation

func ensureDirExists(_ path: String) -> Bool {
  var isDir: ObjCBool = false
  let exists = FileManager.default.fileExists(atPath: path, isDirectory: &isDir)
  if (!exists || !isDir.boolValue) {
    do {
      try FileManager.default.createDirectory(atPath: path, withIntermediateDirectories: true, attributes: nil)
    } catch {
      return false
    }
  }
  return true
}

func generatePathInDirectory(_ directory: String, withExtension: String) -> URL? {
  let fileName = UUID().uuidString.appending(withExtension)
  if (!ensureDirExists(directory)) {
    return nil
  }
  var directoryUrl = URL(fileURLWithPath: directory, isDirectory: true)
  directoryUrl.appendPathComponent(fileName)
  return directoryUrl
}

var cacheDirectoryPath: String {
  get { return NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true)[0] }
}
