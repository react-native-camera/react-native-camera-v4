package com.reactnativecamera.barcode

import com.facebook.react.bridge.*

class BarcodeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return "RNCameraBarcode"
  }

  @ReactMethod
  fun setOptions(viewId: Int, options: ReadableMap) {
    val barcodeTypes = mutableListOf<String>()
    val rawBarcodeTypes = options.getArray("barcodeTypes")

    if (rawBarcodeTypes != null) {
      for (i in 0 until rawBarcodeTypes.size()) {
        val barcodeType = rawBarcodeTypes.getString(i)
        if (barcodeType != null) {
          barcodeTypes.add(barcodeType)
        }
      }
    }

    val rawRectOfInterestOption = options.getMap("rectOfInterest")
    var rectOfInterest: RectOfInterest? = null

    if (rawRectOfInterestOption != null) {
      val x = rawRectOfInterestOption.getDouble("x").toInt()
      val y = rawRectOfInterestOption.getDouble("y").toInt()
      val width = rawRectOfInterestOption.getDouble("width").toInt()
      val height = rawRectOfInterestOption.getDouble("height").toInt()
      rectOfInterest = RectOfInterest(x, y, width, height)
    }

    var disabled = false

    try {
      disabled = options.getBoolean("disabled")
    } catch (e: Throwable) {}

    manager.setOptions(viewId, BarcodeOptions(barcodeTypes, rectOfInterest, disabled))
  }

  @ReactMethod
  fun resume(viewId: Int) {
    manager.resume(viewId)
  }

  val manager = BarcodePluginManager(reactContext)
}
