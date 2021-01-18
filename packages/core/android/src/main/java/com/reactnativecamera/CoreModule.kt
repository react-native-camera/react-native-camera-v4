package com.reactnativecamera

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import com.reactnativecamera.utils.ScopedContext
import com.reactnativecamera.view.AspectRatio
import com.reactnativecamera.view.RNCameraView
import com.reactnativecamera.view.Size
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CoreModule(reactContext: ReactApplicationContext?) : ReactContextBaseJavaModule(reactContext) {
  private val mScopedContext: ScopedContext = ScopedContext(reactContext)

  override fun getName(): String {
    return "RNCameraModule"
  }

  override fun getConstants(): MutableMap<String, Any> {
    return Collections.unmodifiableMap(object : HashMap<String, Any>() {
      private val typeConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("front", Constants.FACING_FRONT)
            put("back", Constants.FACING_BACK)
          }
        })
      private val flashModeConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("off", Constants.FLASH_OFF)
            put("on", Constants.FLASH_ON)
            put("auto", Constants.FLASH_AUTO)
            put("torch", Constants.FLASH_TORCH)
          }
        })
      private val autoFocusConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("on", true)
            put("off", false)
          }
        })
      private val whiteBalanceConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("auto", Constants.WB_AUTO)
            put("cloudy", Constants.WB_CLOUDY)
            put("sunny", Constants.WB_SUNNY)
            put("shadow", Constants.WB_SHADOW)
            put("fluorescent", Constants.WB_FLUORESCENT)
            put("incandescent", Constants.WB_INCANDESCENT)
          }
        })
      private val videoQualityConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("2160p", VIDEO_2160P)
            put("1080p", VIDEO_1080P)
            put("720p", VIDEO_720P)
            put("480p", VIDEO_480P)
            put("4:3", VIDEO_4x3)
          }
        })
      private val googleVisionBarcodeModeConstants: Map<String, Any>
        private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
          init {
            put("NORMAL", GOOGLE_VISION_BARCODE_MODE_NORMAL)
            put("ALTERNATE", GOOGLE_VISION_BARCODE_MODE_ALTERNATE)
            put("INVERTED", GOOGLE_VISION_BARCODE_MODE_INVERTED)
          }
        })

      init {
        put("Type", typeConstants)
        put("FlashMode", flashModeConstants)
        put("AutoFocus", autoFocusConstants)
        put("WhiteBalance", whiteBalanceConstants)
        put("VideoQuality", videoQualityConstants)
        put("BarCodeType", barCodeConstants)
        put("FaceDetection", Collections.unmodifiableMap(object : HashMap<String, Any>() {
          private val faceDetectionModeConstants: Map<String, Any>
            private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
              init {
                put("fast", RNFaceDetector.FAST_MODE)
                put("accurate", RNFaceDetector.ACCURATE_MODE)
              }
            })
          private val faceDetectionClassificationsConstants: Map<String, Any>
            private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
              init {
                put("all", RNFaceDetector.ALL_CLASSIFICATIONS)
                put("none", RNFaceDetector.NO_CLASSIFICATIONS)
              }
            })
          private val faceDetectionLandmarksConstants: Map<String, Any>
            private get() = Collections.unmodifiableMap(object : HashMap<String, Any>() {
              init {
                put("all", RNFaceDetector.ALL_LANDMARKS)
                put("none", RNFaceDetector.NO_LANDMARKS)
              }
            })

          init {
            put("Mode", faceDetectionModeConstants)
            put("Landmarks", faceDetectionLandmarksConstants)
            put("Classifications", faceDetectionClassificationsConstants)
          }
        }))
        put("GoogleVisionBarcodeDetection", Collections.unmodifiableMap(object : HashMap<String?, Any?>() {
          init {
            put("BarcodeType", BarcodeFormatUtils.REVERSE_FORMATS)
            put("BarcodeMode", googleVisionBarcodeModeConstants)
          }
        }))
        put("Orientation", Collections.unmodifiableMap(object : HashMap<String?, Any?>() {
          init {
            put("auto", Constants.ORIENTATION_AUTO)
            put("portrait", Constants.ORIENTATION_UP)
            put("portraitUpsideDown", Constants.ORIENTATION_DOWN)
            put("landscapeLeft", Constants.ORIENTATION_LEFT)
            put("landscapeRight", Constants.ORIENTATION_RIGHT)
          }
        }))
      }
    })
  }

  private fun useCameraView(viewTag: Int, requireOpen: Boolean, run: (view: RNCameraView?) -> Unit) {
    val context: ReactApplicationContext = reactApplicationContext
    val uiManager: UIManagerModule = context.getNativeModule(UIManagerModule::class.java)
    uiManager.addUIBlock { nativeViewHierarchyManager ->
      val cameraView: RNCameraView
      try {
        cameraView = nativeViewHierarchyManager.resolveView(viewTag) as RNCameraView
        if (requireOpen) {
          if (cameraView.isCameraOpened()) {
            run(cameraView)
          } else {
            run(null)
          }
        } else {
          run(cameraView)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun pausePreview(viewTag: Int) {
    useCameraView(viewTag, true) { it?.pausePreview() }
  }

  @ReactMethod
  fun resumePreview(viewTag: Int) {
    useCameraView(viewTag, true) { it?.resumePreview() }
  }

  @ReactMethod
  fun takePicture(options: ReadableMap?, viewTag: Int, promise: Promise) {
    val cacheDirectory: File? = mScopedContext.cacheDirectory
    useCameraView(viewTag, true) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running")
      } else {
        it.takePicture(options, promise, cacheDirectory)
      }
    }
  }

  @ReactMethod
  fun record(options: ReadableMap?, viewTag: Int, promise: Promise) {
    val cacheDirectory: File? = mScopedContext.cacheDirectory
    useCameraView(viewTag,true) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running")
      } else {
        try {
          it.record(options, promise, cacheDirectory)
        } catch (e: Throwable) {
          promise.reject("E_CAPTURE_FAILED", e.message)
        }
      }
    }
  }

  @ReactMethod
  fun stopRecording(viewTag: Int) {
    useCameraView(viewTag, true) {
      try {
        it.stopRecording()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun pauseRecording(viewTag: Int) {
    useCameraView(viewTag, true) {
      try {
        it.pauseRecording()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun resumeRecording(viewTag: Int) {
    useCameraView(viewTag, true) {
      try {
        it.resumeRecording()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun getSupportedRatios(viewTag: Int, promise: Promise) {
    useCameraView(viewTag, false) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera view not found")
      } else {
        try {
          val result: WritableArray = Arguments.createArray()
          val ratios: Set<AspectRatio?>? = it.supportedAspectRatios
          if (ratios != null) {
            for (ratio in ratios) {
              if (ratio != null) {
                result.pushString(ratio.toString())
              }
            }
          }
          promise.resolve(result)
        } catch (e: Exception) {
          promise.reject("E_GET_SUPPORTED_RATIOS_FAILED", e.message)
        }
      }
    }
  }

  @ReactMethod
  fun getCameraIds(viewTag: Int, promise: Promise) {
    useCameraView(viewTag, false) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera view not found")
      } else {
        try {
          val result: WritableArray = Arguments.createArray()
          val ids: List<Properties?>? = it.cameraIds
          if (ids != null) {
            for (p in ids) {
              if (p != null) {
                val m: WritableMap = WritableNativeMap()
                m.putString("id", p.getProperty("id"))
                m.putInt("type", Integer.valueOf(p.getProperty("type")))
                result.pushMap(m)
              }
            }
          }
          promise.resolve(result)
        } catch (e: Exception) {
          promise.reject("E_GET_CAMERA_IDS_FAILED", e.message)
        }
      }
    }
  }

  @ReactMethod
  fun getAvailablePictureSizes(ratio: String, viewTag: Int, promise: Promise) {
    useCameraView(viewTag, false) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera view not found")
      } else {
        try {
          val result: WritableArray = Arguments.createArray()
          val sizes: SortedSet<Size?>? = it.getAvailablePictureSizes(AspectRatio.parse(ratio))
          if (sizes != null) {
            for (size in sizes) {
              if (size != null) {
                result.pushString(size.toString())
              }
            }
          }
          promise.resolve(result)
        } catch (e: Exception) {
          promise.reject("E_GET_AVAILABLE_PICTURE_SIZES_FAILED", e.message)
        }
      }
    }
  }

  @ReactMethod
  fun checkIfRecordAudioPermissionsAreDefined(promise: Promise) {
    try {
      val info: PackageInfo? = currentActivity?.packageManager?.getPackageInfo(
        reactApplicationContext.packageName,
        PackageManager.GET_PERMISSIONS
      )
      if (info?.requestedPermissions != null) {
        for (p in info.requestedPermissions) {
          if (p == Manifest.permission.RECORD_AUDIO) {
            promise.resolve(true)
            return
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    promise.resolve(false)
  }

  @ReactMethod
  fun getSupportedPreviewFpsRange(viewTag: Int, promise: Promise) {
    useCameraView(viewTag, false) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera view not found")
      } else {
        try {
          val result: WritableArray = Arguments.createArray()
          val ranges: ArrayList<IntArray?>? = it.supportedPreviewFpsRange
          if (ranges != null) {
            for (range in ranges) {
              if (range != null) {
                val m: WritableMap = WritableNativeMap()
                m.putInt("MAXIMUM_FPS", range[0])
                m.putInt("MINIMUM_FPS", range[1])
                result.pushMap(m)
              }
            }
          }
          promise.resolve(result)
        } catch (e: Exception) {
          promise.reject("E_GET_SUPPORTED_PREVIEW_FPS_RANGE_FAILED", e.message)
        }
      }
    }
  }

  companion object {
    private const val TAG = "CameraModule"
    const val VIDEO_2160P = 0
    const val VIDEO_1080P = 1
    const val VIDEO_720P = 2
    const val VIDEO_480P = 3
    const val VIDEO_4x3 = 4
    const val GOOGLE_VISION_BARCODE_MODE_NORMAL = 0
    const val GOOGLE_VISION_BARCODE_MODE_ALTERNATE = 1
    const val GOOGLE_VISION_BARCODE_MODE_INVERTED = 2
    private val barCodeConstants: Map<String, Any> = Collections.unmodifiableMap(object : HashMap<String, Any>() {
      init {
        put("aztec", BarcodeFormat.AZTEC.toString())
        put("ean13", BarcodeFormat.EAN_13.toString())
        put("ean8", BarcodeFormat.EAN_8.toString())
        put("qr", BarcodeFormat.QR_CODE.toString())
        put("pdf417", BarcodeFormat.PDF_417.toString())
        put("upc_e", BarcodeFormat.UPC_E.toString())
        put("datamatrix", BarcodeFormat.DATA_MATRIX.toString())
        put("code39", BarcodeFormat.CODE_39.toString())
        put("code93", BarcodeFormat.CODE_93.toString())
        put("interleaved2of5", BarcodeFormat.ITF.toString())
        put("codabar", BarcodeFormat.CODABAR.toString())
        put("code128", BarcodeFormat.CODE_128.toString())
        put("maxicode", BarcodeFormat.MAXICODE.toString())
        put("rss14", BarcodeFormat.RSS_14.toString())
        put("rssexpanded", BarcodeFormat.RSS_EXPANDED.toString())
        put("upc_a", BarcodeFormat.UPC_A.toString())
        put("upc_ean", BarcodeFormat.UPC_EAN_EXTENSION.toString())
      }
    })
  }
}
