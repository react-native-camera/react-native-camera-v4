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
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraManager.AvailabilityCallback
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.ReadableMap
import com.reactnativecamera.Constants
import com.reactnativecamera.utils.objectEquals
import java.io.File
import java.io.IOException
import java.util.*

@TargetApi(21)
@RequiresApi(21)
internal open class Camera2(callback: Callback?, preview: PreviewImpl?, context: Context, bgHandler: Handler?) : CameraViewImpl(callback, preview, bgHandler), MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
  companion object {
    private const val TAG = "Camera2"
    private val INTERNAL_FACINGS = SparseIntArray()

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private const val MAX_PREVIEW_WIDTH = 1920

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private const val MAX_PREVIEW_HEIGHT = 1080
    private const val FOCUS_AREA_SIZE_DEFAULT = 300
    private const val FOCUS_METERING_AREA_WEIGHT_DEFAULT = 1000

    // This is a helper method to query Camera2 legacy status so we don't need
    // to instantiate and set all its props in order to check if it is legacy or not
    // and then fallback to Camera1. This way, legacy devices can fall back to Camera1 right away
    // This method makes sure all cameras are not legacy, so further checks are not needed.
    fun isLegacy(context: Context): Boolean {
      return try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = manager.cameraIdList
        for (id in ids) {
          val characteristics = manager.getCameraCharacteristics(id!!)
          val level = characteristics.get(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
          if (level == null ||
            level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            Log.w(TAG, "Camera2 can only run in legacy mode and should not be used.")
            return true
          }
        }
        false
      } catch (ex: CameraAccessException) {
        Log.e(TAG, "Failed to check camera legacy status, returning true.", ex)
        true
      }
    }

    init {
      INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
      INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
    }
  }

  private val mCameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  private val mCameraDeviceCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      mCamera = camera
      mCallback.onCameraOpened()
      startCaptureSession()
    }

    override fun onClosed(camera: CameraDevice) {
      mCallback.onCameraClosed()
    }

    override fun onDisconnected(camera: CameraDevice) {
      mCamera = null
    }

    override fun onError(camera: CameraDevice, error: Int) {
      Log.e(TAG, "onError: " + camera.id + " (" + error + ")")
      mCamera = null
    }
  }
  private val mSessionCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
    override fun onConfigured(session: CameraCaptureSession) {
      if (mCamera == null) {
        return
      }
      mCaptureSession = session
      mInitialCropRegion = mPreviewRequestBuilder!!.get(CaptureRequest.SCALER_CROP_REGION)
      updateAutoFocus()
      updateFlash()
      updateFocusDepth()
      updateWhiteBalance()
      updateZoom()
      try {
        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
          mCaptureCallback, null)
      } catch (e: CameraAccessException) {
        Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e)
      } catch (e: IllegalStateException) {
        Log.e(TAG, "Failed to start camera preview.", e)
      }
    }

    override fun onConfigureFailed(session: CameraCaptureSession) {
      Log.e(TAG, "Failed to configure capture session.")
    }

    override fun onClosed(session: CameraCaptureSession) {
      if (mCaptureSession != null && mCaptureSession == session) {
        mCaptureSession = null
      }
    }
  }
  private var mCaptureCallback: PictureCaptureCallback = object : PictureCaptureCallback() {
    override fun onPrecaptureRequired() {
      mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
      setState(STATE_PRECAPTURE)
      try {
        mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), this, null)
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
      } catch (e: CameraAccessException) {
        Log.e(TAG, "Failed to run precapture sequence.", e)
      }
    }

    override fun onReady() {
      captureStillPicture()
    }
  }
  private val mOnImageAvailableListener = OnImageAvailableListener { reader ->
    val image = reader.acquireNextImage()
    val planes: Array<Plane> = image.planes
    if (planes.isNotEmpty()) {
      val buffer = planes[0].buffer
      val data = ByteArray(buffer.remaining())
      buffer[data]
      if (image.format == ImageFormat.JPEG) {
        // @TODO: implement deviceOrientation
        mCallback.onPictureTaken(data, 0)
      } else {
        mCallback.onFramePreview(data, image.width, image.height, mDisplayOrientation)
      }
      image.close()
    }
  }
  private var mCameraId: String? = null
  private var _mCameraId: String? = null
  private var mCameraCharacteristics: CameraCharacteristics? = null
  var mCamera: CameraDevice? = null
  var sound = MediaActionSound()
  var mCaptureSession: CameraCaptureSession? = null
  var mPreviewRequestBuilder: CaptureRequest.Builder? = null
  var mAvailableCameras: MutableSet<String> = HashSet()
  private var mStillImageReader: ImageReader? = null
  private var mScanImageReader: ImageReader? = null
  private var mImageFormat: Int
  private var mMediaRecorder: MediaRecorder? = null
  private var mVideoPath: String? = null
  private var mIsRecording = false
  private val mPreviewSizes = SizeMap()
  private val mPictureSizes = SizeMap()
  private var mPictureSize: Size? = null
  private var mFacing = 0
  private var mAspectRatio: AspectRatio = Constants.DEFAULT_ASPECT_RATIO
  private var mInitialRatio: AspectRatio? = null
  private var mAutoFocus = false
  private var mFlash = 0
  private val mExposure = 0f
  private var mCameraOrientation = 0
  private var mDisplayOrientation = 0
  private var mDeviceOrientation = 0
  private var mFocusDepth = 0f
  private var mZoom = 0f
  private var mWhiteBalance = 0
  private var mIsScanning = false
  private var mPlaySoundOnCapture = false
  private var mPlaySoundOnRecord = false
  private var mPreviewSurface: Surface? = null
  private var mInitialCropRegion: Rect? = null
  public override fun start(): Boolean {
    if (!chooseCameraIdByFacing()) {
      mAspectRatio = mInitialRatio!!
      mCallback.onMountError()
      return false
    }
    collectCameraInfo()
    aspectRatio = mInitialRatio!!
    mInitialRatio = null
    prepareStillImageReader()
    prepareScanImageReader()
    startOpeningCamera()
    return true
  }

  public override fun stop() {
    if (mCaptureSession != null) {
      mCaptureSession!!.close()
      mCaptureSession = null
    }
    if (mCamera != null) {
      mCamera!!.close()
      mCamera = null
    }
    if (mStillImageReader != null) {
      mStillImageReader!!.close()
      mStillImageReader = null
    }
    if (mScanImageReader != null) {
      mScanImageReader!!.close()
      mScanImageReader = null
    }
    if (mMediaRecorder != null) {
      mMediaRecorder!!.stop()
      mMediaRecorder!!.reset()
      mMediaRecorder!!.release()
      mMediaRecorder = null
      if (mIsRecording) {
        mCallback.onRecordingEnd()

        // @TODO: implement videoOrientation and deviceOrientation calculation
        mCallback.onVideoRecorded(mVideoPath, 0, 0)
        mIsRecording = false
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
    if (isCameraOpened) {
      stop()
      start()
    }
  }

  public override fun getFacing(): Int {
    return mFacing
  }

  override fun getSupportedPreviewFpsRange(): ArrayList<IntArray> {
    Log.e("CAMERA_2:: ", "getSupportedPreviewFpsRange is not currently supported for Camera2")
    return ArrayList()
  }

  public override fun setCameraId(id: String) {
    if (!objectEquals(_mCameraId, id)) {
      _mCameraId = id

      // only update if our camera ID actually changes
      // from what we currently have.
      // Passing null will always yield true
      if (!objectEquals(_mCameraId, mCameraId)) {
        // this will call chooseCameraIdByFacing
        if (isCameraOpened) {
          stop()
          start()
        }
      }
    }
  }

  public override fun getCameraId(): String {
    return _mCameraId!!
  }

  public override fun getSupportedAspectRatios(): Set<AspectRatio> {
    return mPreviewSizes.ratios()
  }

  public override fun getCameraIds(): List<Properties> {
    return try {
      val ids: MutableList<Properties> = ArrayList()
      val cameraIds = mCameraManager.cameraIdList
      for (id in cameraIds) {
        val p = Properties()
        val characteristics = mCameraManager.getCameraCharacteristics(id)
        val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
        p["id"] = id
        p["type"] = java.lang.String.valueOf(if (internal == CameraCharacteristics.LENS_FACING_FRONT) Constants.FACING_FRONT else Constants.FACING_BACK)
        ids.add(p)
      }
      ids
    } catch (e: CameraAccessException) {
      throw RuntimeException("Failed to get a list of camera ids", e)
    }
  }

  public override fun getAvailablePictureSizes(ratio: AspectRatio): SortedSet<Size> {
    return mPictureSizes.sizes(ratio)!!
  }

  public override fun setPictureSize(size: Size) {
    if (mCaptureSession != null) {
      try {
        mCaptureSession!!.stopRepeating()
      } catch (e: CameraAccessException) {
        e.printStackTrace()
      }
      mCaptureSession!!.close()
      mCaptureSession = null
    }
    if (mStillImageReader != null) {
      mStillImageReader!!.close()
    }
    mPictureSize = size
    prepareStillImageReader()
    startCaptureSession()
  }

  public override fun getPictureSize(): Size {
    return mPictureSize!!
  }

  public override fun setAspectRatio(ratio: AspectRatio): Boolean {
    if (mPreviewSizes.isEmpty) {
      mInitialRatio = ratio
      return false
    }
    if (ratio == mAspectRatio || !mPreviewSizes.ratios().contains(ratio)) {
      // TODO: Better error handling
      return false
    }
    mAspectRatio = ratio
    prepareStillImageReader()
    prepareScanImageReader()
    if (mCaptureSession != null) {
      mCaptureSession!!.close()
      mCaptureSession = null
      startCaptureSession()
    }
    return true
  }

  public override fun getAspectRatio(): AspectRatio {
    return mAspectRatio
  }

  public override fun setAutoFocus(autoFocus: Boolean) {
    if (mAutoFocus == autoFocus) {
      return
    }
    mAutoFocus = autoFocus
    if (mPreviewRequestBuilder != null) {
      updateAutoFocus()
      if (mCaptureSession != null) {
        try {
          mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
            mCaptureCallback, null)
        } catch (e: CameraAccessException) {
          mAutoFocus = !mAutoFocus // Revert
        }
      }
    }
  }

  public override fun getAutoFocus(): Boolean {
    return mAutoFocus
  }

  public override fun setFlash(flash: Int) {
    if (mFlash == flash) {
      return
    }
    val saved = mFlash
    mFlash = flash
    if (mPreviewRequestBuilder != null) {
      updateFlash()
      if (mCaptureSession != null) {
        try {
          mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
            mCaptureCallback, null)
        } catch (e: CameraAccessException) {
          mFlash = saved // Revert
        }
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
    Log.e("CAMERA_2:: ", "Adjusting exposure is not currently supported for Camera2")
  }

  public override fun takePicture(options: ReadableMap) {
    mCaptureCallback.options = options
    if (mAutoFocus) {
      lockFocus()
    } else {
      captureStillPicture()
    }
  }

  public override fun record(path: String, maxDuration: Int, maxFileSize: Int, recordAudio: Boolean, profile: CamcorderProfile, orientation: Int, fps: Int): Boolean {
    if (!mIsRecording) {
      setUpMediaRecorder(path, maxDuration, maxFileSize, recordAudio, profile)
      return try {
        mMediaRecorder!!.prepare()
        if (mCaptureSession != null) {
          mCaptureSession!!.close()
          mCaptureSession = null
        }
        val size = chooseOptimalSize()
        mPreview.setBufferSize(size.width, size.height)
        val surface = previewSurface
        val mMediaRecorderSurface = mMediaRecorder!!.surface
        mPreviewRequestBuilder = mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        mPreviewRequestBuilder!!.addTarget(surface)
        mPreviewRequestBuilder!!.addTarget(mMediaRecorderSurface)
        mCamera!!.createCaptureSession(Arrays.asList(surface, mMediaRecorderSurface),
          mSessionCallback, null)
        mMediaRecorder!!.start()
        mIsRecording = true

        // @TODO: implement videoOrientation and deviceOrientation calculation
        // same TODO as onVideoRecorded
        mCallback.onRecordingStart(mVideoPath, 0, 0)
        if (mPlaySoundOnRecord) {
          sound.play(MediaActionSound.START_VIDEO_RECORDING)
        }
        true
      } catch (e: CameraAccessException) {
        e.printStackTrace()
        false
      } catch (e: IOException) {
        e.printStackTrace()
        false
      }
    }
    return false
  }

  public override fun stopRecording() {
    if (mIsRecording) {
      stopMediaRecorder()
      if (mCaptureSession != null) {
        mCaptureSession!!.close()
        mCaptureSession = null
      }
      startCaptureSession()
    }
  }

  public override fun pauseRecording() {
    pauseMediaRecorder()
  }

  public override fun resumeRecording() {
    resumeMediaRecorder()
  }

  public override fun setFocusDepth(value: Float) {
    if (mFocusDepth == value) {
      return
    }
    val saved = mFocusDepth
    mFocusDepth = value
    if (mCaptureSession != null) {
      updateFocusDepth()
      try {
        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
          mCaptureCallback, null)
      } catch (e: CameraAccessException) {
        mFocusDepth = saved // Revert
      }
    }
  }

  public override fun getFocusDepth(): Float {
    return mFocusDepth
  }

  public override fun setZoom(zoom: Float) {
    if (mZoom == zoom) {
      return
    }
    val saved = mZoom
    mZoom = zoom
    if (mCaptureSession != null) {
      updateZoom()
      try {
        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
          mCaptureCallback, null)
      } catch (e: CameraAccessException) {
        mZoom = saved // Revert
      }
    }
  }

  public override fun getZoom(): Float {
    return mZoom
  }

  public override fun setWhiteBalance(whiteBalance: Int) {
    if (mWhiteBalance == whiteBalance) {
      return
    }
    val saved = mWhiteBalance
    mWhiteBalance = whiteBalance
    if (mCaptureSession != null) {
      updateWhiteBalance()
      try {
        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(),
          mCaptureCallback, null)
      } catch (e: CameraAccessException) {
        mWhiteBalance = saved // Revert
      }
    }
  }

  public override fun getWhiteBalance(): Int {
    return mWhiteBalance
  }

  public override fun setPlaySoundOnCapture(playSoundOnCapture: Boolean) {
    mPlaySoundOnCapture = playSoundOnCapture
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

  public override fun setScanning(isScanning: Boolean) {
    if (mIsScanning == isScanning) {
      return
    }
    mIsScanning = isScanning
    mImageFormat = if (!mIsScanning) {
      ImageFormat.JPEG
    } else {
      ImageFormat.YUV_420_888
    }
    if (mCaptureSession != null) {
      mCaptureSession!!.close()
      mCaptureSession = null
    }
    startCaptureSession()
  }

  public override fun getScanning(): Boolean {
    return mIsScanning
  }

  public override fun getCameraOrientation(): Int {
    return mCameraOrientation
  }

  public override fun setDisplayOrientation(displayOrientation: Int) {
    mDisplayOrientation = displayOrientation
    mPreview.setDisplayOrientation(mDisplayOrientation)
  }

  public override fun setDeviceOrientation(deviceOrientation: Int) {
    mDeviceOrientation = deviceOrientation
    //mPreview.setDisplayOrientation(deviceOrientation); // this is not needed and messes up the display orientation
  }

  /**
   *
   * Chooses a camera ID by the specified camera facing ([.mFacing]).
   *
   * This rewrites [.mCameraId], [.mCameraCharacteristics], and optionally
   * [.mFacing].
   */
  private fun chooseCameraIdByFacing(): Boolean {
    return if (_mCameraId == null) {
      try {
        val internalFacing = INTERNAL_FACINGS[mFacing]
        val ids = mCameraManager.cameraIdList
        if (ids.isEmpty()) { // No camera
          Log.e(TAG, "No cameras available.")
          return false
        }
        for (id in ids) {
          val characteristics = mCameraManager.getCameraCharacteristics(id)
          val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
          if (internal == null) {
            Log.e(TAG, "Unexpected state: LENS_FACING null")
            continue
          }
          if (internal == internalFacing) {
            mCameraId = id
            mCameraCharacteristics = characteristics
            return true
          }
        }
        // Not found
        mCameraId = ids[0]
        mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId!!)
        val internal = mCameraCharacteristics!!.get(CameraCharacteristics.LENS_FACING)
        if (internal == null) {
          Log.e(TAG, "Unexpected state: LENS_FACING null")
          return false
        }
        var i = 0
        val count = INTERNAL_FACINGS.size()
        while (i < count) {
          if (INTERNAL_FACINGS.valueAt(i) == internal) {
            mFacing = INTERNAL_FACINGS.keyAt(i)
            return true
          }
          i++
        }
        // The operation can reach here when the only camera device is an external one.
        // We treat it as facing back.
        mFacing = Constants.FACING_BACK
        true
      } catch (e: CameraAccessException) {
        Log.e(TAG, "Failed to get a list of camera devices", e)
        false
      }
    } else {
      try {
        // need to set the mCameraCharacteristics variable as above and also do the same checks
        // for legacy hardware
        mCameraCharacteristics = mCameraManager.getCameraCharacteristics(_mCameraId!!)

        // set our facing variable so orientation also works as expected
        val internal = mCameraCharacteristics!!.get(CameraCharacteristics.LENS_FACING)
        if (internal == null) {
          Log.e(TAG, "Unexpected state: LENS_FACING null")
          return false
        }
        var i = 0
        val count = INTERNAL_FACINGS.size()
        while (i < count) {
          if (INTERNAL_FACINGS.valueAt(i) == internal) {
            mFacing = INTERNAL_FACINGS.keyAt(i)
            break
          }
          i++
        }
        mCameraId = _mCameraId
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to get camera characteristics", e)
        false
      }
    }
  }

  /**
   *
   * Collects some information from [.mCameraCharacteristics].
   *
   * This rewrites [.mPreviewSizes], [.mPictureSizes],
   * [.mCameraOrientation], and optionally, [.mAspectRatio].
   */
  private fun collectCameraInfo() {
    val map = mCameraCharacteristics!!.get(
      CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      ?: throw IllegalStateException("Failed to get configuration map: $mCameraId")
    mPreviewSizes.clear()
    for (size in map.getOutputSizes(mPreview.outputClass)) {
      val width = size.width
      val height = size.height
      if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
        mPreviewSizes.add(Size(width, height))
      }
    }
    mPictureSizes.clear()
    collectPictureSizes(mPictureSizes, map)
    if (mPictureSize == null) {
      mPictureSize = mPictureSizes.sizes(mAspectRatio)!!.last()
    }
    for (ratio in mPreviewSizes.ratios()) {
      if (!mPictureSizes.ratios().contains(ratio)) {
        mPreviewSizes.remove(ratio)
      }
    }
    if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
      mAspectRatio = mPreviewSizes.ratios().iterator().next()
    }
    mCameraOrientation = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
  }

  protected open fun collectPictureSizes(sizes: SizeMap?, map: StreamConfigurationMap) {
    for (size in map.getOutputSizes(mImageFormat)) {
      mPictureSizes.add(Size(size.width, size.height))
    }
  }

  private fun prepareStillImageReader() {
    if (mStillImageReader != null) {
      mStillImageReader!!.close()
    }
    val imageReader = ImageReader.newInstance(mPictureSize!!.width, mPictureSize!!.height,
      ImageFormat.JPEG, 1)
    mStillImageReader = imageReader
    imageReader.setOnImageAvailableListener(mOnImageAvailableListener, null)
  }

  private fun prepareScanImageReader() {
    if (mScanImageReader != null) {
      mScanImageReader!!.close()
    }
    val largest = mPreviewSizes.sizes(mAspectRatio)!!.last()
    val imageReader = ImageReader.newInstance(largest.width, largest.height,
      ImageFormat.YUV_420_888, 1)
    mScanImageReader = imageReader
    imageReader.setOnImageAvailableListener(mOnImageAvailableListener, null)
  }

  /**
   *
   * Starts opening a camera device.
   *
   * The result will be processed in [.mCameraDeviceCallback].
   */
  @SuppressLint("MissingPermission")
  private fun startOpeningCamera() {
    try {
      mCameraManager.openCamera(mCameraId!!, mCameraDeviceCallback, null)
    } catch (e: CameraAccessException) {
      throw RuntimeException("Failed to open camera: $mCameraId", e)
    }
  }

  /**
   *
   * Starts a capture session for camera preview.
   *
   * This rewrites [.mPreviewRequestBuilder].
   *
   * The result will be continuously processed in [.mSessionCallback].
   */
  fun startCaptureSession() {
    if (!isCameraOpened || !mPreview.isReady || mStillImageReader == null || mScanImageReader == null) {
      return
    }
    val previewSize = chooseOptimalSize()
    mPreview.setBufferSize(previewSize.width, previewSize.height)
    val surface = previewSurface
    try {
      mPreviewRequestBuilder = mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      mPreviewRequestBuilder!!.addTarget(surface)
      if (mIsScanning) {
        mPreviewRequestBuilder!!.addTarget(mScanImageReader!!.surface)
      }
      mCamera!!.createCaptureSession(Arrays.asList(surface, mStillImageReader!!.surface,
        mScanImageReader!!.surface), mSessionCallback, null)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to start capture session", e)
      mCallback.onMountError()
    }
  }

  override fun resumePreview() {
    unlockFocus()
  }

  override fun pausePreview() {
    try {
      mCaptureSession!!.stopRepeating()
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }
  }

  val previewSurface: Surface
    get() = mPreviewSurface ?: mPreview.surface

  override fun setPreviewTexture(surfaceTexture: SurfaceTexture) {
    mPreviewSurface = run {
      val previewSurface = Surface(surfaceTexture)
      previewSurface
    }

    // it may be called from another thread, so make sure we're in main looper
    val handler = Handler(Looper.getMainLooper())
    handler.post {
      if (mCaptureSession != null) {
        mCaptureSession!!.close()
        mCaptureSession = null
      }
      startCaptureSession()
    }
  }

  override fun getPreviewSize(): Size {
    return Size(mPreview.width, mPreview.height)
  }

  /**
   * Chooses the optimal preview size based on [.mPreviewSizes] and the surface size.
   *
   * @return The picked size for camera preview.
   */
  private fun chooseOptimalSize(): Size {
    val surfaceLonger: Int
    val surfaceShorter: Int
    val surfaceWidth = mPreview.width
    val surfaceHeight = mPreview.height
    if (surfaceWidth < surfaceHeight) {
      surfaceLonger = surfaceHeight
      surfaceShorter = surfaceWidth
    } else {
      surfaceLonger = surfaceWidth
      surfaceShorter = surfaceHeight
    }
    val candidates = mPreviewSizes.sizes(mAspectRatio)

    // Pick the smallest of those big enough
    for (size in candidates!!) {
      if (size.width >= surfaceLonger && size.height >= surfaceShorter) {
        return size
      }
    }
    // If no size is big enough, pick the largest one.
    return candidates.last()
  }

  /**
   * Updates the internal state of auto-focus to [.mAutoFocus].
   */
  fun updateAutoFocus() {
    if (mAutoFocus) {
      val modes = mCameraCharacteristics!!.get(
        CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
      // Auto focus is not supported
      if (modes == null || modes.size == 0 ||
        modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF) {
        mAutoFocus = false
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
          CaptureRequest.CONTROL_AF_MODE_OFF)
      } else {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
      }
    } else {
      mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_OFF)
    }
  }

  /**
   * Updates the internal state of flash to [.mFlash].
   */
  fun updateFlash() {
    when (mFlash) {
      Constants.FLASH_OFF -> {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON)
        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
          CaptureRequest.FLASH_MODE_OFF)
      }
      Constants.FLASH_ON -> {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
          CaptureRequest.FLASH_MODE_OFF)
      }
      Constants.FLASH_TORCH -> {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON)
        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
          CaptureRequest.FLASH_MODE_TORCH)
      }
      Constants.FLASH_AUTO -> {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
          CaptureRequest.FLASH_MODE_OFF)
      }
      Constants.FLASH_RED_EYE -> {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
          CaptureRequest.FLASH_MODE_OFF)
      }
    }
  }

  /**
   * Updates the internal state of focus depth to [.mFocusDepth].
   */
  fun updateFocusDepth() {
    if (mAutoFocus) {
      return
    }
    val minimumLens = mCameraCharacteristics!!.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
      ?: throw NullPointerException("Unexpected state: LENS_INFO_MINIMUM_FOCUS_DISTANCE null")
    val value = mFocusDepth * minimumLens
    mPreviewRequestBuilder!!.set(CaptureRequest.LENS_FOCUS_DISTANCE, value)
  }

  /**
   * Updates the internal state of zoom to [.mZoom].
   */
  fun updateZoom() {
    val maxZoom = mCameraCharacteristics!!.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
    val scaledZoom = mZoom * (maxZoom - 1.0f) + 1.0f
    val currentPreview = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    if (currentPreview != null) {
      val currentWidth = currentPreview.width()
      val currentHeight = currentPreview.height()
      val zoomedWidth = (currentWidth / scaledZoom).toInt()
      val zoomedHeight = (currentHeight / scaledZoom).toInt()
      val widthOffset = (currentWidth - zoomedWidth) / 2
      val heightOffset = (currentHeight - zoomedHeight) / 2
      val zoomedPreview = Rect(
        currentPreview.left + widthOffset,
        currentPreview.top + heightOffset,
        currentPreview.right - widthOffset,
        currentPreview.bottom - heightOffset
      )

      // ¯\_(ツ)_/¯ for some devices calculating the Rect for zoom=1 results in a bit different
      // Rect that device claims as its no-zoom crop region and the preview freezes
      if (scaledZoom != 1.0f) {
        mPreviewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, zoomedPreview)
      } else {
        mPreviewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mInitialCropRegion)
      }
    }
  }

  /**
   * Updates the internal state of white balance to [.mWhiteBalance].
   */
  fun updateWhiteBalance() {
    when (mWhiteBalance) {
      Constants.WB_AUTO -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_AUTO)
      Constants.WB_CLOUDY -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
      Constants.WB_FLUORESCENT -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
      Constants.WB_INCANDESCENT -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
      Constants.WB_SHADOW -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_SHADE)
      Constants.WB_SUNNY -> mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
    }
  }

  /**
   * Locks the focus as the first step for a still image capture.
   */
  private fun lockFocus() {
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
      CaptureRequest.CONTROL_AF_TRIGGER_START)
    try {
      mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING)
      mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, null)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to lock focus.", e)
    }
  }

  /**
   * Auto focus on input coordinates
   */
  // Much credit - https://gist.github.com/royshil/8c760c2485257c85a11cafd958548482
  public override fun setFocusArea(x: Float, y: Float) {
    if (mCaptureSession == null) {
      return
    }
    val captureCallbackHandler: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
      override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        super.onCaptureCompleted(session, request, result)
        if (request.tag === "FOCUS_TAG") {
          mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
          try {
            mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(), null, null)
          } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to manual focus.", e)
          }
        }
      }

      override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
        super.onCaptureFailed(session, request, failure)
        Log.e(TAG, "Manual AF failure: $failure")
      }
    }
    try {
      mCaptureSession!!.stopRepeating()
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to manual focus.", e)
    }
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
    try {
      mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), captureCallbackHandler, null)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to manual focus.", e)
    }
    if (isMeteringAreaAFSupported) {
      val focusAreaTouch = calculateFocusArea(x, y)
      mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusAreaTouch))
    }
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
    mPreviewRequestBuilder!!.setTag("FOCUS_TAG")
    try {
      mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), captureCallbackHandler, null)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to manual focus.", e)
    }
  }

  private val isMeteringAreaAFSupported: Boolean
    get() = mCameraCharacteristics!!.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)!! >= 1

  private fun calculateFocusArea(x: Float, y: Float): MeteringRectangle {
    val sensorArraySize = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

    // Current iOS spec has a requirement on sensor orientation that doesn't change, spec followed here.
    val xCoordinate = (y * sensorArraySize!!.height().toFloat()).toInt()
    val yCoordinate = (x * sensorArraySize.width().toFloat()).toInt()
    val halfTouchWidth = 150 //TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
    val halfTouchHeight = 150
    return MeteringRectangle(Math.max(yCoordinate - halfTouchWidth, 0),
      Math.max(xCoordinate - halfTouchHeight, 0),
      halfTouchWidth * 2,
      halfTouchHeight * 2,
      MeteringRectangle.METERING_WEIGHT_MAX - 1)
  }

  /**
   * Captures a still picture.
   */
  fun captureStillPicture() {
    try {
      val captureRequestBuilder = mCamera!!.createCaptureRequest(
        CameraDevice.TEMPLATE_STILL_CAPTURE)
      if (mIsScanning) {
        mImageFormat = ImageFormat.JPEG
        captureRequestBuilder.removeTarget(mScanImageReader!!.surface)
      }
      captureRequestBuilder.addTarget(mStillImageReader!!.surface)
      captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
        mPreviewRequestBuilder!!.get(CaptureRequest.CONTROL_AF_MODE))
      when (mFlash) {
        Constants.FLASH_OFF -> {
          captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON)
          captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
            CaptureRequest.FLASH_MODE_OFF)
        }
        Constants.FLASH_ON -> captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        Constants.FLASH_TORCH -> {
          captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON)
          captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
            CaptureRequest.FLASH_MODE_TORCH)
        }
        Constants.FLASH_AUTO -> captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        Constants.FLASH_RED_EYE -> captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
      }
      captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, outputRotation)
      if (mCaptureCallback.options!!.hasKey("quality")) {
        val quality = (mCaptureCallback.options!!.getDouble("quality") * 100).toInt()
        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, quality.toByte())
      }
      captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mPreviewRequestBuilder!!.get(CaptureRequest.SCALER_CROP_REGION))
      // Stop preview and capture a still picture.
      mCaptureSession!!.stopRepeating()
      mCaptureSession!!.capture(captureRequestBuilder.build(),
        object : CameraCaptureSession.CaptureCallback() {
          override fun onCaptureCompleted(session: CameraCaptureSession,
                                          request: CaptureRequest,
                                          result: TotalCaptureResult) {
            if (mCaptureCallback.options!!.hasKey("pauseAfterCapture")
              && !mCaptureCallback.options!!.getBoolean("pauseAfterCapture")) {
              unlockFocus()
            }
            if (mPlaySoundOnCapture) {
              sound.play(MediaActionSound.SHUTTER_CLICK)
            }
          }
        }, null)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Cannot capture a still picture.", e)
    }
  }

  // updated and copied from Camera1
  private val outputRotation: Int
    get() {
      val sensorOrientation = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

      // updated and copied from Camera1
      return if (mFacing == Constants.FACING_BACK) {
        (sensorOrientation + mDeviceOrientation) % 360
      } else {
        val landscapeFlip = if (isLandscape(mDeviceOrientation)) 180 else 0
        (sensorOrientation + mDeviceOrientation + landscapeFlip) % 360
      }
    }

  /**
   * Test if the supplied orientation is in landscape.
   *
   * @param orientationDegrees Orientation in degrees (0,90,180,270)
   * @return True if in landscape, false if portrait
   */
  private fun isLandscape(orientationDegrees: Int): Boolean {
    return orientationDegrees == Constants.LANDSCAPE_90 || orientationDegrees == Constants.LANDSCAPE_270
  }

  private fun setUpMediaRecorder(path: String, maxDuration: Int, maxFileSize: Int, recordAudio: Boolean, profile: CamcorderProfile) {
    mMediaRecorder = MediaRecorder()
    mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
    if (recordAudio) {
      mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
    }
    mMediaRecorder!!.setOutputFile(path)
    mVideoPath = path
    var camProfile = profile
    if (!CamcorderProfile.hasProfile(mCameraId!!.toInt(), profile.quality)) {
      camProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
    }
    camProfile.videoBitRate = profile.videoBitRate
    setCamcorderProfile(camProfile, recordAudio)
    mMediaRecorder!!.setOrientationHint(outputRotation)
    if (maxDuration != -1) {
      mMediaRecorder!!.setMaxDuration(maxDuration)
    }
    if (maxFileSize != -1) {
      mMediaRecorder!!.setMaxFileSize(maxFileSize.toLong())
    }
    mMediaRecorder!!.setOnInfoListener(this)
    mMediaRecorder!!.setOnErrorListener(this)
  }

  private fun setCamcorderProfile(profile: CamcorderProfile, recordAudio: Boolean) {
    mMediaRecorder!!.setOutputFormat(profile.fileFormat)
    mMediaRecorder!!.setVideoFrameRate(profile.videoFrameRate)
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

  private fun stopMediaRecorder() {
    mIsRecording = false
    try {
      mCaptureSession!!.stopRepeating()
      mCaptureSession!!.abortCaptures()
      mMediaRecorder!!.stop()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    mMediaRecorder!!.reset()
    mMediaRecorder!!.release()
    mMediaRecorder = null
    mCallback.onRecordingEnd()
    if (mPlaySoundOnRecord) {
      sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }
    if (mVideoPath == null || !File(mVideoPath).exists()) {
      // @TODO: implement videoOrientation and deviceOrientation calculation
      mCallback.onVideoRecorded(null, 0, 0)
      return
    }
    // @TODO: implement videoOrientation and deviceOrientation calculation
    mCallback.onVideoRecorded(mVideoPath, 0, 0)
    mVideoPath = null
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

  /**
   * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
   * capturing a still picture.
   */
  fun unlockFocus() {
    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
      CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
    try {
      mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, null)
      updateAutoFocus()
      updateFlash()
      if (mIsScanning) {
        mImageFormat = ImageFormat.YUV_420_888
        startCaptureSession()
      } else {
        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
          CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(), mCaptureCallback,
          null)
        mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
      }
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to restart camera preview.", e)
    }
  }

  /**
   * Called when an something occurs while recording.
   */
  override fun onInfo(mr: MediaRecorder, what: Int, extra: Int) {
    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
      what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
      stopRecording()
    }
  }

  /**
   * Called when an error occurs while recording.
   */
  override fun onError(mr: MediaRecorder, what: Int, extra: Int) {
    stopRecording()
  }

  /**
   * A [CameraCaptureSession.CaptureCallback] for capturing a still picture.
   */
  private abstract class PictureCaptureCallback internal constructor() : CameraCaptureSession.CaptureCallback() {
    private var mState = 0
    var options: ReadableMap? = null
    fun setState(state: Int) {
      mState = state
    }

    override fun onCaptureProgressed(session: CameraCaptureSession,
                                     request: CaptureRequest, partialResult: CaptureResult) {
      process(partialResult)
    }

    override fun onCaptureCompleted(session: CameraCaptureSession,
                                    request: CaptureRequest, result: TotalCaptureResult) {
      process(result)
    }

    private fun process(result: CaptureResult) {
      when (mState) {
        STATE_LOCKING -> {
          val af = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
          if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
            af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
            val ae = result.get(CaptureResult.CONTROL_AE_STATE)
            if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
              setState(STATE_CAPTURING)
              onReady()
            } else {
              setState(STATE_LOCKED)
              onPrecaptureRequired()
            }
          }
        }
        STATE_PRECAPTURE -> {
          val ae = result.get(CaptureResult.CONTROL_AE_STATE)
          if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
            setState(STATE_WAITING)
          }
        }
        STATE_WAITING -> {
          val ae = result.get(CaptureResult.CONTROL_AE_STATE)
          if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
            setState(STATE_CAPTURING)
            onReady()
          }
        }
      }
    }

    /**
     * Called when it is ready to take a still picture.
     */
    abstract fun onReady()

    /**
     * Called when it is necessary to run the precapture sequence.
     */
    abstract fun onPrecaptureRequired()

    companion object {
      const val STATE_PREVIEW = 0
      const val STATE_LOCKING = 1
      const val STATE_LOCKED = 2
      const val STATE_PRECAPTURE = 3
      const val STATE_WAITING = 4
      const val STATE_CAPTURING = 5
    }
  }

  init {
    mCameraManager.registerAvailabilityCallback(object : AvailabilityCallback() {
      override fun onCameraAvailable(cameraId: String) {
        super.onCameraAvailable(cameraId)
        mAvailableCameras.add(cameraId)
      }

      override fun onCameraUnavailable(cameraId: String) {
        super.onCameraUnavailable(cameraId)
        mAvailableCameras.remove(cameraId)
      }
    }, null)
    mImageFormat = if (mIsScanning) ImageFormat.YUV_420_888 else ImageFormat.JPEG
    mPreview.setCallback(object : PreviewImpl.Callback {
      override fun onSurfaceChanged() {
        startCaptureSession()
      }

      override fun onSurfaceDestroyed() {
        stop()
      }
    })
  }
}
