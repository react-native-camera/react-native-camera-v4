package com.reactnativecamera

import android.view.ViewGroup
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.events.Event
import com.reactnativecamera.view.AspectRatio
import com.reactnativecamera.view.CameraView
import com.reactnativecamera.view.Size

abstract class Plugin(
  open val name: String,
  protected val delegate: PluginDelegate
) {

  abstract fun onFramePreview(cameraView: CameraView, data: ByteArray, width: Int, height: Int, rotation: Int)
  abstract fun dispose()

  var cameraViewSize: Size = Size(0, 0)
  var cameraViewAspectRatio: AspectRatio? = null

  protected fun <T : Event<out Event<*>>?> emitEvent(event: Event<T>) {
    val reactContext: ReactContext = delegate.cameraView.context as ReactContext
    reactContext.runOnNativeModulesQueueThread {
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    }
  }
}
