package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class PictureTakenEvent private constructor() : Event<PictureTakenEvent>() {
  override fun getCoalescingKey(): Short {
    return 0
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_PICTURE_TAKEN.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    return Arguments.createMap()
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<PictureTakenEvent> = Pools.SynchronizedPool<PictureTakenEvent>(3)
    fun obtain(viewTag: Int): PictureTakenEvent {
      var event: PictureTakenEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = PictureTakenEvent()
      }
      event.init(viewTag)
      return event
    }
  }
}
