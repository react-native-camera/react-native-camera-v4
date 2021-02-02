import React, { FunctionComponent } from 'react'
import { StyleSheet, TouchableOpacity, View, ViewStyle } from 'react-native'

export const MainButton: FunctionComponent = () => {
  return (
    <TouchableOpacity style={styles.container}>
      <View style={styles.inner} />
    </TouchableOpacity>
  )
}

const styles = StyleSheet.create<{
  container: ViewStyle
  inner: ViewStyle
}>({
  container: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#EEE',
    justifyContent: 'center',
    alignItems: 'center',
  },
  inner: {
    width: 50,
    height: 50,
    borderRadius: 25,
    borderColor: '#CCC',
    borderWidth: 2,
  },
})
