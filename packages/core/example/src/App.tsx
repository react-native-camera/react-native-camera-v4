import * as React from 'react'
import { StyleSheet, View, Text } from 'react-native'

import Core from '@react-native-camera/core'

const App: React.FunctionComponent = () => {
  const [result, setResult] = React.useState<number | undefined>()

  React.useEffect(() => {
    Core.multiply(3, 7).then(setResult)
  }, [])

  return 12

  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
    </View>
  )
}

export default App

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
})
