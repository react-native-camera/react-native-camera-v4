import { FpsRange } from './camera'

export interface AndroidCameraManager {
  checkRecordAudioAuthorizationStatus(): Promise<boolean>
  checkIfRecordAudioPermissionsAreDefined(): Promise<boolean>
  getSupportedPreviewFpsRange(cameraHandle: number): Promise<FpsRange[]>
}
