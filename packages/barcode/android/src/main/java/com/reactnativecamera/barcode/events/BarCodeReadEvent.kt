package com.reactnativecamera.barcode.events

import android.util.Base64
import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.zxing.Result
import com.reactnativecamera.view.RNCameraViewManager
import java.util.*

class BarCodeReadEvent private constructor(
  private var mBarCode: Result,
  private var mWidth: Int,
  private var mHeight: Int
) : Event<BarCodeReadEvent>() {

  /**
   * We want every distinct barcode to be reported to the JS listener.
   * If we return some static value as a coalescing key there may be two barcode events
   * containing two different barcodes waiting to be transmitted to JS
   * that would get coalesced (because both of them would have the same coalescing key).
   * So let's differentiate them with a hash of the contents (mod short's max value).
   */
  override fun getCoalescingKey(): Short {
    val hashCode = mBarCode.text.hashCode() % Short.MAX_VALUE
    return hashCode.toShort()
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_BAR_CODE_READ.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
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
    private val EVENTS_POOL = Pools.SynchronizedPool<BarCodeReadEvent>(3)
    fun obtain(viewTag: Int, barCode: Result, width: Int, height: Int): BarCodeReadEvent {
      var event = EVENTS_POOL.acquire()
      if (event == null) {
        event = BarCodeReadEvent(barCode, width, height)
      }
      event.init(viewTag)
      return event
    }
  }
}
