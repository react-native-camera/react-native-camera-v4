import * as React from 'react'
import { Alert, StyleSheet, ViewStyle } from 'react-native'

import { Camera } from '@react-native-camera/core'

import { BarcodePlugin, BarCodeType } from '../../src'

const BARCODE_TYPES: BarCodeType[] = ['qr']

const App: React.FunctionComponent = () => {
  return (
    <Camera
      style={styles.camera}
      androidCameraPermissionOptions={{
        title: 'Camera permissions',
        message: 'Grant permissions to use Camera',
        buttonPositive: 'Grant',
      }}
      captureAudio={false}
    >
      <BarcodePlugin
        barcodeTypes={BARCODE_TYPES}
        onBarCodeRead={React.useCallback((result, resume) => {
          Alert.alert(
            result.type,
            result.data,
            [{ text: 'Continue', onPress: resume }],
            {
              onDismiss: resume,
            }
          )
        }, [])}
      />
    </Camera>
  )
}

export default App

const styles = StyleSheet.create<{
  camera: ViewStyle
}>({
  camera: {
    flex: 1,
  },
})
