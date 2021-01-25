package com.reactnativecamera

import com.reactnativecamera.view.RNCameraView

abstract class PluginManager<TPlugin : Plugin> {
  abstract val name: String

  open fun attachCameraInstance(view: RNCameraView): TPlugin {
    val plugin = createPlugin(view)
    mAttachedCameraInstances[view.id] = plugin
    return plugin
  }

  open fun detachCameraInstance(viewId: Int) {
    mAttachedCameraInstances.remove(viewId)
  }

  val attachedCameraInstances: Map<Int, TPlugin>
    get() = mAttachedCameraInstances

  protected abstract fun createPlugin(view: RNCameraView): TPlugin

  private var mAttachedCameraInstances = mutableMapOf<Int, TPlugin>()
}
