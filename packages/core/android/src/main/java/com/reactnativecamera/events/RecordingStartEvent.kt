package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class RecordingStartEvent private constructor() : Event<RecordingStartEvent>() {
  private var mResponse: WritableMap? = null
  private fun init(viewTag: Int, response: WritableMap?) {
    super.init(viewTag)
    mResponse = response
  }

  // @Override
  // public short getCoalescingKey() {
  //     int hashCode = mResponse.getString("uri").hashCode() % Short.MAX_VALUE;
  //     return (short) hashCode;
  // }
  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_RECORDING_START.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, mResponse)
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<RecordingStartEvent> = Pools.SynchronizedPool<RecordingStartEvent>(3)
    fun obtain(viewTag: Int, response: WritableMap?): RecordingStartEvent {
      var event: RecordingStartEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = RecordingStartEvent()
      }
      event.init(viewTag, response)
      return event
    }
  }
}
