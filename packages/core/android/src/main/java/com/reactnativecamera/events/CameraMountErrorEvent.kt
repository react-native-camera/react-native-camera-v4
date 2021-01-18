package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class CameraMountErrorEvent private constructor() : Event<CameraMountErrorEvent>() {
  private var mError: String? = null
  private fun init(viewTag: Int, error: String) {
    super.init(viewTag)
    mError = error
  }

  override fun getCoalescingKey(): Short {
    return 0
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_MOUNT_ERROR.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, serializeEventData())
  }

  private fun serializeEventData(): WritableMap {
    val arguments: WritableMap = Arguments.createMap()
    arguments.putString("message", mError)
    return arguments
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<CameraMountErrorEvent> = Pools.SynchronizedPool<CameraMountErrorEvent>(3)
    fun obtain(viewTag: Int, error: String): CameraMountErrorEvent {
      var event: CameraMountErrorEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = CameraMountErrorEvent()
      }
      event.init(viewTag, error)
      return event
    }
  }
}
