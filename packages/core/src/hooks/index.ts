import { useCallback, useContext, useEffect, useState } from 'react'

import { PluginRegistry, PluginRegistryContext } from '../PluginRegistry'

const useCameraPluginRegistry = (): PluginRegistry => {
  const registry = useContext(PluginRegistryContext)

  if (!registry) {
    throw new Error(
      'No PluginRegistryContext found. You can only use a Plugin as a children of Camera. Also make sure you use the Camera component exported as Camera, do not import RNCamera manually as it does not include a plugin registry.'
    )
  }

  return registry
}

export const useRegisterCameraPlugin = (plugin: string): (() => void) => {
  const registry = useCameraPluginRegistry()

  return useCallback(() => {
    registry.addPlugin(plugin)
    return () => registry.removePlugin(plugin)
  }, [plugin, registry])
}

export const useCameraViewId = (): number | undefined => {
  const registry = useCameraPluginRegistry()
  const [cameraViewId, setCameraViewId] = useState<number | undefined>()

  useEffect(() => registry.subscribeToCameraViewId(setCameraViewId), [registry])

  return cameraViewId
}
