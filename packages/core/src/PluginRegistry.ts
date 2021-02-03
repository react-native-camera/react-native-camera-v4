import { createContext } from 'react'

export type PluginsChangedCallback = (plugins: string[]) => void
export type CameraIdChangedCallback = (cameraId: number | undefined) => void
export class PluginRegistry {
  plugins: string[] = []

  constructor(onPluginsChanged: PluginsChangedCallback) {
    this.callback = onPluginsChanged
  }

  setCameraId(cameraViewId: number | undefined): void {
    this.cameraViewId = cameraViewId

    if (!this.enabled) {
      this.enabled = true
      if (this.plugins.length) {
        this.emitPlugins()
      }
    }

    this.cameraViewIdCallback?.(this.cameraViewId)
  }

  addPlugin(plugin: string): void {
    if (!this.plugins.includes(plugin)) {
      this.plugins = [...this.plugins, plugin]
      this.emitPlugins()
    }
  }

  removePlugin(plugin: string): void {
    if (this.plugins.includes(plugin)) {
      this.plugins = this.plugins.filter((p) => p !== plugin)
      this.emitPlugins()
    }
  }

  subscribeToCameraViewId(callback: CameraIdChangedCallback): () => void {
    this.cameraViewIdCallback = callback
    callback(this.cameraViewId)

    return () => {
      this.cameraViewIdCallback = undefined
    }
  }

  private cameraViewIdCallback: CameraIdChangedCallback | undefined
  private callback: PluginsChangedCallback
  private cameraViewId: number | undefined
  private enabled = false

  private emitPlugins() {
    if (this.enabled) {
      this.callback(this.plugins)
    }
  }
}

export const PluginRegistryContext = createContext<PluginRegistry | undefined>(
  undefined
)
