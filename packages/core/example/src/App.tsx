import * as React from 'react'

import { Camera } from '@react-native-camera/core'

const App: React.FunctionComponent = () => {
  return (
    <Camera
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
    />
  )
}

export default App
