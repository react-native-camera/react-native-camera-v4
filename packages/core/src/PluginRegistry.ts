import { createContext } from 'react'

export type PluginsChangedCallback = (plugins: string[]) => void
export class PluginRegistry {
  plugins: string[] = []
  cameraViewId: number | undefined

  constructor(onPluginsChanged: PluginsChangedCallback) {
    this.callback = onPluginsChanged
  }

  enable(): void {
    this.enabled = true
    if (this.plugins.length) {
      this.emitPlugins()
    }
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

  private callback: PluginsChangedCallback
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
