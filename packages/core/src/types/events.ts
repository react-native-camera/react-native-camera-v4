import { Exif, OrientationNumber } from './camera'
import { Point } from './geometry'

export interface PictureSavedEvent {
  deviceOrientation: OrientationNumber
  pictureOrientation: OrientationNumber
  width: number
  height: number
  uri?: string
  base64?: string
  exif?: Exif
}

export interface RecordingStartEvent {
  deviceOrientation: OrientationNumber
  videoOrientation: OrientationNumber
  uri: string
}

export interface VideoRecordedEvent extends RecordingStartEvent {
  isRecordingInterrupted: boolean
}

export interface SubjectAreaChangedEvent {
  prevPoint: Point
}
