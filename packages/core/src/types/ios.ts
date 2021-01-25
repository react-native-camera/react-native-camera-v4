export interface IOSVideStabilizationModes {
  off: number
  standard: number
  cinematic: number
  auto: number
}

export interface IOSCameraManager {
  checkVideoAuthorizationStatus(): Promise<boolean>
  checkRecordAudioAuthorizationStatus(): Promise<boolean>
  getIOSCameraIds(): Promise<string[]>
}
