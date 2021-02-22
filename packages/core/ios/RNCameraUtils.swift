import Foundation
import AVFoundation

// These log helpers are needed since RCTLogXX are objc macros not available for swift
func rctLogWarn(_ message: String) {
  RCTDefaultLogFunction(
    RCTLogLevel.warning,
    RCTLogSource.native,
    nil,
    nil,
    message
  )
}
func rctLogError(_ message: String) {
  RCTDefaultLogFunction(
    RCTLogLevel.error,
    RCTLogSource.native,
    nil,
    nil,
    message
  )
}
