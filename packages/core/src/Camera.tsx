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
  const { current: pluginRegistry } = useRef(new PluginRegistry())

  useEffect(() => {
    if (cameraViewId) {
      const listener = (plugins: string[]) => setPlugins(plugins)
      pluginRegistry.addListener(
        PluginRegistry.PLUGINS_CHANGED_EVENT_NAME,
        listener
      )
      return () =>
        pluginRegistry.removeListener(
          PluginRegistry.PLUGINS_CHANGED_EVENT_NAME,
          listener
        )
    }
  }, [cameraViewId, pluginRegistry])

  return (
    <PluginRegistryContext.Provider value={pluginRegistry}>
      <RNCamera
        plugins={plugins}
        onCameraViewId={useCallback(
          (cameraViewId: number) => setCameraViewId(cameraViewId),
          []
        )}
        {...props}
      >
        {children}
      </RNCamera>
    </PluginRegistryContext.Provider>
  )
}
