import { PermissionsAndroid, Platform, Rationale } from 'react-native'

import { CameraManager } from './native'
import {
  AndroidCameraManager,
  IOSCameraManager,
  WindowsCameraManager,
} from './types'

export enum RecordAudioPermissionStatus {
  AUTHORIZED = 'AUTHORIZED',
  PENDING_AUTHORIZATION = 'PENDING_AUTHORIZATION',
  NOT_AUTHORIZED = 'NOT_AUTHORIZED',
}

export enum CameraPermissionStatus {
  READY = 'READY',
  PENDING_AUTHORIZATION = 'PENDING_AUTHORIZATION',
  NOT_AUTHORIZED = 'NOT_AUTHORIZED',
}

export interface CameraPermissionsStatus {
  hasCameraPermissions: boolean
  hasRecordAudioPermissions: boolean
}

export const requestPermissions = async (
  captureAudio: boolean,
  androidCameraPermissionOptions?: Rationale,
  androidRecordAudioPermissionOptions?: Rationale
): Promise<CameraPermissionsStatus> => {
  let hasCameraPermissions = false
  let hasRecordAudioPermissions = false

  const androidCameraManager =
    Platform.OS === 'android'
      ? (CameraManager as AndroidCameraManager)
      : undefined

  const iosCameraManager =
    Platform.OS === 'ios' ? (CameraManager as IOSCameraManager) : undefined

  const windowsCameraManager =
    Platform.OS === 'windows'
      ? (CameraManager as WindowsCameraManager)
      : undefined

  if (iosCameraManager) {
    hasCameraPermissions = await iosCameraManager.checkVideoAuthorizationStatus()
  } else if (androidCameraManager) {
    const cameraPermissionResult = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA,
      androidCameraPermissionOptions
    )
    if (typeof cameraPermissionResult === 'boolean') {
      hasCameraPermissions = cameraPermissionResult
    } else {
      hasCameraPermissions =
        cameraPermissionResult === PermissionsAndroid.RESULTS.GRANTED
    }
  } else if (windowsCameraManager) {
    hasCameraPermissions = await windowsCameraManager.checkMediaCapturePermission()
  }

  if (captureAudio) {
    if (iosCameraManager) {
      hasRecordAudioPermissions = await iosCameraManager.checkRecordAudioAuthorizationStatus()
    } else if (androidCameraManager) {
      if (
        await androidCameraManager.checkIfRecordAudioPermissionsAreDefined()
      ) {
        const audioPermissionResult = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          androidRecordAudioPermissionOptions
        )
        if (typeof audioPermissionResult === 'boolean') {
          hasRecordAudioPermissions = audioPermissionResult
        } else {
          hasRecordAudioPermissions =
            audioPermissionResult === PermissionsAndroid.RESULTS.GRANTED
        }
      } else if (__DEV__) {
        // eslint-disable-next-line no-console
        console.warn(
          `The 'captureAudio' property set on RNCamera instance but 'RECORD_AUDIO' permissions not defined in the applications 'AndroidManifest.xml'. ` +
            `If you want to record audio you will have to add '<uses-permission android:name="android.permission.RECORD_AUDIO"/>' to your 'AndroidManifest.xml'. ` +
            `Otherwise you should set the 'captureAudio' property on the component instance to 'false'.`
        )
      }
    } else if (windowsCameraManager) {
      hasRecordAudioPermissions = await windowsCameraManager.checkMediaCapturePermission()
    }
  }

  return {
    hasCameraPermissions,
    hasRecordAudioPermissions,
  }
}
