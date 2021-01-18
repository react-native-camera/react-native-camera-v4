package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class CameraReadyEvent private constructor() : Event<CameraReadyEvent>() {
  override fun getCoalescingKey(): Short {
    return 0
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_CAMERA_READY.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    return Arguments.createMap()
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<CameraReadyEvent> = Pools.SynchronizedPool<CameraReadyEvent>(3)
    fun obtain(viewTag: Int): CameraReadyEvent {
      var event: CameraReadyEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = CameraReadyEvent()
      }
      event.init(viewTag)
      return event
    }
  }
}
