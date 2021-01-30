import React, { FunctionComponent, useEffect } from 'react'
import { NativeModules } from 'react-native'

import {
  useCameraViewId,
  useRegisterCameraPlugin,
} from '@react-native-camera/core'

import { BarcodeNativeModule, BarcodeOptions } from './types'

const BarcodeModule: BarcodeNativeModule = NativeModules.RNCameraBarcode

const PLUGIN_NAME = 'barcode'

export const BarcodePlugin: FunctionComponent<BarcodeOptions> = (options) => {
  const cameraId = useCameraViewId()
  const registerPlugin = useRegisterCameraPlugin(PLUGIN_NAME)

  useEffect(() => {
    if (cameraId) {
      BarcodeModule.setOptions(cameraId, options)
      registerPlugin()
    }
  }, [cameraId, options, registerPlugin])

  return null
}
