import { AndroidCameraManager } from './android'
import { ParsedRecordingOptions, PictureOptions } from './camera'
import { PictureSavedEvent, VideoRecordedEvent } from './events'
import { IOSCameraManager, IOSVideStabilizationModes } from './ios'
import { WindowsCameraManager } from './windows'

export interface NativeCameraManagerConstants {
  Type: {
    front: number
    back: number
  }
  FlashMode: {
    off: number
    on: number
    auto: number
  }
  AutoFocus: {
    on: boolean | number
    off: boolean | number
  }
  WhiteBalance: {
    auto: number
    cloudy: number
    sunny: number
    shadow: number
    fluorescent: number
    incandescent: number
  }
  VideoQuality: {
    '2160p': number
    '1080p': number
    '720p': number
    '480p': number
    '4:3': number
  }
  Orientation: {
    auto: number
    portrait: number
    portraitUpsideDown: number
    landscapeLeft: number
    landscapeRight: number
  }
  // iOS only
  VideoStabilizationModes?: IOSVideStabilizationModes
}

export interface NativeCameraManagerMethods {
  takePicture(
    options: PictureOptions,
    cameraHandle: number
  ): Promise<PictureSavedEvent>
  getSupportedRatios(cameraHandle: number): Promise<string[]>
  getCameraIds(cameraHandle: number): Promise<string[]>
  getAvailablePictureSizes(
    ratio: string,
    cameraHandle: number
  ): Promise<string[]>
  record(
    options: ParsedRecordingOptions,
    cameraHandle: number
  ): Promise<VideoRecordedEvent>
  stopRecording(cameraHandle: number): void
  pauseRecording(cameraHandle: number): void
  resumeRecording(cameraHandle: number): void
  pausePreview(cameraHandle: number): void
  resumePreview(cameraHandle: number): void
  isRecording(cameraHandle: number): boolean
}

export type NativeCameraManager = NativeCameraManagerConstants &
  NativeCameraManagerMethods &
  (IOSCameraManager | AndroidCameraManager | WindowsCameraManager)
