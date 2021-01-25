package com.reactnativecamera.barcode

import com.reactnativecamera.Plugin
import com.reactnativecamera.PluginManager
import com.reactnativecamera.view.RNCameraView

class BarcodePluginManager : PluginManager<BarcodePlugin>() {
  override val name: String
    get() = NAME

  override fun createPlugin(view: RNCameraView): BarcodePlugin {
    val viewId = view.id
    val pluginOptions = mOptions[view.id] ?: throw Error("Missing options for barcode plugin for view $viewId")
    return BarcodePlugin(pluginOptions, view)
  }

  override fun detachCameraInstance(viewId: Int) {
    super.detachCameraInstance(viewId)
    mOptions.remove(viewId)
  }

  fun setOptions(viewId: Int, options: BarcodeOptions) {
    mOptions[viewId] = options
    val plugin = attachedCameraInstances[viewId] ?: return
    plugin.rectOfInterest = options.rectOfInterest
    plugin.setBarCodeTypes(options.barcodeTypes)
  }

  private var mOptions = mutableMapOf<Int, BarcodeOptions>()

  companion object {
    const val NAME = "barcode"
  }
}
