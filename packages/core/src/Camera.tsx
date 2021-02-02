import React, {
  FunctionComponent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'

import { PluginRegistry, PluginRegistryContext } from './PluginRegistry'
import RNCamera from './RNCamera'
import { CameraProps } from './types'

export const Camera: FunctionComponent<CameraProps> = ({
  children,
  ...props
}) => {
  const [plugins, setPlugins] = useState<string[]>([])
  const [cameraViewId, setCameraViewId] = useState<number | undefined>()
  const { current: pluginRegistry } = useRef(new PluginRegistry(setPlugins))

  useEffect(() => {
    if (cameraViewId) {
      pluginRegistry.enable()
    }
  }, [cameraViewId, pluginRegistry])

  return (
    <PluginRegistryContext.Provider value={pluginRegistry}>
      <RNCamera
        {...props}
        plugins={plugins}
        onCameraViewId={useCallback(
          (cameraViewId: number) => setCameraViewId(cameraViewId),
          []
        )}
      >
        {children}
      </RNCamera>
    </PluginRegistryContext.Provider>
  )
}
