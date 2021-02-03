import { FunctionComponent, useEffect } from 'react'
import { NativeEventEmitter, NativeModules } from 'react-native'

import {
  useCameraViewId,
  useRegisterCameraPlugin,
} from '@react-native-camera/core'

import {
  BarcodeNativeModule,
  BarcodePluginProps,
  BarcodeReadResult,
} from './types'

const BarcodeModule: BarcodeNativeModule = NativeModules.RNCameraBarcode

const PLUGIN_NAME = 'barcode'

export const BarcodePlugin: FunctionComponent<BarcodePluginProps> = ({
  onBarCodeRead,
  ...options
}) => {
  const cameraId = useCameraViewId()
  const registerPlugin = useRegisterCameraPlugin(PLUGIN_NAME)

  useEffect(() => {
    if (cameraId) {
      BarcodeModule.setOptions(cameraId, options)
      registerPlugin()
    }
  }, [cameraId, options, registerPlugin])

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.RNCameraBarcode)

    const sub = eventEmitter.addListener(
      'onBarCodeRead',
      (result: BarcodeReadResult) => {
        if (cameraId === result.viewId) {
          onBarCodeRead(result, () =>
            NativeModules.RNCameraBarcode.resume(result.viewId)
          )
        }
      }
    )

    return () => eventEmitter.removeSubscription(sub)
  }, [cameraId, onBarCodeRead])

  return null
}
