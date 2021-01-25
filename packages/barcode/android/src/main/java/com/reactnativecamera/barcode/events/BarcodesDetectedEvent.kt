package com.reactnativecamera.barcode.events

import android.util.Base64
import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class BarcodesDetectedEvent private constructor(
  private var mBarcodes: WritableArray,
  private var mCompressedImage: ByteArray
) : Event<BarcodesDetectedEvent>() {

  /**
   * note(@sjchmiela)
   * Should the events about detected barcodes coalesce, the best strategy will be
   * to ensure that events with different barcodes count are always being transmitted.
   */
  override fun getCoalescingKey(): Short {
    return if (mBarcodes.size() > Short.MAX_VALUE) {
      Short.MAX_VALUE
    } else mBarcodes.size().toShort()
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_BARCODES_DETECTED.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    val event = Arguments.createMap()
    event.putString("type", "barcode")
    event.putArray("barcodes", mBarcodes)
    event.putInt("target", viewTag)
    event.putString("image", Base64.encodeToString(mCompressedImage, Base64.NO_WRAP))
    return event
  }

  companion object {
    private val EVENTS_POOL = Pools.SynchronizedPool<BarcodesDetectedEvent>(3)
    fun obtain(
      viewTag: Int,
      barcodes: WritableArray,
      compressedImage: ByteArray): BarcodesDetectedEvent {
      var event = EVENTS_POOL.acquire()
      if (event == null) {
        event = BarcodesDetectedEvent(barcodes, compressedImage)
      }
      event.init(viewTag)
      return event
    }
  }
}
