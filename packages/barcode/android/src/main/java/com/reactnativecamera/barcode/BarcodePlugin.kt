package com.reactnativecamera.barcode

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.reactnativecamera.Plugin
import com.reactnativecamera.PluginDelegate
import com.reactnativecamera.barcode.events.BarCodeReadEvent
import com.reactnativecamera.barcode.tasks.BarCodeScannerAsyncTask
import com.reactnativecamera.barcode.tasks.BarCodeScannerAsyncTaskDelegate
import com.reactnativecamera.view.CameraView
import java.util.*

class BarcodePlugin(
  options: BarcodeOptions,
  delegate: PluginDelegate,
  private val reactContext: ReactApplicationContext
) : Plugin(BarcodePluginManager.NAME, delegate), BarCodeScannerAsyncTaskDelegate {
  private var mMultiFormatReader: MultiFormatReader
  private var mBarCodeTypes = options.barcodeTypes
  var rectOfInterest: RectOfInterest? = options.rectOfInterest

  init {
    mMultiFormatReader = makeMultiFormatReader()
    resume()
  }

  @Volatile
  private var barCodeScannerTaskLock = false

  val scanning: Boolean
    get() = delegate.cameraView.scanning

  fun setBarCodeTypes(barCodeTypes: List<String>) {
    mBarCodeTypes = barCodeTypes
    mMultiFormatReader = makeMultiFormatReader()
  }

  fun stop() {
    delegate.cameraView.scanning = false
  }

  fun resume() {
    delegate.cameraView.scanning = true
  }


  override fun onFramePreview(cameraView: CameraView, data: ByteArray, width: Int, height: Int, rotation: Int) {
    if (!barCodeScannerTaskLock) {
      barCodeScannerTaskLock = true
      BarCodeScannerAsyncTask(
        this,
        mMultiFormatReader,
        data,
        width,
        height,
        rectOfInterest
      ).execute()
    }
  }

  override fun dispose() {
    stop()
  }

  override fun onBarCodeRead(barCode: Result?, width: Int, height: Int, imageData: ByteArray) {
    val result = barCode ?: return

    val event = BarCodeReadEvent(result, width, height)

    if (!mBarCodeTypes.contains(event.type)) {
      return
    }

    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(
        BarCodeReadEvent.EVENT_NAME,
        event.serializeEventData(delegate.cameraView.id)
      );

    stop()
  }

  override fun onBarCodeScanningTaskCompleted() {
    barCodeScannerTaskLock = false
    mMultiFormatReader.reset()
  }

  private fun makeMultiFormatReader(): MultiFormatReader {
    val multiFormatReader = MultiFormatReader()
    mMultiFormatReader = multiFormatReader
    val hints: EnumMap<DecodeHintType, Any> = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
    val decodeFormats: EnumSet<BarcodeFormat> = EnumSet.noneOf(BarcodeFormat::class.java)
    for (code in mBarCodeTypes) {
      val formatString = Barcodes.VALID_BAR_CODE_TYPES[code]
      if (formatString != null) {
        decodeFormats.add(BarcodeFormat.valueOf(formatString))
      }
    }
    hints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
    multiFormatReader.setHints(hints)
    return multiFormatReader
  }
}
