import { Rationale, ViewProps } from 'react-native'

import {
  CameraPermissionStatus,
  RecordAudioPermissionStatus,
} from '../permissions'

import { WhiteBalanceSettings } from './camera'
import {
  PictureSavedEvent,
  RecordingStartEvent,
  SubjectAreaChangedEvent,
  VideoRecordedEvent,
} from './events'
import { Point, Rect } from './geometry'
import { IOSVideStabilizationModes } from './ios'
import { NativeCameraManagerConstants } from './native'

export interface CameraProps extends ViewProps {
  androidCameraPermissionOptions: Rationale
  androidRecordAudioPermissionOptions: Rationale
  zoom?: number
  useNativeZoom?: boolean
  maxZoom?: number
  ratio?: string
  focusDepth?: number
  type?: keyof NativeCameraManagerConstants['Type']
  flashMode?: keyof NativeCameraManagerConstants['FlashMode']
  videoStabilizationMode?: keyof IOSVideStabilizationModes
  cameraId?: string
  onCameraReady?: () => void
  onAudioInterrupted?: () => void
  onAudioConnected?: () => void
  onMountError?: (error: string) => void
  onPictureTaken?: () => void
  onPictureSaved?: (event: PictureSavedEvent) => void
  onRecordingStart?: (event: RecordingStartEvent) => void
  onRecordingEnd?: () => void
  onVideoRecorded?: (event: VideoRecordedEvent) => void
  onTap?: (point: Point) => void
  onDoubleTap?: (point: Point) => void
  onSubjectAreaChanged?: (event: SubjectAreaChangedEvent) => void
  onStatusChange?: (status: {
    cameraStatus: CameraPermissionStatus
    recordAudioPermissionStatus: RecordAudioPermissionStatus
  }) => void
  exposure?: number
  barCodeTypes?: Array<string>
  // Custom white balance settings are supported in iOS only
  whiteBalance?:
    | keyof NativeCameraManagerConstants['WhiteBalance']
    | WhiteBalanceSettings
  autoFocus?: boolean
  autoFocusPointOfInterest?: { x: number; y: number }
  captureAudio?: boolean
  keepAudioSession?: boolean
  useCamera2Api?: boolean
  playSoundOnCapture?: boolean
  playSoundOnRecord?: boolean
  pictureSize?: string
  rectOfInterest?: Rect
  mirrorVideo?: boolean
}

export interface ParsedCameraProps
  extends Omit<
    CameraProps,
    | 'type'
    | 'flashMode'
    | 'autoFocus'
    | 'whiteBalance'
    | 'videoStabilizationMode'
  > {
  type?: number
  flashMode?: number
  autoFocus?: boolean | number
  whiteBalance?: number | WhiteBalanceSettings
  videoStabilizationMode?: number
  touchDetectorEnabled: boolean
}
