import * as React from 'react'
import { StyleSheet, View, ViewStyle } from 'react-native'

import { Camera } from '@react-native-camera/core'

import { MainButton } from './MainButton'
import { FlashButton } from './FlashButton'

const App: React.FunctionComponent = () => {
  const [flashEnabled, setFlashEnabled] = React.useState(false)
  return (
    <>
      <Camera
        style={styles.camera}
        androidCameraPermissionOptions={{
          title: 'Camera permissions',
          message: 'Grant permissions to use Camera',
          buttonPositive: 'Grant',
        }}
        androidRecordAudioPermissionOptions={{
          title: 'Record audio permissions',
          message: 'Grant permissions to record audio',
          buttonPositive: 'Grant',
        }}
        flashMode={flashEnabled ? 'torch' : 'off'}
        onCameraReady={React.useCallback(() => {
          console.log('Camera ready')
        }, [])}
      />
      <View style={styles.controls}>
        <View style={styles.subControlsLeft}>
          <FlashButton
            flashEnabled={flashEnabled}
            onPress={React.useCallback(
              () => setFlashEnabled((prev) => !prev),
              []
            )}
          />
        </View>
        <MainButton />
        <View style={styles.subControlsRight} />
      </View>
    </>
  )
}

const styles = StyleSheet.create<{
  camera: ViewStyle
  controls: ViewStyle
  subControlsLeft: ViewStyle
  subControlsRight: ViewStyle
}>({
  camera: {
    flex: 1,
  },
  controls: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(0,0,0,0.5)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 50,
  },
  subControlsLeft: {
    flex: 1,
    alignItems: 'flex-end',
    paddingRight: 50,
  },
  subControlsRight: {
    flex: 1,
    alignItems: 'flex-start',
    paddingLeft: 50,
  },
})

export default App
