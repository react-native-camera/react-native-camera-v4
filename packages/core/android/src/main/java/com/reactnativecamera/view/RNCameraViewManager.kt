package com.reactnativecamera.view

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import java.util.*

class RNCameraViewManager : ViewGroupManager<RNCameraView>() {
  enum class Events(private val mName: String) {
    EVENT_CAMERA_READY("onCameraReady"), EVENT_ON_MOUNT_ERROR("onMountError"), EVENT_ON_BAR_CODE_READ("onBarCodeRead"), EVENT_ON_FACES_DETECTED("onFacesDetected"), EVENT_ON_BARCODES_DETECTED("onGoogleVisionBarcodesDetected"), EVENT_ON_FACE_DETECTION_ERROR("onFaceDetectionError"), EVENT_ON_BARCODE_DETECTION_ERROR("onGoogleVisionBarcodeDetectionError"), EVENT_ON_TEXT_RECOGNIZED("onTextRecognized"), EVENT_ON_PICTURE_TAKEN("onPictureTaken"), EVENT_ON_PICTURE_SAVED("onPictureSaved"), EVENT_ON_RECORDING_START("onRecordingStart"), EVENT_ON_RECORDING_END("onRecordingEnd"), EVENT_ON_TOUCH("onTouch");

    override fun toString(): String {
      return mName
    }
  }

  override fun onDropViewInstance(view: RNCameraView) {
    view.onHostDestroy()
    super.onDropViewInstance(view)
  }

  override fun getName(): String {
    return REACT_CLASS
  }

  override fun createViewInstance(themedReactContext: ThemedReactContext): RNCameraView {
    return RNCameraView(themedReactContext)
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
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

  @ReactProp(name = "barCodeTypes")
  fun setBarCodeTypes(view: RNCameraView, barCodeTypes: ReadableArray?) {
    if (barCodeTypes == null) {
      return
    }
    val result: MutableList<String?> = ArrayList(barCodeTypes.size())
    for (i in 0 until barCodeTypes.size()) {
      result.add(barCodeTypes.getString(i))
    }
    view.setBarCodeTypes(result)
  }

  @ReactProp(name = "detectedImageInEvent")
  fun setDetectedImageInEvent(view: RNCameraView, detectedImageInEvent: Boolean) {
    view.setDetectedImageInEvent(detectedImageInEvent)
  }

  @ReactProp(name = "barCodeScannerEnabled")
  fun setBarCodeScanning(view: RNCameraView, barCodeScannerEnabled: Boolean) {
    view.setShouldScanBarCodes(barCodeScannerEnabled)
  }

  @ReactProp(name = "useCamera2Api")
  fun setUseCamera2Api(view: RNCameraView, useCamera2Api: Boolean) {
    view.setUsingCamera2Api(useCamera2Api)
  }

  @ReactProp(name = "touchDetectorEnabled")
  fun setTouchDetectorEnabled(view: RNCameraView, touchDetectorEnabled: Boolean) {
    view.setShouldDetectTouches(touchDetectorEnabled)
  }

  @ReactProp(name = "faceDetectorEnabled")
  fun setFaceDetecting(view: RNCameraView, faceDetectorEnabled: Boolean) {
    view.setShouldDetectFaces(faceDetectorEnabled)
  }

  @ReactProp(name = "faceDetectionMode")
  fun setFaceDetectionMode(view: RNCameraView, mode: Int) {
    view.setFaceDetectionMode(mode)
  }

  @ReactProp(name = "faceDetectionLandmarks")
  fun setFaceDetectionLandmarks(view: RNCameraView, landmarks: Int) {
    view.setFaceDetectionLandmarks(landmarks)
  }

  @ReactProp(name = "faceDetectionClassifications")
  fun setFaceDetectionClassifications(view: RNCameraView, classifications: Int) {
    view.setFaceDetectionClassifications(classifications)
  }

  @ReactProp(name = "trackingEnabled")
  fun setTracking(view: RNCameraView, trackingEnabled: Boolean) {
    view.setTracking(trackingEnabled)
  }

  @ReactProp(name = "googleVisionBarcodeDetectorEnabled")
  fun setGoogleVisionBarcodeDetecting(view: RNCameraView, googleBarcodeDetectorEnabled: Boolean) {
    view.setShouldGoogleDetectBarcodes(googleBarcodeDetectorEnabled)
  }

  @ReactProp(name = "googleVisionBarcodeType")
  fun setGoogleVisionBarcodeType(view: RNCameraView, barcodeType: Int) {
    view.setGoogleVisionBarcodeType(barcodeType)
  }

  @ReactProp(name = "googleVisionBarcodeMode")
  fun setGoogleVisionBarcodeMode(view: RNCameraView, barcodeMode: Int) {
    view.setGoogleVisionBarcodeMode(barcodeMode)
  }

  @ReactProp(name = "textRecognizerEnabled")
  fun setTextRecognizing(view: RNCameraView, textRecognizerEnabled: Boolean) {
    view.setShouldRecognizeText(textRecognizerEnabled)
  }

  /**---limit scan area addition--- */
  @ReactProp(name = "rectOfInterest")
  fun setRectOfInterest(view: RNCameraView, coordinates: ReadableMap?) {
    if (coordinates != null) {
      val x = coordinates.getDouble("x").toFloat()
      val y = coordinates.getDouble("y").toFloat()
      val width = coordinates.getDouble("width").toFloat()
      val height = coordinates.getDouble("height").toFloat()
      view.setRectOfInterest(x, y, width, height)
    }
  }

  @ReactProp(name = "cameraViewDimensions")
  fun setCameraViewDimensions(view: RNCameraView, dimensions: ReadableMap?) {
    if (dimensions != null) {
      val cameraViewWidth = dimensions.getDouble("width").toInt()
      val cameraViewHeight = dimensions.getDouble("height").toInt()
      view.setCameraViewDimensions(cameraViewWidth, cameraViewHeight)
    }
  }

  /**---limit scan area addition--- */
  companion object {
    private const val REACT_CLASS = "RNCamera"
  }
}
