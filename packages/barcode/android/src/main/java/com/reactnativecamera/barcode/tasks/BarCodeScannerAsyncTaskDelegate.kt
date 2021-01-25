package com.reactnativecamera.barcode.tasks

import com.google.zxing.Result

interface BarCodeScannerAsyncTaskDelegate {
  fun onBarCodeRead(barCode: Result?, width: Int, height: Int, imageData: ByteArray)
  fun onBarCodeScanningTaskCompleted()
}
