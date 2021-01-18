package com.reactnativecamera.events

import androidx.core.util.Pools
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.view.RNCameraViewManager

class PictureSavedEvent private constructor() : Event<PictureSavedEvent>() {
  private var mResponse: WritableMap? = null
  private fun init(viewTag: Int, response: WritableMap?) {
    super.init(viewTag)
    mResponse = response
  }

  override fun getCoalescingKey(): Short {
    val hashCode: Int = mResponse?.getMap("data")?.getString("uri").hashCode() % Short.MAX_VALUE
    return hashCode.toShort()
  }

  override fun getEventName(): String {
    return RNCameraViewManager.Events.EVENT_ON_PICTURE_SAVED.toString()
  }

  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, eventName, mResponse)
  }

  companion object {
    private val EVENTS_POOL: Pools.SynchronizedPool<PictureSavedEvent> = Pools.SynchronizedPool(5)
    fun obtain(viewTag: Int, response: WritableMap?): PictureSavedEvent {
      var event: PictureSavedEvent? = EVENTS_POOL.acquire()
      if (event == null) {
        event = PictureSavedEvent()
      }
      event.init(viewTag, response)
      return event
    }
  }
}
