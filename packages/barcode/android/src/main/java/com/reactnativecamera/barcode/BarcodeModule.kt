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
      val x = rawRectOfInterestOption.getDouble("x").toFloat()
      val y = rawRectOfInterestOption.getDouble("y").toFloat()
      val width = rawRectOfInterestOption.getDouble("width").toFloat()
      val height = rawRectOfInterestOption.getDouble("height").toFloat()
      rectOfInterest = RectOfInterest(x, y, width, height)
    }

    manager.setOptions(viewId, BarcodeOptions(barcodeTypes, rectOfInterest))
  }

  val manager = BarcodePluginManager()
}
