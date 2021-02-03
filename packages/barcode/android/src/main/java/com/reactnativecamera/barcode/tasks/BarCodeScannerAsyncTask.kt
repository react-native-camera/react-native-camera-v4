package com.reactnativecamera.barcode.tasks

import android.os.AsyncTask
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.reactnativecamera.barcode.RectOfInterest

class BarCodeScannerAsyncTask     //  note(sjchmiela): From my short research it's ok to ignore rotation of the image.
(
  private val mDelegate: BarCodeScannerAsyncTaskDelegate,
  private val mMultiFormatReader: MultiFormatReader,
  private val mImageData: ByteArray,
  private val mWidth: Int,
  private val mHeight: Int,
  private val mRectOfInterest: RectOfInterest? = null
) : AsyncTask<Void?, Void?, Result?>() {

  override fun doInBackground(vararg ignored: Void?): Result? {
    if (isCancelled) {
      return null
    }
    var result: Result? = null

    val left: Int = mRectOfInterest?.x ?: 0
    val top = mRectOfInterest?.y ?: 0
    val scanWidth = mRectOfInterest?.width ?: mWidth
    val scanHeight = mRectOfInterest?.height ?: mHeight
    try {
      val bitmap = generateBitmapFromImageData(
        mImageData,
        mWidth,
        mHeight,
        false,
        left,
        top,
        scanWidth,
        scanHeight
      )
      result = mMultiFormatReader.decodeWithState(bitmap)
    } catch (e: NotFoundException) {
      val bitmap = generateBitmapFromImageData(
        rotateImage(mImageData, mWidth, mHeight),
        mHeight,
        mWidth,
        false,
        mHeight - scanHeight - top,
        left,
        scanHeight,
        scanWidth
      )
      try {
        result = mMultiFormatReader.decodeWithState(bitmap)
      } catch (e1: NotFoundException) {
        val invertedBitmap = generateBitmapFromImageData(
          mImageData,
          mWidth,
          mHeight,
          true,
          mWidth - scanWidth - left,
          mHeight - scanHeight - top,
          scanWidth,
          scanHeight
        )
        try {
          result = mMultiFormatReader.decodeWithState(invertedBitmap)
        } catch (e2: NotFoundException) {
          val invertedRotatedBitmap = generateBitmapFromImageData(
            rotateImage(mImageData, mWidth, mHeight),
            mHeight,
            mWidth,
            true,
            top,
            mWidth - scanWidth - left,
            scanHeight,
            scanWidth
          )
          try {
            result = mMultiFormatReader.decodeWithState(invertedRotatedBitmap)
          } catch (e3: NotFoundException) {
            //no barcode Found
          }
        }
      }
    } catch (t: Throwable) {
      t.printStackTrace()
    }
    return result
  }

  override fun onPostExecute(result: Result?) {
    super.onPostExecute(result)
    if (result != null) {
      mDelegate.onBarCodeRead(result, mWidth, mHeight, mImageData)
    }
    mDelegate.onBarCodeScanningTaskCompleted()
  }

  private fun rotateImage(imageData: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(imageData.size)
    for (y in 0 until height) {
      for (x in 0 until width) {
        rotated[x * height + height - y - 1] = imageData[x + y * width]
      }
    }
    return rotated
  }

  private fun generateBitmapFromImageData(imageData: ByteArray, width: Int, height: Int, inverse: Boolean, left: Int, top: Int, sWidth: Int, sHeight: Int): BinaryBitmap {
    val source: PlanarYUVLuminanceSource = PlanarYUVLuminanceSource(
      imageData,  // byte[] yuvData
      width,  // int dataWidth
      height,  // int dataHeight
      left,  // int left
      top,  // int top
      sWidth,  // int width
      sHeight,  // int height
      false // boolean reverseHorizontal
    )

    return if (inverse) {
      BinaryBitmap(HybridBinarizer(source.invert()))
    } else {
      BinaryBitmap(HybridBinarizer(source))
    }
  }
}
