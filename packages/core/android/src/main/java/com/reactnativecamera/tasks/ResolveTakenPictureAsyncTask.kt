package com.reactnativecamera.tasks

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.facebook.react.bridge.*
import com.reactnativecamera.utils.RNFileUtils
import com.reactnativecamera.view.RNCameraViewHelper
import java.io.*

class ResolveTakenPictureAsyncTask(
  private val mImageData: ByteArray,
  private val mPromise: Promise,
  private val mOptions: ReadableMap,
  private val mCacheDirectory: File,
  private val mDeviceOrientation: Int,
  private val mPictureSavedDelegate: PictureSavedDelegate
) : AsyncTask<Void?, Void?, WritableMap?>() {
  private var mBitmap: Bitmap? = null
  private val quality: Int
    get() = (mOptions.getDouble("quality") * 100).toInt()

  // loads bitmap only if necessary
  @Throws(IOException::class)
  private fun loadBitmap(): Bitmap {
    if (mBitmap == null) {
      val bitmap = BitmapFactory.decodeByteArray(mImageData, 0, mImageData.size)
      mBitmap = bitmap
      return bitmap
    }

    val bitmap = mBitmap
    return bitmap ?: throw IOException("Failed to decode Image Bitmap")
  }

  override fun doInBackground(vararg voids: Void?): WritableMap? {
    val response = Arguments.createMap()
    var inputStream: ByteArrayInputStream? = null
    var exifInterface: ExifInterface? = null
    var exifData: WritableMap? = null
    var exifExtraData: ReadableMap? = null
    var orientationChanged = false
    response.putInt("deviceOrientation", mDeviceOrientation)
    response.putInt("pictureOrientation", if (mOptions.hasKey("orientation")) mOptions.getInt("orientation") else mDeviceOrientation)
    try {
      // this replaces the skipProcessing flag, we will process only if needed, and in
      // an orderly manner, so that skipProcessing is the default behaviour if no options are given
      // and this behaves more like the iOS version.
      // We will load all data lazily only when needed.

      // this should not incurr in any overhead if not read/used
      inputStream = ByteArrayInputStream(mImageData)


      // Rotate the bitmap to the proper orientation if requested
      if (mOptions.hasKey("fixOrientation") && mOptions.getBoolean("fixOrientation")) {
        exifInterface = ExifInterface(inputStream)

        // Get orientation of the image from mImageData via inputStream
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
          val bitmap = loadBitmap()
          mBitmap = rotateBitmap(bitmap, getImageRotation(orientation))
          orientationChanged = true
        }
      }
      if (mOptions.hasKey("width")) {
        val bitmap = loadBitmap()
        mBitmap = resizeBitmap(bitmap, mOptions.getInt("width"))
      }
      if (mOptions.hasKey("mirrorImage") && mOptions.getBoolean("mirrorImage")) {
        val bitmap = loadBitmap()
        mBitmap = flipHorizontally(bitmap)
      }


      // EXIF code - we will adjust exif info later if we manipulated the bitmap
      val writeExifToResponse = mOptions.hasKey("exif") && mOptions.getBoolean("exif")

      // default to true if not provided so it is consistent with iOS and with what happens if no
      // processing is done and the image is saved as is.
      var writeExifToFile = true
      if (mOptions.hasKey("writeExif")) {
        when (mOptions.getType("writeExif")) {
          ReadableType.Boolean -> writeExifToFile = mOptions.getBoolean("writeExif")
          ReadableType.Map -> {
            exifExtraData = mOptions.getMap("writeExif")
            writeExifToFile = true
          }
        }
      }

      val bitmap = mBitmap

      // Read Exif data if needed
      if (writeExifToResponse || writeExifToFile) {

        // if we manipulated the image, or need to add extra data, or need to add it to the response,
        // then we need to load the actual exif data.
        // Otherwise we can just use w/e exif data we have right now in our byte array
        if (bitmap != null || exifExtraData != null || writeExifToResponse) {
          if (exifInterface == null) {
            exifInterface = ExifInterface(inputStream)
          }
          exifData = RNCameraViewHelper.getExifData(exifInterface)
          if (exifExtraData != null) {
            exifData.merge(exifExtraData)
          }
        }

        // if we did anything to the bitmap, adjust exif
        if (bitmap != null) {
          exifData!!.putInt("width", bitmap.getWidth())
          exifData.putInt("height", bitmap.getHeight())
          if (orientationChanged) {
            exifData.putInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          }
        }

        // Write Exif data to the response if requested
        if (writeExifToResponse) {
          val exifDataCopy = Arguments.createMap()
          exifDataCopy.merge(exifData!!)
          response.putMap("exif", exifDataCopy)
        }
      }


      // final processing
      // Based on whether or not we loaded the full bitmap into memory, final processing differs
      if (bitmap == null) {

        // set response dimensions. If we haven't read our bitmap, get it efficiently
        // without loading the actual bitmap into memory
        val options: BitmapFactory.Options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(mImageData, 0, mImageData.size, options)
        response.putInt("width", options.outWidth)
        response.putInt("height", options.outHeight)


        // save to file if requested
        if (!mOptions.hasKey("doNotSave") || !mOptions.getBoolean("doNotSave")) {

          // Prepare file output
          val imageFile = File(imagePath)
          imageFile.createNewFile()
          val fOut = FileOutputStream(imageFile)

          // Save byte array (it is already a JPEG)
          fOut.write(mImageData)
          fOut.flush()
          fOut.close()

          // update exif data if needed.
          // Since we didn't modify the image, we only update if we have extra exif info
          if (writeExifToFile && exifExtraData != null) {
            val fileExifInterface = ExifInterface(imageFile.absolutePath)
            RNCameraViewHelper.setExifData(fileExifInterface, exifExtraData)
            fileExifInterface.saveAttributes()
          } else if (!writeExifToFile) {
            // if we were requested to NOT store exif, we actually need to
            // clear the exif tags
            val fileExifInterface = ExifInterface(imageFile.absolutePath)
            RNCameraViewHelper.clearExifData(fileExifInterface)
            fileExifInterface.saveAttributes()
          }
          // else: exif is unmodified, no need to update anything

          // Return file system URI
          val fileUri = Uri.fromFile(imageFile).toString()
          response.putString("uri", fileUri)
        }
        if (mOptions.hasKey("base64") && mOptions.getBoolean("base64")) {
          response.putString("base64", Base64.encodeToString(mImageData, Base64.NO_WRAP))
        }
      } else {

        // get response dimensions right from the bitmap if we have it
        response.putInt("width", bitmap.width)
        response.putInt("height", bitmap.height)

        // Cache compressed image in imageStream
        val imageStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, imageStream)


        // Write compressed image to file in cache directory unless otherwise specified
        if (!mOptions.hasKey("doNotSave") || !mOptions.getBoolean("doNotSave")) {
          val filePath = writeStreamToFile(imageStream)

          // since we lost any exif data on bitmap creation, we only need
          // to add it if requested
          if (writeExifToFile && exifData != null) {
            val fileExifInterface = ExifInterface(filePath!!)
            RNCameraViewHelper.setExifData(fileExifInterface, exifData)
            fileExifInterface.saveAttributes()
          }
          val imageFile = File(filePath)
          val fileUri = Uri.fromFile(imageFile).toString()
          response.putString("uri", fileUri)
        }

        // Write base64-encoded image to the response if requested
        if (mOptions.hasKey("base64") && mOptions.getBoolean("base64")) {
          response.putString("base64", Base64.encodeToString(imageStream.toByteArray(), Base64.NO_WRAP))
        }
      }
      return response
    } catch (e: Resources.NotFoundException) {
      mPromise.reject(ERROR_TAG, "Documents directory of the app could not be found.", e)
      e.printStackTrace()
    } catch (e: IOException) {
      mPromise.reject(ERROR_TAG, "An unknown I/O exception has occurred.", e)
      e.printStackTrace()
    } finally {
      try {
        inputStream?.close()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
    return null
  }

  private fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle.toFloat())
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }

  private fun resizeBitmap(bm: Bitmap, newWidth: Int): Bitmap {
    val width: Int = bm.width
    val height: Int = bm.height
    val scaleRatio = newWidth.toFloat() / width.toFloat()
    return Bitmap.createScaledBitmap(bm, newWidth, (height * scaleRatio).toInt(), true)
  }

  private fun flipHorizontally(source: Bitmap): Bitmap {
    val matrix = Matrix()
    matrix.preScale(-1.0f, 1.0f)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }

  // Get rotation degrees from Exif orientation enum
  private fun getImageRotation(orientation: Int): Int {
    var rotationDegrees = 0
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90
      ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180
      ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270
    }
    return rotationDegrees
  }

  @get:Throws(IOException::class)
  private val imagePath: String?
    get() = if (mOptions.hasKey("path")) {
      mOptions.getString("path")
    } else RNFileUtils.getOutputFilePath(mCacheDirectory, ".jpg")

  @Throws(IOException::class)
  private fun writeStreamToFile(inputStream: ByteArrayOutputStream): String? {
    var outputPath: String? = null
    var exception: IOException? = null
    var outputStream: FileOutputStream? = null
    try {
      outputPath = imagePath
      outputStream = FileOutputStream(outputPath)
      inputStream.writeTo(outputStream)
    } catch (e: IOException) {
      e.printStackTrace()
      exception = e
    } finally {
      try {
        outputStream?.close()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
    if (exception != null) {
      throw exception
    }
    return outputPath
  }

  override fun onPostExecute(response: WritableMap?) {
    super.onPostExecute(response)

    // If the response is not null everything went well and we can resolve the promise.
    if (mOptions.hasKey("fastMode") && mOptions.getBoolean("fastMode")) {
      val wrapper = Arguments.createMap()
      wrapper.putInt("id", mOptions.getInt("id"))
      wrapper.putMap("data", response)
      mPictureSavedDelegate.onPictureSaved(wrapper)
    } else {
      mPromise.resolve(response)
    }
  }

  companion object {
    private const val ERROR_TAG = "E_TAKING_PICTURE_FAILED"
  }
}
