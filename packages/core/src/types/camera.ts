import { NativeCameraManager } from './native'

export type Orientation =
  | 'auto'
  | 'landscapeLeft'
  | 'landscapeRight'
  | 'portrait'
  | 'portraitUpsideDown'

export type OrientationNumber = 1 | 2 | 3 | 4

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Exif = { [name: string]: any }

export interface FpsRange {
  MAXIMUM_FPS: number
  MINIMUM_FPS: number
}

export interface PictureOptions {
  quality?: number
  orientation?: Orientation | OrientationNumber
  base64?: boolean
  mirrorImage?: boolean
  exif?: boolean
  writeExif?: boolean | Exif
  width?: number
  fixOrientation?: boolean
  forceUpOrientation?: boolean
  pauseAfterCapture?: boolean
}

export interface RecordingOptions {
  maxDuration?: number
  maxFileSize?: number
  orientation?: Orientation
  quality?: keyof NativeCameraManager['VideoQuality']
  fps?: number
  codec?: string
  mute?: boolean
  path?: string
  videoBitrate?: number
}

export interface ParsedRecordingOptions
  extends Omit<RecordingOptions, 'quality' | 'orientation'> {
  quality?: number
  orientation?: number
}

export interface WhiteBalanceSettings {
  temperature: number
  tint: number
  redGainOffset?: number
  greenGainOffset?: number
  blueGainOffset?: number
}
