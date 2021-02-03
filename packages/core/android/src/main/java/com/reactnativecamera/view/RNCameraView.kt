package com.reactnativecamera.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.media.CamcorderProfile
import android.os.AsyncTask
import android.os.Build
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ThemedReactContext
import com.reactnativecamera.Constants
import com.reactnativecamera.Plugin
import com.reactnativecamera.PluginDelegate
import com.reactnativecamera.tasks.PictureSavedDelegate
import com.reactnativecamera.tasks.ResolveTakenPictureAsyncTask
import com.reactnativecamera.utils.RNFileUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class RNCameraView(themedReactContext: ThemedReactContext) : CameraView(themedReactContext, true),
  LifecycleEventListener, PluginDelegate, PictureSavedDelegate {
  private val mThemedReactContext: ThemedReactContext = themedReactContext
  private val mPictureTakenPromises: Queue<Promise> = ConcurrentLinkedQueue<Promise>()
  private val mPictureTakenOptions: MutableMap<Promise, ReadableMap> = ConcurrentHashMap()
  private val mPictureTakenDirectories: MutableMap<Promise, File> = ConcurrentHashMap()
  private var mVideoRecordedPromise: Promise? = null
  private var mScaleGestureDetector: ScaleGestureDetector? = null
  private var mGestureDetector: GestureDetector? = null
  private var mIsPaused = false
  private var mIsNew = true
  private var mIsRecording = false
  private var mIsRecordingInterrupted = false
  private var mUseNativeZoom = false

  var plugins = mutableMapOf<String, Plugin>()

  private var mShouldDetectTouches = false
  private var mPaddingX = 0
  private var mPaddingY = 0

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val preview = view ?: return
    val width = (right - left).toFloat()
    val height = (bottom - top).toFloat()
    val ratio = aspectRatio!!.toFloat()
    val orientation = resources.configuration.orientation
    val correctHeight: Int
    val correctWidth: Int
    setBackgroundColor(Color.BLACK)
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      if (ratio * height < width) {
        correctHeight = (width / ratio).toInt()
        correctWidth = width.toInt()
      } else {
        correctWidth = (height * ratio).toInt()
        correctHeight = height.toInt()
      }
    } else {
      if (ratio * width > height) {
        correctHeight = (width * ratio).toInt()
        correctWidth = width.toInt()
      } else {
        correctWidth = (height / ratio).toInt()
        correctHeight = height.toInt()
      }
    }
    val paddingX = ((width - correctWidth) / 2).toInt()
    val paddingY = ((height - correctHeight) / 2).toInt()
    mPaddingX = paddingX
    mPaddingY = paddingY
    preview.layout(paddingX, paddingY, correctWidth + paddingX, correctHeight + paddingY)
  }

  override val cameraView: RNCameraView
    get() = this

  @SuppressLint("all")
  override fun requestLayout() {
    // React handles this for us, so we don't need to call super.requestLayout();
  }

  fun takePicture(options: ReadableMap, promise: Promise, cacheDirectory: File) {
    mBgHandler.post {
      mPictureTakenPromises.add(promise)
      mPictureTakenOptions[promise] = options
      mPictureTakenDirectories[promise] = cacheDirectory
      try {
        super@RNCameraView.takePicture(options)
      } catch (e: Exception) {
        mPictureTakenPromises.remove(promise)
        mPictureTakenOptions.remove(promise)
        mPictureTakenDirectories.remove(promise)
        promise.reject("E_TAKE_PICTURE_FAILED", e.message)
      }
    }
  }

  override fun onPictureSaved(response: WritableMap) {
    RNCameraViewHelper.emitPictureSavedEvent(this, response)
  }

  fun record(options: ReadableMap, promise: Promise, cacheDirectory: File) {
    mBgHandler.post {
      try {
        val path: String = if (options.hasKey("path")) {
          options.getString("path")!!
        } else RNFileUtils.getOutputFilePath(cacheDirectory, ".mp4")
        val maxDuration = if (options.hasKey("maxDuration")) options.getInt("maxDuration") else -1
        val maxFileSize = if (options.hasKey("maxFileSize")) options.getInt("maxFileSize") else -1
        val fps = if (options.hasKey("fps")) options.getInt("fps") else -1
        var profile: CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
        if (options.hasKey("quality")) {
          profile = RNCameraViewHelper.getCamcorderProfile(options.getInt("quality"))
        }
        if (options.hasKey("videoBitrate")) {
          profile.videoBitRate = options.getInt("videoBitrate")
        }
        var recordAudio = true
        if (options.hasKey("mute")) {
          recordAudio = !options.getBoolean("mute")
        }
        var orientation = Constants.ORIENTATION_AUTO
        if (options.hasKey("orientation")) {
          orientation = options.getInt("orientation")
        }
        if (super@RNCameraView.record(path, maxDuration * 1000, maxFileSize, recordAudio, profile, orientation, fps)) {
          mIsRecording = true
          mVideoRecordedPromise = promise
        } else {
          promise.reject("E_RECORDING_FAILED", "Starting video recording failed. Another recording might be in progress.")
        }
      } catch (e: IOException) {
        promise.reject("E_RECORDING_FAILED", "Starting video recording failed - could not create video file.")
      }
    }
  }

  fun setShouldDetectTouches(shouldDetectTouches: Boolean) {
    mGestureDetector = if (!mShouldDetectTouches && shouldDetectTouches) {
      GestureDetector(mThemedReactContext, onGestureListener)
    } else {
      null
    }
    mShouldDetectTouches = shouldDetectTouches
  }

  fun setUseNativeZoom(useNativeZoom: Boolean) {
    mScaleGestureDetector = if (!mUseNativeZoom && useNativeZoom) {
      ScaleGestureDetector(mThemedReactContext, onScaleGestureListener)
    } else {
      null
    }
    mUseNativeZoom = useNativeZoom
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (mUseNativeZoom) {
      mScaleGestureDetector?.onTouchEvent(event)
    }
    if (mShouldDetectTouches) {
      mGestureDetector!!.onTouchEvent(event)
    }
    return true
  }


  override fun onHostResume() {
    if (hasCameraPermissions()) {
      mBgHandler.post {
        if (mIsPaused && !isCameraOpened || mIsNew) {
          mIsPaused = false
          mIsNew = false
          start()
        }
      }
    } else {
      RNCameraViewHelper.emitMountErrorEvent(this, "Camera permissions not granted - component could not be rendered.")
    }
  }

  override fun onHostPause() {
    if (mIsRecording) {
      mIsRecordingInterrupted = true
    }
    if (!mIsPaused && isCameraOpened) {
      mIsPaused = true
      stop()
    }
  }

  override fun onHostDestroy() {
    mThemedReactContext.removeLifecycleEventListener(this)

    // camera release can be quite expensive. Run in on bg handler
    // and cleanup last once everything has finished
    mBgHandler.post {
      stop()
      cleanup()
    }
  }

  private fun onZoom(scale: Float) {
    val currentZoom = zoom
    val nextZoom = currentZoom + (scale - 1.0f)
    zoom = if (nextZoom > currentZoom) {
      Math.min(nextZoom, 1.0f)
    } else {
      Math.max(nextZoom, 0.0f)
    }
  }

  private fun hasCameraPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val result: Int = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
      result == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  private fun scalePosition(raw: Float): Int {
    val resources: Resources = resources
    val config: Configuration = resources.getConfiguration()
    val dm: DisplayMetrics = resources.getDisplayMetrics()
    return (raw / dm.density) as Int
  }

  private val onGestureListener: GestureDetector.SimpleOnGestureListener = object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(e: MotionEvent): Boolean {
      RNCameraViewHelper.emitTouchEvent(this@RNCameraView, false, scalePosition(e.x), scalePosition(e.y))
      return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
      RNCameraViewHelper.emitTouchEvent(this@RNCameraView, true, scalePosition(e.x), scalePosition(e.y))
      return true
    }
  }
  private val onScaleGestureListener: ScaleGestureDetector.OnScaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {
    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
      onZoom(scaleGestureDetector.scaleFactor)
      return true
    }

    override fun onScaleBegin(scaleGestureDetector: ScaleGestureDetector): Boolean {
      onZoom(scaleGestureDetector.scaleFactor)
      return true
    }

    override fun onScaleEnd(scaleGestureDetector: ScaleGestureDetector) {}
  }

  init {
    themedReactContext.addLifecycleEventListener(this)
    addCallback(object : Callback() {
      override fun onCameraOpened(cameraView: CameraView) {
        RNCameraViewHelper.emitCameraReadyEvent(cameraView)
      }

      override fun onMountError(cameraView: CameraView) {
        RNCameraViewHelper.emitMountErrorEvent(cameraView, "Camera view threw an error - component could not be rendered.")
      }

      override fun onPictureTaken(cameraView: CameraView, data: ByteArray, deviceOrientation: Int) {
        val promise: Promise = mPictureTakenPromises.poll()
        val options: ReadableMap = mPictureTakenOptions.remove(promise)!!
        if (options.hasKey("fastMode") && options.getBoolean("fastMode")) {
          promise.resolve(null)
        }
        val cacheDirectory: File = mPictureTakenDirectories.remove(promise)!!
        ResolveTakenPictureAsyncTask(
          data,
          promise,
          options,
          cacheDirectory,
          deviceOrientation,
          this@RNCameraView
        ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        RNCameraViewHelper.emitPictureTakenEvent(cameraView)
      }

      override fun onRecordingStart(cameraView: CameraView, path: String, videoOrientation: Int, deviceOrientation: Int) {
        val result: WritableMap = Arguments.createMap()
        result.putInt("videoOrientation", videoOrientation)
        result.putInt("deviceOrientation", deviceOrientation)
        result.putString("uri", RNFileUtils.uriFromFile(File(path)).toString())
        RNCameraViewHelper.emitRecordingStartEvent(cameraView, result)
      }

      override fun onRecordingEnd(cameraView: CameraView) {
        RNCameraViewHelper.emitRecordingEndEvent(cameraView)
      }

      override fun onVideoRecorded(cameraView: CameraView, path: String?, videoOrientation: Int, deviceOrientation: Int) {
        val promise = mVideoRecordedPromise
        if (promise != null) {
          if (path != null) {
            val result: WritableMap = Arguments.createMap()
            result.putBoolean("isRecordingInterrupted", mIsRecordingInterrupted)
            result.putInt("videoOrientation", videoOrientation)
            result.putInt("deviceOrientation", deviceOrientation)
            result.putString("uri", RNFileUtils.uriFromFile(File(path)).toString())
            promise.resolve(result)
          } else {
            promise.reject("E_RECORDING", "Couldn't stop recording - there is none in progress")
          }
          mIsRecording = false
          mIsRecordingInterrupted = false
          mVideoRecordedPromise = null
        }
      }

      override fun onFramePreview(cameraView: CameraView, data: ByteArray, width: Int, height: Int, orientation: Int) {
        plugins.values.forEach {
          it.onFramePreview(cameraView, data, width, height, orientation)
        }
      }
    })
  }
}
