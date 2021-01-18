package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class TouchEvent private constructor() : Event<TouchEvent>() {
  private var mX = 0
  private var mY = 0
  private var mIsDoubleTap = false
  private fun init(viewTag: Int, isDoubleTap: Boolean, x: Int, y: Int) {
    super.init(viewTag)
    mX = x
    mY = y
    mIsDoubleTap = isDoubleTap
  }

  override fun getCoalescingKey(): Short {
    return 0
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_TOUCH.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    val event: WritableMap = Arguments.createMap()
    event.putInt("target", viewTag)
    val touchOrigin: WritableMap = Arguments.createMap()
    touchOrigin.putInt("x", mX)
    touchOrigin.putInt("y", mY)
    event.putBoolean("isDoubleTap", mIsDoubleTap)
    event.putMap("touchOrigin", touchOrigin)
    return event
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<TouchEvent> = Pools.SynchronizedPool(3)
    fun obtain(viewTag: Int, isDoubleTap: Boolean, x: Int, y: Int): TouchEvent {
      var event: TouchEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = TouchEvent()
      }
      event.init(viewTag, isDoubleTap, x, y)
      return event
    }
  }
}
