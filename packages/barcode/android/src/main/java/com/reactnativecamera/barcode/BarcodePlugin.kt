package com.reactnativecamera.barcode

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
  delegate: PluginDelegate
) : Plugin(BarcodePluginManager.NAME, delegate), BarCodeScannerAsyncTaskDelegate {
  private var mMultiFormatReader: MultiFormatReader
  private var mBarCodeTypes = options.barcodeTypes
  var rectOfInterest: RectOfInterest? = options.rectOfInterest

  init {
    mMultiFormatReader = makeMultiFormatReader()
  }

  @Volatile
  private var barCodeScannerTaskLock = false

  fun setBarCodeTypes(barCodeTypes: List<String>) {
    mBarCodeTypes = barCodeTypes
    mMultiFormatReader = makeMultiFormatReader()
  }


  override fun onFramePreview(cameraView: CameraView, data: ByteArray, width: Int, height: Int, rotation: Int) {
    if (!barCodeScannerTaskLock) {
      barCodeScannerTaskLock = true
      val delegate: BarCodeScannerAsyncTaskDelegate? = cameraView as BarCodeScannerAsyncTaskDelegate?
      BarCodeScannerAsyncTask(
        this,
        mMultiFormatReader,
        data,
        width,
        height,
        rectOfInterest,
        cameraViewSize.width,
        cameraViewSize.height,
        cameraViewAspectRatio!!.toFloat()
      ).execute()
    }
  }

  override fun dispose() {

  }

  override fun onBarCodeRead(barCode: Result?, width: Int, height: Int, imageData: ByteArray) {
    if (barCode == null) {
      return
    }

    val barCodeType = barCode.barcodeFormat.toString()
    if (!mBarCodeTypes.contains(barCodeType)) {
      return
    }

    emitEvent(BarCodeReadEvent.obtain(delegate.cameraView.id, barCode, width, height))
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
