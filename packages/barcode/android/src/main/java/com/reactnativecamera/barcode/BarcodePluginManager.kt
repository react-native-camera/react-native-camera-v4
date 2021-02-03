package com.reactnativecamera.barcode

import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativecamera.PluginManager
import com.reactnativecamera.view.RNCameraView

class BarcodePluginManager(private val reactContext: ReactApplicationContext) : PluginManager<BarcodePlugin>() {
  override val name: String
    get() = NAME

  override fun createPlugin(view: RNCameraView): BarcodePlugin {
    val viewId = view.id
    val pluginOptions = mOptions[view.id] ?: throw Error("Missing options for barcode plugin for view $viewId")
    return BarcodePlugin(pluginOptions, view, reactContext)
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

    val disabled = options.disabled ?: false

    if (disabled && plugin.scanning) {
      plugin.stop()
    }

    if (!disabled && !plugin.scanning){
      plugin.resume()
    }
  }

  fun resume(viewId: Int) {
    val plugin = attachedCameraInstances[viewId] ?: return
    val disabled = mOptions[viewId]?.disabled ?: false
    if (!disabled) {
      plugin.resume()
    }
  }

  private var mOptions = mutableMapOf<Int, BarcodeOptions>()

  companion object {
    const val NAME = "barcode"
  }
}
