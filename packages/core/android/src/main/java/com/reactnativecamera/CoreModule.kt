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

  private val pluginManagers = mutableMapOf<String, PluginManager<*>>()

  fun registerPluginManager(pluginManager: PluginManager<*>) {
    pluginManagers[pluginManager.name] = pluginManager
  }

  fun getPluginManager(name: String): PluginManager<*> {
    return pluginManagers[name] ?: throw Error("Camera plugin $name was not found")
  }

  override fun getName(): String {
    return "RNCameraModule"
  }

  override fun getConstants(): MutableMap<String, Any> {
    return mutableMapOf(
      "Type" to mapOf(
        "front" to Constants.FACING_FRONT,
        "back" to Constants.FACING_BACK
      ),
      "FlashMode" to mapOf(
        "off" to Constants.FLASH_OFF,
        "on" to Constants.FLASH_ON,
        "auto" to Constants.FLASH_AUTO,
        "torch" to Constants.FLASH_TORCH
      ),
      "AutoFocus" to mapOf(
        "on" to true,
        "off" to false
      ),
      "WhiteBalance" to mapOf(
        "auto" to Constants.WB_AUTO,
        "cloudy" to Constants.WB_CLOUDY,
        "sunny" to Constants.WB_SUNNY,
        "shadow" to Constants.WB_SHADOW,
        "fluorescent" to Constants.WB_FLUORESCENT,
        "incandescent" to Constants.WB_INCANDESCENT
      ),
      "VideoQuality" to mapOf(
        "2160p" to VIDEO_2160P,
        "1080p" to VIDEO_1080P,
        "720p" to VIDEO_720P,
        "480p" to VIDEO_480P,
        "4:3" to VIDEO_4x3
      ),
      "Orientation" to mapOf(
        "auto" to Constants.ORIENTATION_AUTO,
        "portrait" to Constants.ORIENTATION_UP,
        "portraitUpsideDown" to Constants.ORIENTATION_DOWN,
        "landscapeLeft" to Constants.ORIENTATION_LEFT,
        "landscapeRight" to Constants.ORIENTATION_RIGHT
      )
    )
  }

  private fun useCameraView(viewTag: Int, requireOpen: Boolean, run: (view: RNCameraView?) -> Unit) {
    val context: ReactApplicationContext = reactApplicationContext
    val uiManager: UIManagerModule = context.getNativeModule(UIManagerModule::class.java)
    uiManager.addUIBlock { nativeViewHierarchyManager ->
      val cameraView: RNCameraView
      try {
        cameraView = nativeViewHierarchyManager.resolveView(viewTag) as RNCameraView
        if (requireOpen) {
          if (cameraView.isCameraOpened) {
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
  fun takePicture(options: ReadableMap, viewTag: Int, promise: Promise) {
    val cacheDirectory: File = mScopedContext.cacheDirectory!!
    useCameraView(viewTag, true) {
      if (it == null) {
        promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running")
      } else {
        it.takePicture(options, promise, cacheDirectory)
      }
    }
  }

  @ReactMethod
  fun record(options: ReadableMap, viewTag: Int, promise: Promise) {
    val cacheDirectory: File = mScopedContext.cacheDirectory!!
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
        it!!.stopRecording()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun pauseRecording(viewTag: Int) {
    useCameraView(viewTag, true) {
      try {
        it!!.pauseRecording()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
  }

  @ReactMethod
  fun resumeRecording(viewTag: Int) {
    useCameraView(viewTag, true) {
      try {
        it!!.resumeRecording()
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
          val sizes: SortedSet<Size> = it.getAvailablePictureSizes(AspectRatio.parse(ratio))
          for (size in sizes) {
            result.pushString(size.toString())
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
          val ranges: ArrayList<IntArray> = it.supportedPreviewFpsRange
          for (range in ranges) {
            val m: WritableMap = WritableNativeMap()
            m.putInt("MAXIMUM_FPS", range[0])
            m.putInt("MINIMUM_FPS", range[1])
            result.pushMap(m)
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
    var instance: CoreModule? = null
    const val VIDEO_2160P = 0
    const val VIDEO_1080P = 1
    const val VIDEO_720P = 2
    const val VIDEO_480P = 3
    const val VIDEO_4x3 = 4
  }
}
