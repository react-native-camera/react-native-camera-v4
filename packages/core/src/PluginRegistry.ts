import { createContext } from 'react'
import { EventEmitter } from 'react-native'

export class PluginRegistry extends EventEmitter {
  static PLUGINS_CHANGED_EVENT_NAME = 'pluginsChanged'

  plugins: string[] = []
  cameraViewId: number | undefined

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

  private emitPlugins() {
    this.emit(PluginRegistry.PLUGINS_CHANGED_EVENT_NAME, this.plugins)
  }
}

export const PluginRegistryContext = createContext<PluginRegistry | undefined>(
  undefined
)
