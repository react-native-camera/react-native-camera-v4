package com.reactnativecamera.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.CamcorderProfile
import android.os.Build
import android.view.ViewGroup
import androidx.exifinterface.media.ExifInterface
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.reactnativecamera.Constants
import com.reactnativecamera.CoreModule
import com.reactnativecamera.events.*
import java.text.SimpleDateFormat
import java.util.*

object RNCameraViewHelper {
  val exifTags = arrayOf(
    arrayOf("string", ExifInterface.TAG_ARTIST),
    arrayOf("int", ExifInterface.TAG_BITS_PER_SAMPLE),
    arrayOf("int", ExifInterface.TAG_COMPRESSION),
    arrayOf("string", ExifInterface.TAG_COPYRIGHT),
    arrayOf("string", ExifInterface.TAG_DATETIME),
    arrayOf("string", ExifInterface.TAG_IMAGE_DESCRIPTION),
    arrayOf("int", ExifInterface.TAG_IMAGE_LENGTH),
    arrayOf("int", ExifInterface.TAG_IMAGE_WIDTH),
    arrayOf("int", ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT),
    arrayOf("int", ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH),
    arrayOf("string", ExifInterface.TAG_MAKE),
    arrayOf("string", ExifInterface.TAG_MODEL),
    arrayOf("int", ExifInterface.TAG_ORIENTATION),
    arrayOf("int", ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION),
    arrayOf("int", ExifInterface.TAG_PLANAR_CONFIGURATION),
    arrayOf("double", ExifInterface.TAG_PRIMARY_CHROMATICITIES),
    arrayOf("double", ExifInterface.TAG_REFERENCE_BLACK_WHITE),
    arrayOf("int", ExifInterface.TAG_RESOLUTION_UNIT),
    arrayOf("int", ExifInterface.TAG_ROWS_PER_STRIP),
    arrayOf("int", ExifInterface.TAG_SAMPLES_PER_PIXEL),
    arrayOf("string", ExifInterface.TAG_SOFTWARE),
    arrayOf("int", ExifInterface.TAG_STRIP_BYTE_COUNTS),
    arrayOf("int", ExifInterface.TAG_STRIP_OFFSETS),
    arrayOf("int", ExifInterface.TAG_TRANSFER_FUNCTION),
    arrayOf("double", ExifInterface.TAG_WHITE_POINT),
    arrayOf("double", ExifInterface.TAG_X_RESOLUTION),
    arrayOf("double", ExifInterface.TAG_Y_CB_CR_COEFFICIENTS),
    arrayOf("int", ExifInterface.TAG_Y_CB_CR_POSITIONING),
    arrayOf("int", ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING),
    arrayOf("double", ExifInterface.TAG_Y_RESOLUTION),
    arrayOf("double", ExifInterface.TAG_APERTURE_VALUE),
    arrayOf("double", ExifInterface.TAG_BRIGHTNESS_VALUE),
    arrayOf("string", ExifInterface.TAG_CFA_PATTERN),
    arrayOf("int", ExifInterface.TAG_COLOR_SPACE),
    arrayOf("string", ExifInterface.TAG_COMPONENTS_CONFIGURATION),
    arrayOf("double", ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL),
    arrayOf("int", ExifInterface.TAG_CONTRAST),
    arrayOf("int", ExifInterface.TAG_CUSTOM_RENDERED),
    arrayOf("string", ExifInterface.TAG_DATETIME_DIGITIZED),
    arrayOf("string", ExifInterface.TAG_DATETIME_ORIGINAL),
    arrayOf("string", ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION),
    arrayOf("double", ExifInterface.TAG_DIGITAL_ZOOM_RATIO),
    arrayOf("string", ExifInterface.TAG_EXIF_VERSION),
    arrayOf("double", ExifInterface.TAG_EXPOSURE_BIAS_VALUE),
    arrayOf("double", ExifInterface.TAG_EXPOSURE_INDEX),
    arrayOf("int", ExifInterface.TAG_EXPOSURE_MODE),
    arrayOf("int", ExifInterface.TAG_EXPOSURE_PROGRAM),
    arrayOf("double", ExifInterface.TAG_EXPOSURE_TIME),
    arrayOf("double", ExifInterface.TAG_F_NUMBER),
    arrayOf("string", ExifInterface.TAG_FILE_SOURCE),
    arrayOf("int", ExifInterface.TAG_FLASH),
    arrayOf("double", ExifInterface.TAG_FLASH_ENERGY),
    arrayOf("string", ExifInterface.TAG_FLASHPIX_VERSION),
    arrayOf("double", ExifInterface.TAG_FOCAL_LENGTH),
    arrayOf("int", ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
    arrayOf("int", ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT),
    arrayOf("double", ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION),
    arrayOf("double", ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION),
    arrayOf("int", ExifInterface.TAG_GAIN_CONTROL),
    arrayOf("int", ExifInterface.TAG_ISO_SPEED_RATINGS),
    arrayOf("string", ExifInterface.TAG_IMAGE_UNIQUE_ID),
    arrayOf("int", ExifInterface.TAG_LIGHT_SOURCE),
    arrayOf("string", ExifInterface.TAG_MAKER_NOTE),
    arrayOf("double", ExifInterface.TAG_MAX_APERTURE_VALUE),
    arrayOf("int", ExifInterface.TAG_METERING_MODE),
    arrayOf("int", ExifInterface.TAG_NEW_SUBFILE_TYPE),
    arrayOf("string", ExifInterface.TAG_OECF),
    arrayOf("int", ExifInterface.TAG_PIXEL_X_DIMENSION),
    arrayOf("int", ExifInterface.TAG_PIXEL_Y_DIMENSION),
    arrayOf("string", ExifInterface.TAG_RELATED_SOUND_FILE),
    arrayOf("int", ExifInterface.TAG_SATURATION),
    arrayOf("int", ExifInterface.TAG_SCENE_CAPTURE_TYPE),
    arrayOf("string", ExifInterface.TAG_SCENE_TYPE),
    arrayOf("int", ExifInterface.TAG_SENSING_METHOD),
    arrayOf("int", ExifInterface.TAG_SHARPNESS),
    arrayOf("double", ExifInterface.TAG_SHUTTER_SPEED_VALUE),
    arrayOf("string", ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE),
    arrayOf("string", ExifInterface.TAG_SPECTRAL_SENSITIVITY),
    arrayOf("int", ExifInterface.TAG_SUBFILE_TYPE),
    arrayOf("string", ExifInterface.TAG_SUBSEC_TIME),
    arrayOf("string", ExifInterface.TAG_SUBSEC_TIME_DIGITIZED),
    arrayOf("string", ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
    arrayOf("int", ExifInterface.TAG_SUBJECT_AREA),
    arrayOf("double", ExifInterface.TAG_SUBJECT_DISTANCE),
    arrayOf("int", ExifInterface.TAG_SUBJECT_DISTANCE_RANGE),
    arrayOf("int", ExifInterface.TAG_SUBJECT_LOCATION),
    arrayOf("string", ExifInterface.TAG_USER_COMMENT),
    arrayOf("int", ExifInterface.TAG_WHITE_BALANCE),
    arrayOf("int", ExifInterface.TAG_GPS_ALTITUDE_REF),
    arrayOf("string", ExifInterface.TAG_GPS_AREA_INFORMATION),
    arrayOf("double", ExifInterface.TAG_GPS_DOP),
    arrayOf("string", ExifInterface.TAG_GPS_DATESTAMP),
    arrayOf("double", ExifInterface.TAG_GPS_DEST_BEARING),
    arrayOf("string", ExifInterface.TAG_GPS_DEST_BEARING_REF),
    arrayOf("double", ExifInterface.TAG_GPS_DEST_DISTANCE),
    arrayOf("string", ExifInterface.TAG_GPS_DEST_DISTANCE_REF),
    arrayOf("double", ExifInterface.TAG_GPS_DEST_LATITUDE),
    arrayOf("string", ExifInterface.TAG_GPS_DEST_LATITUDE_REF),
    arrayOf("double", ExifInterface.TAG_GPS_DEST_LONGITUDE),
    arrayOf("string", ExifInterface.TAG_GPS_DEST_LONGITUDE_REF),
    arrayOf("int", ExifInterface.TAG_GPS_DIFFERENTIAL),
    arrayOf("double", ExifInterface.TAG_GPS_IMG_DIRECTION),
    arrayOf("string", ExifInterface.TAG_GPS_IMG_DIRECTION_REF),
    arrayOf("string", ExifInterface.TAG_GPS_LATITUDE_REF),
    arrayOf("string", ExifInterface.TAG_GPS_LONGITUDE_REF),
    arrayOf("string", ExifInterface.TAG_GPS_MAP_DATUM),
    arrayOf("string", ExifInterface.TAG_GPS_MEASURE_MODE),
    arrayOf("string", ExifInterface.TAG_GPS_PROCESSING_METHOD),
    arrayOf("string", ExifInterface.TAG_GPS_SATELLITES),
    arrayOf("double", ExifInterface.TAG_GPS_SPEED),
    arrayOf("string", ExifInterface.TAG_GPS_SPEED_REF),
    arrayOf("string", ExifInterface.TAG_GPS_STATUS),
    arrayOf("string", ExifInterface.TAG_GPS_TIMESTAMP),
    arrayOf("double", ExifInterface.TAG_GPS_TRACK),
    arrayOf("string", ExifInterface.TAG_GPS_TRACK_REF),
    arrayOf("string", ExifInterface.TAG_GPS_VERSION_ID),
    arrayOf("string", ExifInterface.TAG_INTEROPERABILITY_INDEX),
    arrayOf("int", ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH),
    arrayOf("int", ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH),
    arrayOf("int", ExifInterface.TAG_DNG_VERSION),
    arrayOf("int", ExifInterface.TAG_DEFAULT_CROP_SIZE),
    arrayOf("int", ExifInterface.TAG_ORF_PREVIEW_IMAGE_START),
    arrayOf("int", ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH),
    arrayOf("int", ExifInterface.TAG_ORF_ASPECT_FRAME),
    arrayOf("int", ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER),
    arrayOf("int", ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER),
    arrayOf("int", ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER),
    arrayOf("int", ExifInterface.TAG_RW2_SENSOR_TOP_BORDER),
    arrayOf("int", ExifInterface.TAG_RW2_ISO)
  )

  // Run all events on native modules queue thread since they might be fired
  // from other non RN threads.
  // Mount error event
  fun emitMountErrorEvent(view: ViewGroup, error: String) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: CameraMountErrorEvent = CameraMountErrorEvent.obtain(view.id, error)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // Camera ready event
  fun emitCameraReadyEvent(view: ViewGroup) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: CameraReadyEvent = CameraReadyEvent.obtain(view.id)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // Picture saved event
  fun emitPictureSavedEvent(view: ViewGroup, response: WritableMap?) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: PictureSavedEvent = PictureSavedEvent.obtain(view.id, response)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // Picture taken event
  fun emitPictureTakenEvent(view: ViewGroup) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: PictureTakenEvent = PictureTakenEvent.obtain(view.id)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // video recording start/end events
  fun emitRecordingStartEvent(view: ViewGroup, response: WritableMap?) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: RecordingStartEvent = RecordingStartEvent.obtain(view.id, response)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  fun emitRecordingEndEvent(view: ViewGroup) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: RecordingEndEvent = RecordingEndEvent.obtain(view.id)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // Touch event
  fun emitTouchEvent(view: ViewGroup, isDoubleTap: Boolean, x: Int, y: Int) {
    val reactContext: ReactContext = view.context as ReactContext
    reactContext.runOnNativeModulesQueueThread(Runnable {
      val event: TouchEvent = TouchEvent.obtain(view.getId(), isDoubleTap, x, y)
      reactContext.getNativeModule(UIManagerModule::class.java).eventDispatcher.dispatchEvent(event)
    })
  }

  // Utilities
  fun getCorrectCameraRotation(rotation: Int, facing: Int, cameraOrientation: Int): Int {
    return if (facing == CameraView.FACING_FRONT) {
      // Tested the below line and there's no need to do the mirror calculation
      (cameraOrientation + rotation) % 360
    } else {
      val landscapeFlip = if (rotationIsLandscape(rotation)) 180 else 0
      (cameraOrientation - rotation + landscapeFlip) % 360
    }
  }

  private fun rotationIsLandscape(rotation: Int): Boolean {
    return rotation == Constants.LANDSCAPE_90 ||
      rotation == Constants.LANDSCAPE_270
  }

  private fun getCamcorderProfileQualityFromCameraModuleConstant(quality: Int): Int {
    when (quality) {
      CoreModule.VIDEO_2160P -> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          CamcorderProfile.QUALITY_2160P
        } else CamcorderProfile.QUALITY_1080P
      }
      CoreModule.VIDEO_1080P -> return CamcorderProfile.QUALITY_1080P
      CoreModule.VIDEO_720P -> return CamcorderProfile.QUALITY_720P
      CoreModule.VIDEO_480P -> return CamcorderProfile.QUALITY_480P
      CoreModule.VIDEO_4x3 -> return CamcorderProfile.QUALITY_480P
    }
    return CamcorderProfile.QUALITY_HIGH
  }

  fun getCamcorderProfile(quality: Int): CamcorderProfile {
    var profile: CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
    val camcorderQuality = getCamcorderProfileQualityFromCameraModuleConstant(quality)
    if (CamcorderProfile.hasProfile(camcorderQuality)) {
      profile = CamcorderProfile.get(camcorderQuality)
      if (quality == CoreModule.VIDEO_4x3) {
        profile.videoFrameWidth = 640
      }
    }
    return profile
  }

  fun getExifData(exifInterface: ExifInterface): WritableMap {
    val exifMap: WritableMap = Arguments.createMap()
    for (tagInfo in exifTags) {
      val name = tagInfo[1]
      if (exifInterface.getAttribute(name) != null) {
        when (tagInfo[0]) {
          "string" -> exifMap.putString(name, exifInterface.getAttribute(name))
          "int" -> exifMap.putInt(name, exifInterface.getAttributeInt(name, 0))
          "double" -> exifMap.putDouble(name, exifInterface.getAttributeDouble(name, 0.0))
        }
      }
    }
    val latLong = exifInterface.latLong
    if (latLong != null) {
      exifMap.putDouble(ExifInterface.TAG_GPS_LATITUDE, latLong[0])
      exifMap.putDouble(ExifInterface.TAG_GPS_LONGITUDE, latLong[1])
      exifMap.putDouble(ExifInterface.TAG_GPS_ALTITUDE, exifInterface.getAltitude(0.0))
    }
    return exifMap
  }

  fun setExifData(exifInterface: ExifInterface, exifMap: ReadableMap) {
    for (tagInfo in exifTags) {
      val name = tagInfo[1]
      if (exifMap.hasKey(name)) {
        when (tagInfo[0]) {
          "string" -> exifInterface.setAttribute(name, exifMap.getString(name))
          "int" -> {
            exifInterface.setAttribute(name, exifMap.getInt(name).toString())
            exifMap.getInt(name)
          }
          "double" -> {
            exifInterface.setAttribute(name, exifMap.getDouble(name).toString())
            exifMap.getDouble(name)
          }
        }
      }
    }
    if (exifMap.hasKey(ExifInterface.TAG_GPS_LATITUDE) && exifMap.hasKey(ExifInterface.TAG_GPS_LONGITUDE)) {
      exifInterface.setLatLong(exifMap.getDouble(ExifInterface.TAG_GPS_LATITUDE),
        exifMap.getDouble(ExifInterface.TAG_GPS_LONGITUDE))
    }
    if (exifMap.hasKey(ExifInterface.TAG_GPS_ALTITUDE)) {
      exifInterface.setAltitude(exifMap.getDouble(ExifInterface.TAG_GPS_ALTITUDE))
    }
  }

  // clears exif values in place
  fun clearExifData(exifInterface: ExifInterface) {
    for (tagInfo in exifTags) {
      exifInterface.setAttribute(tagInfo[1], null)
    }

    // these are not part of our tag list, remove by hand
    exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
    exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
    exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
  }

  fun generateSimulatorPhoto(width: Int, height: Int): Bitmap {
    val fakePhoto: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(fakePhoto)
    val background = Paint()
    background.color = Color.BLACK
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
    val textPaint = Paint()
    textPaint.color = Color.YELLOW
    textPaint.textSize = 35f
    val calendar = Calendar.getInstance()
    val simpleDateFormat = SimpleDateFormat("yyyy.MM.dd G '->' HH:mm:ss z")
    canvas.drawText(simpleDateFormat.format(calendar.time), width * 0.1f, height * 0.2f, textPaint)
    canvas.drawText(simpleDateFormat.format(calendar.time), width * 0.2f, height * 0.4f, textPaint)
    canvas.drawText(simpleDateFormat.format(calendar.time), width * 0.3f, height * 0.6f, textPaint)
    canvas.drawText(simpleDateFormat.format(calendar.time), width * 0.4f, height * 0.8f, textPaint)
    return fakePhoto
  }
}
