package com.reactnativecamera.view

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.reactnativecamera.CoreModule
import java.util.*

class RNCameraViewManager : ViewGroupManager<RNCameraView>() {
  enum class Events(private val mName: String) {
    EVENT_CAMERA_READY("onCameraReady"),
    EVENT_ON_MOUNT_ERROR("onMountError"),
    EVENT_ON_TEXT_RECOGNIZED("onTextRecognized"),
    EVENT_ON_PICTURE_TAKEN("onPictureTaken"),
    EVENT_ON_PICTURE_SAVED("onPictureSaved"),
    EVENT_ON_RECORDING_START("onRecordingStart"),
    EVENT_ON_RECORDING_END("onRecordingEnd"),
    EVENT_ON_TOUCH("onTouch");

    override fun toString(): String {
      return mName
    }
  }

  var mModule: CoreModule? = null

  override fun onDropViewInstance(view: RNCameraView) {
    view.onHostDestroy()
    view.plugins.forEach { (name, plugin) ->
      plugin.dispose()
      module.getPluginManager(name).detachCameraInstance(view.id)
    }
    super.onDropViewInstance(view)
  }

  override fun getName(): String {
    return REACT_CLASS
  }

  override fun createViewInstance(themedReactContext: ThemedReactContext): RNCameraView {
    return RNCameraView(themedReactContext)
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
    val builder = MapBuilder.builder<String, Any>()
    for (event in Events.values()) {
      builder.put(event.toString(), MapBuilder.of("registrationName", event.toString()))
    }
    return builder.build()
  }

  @ReactProp(name = "type")
  fun setType(view: RNCameraView, type: Int) {
    view.facing = type
  }

  @ReactProp(name = "cameraId")
  fun setCameraId(view: RNCameraView, id: String?) {
    view.cameraId = id
  }

  @ReactProp(name = "ratio")
  fun setRatio(view: RNCameraView, ratio: String?) {
    view.setAspectRatio(AspectRatio.parse(ratio!!))
  }

  @ReactProp(name = "flashMode")
  fun setFlashMode(view: RNCameraView, torchMode: Int) {
    view.flash = torchMode
  }

  @ReactProp(name = "exposure")
  fun setExposureCompensation(view: RNCameraView, exposure: Float) {
    view.exposureCompensation = exposure
  }

  @ReactProp(name = "autoFocus")
  fun setAutoFocus(view: RNCameraView, autoFocus: Boolean) {
    view.autoFocus = autoFocus
  }

  @ReactProp(name = "focusDepth")
  fun setFocusDepth(view: RNCameraView, depth: Float) {
    view.focusDepth = depth
  }

  @ReactProp(name = "autoFocusPointOfInterest")
  fun setAutoFocusPointOfInterest(view: RNCameraView, coordinates: ReadableMap?) {
    if (coordinates != null) {
      val x = coordinates.getDouble("x").toFloat()
      val y = coordinates.getDouble("y").toFloat()
      view.setAutoFocusPointOfInterest(x, y)
    }
  }

  @ReactProp(name = "zoom")
  fun setZoom(view: RNCameraView, zoom: Float) {
    view.zoom = zoom
  }

  @ReactProp(name = "useNativeZoom")
  fun setUseNativeZoom(view: RNCameraView, useNativeZoom: Boolean) {
    view.setUseNativeZoom(useNativeZoom)
  }

  @ReactProp(name = "whiteBalance")
  fun setWhiteBalance(view: RNCameraView, whiteBalance: Int) {
    view.whiteBalance = whiteBalance
  }

  @ReactProp(name = "pictureSize")
  fun setPictureSize(view: RNCameraView, size: String) {
    view.pictureSize = if (size == "None") null else Size.parse(size)
  }

  @ReactProp(name = "playSoundOnCapture")
  fun setPlaySoundOnCapture(view: RNCameraView, playSoundOnCapture: Boolean) {
    view.playSoundOnCapture = playSoundOnCapture
  }

  @ReactProp(name = "playSoundOnRecord")
  fun setPlaySoundOnRecord(view: RNCameraView, playSoundOnRecord: Boolean) {
    view.playSoundOnRecord = playSoundOnRecord
  }

  @ReactProp(name = "useCamera2Api")
  fun setUseCamera2Api(view: RNCameraView, useCamera2Api: Boolean) {
    view.setUsingCamera2Api(useCamera2Api)
  }

  @ReactProp(name = "touchDetectorEnabled")
  fun setTouchDetectorEnabled(view: RNCameraView, touchDetectorEnabled: Boolean) {
    view.setShouldDetectTouches(touchDetectorEnabled)
  }

  @ReactProp(name = "plugins")
  fun setPlugins(view: RNCameraView, plugins: ReadableArray?) {
    val pluginList = mutableListOf<String>()

    if (plugins != null) {
      for (i in 0 until plugins.size()) {
        val plugin = plugins.getString(i)
        if (plugin != null) {
          pluginList.add((plugin))
        }
      }
    }

    // Remove plugins
    view.plugins.forEach { (name, plugin) ->
      if (!pluginList.contains(name)) {
        plugin.dispose()
        view.plugins.remove(name)
      }
    }

    // Add plugins
    pluginList.forEach {
      if (!view.plugins.contains((it))) {
        view.plugins[it] = module.getPluginManager(it).attachCameraInstance(view)
      }
    }
  }

  private val module: CoreModule
    get() = mModule
      ?: throw Error("RNCameraViewManager does not have an associated CoreModule, cannot integrate plugins")

  /**---limit scan area addition--- */
  companion object {
    private const val REACT_CLASS = "RNCamera"
  }
}
