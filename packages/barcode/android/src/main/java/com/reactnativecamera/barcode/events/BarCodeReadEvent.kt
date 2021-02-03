package com.reactnativecamera.barcode.events

import android.util.Base64
import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.reactnativecamera.barcode.Barcodes
import com.reactnativecamera.view.RNCameraViewManager
import java.util.*

class BarCodeReadEvent constructor(
  private var mBarCode: Result,
  private var mWidth: Int,
  private var mHeight: Int
) {
  val type = Barcodes.inverse(mBarCode.barcodeFormat)

  fun serializeEventData(viewTag: Int): WritableMap {
    val event = Arguments.createMap()
    val eventOrigin = Arguments.createMap()
    event.putInt("target", viewTag)
    event.putString("data", mBarCode.text)
    val rawBytes = mBarCode.rawBytes
    if (rawBytes != null && rawBytes.isNotEmpty()) {
      val formatter = Formatter()
      for (b in rawBytes) {
        formatter.format("%02x", b)
      }
      event.putString("rawData", formatter.toString())
      formatter.close()
    }


    event.putInt("viewId", viewTag)
    event.putString("type", mBarCode.barcodeFormat.toString())
    val resultPoints = Arguments.createArray()
    val points = mBarCode.resultPoints
    for (point in points) {
      if (point != null) {
        val newPoint = Arguments.createMap()
        newPoint.putString("x", point.x.toString())
        newPoint.putString("y", point.y.toString())
        resultPoints.pushMap(newPoint)
      }
    }
    eventOrigin.putArray("origin", resultPoints)
    eventOrigin.putInt("height", mHeight)
    eventOrigin.putInt("width", mWidth)
    event.putMap("bounds", eventOrigin)
    return event
  }

  companion object {
    const val EVENT_NAME = "onBarCodeRead"
  }
}
