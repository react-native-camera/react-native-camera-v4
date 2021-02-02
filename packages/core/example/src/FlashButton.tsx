import React, { FunctionComponent } from 'react'
import {
  StyleSheet,
  TextStyle,
  TouchableOpacity,
  TouchableOpacityProps,
  ViewStyle,
} from 'react-native'
import MaterialIcon from 'react-native-vector-icons/MaterialIcons'

export interface FlashButtonProps extends TouchableOpacityProps {
  flashEnabled: boolean
}

export const FlashButton: FunctionComponent<FlashButtonProps> = ({
  flashEnabled,
  ...props
}) => {
  return (
    <TouchableOpacity {...props} style={styles.container}>
      <MaterialIcon
        style={styles.icon}
        name={`flash-${flashEnabled ? 'on' : 'off'}`}
        color="#FFF"
      />
    </TouchableOpacity>
  )
}

const styles = StyleSheet.create<{ container: ViewStyle; icon: TextStyle }>({
  container: {},
  icon: {
    fontSize: 30,
  },
})
