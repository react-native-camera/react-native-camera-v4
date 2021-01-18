package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class RecordingEndEvent private constructor() : Event<RecordingEndEvent>() {
  override fun getCoalescingKey(): Short {
    return 0
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_RECORDING_END.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    return Arguments.createMap()
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<RecordingEndEvent> = Pools.SynchronizedPool<RecordingEndEvent>(3)
    fun obtain(viewTag: Int): RecordingEndEvent {
      var event: RecordingEndEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = RecordingEndEvent()
      }
      event.init(viewTag)
      return event
    }
  }
}
