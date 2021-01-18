/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reactnativecamera.view

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.*
import android.media.CamcorderProfile
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.SurfaceHolder
import androidx.collection.SparseArrayCompat
import com.facebook.react.bridge.ReadableMap
import com.reactnativecamera.Constants
import com.reactnativecamera.utils.objectEquals
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

internal class Camera1(callback: Callback?, preview: PreviewImpl, bgHandler: Handler?) : CameraViewImpl(callback, preview, bgHandler), MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener, PreviewCallback {
  companion object {
    private const val INVALID_CAMERA_ID = -1
    private val FLASH_MODES = SparseArrayCompat<String>()
    private val WB_MODES = SparseArrayCompat<String>()
    private const val FOCUS_AREA_SIZE_DEFAULT = 300
    private const val FOCUS_METERING_AREA_WEIGHT_DEFAULT = 1000
    private const val DELAY_MILLIS_BEFORE_RESETTING_FOCUS = 3000

    init {
      FLASH_MODES.put(Constants.FLASH_OFF, Parameters.FLASH_MODE_OFF)
      FLASH_MODES.put(Constants.FLASH_ON, Parameters.FLASH_MODE_ON)
      FLASH_MODES.put(Constants.FLASH_TORCH, Parameters.FLASH_MODE_TORCH)
      FLASH_MODES.put(Constants.FLASH_AUTO, Parameters.FLASH_MODE_AUTO)
      FLASH_MODES.put(Constants.FLASH_RED_EYE, Parameters.FLASH_MODE_RED_EYE)
    }

    init {
      WB_MODES.put(Constants.WB_AUTO, Parameters.WHITE_BALANCE_AUTO)
      WB_MODES.put(Constants.WB_CLOUDY, Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT)
      WB_MODES.put(Constants.WB_SUNNY, Parameters.WHITE_BALANCE_DAYLIGHT)
      WB_MODES.put(Constants.WB_SHADOW, Parameters.WHITE_BALANCE_SHADE)
      WB_MODES.put(Constants.WB_FLUORESCENT, Parameters.WHITE_BALANCE_FLUORESCENT)
      WB_MODES.put(Constants.WB_INCANDESCENT, Parameters.WHITE_BALANCE_INCANDESCENT)
    }
  }

  private val mHandler = Handler()
  private var mCameraId = 0
  private var _mCameraId: String? = null
  private val isPictureCaptureInProgress = AtomicBoolean(false)
  var mCamera: Camera? = null

  // do not instantiate this every time since it allocates unnecessary resources
  var sound = MediaActionSound()
  private var mCameraParameters: Camera.Parameters? = null
  private val mCameraInfo = CameraInfo()
  private var mMediaRecorder: MediaRecorder? = null
  private var mVideoPath: String? = null
  private val mIsRecording = AtomicBoolean(false)
  private val mPreviewSizes = SizeMap()
  private var mIsPreviewActive = false
  private var mShowingPreview = true // preview enabled by default
  private val mPictureSizes = SizeMap()
  private var mPictureSize: Size? = null
  private var mAspectRatio: AspectRatio? = null
  private var mAutoFocus = false
  private var mFacing = 0
  private var mFlash = 0
  private var mExposure = 0f
  private var mDisplayOrientation = 0
  private var mDeviceOrientation = 0
  private var mOrientation = Constants.ORIENTATION_AUTO
  private var mZoom = 0f
  private var mWhiteBalance = 0
  private var mIsScanning = false
  private var mPlaySoundOnCapture = false
  private var mPlaySoundOnRecord = false
  private var mustUpdateSurface = false
  private var surfaceWasDestroyed = false
  private var mPreviewTexture: SurfaceTexture? = null
  private fun updateSurface() {
    if (mCamera != null) {

      // do not update surface if we are currently capturing
      // since it will break capture events/video due to the
      // pause preview calls
      // capture callbacks will handle it if needed afterwards.
      if (!isPictureCaptureInProgress.get() && !mIsRecording.get()) {
        mBgHandler.post(Runnable {
          synchronized(this@Camera1) {
            // check for camera null again since it might have changed
            if (mCamera != null) {
              mustUpdateSurface = false
              setUpPreview()
              adjustCameraParameters()

              // only start preview if we are showing it
              if (mShowingPreview) {
                startCameraPreview()
              }
            }
          }
        })
      } else {
        mustUpdateSurface = true
      }
    }
  }

  public override fun start(): Boolean {
    synchronized(this) {
      chooseCamera()
      if (!openCamera()) {
        mCallback.onMountError()
        // returning false will result in invoking this method again
        return true
      }

      // if our preview layer is not ready
      // do not set it up. Surface handler will do it for us
      // once ready.
      // This prevents some redundant camera work
      if (mPreview.isReady()) {
        setUpPreview()
        if (mShowingPreview) {
          startCameraPreview()
        }
      }
      return true
    }
  }

  public override fun stop() {

    // make sure no other threads are trying to do this at the same time
    // such as another call to stop from surface destroyed
    // or host destroyed. Should avoid crashes with concurrent calls
    synchronized(this) {
      if (mMediaRecorder != null) {
        try {
          mMediaRecorder!!.stop()
        } catch (e: RuntimeException) {
          Log.e("CAMERA_1::", "mMediaRecorder.stop() failed", e)
        }
        try {
          mMediaRecorder!!.reset()
          mMediaRecorder!!.release()
        } catch (e: RuntimeException) {
          Log.e("CAMERA_1::", "mMediaRecorder.release() failed", e)
        }
        mMediaRecorder = null
        if (mIsRecording.get()) {
          mCallback.onRecordingEnd()
          val deviceOrientation: Int = displayOrientationToOrientationEnum(mDeviceOrientation)
          mCallback.onVideoRecorded(mVideoPath, if (mOrientation != Constants.ORIENTATION_AUTO) mOrientation else deviceOrientation, deviceOrientation)
        }
      }
      if (mCamera != null) {
        mIsPreviewActive = false
        try {
          mCamera!!.stopPreview()
          mCamera!!.setPreviewCallback(null)
        } catch (e: Exception) {
          Log.e("CAMERA_1::", "stop preview cleanup failed", e)
        }
      }
      releaseCamera()
    }
  }

  // Suppresses Camera#setPreviewTexture
  @SuppressLint("NewApi")
  fun setUpPreview() {
    try {
      surfaceWasDestroyed = false
      if (mCamera != null) {
        if (mPreviewTexture != null) {
          mCamera!!.setPreviewTexture(mPreviewTexture)
        } else if (mPreview.outputClass == SurfaceHolder::class.java) {
          val needsToStopPreview = mIsPreviewActive
          if (needsToStopPreview) {
            mCamera!!.stopPreview()
            mIsPreviewActive = false
          }
          mCamera!!.setPreviewDisplay(mPreview.surfaceHolder)
          if (needsToStopPreview) {
            startCameraPreview()
          }
        } else {
          mCamera!!.setPreviewTexture(mPreview.surfaceTexture as SurfaceTexture)
        }
      }
    } catch (e: Exception) {
      Log.e("CAMERA_1::", "setUpPreview failed", e)
    }
  }

  private fun startCameraPreview() {
    // only start the preview if we didn't yet.
    if (!mIsPreviewActive && mCamera != null) {
      try {
        mIsPreviewActive = true
        mCamera!!.startPreview()
        if (mIsScanning) {
          mCamera!!.setPreviewCallback(this)
        }
      } catch (e: Exception) {
        mIsPreviewActive = false
        Log.e("CAMERA_1::", "startCameraPreview failed", e)
      }
    }
  }

  override fun resumePreview() {
    mBgHandler.post(object : Runnable {
      override fun run() {
        synchronized(this) {
          mShowingPreview = true
          startCameraPreview()
        }
      }
    })
  }

  override fun pausePreview() {
    synchronized(this) {
      mIsPreviewActive = false
      mShowingPreview = false
      if (mCamera != null) {
        mCamera!!.stopPreview()
      }
    }
  }

  public override fun isCameraOpened(): Boolean {
    return mCamera != null
  }

  public override fun setFacing(facing: Int) {
    if (mFacing == facing) {
      return
    }
    mFacing = facing
    mBgHandler.post {
      if (isCameraOpened) {
        stop()
        start()
      }
    }
  }

  public override fun getFacing(): Int {
    return mFacing
  }

  public override fun setCameraId(id: String) {
    if (!objectEquals(_mCameraId, id)) {
      _mCameraId = id

      // only update if our camera ID actually changes
      // from what we currently have.
      // Passing null will always yield true
      if (!objectEquals(_mCameraId, mCameraId.toString())) {
        // this will call chooseCamera
        mBgHandler.post {
          if (isCameraOpened) {
            stop()
            start()
          }
        }
      }
    }
  }

  public override fun getCameraId(): String {
    return (_mCameraId)!!
  }

  public override fun getSupportedAspectRatios(): Set<AspectRatio> {
    val idealAspectRatios = mPreviewSizes
    for (aspectRatio: AspectRatio? in idealAspectRatios.ratios()) {
      if (mPictureSizes.sizes(aspectRatio) == null) {
        idealAspectRatios.remove(aspectRatio)
      }
    }
    return idealAspectRatios.ratios()
  }

  public override fun getCameraIds(): List<Properties> {
    val ids: MutableList<Properties> = ArrayList()
    val info = CameraInfo()
    var i = 0
    val count = Camera.getNumberOfCameras()
    while (i < count) {
      val p = Properties()
      Camera.getCameraInfo(i, info)
      p["id"] = i.toString()
      p["type"] = info.facing.toString()
      ids.add(p)
      i++
    }
    return ids
  }

  public override fun getAvailablePictureSizes(ratio: AspectRatio): SortedSet<Size> {
    return (mPictureSizes.sizes(ratio))!!
  }

  // Returns the best available size match for a given
  // width and height
  // returns the biggest available size
  private fun getBestSizeMatch(desiredWidth: Int, desiredHeight: Int, sizes: SortedSet<Size>?): Size? {
    if (sizes == null || sizes.isEmpty()) {
      return null
    }
    var result = sizes.last()

    // iterate from smallest to largest, and stay with the closest-biggest match
    if (desiredWidth != 0 && desiredHeight != 0) {
      for (size: Size in sizes) {
        if (desiredWidth <= size.width && desiredHeight <= size.height) {
          result = size
          break
        }
      }
    }
    return result
  }

  public override fun setPictureSize(size: Size) {

    // if no changes, don't do anything
    if (mPictureSize == null) {
      return
    } else if (size == mPictureSize) {
      return
    }
    mPictureSize = size

    // if camera is opened, request parameters update
    if (isCameraOpened) {
      mBgHandler.post {
        synchronized(this@Camera1) {
          if (mCamera != null) {
            adjustCameraParameters()
          }
        }
      }
    }
  }

  public override fun getPictureSize(): Size {
    return (mPictureSize)!!
  }

  public override fun setAspectRatio(ratio: AspectRatio): Boolean {
    if (mAspectRatio == null || !isCameraOpened) {
      // Handle this later when camera is opened
      mAspectRatio = ratio
      return true
    } else if (!mAspectRatio!!.equals(ratio)) {
      val sizes: Set<Size>? = mPreviewSizes.sizes(ratio)
      if (sizes == null) {
        // do nothing, ratio remains unchanged. Consistent with Camera2 and initial mount behaviour
        Log.w("CAMERA_1::", "setAspectRatio received an unsupported value and will be ignored.")
      } else {
        mAspectRatio = ratio
        mBgHandler.post {
          synchronized(this@Camera1) {
            if (mCamera != null) {
              adjustCameraParameters()
            }
          }
        }
        return true
      }
    }
    return false
  }

  public override fun getAspectRatio(): AspectRatio {
    return (mAspectRatio)!!
  }

  public override fun setAutoFocus(autoFocus: Boolean) {
    if (mAutoFocus == autoFocus) {
      return
    }
    synchronized(this) {
      if (setAutoFocusInternal(autoFocus)) {
        try {
          if (mCamera != null) {
            mCamera!!.setParameters(mCameraParameters)
          }
        } catch (e: RuntimeException) {
          Log.e("CAMERA_1::", "setParameters failed", e)
        }
      }
    }
  }

  public override fun getAutoFocus(): Boolean {
    if (!isCameraOpened) {
      return mAutoFocus
    }
    val focusMode = mCameraParameters!!.focusMode
    return focusMode != null && focusMode.contains("continuous")
  }

  public override fun setFlash(flash: Int) {
    if (flash == mFlash) {
      return
    }
    if (setFlashInternal(flash)) {
      try {
        if (mCamera != null) {
          mCamera!!.parameters = mCameraParameters
        }
      } catch (e: RuntimeException) {
        Log.e("CAMERA_1::", "setParameters failed", e)
      }
    }
  }

  public override fun getFlash(): Int {
    return mFlash
  }

  public override fun getExposureCompensation(): Float {
    return mExposure
  }

  public override fun setExposureCompensation(exposure: Float) {
    if (exposure == mExposure) {
      return
    }
    if (setExposureInternal(exposure)) {
      try {
        if (mCamera != null) {
          mCamera!!.parameters = mCameraParameters
        }
      } catch (e: RuntimeException) {
        Log.e("CAMERA_1::", "setParameters failed", e)
      }
    }
  }

  public override fun setFocusDepth(value: Float) {
    // not supported for Camera1
  }

  public override fun getFocusDepth(): Float {
    return 0 as Float
  }

  public override fun setZoom(zoom: Float) {
    if (zoom == mZoom) {
      return
    }
    if (setZoomInternal(zoom)) {
      try {
        if (mCamera != null) {
          mCamera!!.parameters = mCameraParameters
        }
      } catch (e: RuntimeException) {
        Log.e("CAMERA_1::", "setParameters failed", e)
      }
    }
  }

  public override fun getZoom(): Float {
    return mZoom
  }

  public override fun setWhiteBalance(whiteBalance: Int) {
    if (whiteBalance == mWhiteBalance) {
      return
    }
    if (setWhiteBalanceInternal(whiteBalance)) {
      try {
        if (mCamera != null) {
          mCamera!!.parameters = mCameraParameters
        }
      } catch (e: RuntimeException) {
        Log.e("CAMERA_1::", "setParameters failed", e)
      }
    }
  }

  public override fun getWhiteBalance(): Int {
    return mWhiteBalance
  }

  public override fun setScanning(isScanning: Boolean) {
    if (isScanning == mIsScanning) {
      return
    }
    setScanningInternal(isScanning)
  }

  public override fun getScanning(): Boolean {
    return mIsScanning
  }

  public override fun takePicture(options: ReadableMap) {
    if (!isCameraOpened) {
      throw IllegalStateException(
        "Camera is not ready. Call start() before takePicture().")
    }
    if (!mIsPreviewActive) {
      throw IllegalStateException("Preview is paused - resume it before taking a picture.")
    }

    // UPDATE: Take picture right away instead of requesting/waiting for focus.
    // This will match closer what the native camera does,
    // and will capture whatever is on the preview without changing the camera focus.
    // This change will also help with autoFocusPointOfInterest not being usable to capture (Issue #2420)
    // and with takePicture never returning/resolving if the focus was reset (Issue #2421)
    takePictureInternal(options)
  }

  fun orientationEnumToRotation(orientation: Int): Int {
    return when (orientation) {
      Constants.ORIENTATION_UP -> 0
      Constants.ORIENTATION_DOWN -> 180
      Constants.ORIENTATION_LEFT -> 270
      Constants.ORIENTATION_RIGHT -> 90
      else -> Constants.ORIENTATION_UP
    }
  }

  fun displayOrientationToOrientationEnum(rotation: Int): Int {
    return when (rotation) {
      0 -> Constants.ORIENTATION_UP
      90 -> Constants.ORIENTATION_RIGHT
      180 -> Constants.ORIENTATION_DOWN
      270 -> Constants.ORIENTATION_LEFT
      else -> 1
    }
  }

  fun takePictureInternal(options: ReadableMap) {
    // if not capturing already, atomically set it to true
    if (!mIsRecording.get() && isPictureCaptureInProgress.compareAndSet(false, true)) {
      try {
        if (options.hasKey("orientation") && options.getInt("orientation") != Constants.ORIENTATION_AUTO) {
          mOrientation = options.getInt("orientation")
          val rotation = orientationEnumToRotation(mOrientation)
          mCameraParameters!!.setRotation(calcCameraRotation(rotation))
          try {
            mCamera!!.parameters = mCameraParameters
          } catch (e: RuntimeException) {
            Log.e("CAMERA_1::", "setParameters rotation failed", e)
          }
        }

        // set quality on capture since we might not process the image bitmap if not needed now.
        // This also achieves a much faster JPEG compression speed since it's done on the hardware
        if (options.hasKey("quality")) {
          mCameraParameters!!.jpegQuality = (options.getDouble("quality") * 100).toInt()
          try {
            mCamera!!.parameters = mCameraParameters
          } catch (e: RuntimeException) {
            Log.e("CAMERA_1::", "setParameters quality failed", e)
          }
        }
        mCamera!!.takePicture(null, null, null, object : PictureCallback {
          override fun onPictureTaken(data: ByteArray, camera: Camera) {

            // this shouldn't be needed and messes up autoFocusPointOfInterest
            // camera.cancelAutoFocus();
            if (mPlaySoundOnCapture) {
              sound.play(MediaActionSound.SHUTTER_CLICK)
            }

            // our camera might have been released
            // when this callback fires, so make sure we have
            // exclusive access when restoring its preview
            synchronized(this@Camera1) {
              if (mCamera != null) {
                if (options.hasKey("pauseAfterCapture") && !options.getBoolean("pauseAfterCapture")) {
                  mCamera!!.startPreview()
                  mIsPreviewActive = true
                  if (mIsScanning) {
                    mCamera!!.setPreviewCallback(this@Camera1)
                  }
                } else {
                  mCamera!!.stopPreview()
                  mIsPreviewActive = false
                  mCamera!!.setPreviewCallback(null)
                }
              }
            }
            isPictureCaptureInProgress.set(false)
            mOrientation = Constants.ORIENTATION_AUTO
            mCallback.onPictureTaken(data, displayOrientationToOrientationEnum(mDeviceOrientation))
            if (mustUpdateSurface) {
              updateSurface()
            }
          }
        })
      } catch (e: Exception) {
        isPictureCaptureInProgress.set(false)
        throw e
      }
    } else {
      throw IllegalStateException("Camera capture failed. Camera is already capturing.")
    }
  }

  public override fun record(path: String, maxDuration: Int, maxFileSize: Int, recordAudio: Boolean, profile: CamcorderProfile, orientation: Int, fps: Int): Boolean {

    // make sure compareAndSet is last because we are setting it
    if (!isPictureCaptureInProgress.get() && mIsRecording.compareAndSet(false, true)) {
      if (orientation != Constants.ORIENTATION_AUTO) {
        mOrientation = orientation
      }
      try {
        setUpMediaRecorder(path, maxDuration, maxFileSize, recordAudio, profile, fps)
        mMediaRecorder!!.prepare()
        mMediaRecorder!!.start()

        // after our media recorder is set and started, we must update
        // some camera parameters again because the recorder's exclusive access (after unlock is called)
        // might interfere with the camera parameters (e.g., flash and zoom)
        // This should also be safe to call since both recording and
        // camera parameters are getting set by the same thread and process.
        // https://stackoverflow.com/a/14855668/1777914
        try {
          mCamera!!.parameters = mCameraParameters
        } catch (e: Exception) {
          Log.e("CAMERA_1::", "Record setParameters failed", e)
        }
        val deviceOrientation = displayOrientationToOrientationEnum(mDeviceOrientation)
        mCallback.onRecordingStart(path, if (mOrientation != Constants.ORIENTATION_AUTO) mOrientation else deviceOrientation, deviceOrientation)
        if (mPlaySoundOnRecord) {
          sound.play(MediaActionSound.START_VIDEO_RECORDING)
        }
        return true
      } catch (e: Exception) {
        mIsRecording.set(false)
        Log.e("CAMERA_1::", "Record start failed", e)
        return false
      }
    }
    return false
  }

  public override fun stopRecording() {
    if (mIsRecording.compareAndSet(true, false)) {
      stopMediaRecorder()
      if (mCamera != null) {
        mCamera!!.lock()
      }
      if (mustUpdateSurface) {
        updateSurface()
      }
    }
  }

  public override fun pauseRecording() {
    pauseMediaRecorder()
  }

  public override fun resumeRecording() {
    resumeMediaRecorder()
  }

  public override fun getCameraOrientation(): Int {
    return mCameraInfo.orientation
  }

  public override fun setDisplayOrientation(displayOrientation: Int) {
    synchronized(this) {
      if (mDisplayOrientation == displayOrientation) {
        return
      }
      mDisplayOrientation = displayOrientation
      if (isCameraOpened) {
        val needsToStopPreview: Boolean = mIsPreviewActive
        if (needsToStopPreview) {
          mCamera!!.stopPreview()
          mIsPreviewActive = false
        }
        try {
          mCamera!!.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
        } catch (e: RuntimeException) {
          Log.e("CAMERA_1::", "setDisplayOrientation failed", e)
        }
        if (needsToStopPreview) {
          startCameraPreview()
        }
      }
    }
  }

  public override fun setDeviceOrientation(deviceOrientation: Int) {
    synchronized(this) {
      if (mDeviceOrientation == deviceOrientation) {
        return
      }
      mDeviceOrientation = deviceOrientation
      if (isCameraOpened && (mOrientation == Constants.ORIENTATION_AUTO) && !mIsRecording.get() && !isPictureCaptureInProgress.get()) {
        mCameraParameters!!.setRotation(calcCameraRotation(deviceOrientation))
        try {
          mCamera!!.setParameters(mCameraParameters)
        } catch (e: RuntimeException) {
          Log.e("CAMERA_1::", "setParameters failed", e)
        }
      }
    }
  }

  override fun setPreviewTexture(surfaceTexture: SurfaceTexture) {
    mBgHandler.post(object : Runnable {
      override fun run() {
        try {
          if (mCamera == null) {
            mPreviewTexture = surfaceTexture
            return
          }
          mCamera!!.stopPreview()
          mIsPreviewActive = false
          if (surfaceTexture == null) {
            mCamera!!.setPreviewTexture(mPreview.surfaceTexture as SurfaceTexture)
          } else {
            mCamera!!.setPreviewTexture(surfaceTexture)
          }
          mPreviewTexture = surfaceTexture
          startCameraPreview()
        } catch (e: IOException) {
          Log.e("CAMERA_1::", "setPreviewTexture failed", e)
        }
      }
    })
  }

  override fun getPreviewSize(): Size {
    val cameraSize = mCameraParameters!!.previewSize
    return Size(cameraSize.width, cameraSize.height)
  }

  /**
   * This rewrites [.mCameraId] and [.mCameraInfo].
   */
  private fun chooseCamera() {
    if (_mCameraId == null) {
      try {
        val count = Camera.getNumberOfCameras()
        if (count == 0) {
          //throw new RuntimeException("No camera available.");
          mCameraId = INVALID_CAMERA_ID
          Log.w("CAMERA_1::", "getNumberOfCameras returned 0. No camera available.")
          return
        }
        for (i in 0 until count) {
          Camera.getCameraInfo(i, mCameraInfo)
          if (mCameraInfo.facing == mFacing) {
            mCameraId = i
            return
          }
        }
        // no camera found, set the one we have
        mCameraId = 0
        Camera.getCameraInfo(mCameraId, mCameraInfo)
      } // getCameraInfo may fail if hardware is unavailable
      // and crash the whole app. Return INVALID_CAMERA_ID
      // which will in turn fire a mount error event
      catch (e: Exception) {
        Log.e("CAMERA_1::", "chooseCamera failed.", e)
        mCameraId = INVALID_CAMERA_ID
      }
    } else {
      try {
        mCameraId = _mCameraId!!.toInt()
        Camera.getCameraInfo(mCameraId, mCameraInfo)
      } catch (e: Exception) {
        mCameraId = INVALID_CAMERA_ID
      }
    }
  }

  private fun openCamera(): Boolean {
    if (mCamera != null) {
      releaseCamera()
    }

    // in case we got an invalid camera ID
    // due to no cameras or invalid ID provided,
    // return false so we can raise a mount error
    if (mCameraId == INVALID_CAMERA_ID) {
      return false
    }

    val camera = mCamera ?: return false

    try {
      mCamera = Camera.open(mCameraId)
      val cameraParameters = camera.parameters
      mCameraParameters = cameraParameters

      // Supported preview sizes
      mPreviewSizes.clear()
      for (size: Camera.Size in cameraParameters.getSupportedPreviewSizes()) {
        mPreviewSizes.add(Size(size.width, size.height))
      }

      // Supported picture sizes;
      mPictureSizes.clear()
      for (size: Camera.Size in cameraParameters.getSupportedPictureSizes()) {
        mPictureSizes.add(Size(size.width, size.height))
      }

      // to be consistent with Camera2, and to prevent crashes on some devices
      // do not allow preview sizes that are not also in the picture sizes set
      for (aspectRatio: AspectRatio in mPreviewSizes.ratios()) {
        if (mPictureSizes.sizes(aspectRatio) == null) {
          mPreviewSizes.remove(aspectRatio)
        }
      }

      // AspectRatio
      if (mAspectRatio == null) {
        mAspectRatio = Constants.DEFAULT_ASPECT_RATIO
      }
      adjustCameraParameters()
      camera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation))
      mCallback.onCameraOpened()
      return true
    } catch (e: RuntimeException) {
      return false
    }
  }

  private fun chooseAspectRatio(): AspectRatio? {
    var r: AspectRatio? = null
    for (ratio: AspectRatio in mPreviewSizes.ratios()) {
      r = ratio
      if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
        return ratio
      }
    }
    return r
  }

  fun adjustCameraParameters() {
    var sizes = mPreviewSizes.sizes(mAspectRatio)
    if (sizes == null) { // Not supported
      Log.w("CAMERA_1::", "adjustCameraParameters received an unsupported aspect ratio value and will be ignored.")
      mAspectRatio = chooseAspectRatio()
      sizes = mPreviewSizes.sizes(mAspectRatio)
    }

    // make sure both preview and picture size are always
    // valid for the currently chosen camera and aspect ratio
    val size = chooseOptimalSize(sizes)
    var pictureSize: Size? = null

    // do not alter mPictureSize
    // since it may be valid for other camera/aspect ratio updates
    // just make sure we get the right and most suitable value
    if (mPictureSize != null) {
      pictureSize = getBestSizeMatch(
        mPictureSize!!.width,
        mPictureSize!!.height,
        mPictureSizes.sizes(mAspectRatio)
      )
    } else {
      pictureSize = getBestSizeMatch(
        0,
        0,
        mPictureSizes.sizes(mAspectRatio)
      )
    }
    val needsToStopPreview = mIsPreviewActive
    if (needsToStopPreview) {
      mCamera!!.stopPreview()
      mIsPreviewActive = false
    }
    mCameraParameters!!.setPreviewSize(size!!.width, size.height)
    mCameraParameters!!.setPictureSize(pictureSize!!.width, pictureSize.height)
    if (mOrientation != Constants.ORIENTATION_AUTO) {
      mCameraParameters!!.setRotation(calcCameraRotation(orientationEnumToRotation(mOrientation)))
    } else {
      mCameraParameters!!.setRotation(calcCameraRotation(mDeviceOrientation))
    }
    setAutoFocusInternal(mAutoFocus)
    setFlashInternal(mFlash)
    setExposureInternal(mExposure)
    aspectRatio = (mAspectRatio)!!
    setZoomInternal(mZoom)
    setWhiteBalanceInternal(mWhiteBalance)
    setScanningInternal(mIsScanning)
    setPlaySoundInternal(mPlaySoundOnCapture)
    try {
      mCamera!!.parameters = mCameraParameters
    } catch (e: RuntimeException) {
      Log.e("CAMERA_1::", "setParameters failed", e)
    }
    if (needsToStopPreview) {
      startCameraPreview()
    }
  }

  private fun chooseOptimalSize(sizes: SortedSet<Size>?): Size? {
    if (!mPreview.isReady) { // Not yet laid out
      return sizes!!.first() // Return the smallest size
    }
    val desiredWidth: Int
    val desiredHeight: Int
    val surfaceWidth = mPreview.width
    val surfaceHeight = mPreview.height
    if (isLandscape(mDisplayOrientation)) {
      desiredWidth = surfaceHeight
      desiredHeight = surfaceWidth
    } else {
      desiredWidth = surfaceWidth
      desiredHeight = surfaceHeight
    }
    var result: Size? = null
    for (size: Size in sizes!!) { // Iterate from small to large
      if (desiredWidth <= size.width && desiredHeight <= size.height) {
        return size
      }
      result = size
    }
    return result
  }

  private fun releaseCamera() {
    if (mCamera != null) {
      mCamera!!.release()
      mCamera = null
      mCallback.onCameraClosed()

      // reset these flags
      isPictureCaptureInProgress.set(false)
      mIsRecording.set(false)
    }
  }

  // Most credit: https://github.com/CameraKit/camerakit-android/blob/master/camerakit-core/src/main/api16/com/wonderkiln/camerakit/Camera1.java
  public override fun setFocusArea(x: Float, y: Float) {
    mBgHandler.post(object : Runnable {
      override fun run() {
        synchronized(this@Camera1) {
          if (mCamera != null) {

            // do not create a new object, use existing.
            val parameters: Parameters = mCameraParameters ?: return
            val focusMode: String? = parameters.focusMode
            val rect: Rect = calculateFocusArea(x, y)
            val meteringAreas: MutableList<Area> = ArrayList()
            meteringAreas.add(Area(rect, FOCUS_METERING_AREA_WEIGHT_DEFAULT))
            when {
                (parameters.maxNumFocusAreas != 0) && (focusMode != null) &&
                  (((focusMode == Camera.Parameters.FOCUS_MODE_AUTO) || (focusMode == Camera.Parameters.FOCUS_MODE_MACRO) || (focusMode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) || (focusMode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))) -> {
                  parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                  parameters.focusAreas = meteringAreas
                  if (parameters.maxNumMeteringAreas > 0) {
                    parameters.meteringAreas = meteringAreas
                  }
                  if (!parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    return  //cannot autoFocus
                  }
                  try {
                    mCamera!!.parameters = parameters
                  } catch (e: RuntimeException) {
                    Log.e("CAMERA_1::", "setParameters failed", e)
                  }
                  try {
                    mCamera!!.autoFocus { success, camera ->
                      //resetFocus(success, camera);
                    }
                  } catch (e: RuntimeException) {
                    Log.e("CAMERA_1::", "autoFocus failed", e)
                  }
                }
                parameters.maxNumMeteringAreas > 0 -> {
                  if (!parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    return  //cannot autoFocus
                  }
                  parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                  parameters.focusAreas = meteringAreas
                  parameters.meteringAreas = meteringAreas
                  try {
                    mCamera!!.parameters = parameters
                  } catch (e: RuntimeException) {
                    Log.e("CAMERA_1::", "setParameters failed", e)
                  }
                  try {
                    mCamera!!.autoFocus { _, _ ->
                      //resetFocus(success, camera);
                    }
                  } catch (e: RuntimeException) {
                    Log.e("CAMERA_1::", "autoFocus failed", e)
                  }
                }
                else -> {
                  try {
                    mCamera!!.autoFocus { _, _ ->
                      //mCamera.cancelAutoFocus();
                    }
                  } catch (e: RuntimeException) {
                    Log.e("CAMERA_1::", "autoFocus failed", e)
                  }
                }
            }
          }
        }
      }
    })
  }

  private fun resetFocus(success: Boolean, camera: Camera) {
    mHandler.removeCallbacksAndMessages(null)
    mHandler.postDelayed(object : Runnable {
      override fun run() {
        if (mCamera != null) {
          mCamera!!.cancelAutoFocus()

          // do not create a new object, use existing.
          val parameters = mCameraParameters ?: return
          if (parameters.focusMode !== Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            parameters.focusAreas = null
            parameters.meteringAreas = null
            try {
              mCamera!!.parameters = parameters
            } catch (e: RuntimeException) {
              Log.e("CAMERA_1::", "setParameters failed", e)
            }
          }
          mCamera!!.cancelAutoFocus()
        }
      }
    }, DELAY_MILLIS_BEFORE_RESETTING_FOCUS.toLong())
  }

  private fun calculateFocusArea(x: Float, y: Float): Rect {
    val padding = FOCUS_AREA_SIZE_DEFAULT / 2
    val centerX = (x * 2000).toInt()
    val centerY = (y * 2000).toInt()
    var left = centerX - padding
    var top = centerY - padding
    var right = centerX + padding
    var bottom = centerY + padding
    if (left < 0) left = 0
    if (right > 2000) right = 2000
    if (top < 0) top = 0
    if (bottom > 2000) bottom = 2000
    return Rect(left - 1000, top - 1000, right - 1000, bottom - 1000)
  }

  /**
   * Calculate display orientation
   * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
   *
   * This calculation is used for orienting the preview
   *
   * Note: This is not the same calculation as the camera rotation
   *
   * @param screenOrientationDegrees Screen orientation in degrees
   * @return Number of degrees required to rotate preview
   */
  private fun calcDisplayOrientation(screenOrientationDegrees: Int): Int {
    return if (mCameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
      (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360
    } else {  // back-facing
      (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360
    }
  }

  /**
   * Calculate camera rotation
   *
   * This calculation is applied to the output JPEG either via Exif Orientation tag
   * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
   *
   * Note: This is not the same calculation as the display orientation
   *
   * @param screenOrientationDegrees Screen orientation in degrees
   * @return Number of degrees to rotate image in order for it to view correctly.
   */
  private fun calcCameraRotation(screenOrientationDegrees: Int): Int {
    if (mCameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
      return (mCameraInfo.orientation + screenOrientationDegrees) % 360
    }
    // back-facing
    val landscapeFlip = if (isLandscape(screenOrientationDegrees)) 180 else 0
    return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
  }

  /**
   * Test if the supplied orientation is in landscape.
   *
   * @param orientationDegrees Orientation in degrees (0,90,180,270)
   * @return True if in landscape, false if portrait
   */
  private fun isLandscape(orientationDegrees: Int): Boolean {
    return (orientationDegrees == Constants.LANDSCAPE_90 ||
      orientationDegrees == Constants.LANDSCAPE_270)
  }

  /**
   * @return `true` if [.mCameraParameters] was modified.
   */
  private fun setAutoFocusInternal(autoFocus: Boolean): Boolean {
    mAutoFocus = autoFocus
    if (isCameraOpened) {
      val modes = mCameraParameters!!.supportedFocusModes
      if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        mCameraParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
      } else if (mIsScanning && modes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
        mCameraParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_MACRO
      } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
        mCameraParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
      } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
        mCameraParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
      } else {
        mCameraParameters!!.focusMode = modes[0]
      }
      return true
    } else {
      return false
    }
  }

  /**
   * @return `true` if [.mCameraParameters] was modified.
   */
  private fun setFlashInternal(flash: Int): Boolean {
    if (isCameraOpened) {
      val modes = mCameraParameters!!.supportedFlashModes
      val mode = FLASH_MODES[flash]
      if (modes == null) {
        return false
      }
      if (modes.contains(mode)) {
        mCameraParameters!!.flashMode = mode
        mFlash = flash
        return true
      }
      val currentMode = FLASH_MODES[mFlash]
      if (!modes.contains(currentMode)) {
        mCameraParameters!!.flashMode = Camera.Parameters.FLASH_MODE_OFF
        return true
      }
      return false
    } else {
      mFlash = flash
      return false
    }
  }

  private fun setExposureInternal(exposure: Float): Boolean {
    mExposure = exposure
    if (isCameraOpened) {
      val minExposure = mCameraParameters!!.minExposureCompensation
      val maxExposure = mCameraParameters!!.maxExposureCompensation
      if (minExposure != maxExposure) {
        var scaledValue = 0
        if (mExposure in 0.0..1.0) {
          scaledValue = (mExposure * (maxExposure - minExposure)).toInt() + minExposure
        }
        mCameraParameters!!.exposureCompensation = scaledValue
        return true
      }
    }
    return false
  }

  /**
   * @return `true` if [.mCameraParameters] was modified.
   */
  private fun setZoomInternal(zoom: Float): Boolean {
    if (isCameraOpened && mCameraParameters!!.isZoomSupported) {
      val maxZoom = mCameraParameters!!.maxZoom
      val scaledValue = (zoom * maxZoom).toInt()
      mCameraParameters!!.zoom = scaledValue
      mZoom = zoom
      return true
    } else {
      mZoom = zoom
      return false
    }
  }

  /**
   * @return `true` if [.mCameraParameters] was modified.
   */
  private fun setWhiteBalanceInternal(whiteBalance: Int): Boolean {
    mWhiteBalance = whiteBalance
    if (isCameraOpened) {
      val modes = mCameraParameters!!.supportedWhiteBalance
      val mode = WB_MODES[whiteBalance]
      if (modes != null && modes.contains(mode)) {
        mCameraParameters!!.whiteBalance = mode
        return true
      }
      val currentMode = WB_MODES[mWhiteBalance]
      if (modes == null || !modes.contains(currentMode)) {
        mCameraParameters!!.whiteBalance = Camera.Parameters.WHITE_BALANCE_AUTO
        return true
      }
      return false
    } else {
      return false
    }
  }

  private fun setScanningInternal(isScanning: Boolean) {
    mIsScanning = isScanning
    if (isCameraOpened) {
      if (mIsScanning) {
        mCamera!!.setPreviewCallback(this)
      } else {
        mCamera!!.setPreviewCallback(null)
      }
    }
  }

  private fun setPlaySoundInternal(playSoundOnCapture: Boolean) {
    mPlaySoundOnCapture = playSoundOnCapture
    if (mCamera != null) {
      try {
        // Always disable shutter sound, and play our own.
        // This is because not all devices honor this value when set to true
        val res = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
          mCamera!!.enableShutterSound(false)
        } else {
          false
        }

        // if we fail to disable the shutter sound
        // set mPlaySoundOnCapture to false since it means
        // we cannot change it and the system will play it
        // playing the sound ourselves also makes it consistent with Camera2
        if (!res) {
          mPlaySoundOnCapture = false
        }
      } catch (ex: Exception) {
        Log.e("CAMERA_1::", "setPlaySoundInternal failed", ex)
        mPlaySoundOnCapture = false
      }
    }
  }

  public override fun setPlaySoundOnCapture(playSoundOnCapture: Boolean) {
    if (playSoundOnCapture == mPlaySoundOnCapture) {
      return
    }
    setPlaySoundInternal(playSoundOnCapture)
  }

  public override fun getPlaySoundOnCapture(): Boolean {
    return mPlaySoundOnCapture
  }

  public override fun setPlaySoundOnRecord(playSoundOnRecord: Boolean) {
    mPlaySoundOnRecord = playSoundOnRecord
  }

  public override fun getPlaySoundOnRecord(): Boolean {
    return mPlaySoundOnRecord
  }

  override fun onPreviewFrame(data: ByteArray, camera: Camera) {
    val previewSize = mCameraParameters!!.previewSize
    mCallback.onFramePreview(data, previewSize.width, previewSize.height, mDeviceOrientation)
  }

  private fun setUpMediaRecorder(path: String, maxDuration: Int, maxFileSize: Int, recordAudio: Boolean, profile: CamcorderProfile, fps: Int) {
    mMediaRecorder = MediaRecorder()
    mCamera!!.unlock()
    mMediaRecorder!!.setCamera(mCamera)
    mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
    if (recordAudio) {
      mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
    }
    mMediaRecorder!!.setOutputFile(path)
    mVideoPath = path
    val camProfile: CamcorderProfile
    camProfile = if (CamcorderProfile.hasProfile(mCameraId, profile.quality)) {
      CamcorderProfile.get(mCameraId, profile.quality)
    } else {
      CamcorderProfile.get(mCameraId, CamcorderProfile.QUALITY_HIGH)
    }
    camProfile.videoBitRate = profile.videoBitRate
    setCamcorderProfile(camProfile, recordAudio, fps)
    mMediaRecorder!!.setOrientationHint(calcCameraRotation(if (mOrientation != Constants.ORIENTATION_AUTO) orientationEnumToRotation(mOrientation) else mDeviceOrientation))
    if (maxDuration != -1) {
      mMediaRecorder!!.setMaxDuration(maxDuration)
    }
    if (maxFileSize != -1) {
      mMediaRecorder!!.setMaxFileSize(maxFileSize.toLong())
    }
    mMediaRecorder!!.setOnInfoListener(this)
    mMediaRecorder!!.setOnErrorListener(this)
  }

  private fun stopMediaRecorder() {
    synchronized(this) {
      if (mMediaRecorder != null) {
        try {
          mMediaRecorder!!.stop()
        } catch (ex: RuntimeException) {
          Log.e("CAMERA_1::", "stopMediaRecorder stop failed", ex)
        }
        try {
          mMediaRecorder!!.reset()
          mMediaRecorder!!.release()
        } catch (ex: RuntimeException) {
          Log.e("CAMERA_1::", "stopMediaRecorder reset failed", ex)
        }
        mMediaRecorder = null
      }
      mCallback.onRecordingEnd()
      if (mPlaySoundOnRecord) {
        sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
      }
      val deviceOrientation: Int = displayOrientationToOrientationEnum(mDeviceOrientation)
      if (mVideoPath == null || !File(mVideoPath).exists()) {
        mCallback.onVideoRecorded(null, if (mOrientation != Constants.ORIENTATION_AUTO) mOrientation else deviceOrientation, deviceOrientation)
        return
      }
      mCallback.onVideoRecorded(mVideoPath, if (mOrientation != Constants.ORIENTATION_AUTO) mOrientation else deviceOrientation, deviceOrientation)
      mVideoPath = null
    }
  }

  private fun pauseMediaRecorder() {
    if (Build.VERSION.SDK_INT >= 24) {
      mMediaRecorder!!.pause()
    }
  }

  private fun resumeMediaRecorder() {
    if (Build.VERSION.SDK_INT >= 24) {
      mMediaRecorder!!.resume()
    }
  }

  override fun getSupportedPreviewFpsRange(): ArrayList<IntArray> {
    return mCameraParameters!!.supportedPreviewFpsRange as ArrayList<IntArray>
  }

  private fun isCompatibleWithDevice(fps: Int): Boolean {
    val validValues: ArrayList<IntArray> = supportedPreviewFpsRange
    val accurateFps = fps * 1000
    for (row: IntArray in validValues) {
      val isIncluded = accurateFps >= row[0] && accurateFps <= row[1]
      val greaterThenZero = accurateFps > 0
      val compatibleWithDevice = isIncluded && greaterThenZero
      if (compatibleWithDevice) return true
    }
    Log.w("CAMERA_1::", "fps (framePerSecond) received an unsupported value and will be ignored.")
    return false
  }

  private fun setCamcorderProfile(profile: CamcorderProfile, recordAudio: Boolean, fps: Int) {
    val compatibleFps = if (isCompatibleWithDevice(fps)) fps else profile.videoFrameRate
    mMediaRecorder!!.setOutputFormat(profile.fileFormat)
    mMediaRecorder!!.setVideoFrameRate(compatibleFps)
    mMediaRecorder!!.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
    mMediaRecorder!!.setVideoEncodingBitRate(profile.videoBitRate)
    mMediaRecorder!!.setVideoEncoder(profile.videoCodec)
    if (recordAudio) {
      mMediaRecorder!!.setAudioEncodingBitRate(profile.audioBitRate)
      mMediaRecorder!!.setAudioChannels(profile.audioChannels)
      mMediaRecorder!!.setAudioSamplingRate(profile.audioSampleRate)
      mMediaRecorder!!.setAudioEncoder(profile.audioCodec)
    }
  }

  override fun onInfo(mr: MediaRecorder, what: Int, extra: Int) {
    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
      what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
      stopRecording()
    }
  }

  override fun onError(mr: MediaRecorder, what: Int, extra: Int) {
    stopRecording()
  }

  init {
    preview.setCallback(object : PreviewImpl.Callback {
      override fun onSurfaceChanged() {

        // if we got our surface destroyed
        // we must re-start the camera and surface
        // otherwise, just update our surface
        synchronized(this@Camera1) {
          if (!surfaceWasDestroyed) {
            updateSurface()
          } else {
            mBgHandler.post { start() }
          }
        }
      }

      override fun onSurfaceDestroyed() {

        // need to this early so we don't get buffer errors due to sufrace going away.
        // Then call stop in bg thread since it might be quite slow and will freeze
        // the UI or cause an ANR while it is happening.
        synchronized(this@Camera1) {
          if (mCamera != null) {

            // let the instance know our surface was destroyed
            // and we might need to re-create it and restart the camera
            surfaceWasDestroyed = true
            try {
              mCamera!!.setPreviewCallback(null)
              // note: this might give a debug message that can be ignored.
              mCamera!!.setPreviewDisplay(null)
            } catch (e: Exception) {
              Log.e("CAMERA_1::", "onSurfaceDestroyed preview cleanup failed", e)
            }
          }
        }
        mBgHandler.post { stop() }
      }
    })
  }
}
