import { NativeModules, Platform } from 'react-native'

import { NativeCameraManager } from './types'

export const CameraManager: NativeCameraManager =
  NativeModules.RNCameraManager || NativeModules.RNCameraModule

if (!CameraManager) {
  throw new Error(
    'Cannot find native camera module. Did you forget to install or resintall your dependencies?' +
      (Platform.OS === 'ios' ? '. Or maybe run pod install?' : '') +
      '. Remember to rebuild your app whenever your native depdendencies change'
  )
}
