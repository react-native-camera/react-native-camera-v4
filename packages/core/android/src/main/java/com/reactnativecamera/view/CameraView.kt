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

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.CamcorderProfile
import android.os.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.core.os.ParcelableCompat
import androidx.core.os.ParcelableCompatCreatorCallbacks
import androidx.core.view.ViewCompat
import com.facebook.react.bridge.ReadableMap
import com.reactnativecamera.Constants
import java.util.*

open class CameraView(
  context: Context,
  attrs: AttributeSet?,
  defStyleAttr: Int,
  fallbackToOldApi: Boolean
) : FrameLayout(context, attrs, defStyleAttr) {
  /** Direction the camera faces relative to device screen.  */
  @IntDef(FACING_BACK, FACING_FRONT)
  @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
  annotation class Facing

  /** The mode for for the camera device's flash control  */
  @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
  @IntDef(FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE)
  annotation class Flash

  var mImpl: CameraViewImpl? = null
  private var mCallbacks: CallbackBridge?
  private var mAdjustViewBounds: Boolean
  private val mContext: Context?
  private var mDisplayOrientationDetector: DisplayOrientationDetector?
  protected var mBgThread: HandlerThread?
  protected var mBgHandler: Handler

  constructor(context: Context, fallbackToOldApi: Boolean) : this(context, null, fallbackToOldApi) {}
  constructor(context: Context, attrs: AttributeSet?, fallbackToOldApi: Boolean) : this(context, attrs, 0, fallbackToOldApi) {}

  fun cleanup() {
    if (mBgThread != null) {
      if (Build.VERSION.SDK_INT < 18) {
        mBgThread!!.quit()
      } else {
        mBgThread!!.quitSafely()
      }
      mBgThread = null
    }
  }

  private fun createPreviewImpl(context: Context?): PreviewImpl {
    return TextureViewPreview(context, this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!isInEditMode) {
      mDisplayOrientationDetector!!.enable(ViewCompat.getDisplay(this)!!)
    }
  }

  override fun onDetachedFromWindow() {
    if (!isInEditMode) {
      mDisplayOrientationDetector!!.disable()
    }
    super.onDetachedFromWindow()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (isInEditMode) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      return
    }
    // Handle android:adjustViewBounds
    if (mAdjustViewBounds) {
      if (!isCameraOpened) {
        mCallbacks?.reserveRequestLayoutOnOpen()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        return
      }
      val widthMode = MeasureSpec.getMode(widthMeasureSpec)
      val heightMode = MeasureSpec.getMode(heightMeasureSpec)
      if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
        val ratio = aspectRatio!!
        var height = (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat()).toInt()
        if (heightMode == MeasureSpec.AT_MOST) {
          height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec))
        }
        super.onMeasure(widthMeasureSpec,
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
      } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
        val ratio = aspectRatio!!
        var width = (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat()).toInt()
        if (widthMode == MeasureSpec.AT_MOST) {
          width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec))
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          heightMeasureSpec)
      } else {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      }
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    // Measure the TextureView
    val width = measuredWidth
    val height = measuredHeight
    var ratio = aspectRatio
    if (mDisplayOrientationDetector!!.lastKnownDisplayOrientation % 180 == 0) {
      ratio = ratio!!.inverse()
    }
    assert(ratio != null)
    if (height < width * ratio!!.y / ratio.x) {
      mImpl!!.view.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(width * ratio.y / ratio.x,
          MeasureSpec.EXACTLY))
    } else {
      mImpl!!.view.measure(
        MeasureSpec.makeMeasureSpec(height * ratio.x / ratio.y,
          MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
  }

  override fun onSaveInstanceState(): Parcelable? {
    val state = SavedState(super.onSaveInstanceState())
    state.facing = facing
    state.cameraId = cameraId
    state.ratio = aspectRatio
    state.autoFocus = autoFocus
    state.flash = flash
    state.exposure = exposureCompensation
    state.focusDepth = focusDepth
    state.zoom = zoom
    state.whiteBalance = whiteBalance
    state.playSoundOnCapture = playSoundOnCapture
    state.playSoundOnRecord = playSoundOnRecord
    state.scanning = scanning
    state.pictureSize = pictureSize
    return state
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    if (state !is SavedState) {
      super.onRestoreInstanceState(state)
      return
    }
    super.onRestoreInstanceState(state.superState)
    facing = state.facing
    cameraId = state.cameraId
    setAspectRatio(state.ratio!!)
    autoFocus = state.autoFocus
    flash = state.flash
    exposureCompensation = state.exposure
    focusDepth = state.focusDepth
    zoom = state.zoom
    whiteBalance = state.whiteBalance
    playSoundOnCapture = state.playSoundOnCapture
    playSoundOnRecord = state.playSoundOnRecord
    scanning = state.scanning
    pictureSize = state.pictureSize!!
  }

  fun setUsingCamera2Api(useCamera2: Boolean) {
    if (Build.VERSION.SDK_INT < 21) {
      return
    }
    val wasOpened = isCameraOpened
    val state = onSaveInstanceState()
    if (useCamera2 && !Camera2.isLegacy(mContext!!)) {
      if (wasOpened) {
        stop()
      }
      mImpl = if (Build.VERSION.SDK_INT < 23) {
        Camera2(mCallbacks!!, mImpl!!.mPreview, mContext, mBgHandler)
      } else {
        Camera2Api23(mCallbacks!!, mImpl!!.mPreview, mContext, mBgHandler)
      }
      onRestoreInstanceState(state!!)
    } else {
      if (mImpl is Camera1) {
        return
      }
      if (wasOpened) {
        stop()
      }
      mImpl = Camera1(mCallbacks!!, mImpl!!.mPreview, mBgHandler)
    }
    if (wasOpened) {
      start()
    }
  }

  /**
   * Open a camera device and start showing camera preview. This is typically called from
   * [Activity.onResume].
   */
  fun start() {
    mImpl!!.start()

    // this fallback is no longer needed and was too buggy/slow
    // if (!mImpl.start()) {
    //     if (mImpl.getView() != null) {
    //         this.removeView(mImpl.getView());
    //     }
    //     //store the state and restore this state after fall back to Camera1
    //     Parcelable state = onSaveInstanceState();
    //     // Camera2 uses legacy hardware layer; fall back to Camera1
    //     mImpl = new Camera1(mCallbacks, createPreviewImpl(getContext()), mBgHandler);
    //     onRestoreInstanceState(state);
    //     mImpl.start();
    // }
  }

  /**
   * Stop camera preview and close the device. This is typically called from
   * [Activity.onPause].
   */
  fun stop() {
    mImpl!!.stop()
  }

  /**
   * @return `true` if the camera is opened.
   */
  val isCameraOpened: Boolean
    get() = mImpl!!.isCameraOpened

  /**
   * Add a new callback.
   *
   * @param callback The [Callback] to add.
   * @see .removeCallback
   */
  fun addCallback(callback: Callback) {
    mCallbacks!!.add(callback)
  }

  /**
   * Remove a callback.
   *
   * @param callback The [Callback] to remove.
   * @see .addCallback
   */
  fun removeCallback(callback: Callback) {
    mCallbacks!!.remove(callback)
  }
  /**
   * @return True when this CameraView is adjusting its bounds to preserve the aspect ratio of
   * camera.
   * @see .setAdjustViewBounds
   */
  /**
   * @param adjustViewBounds `true` if you want the CameraView to adjust its bounds to
   * preserve the aspect ratio of camera.
   * @see .getAdjustViewBounds
   */
  var adjustViewBounds: Boolean
    get() = mAdjustViewBounds
    set(adjustViewBounds) {
      if (mAdjustViewBounds != adjustViewBounds) {
        mAdjustViewBounds = adjustViewBounds
        requestLayout()
      }
    }
  val view: View?
    get() = if (mImpl != null) {
      mImpl!!.view
    } else null
  /**
   * Gets the direction that the current camera faces.
   *
   * @return The camera facing.
   */
  /**
   * Chooses camera by the direction it faces.
   *
   * @param facing The camera facing. Must be either [.FACING_BACK] or
   * [.FACING_FRONT].
   */
  @get:Facing
  var facing: Int
    get() = mImpl!!.facing
    set(facing) {
      mImpl!!.facing = facing
    }
  /**
   * Gets the currently set camera ID
   *
   * @return The camera facing.
   */
  /**
   * Chooses camera by its camera iD
   *
   * @param id The camera ID
   */
  var cameraId: String?
    get() = mImpl!!.cameraId
    set(id) {
      mImpl!!.cameraId = id
    }

  /**
   * Gets all the aspect ratios supported by the current camera.
   */
  val supportedAspectRatios: Set<AspectRatio?>?
    get() = mImpl!!.supportedAspectRatios

  /**
   * Gets all the camera IDs supported by the phone as a String
   */
  val cameraIds: List<Properties?>?
    get() = mImpl!!.cameraIds

  /**
   * Sets the aspect ratio of camera.
   *
   * @param ratio The [AspectRatio] to be set.
   */
  open fun setAspectRatio(ratio: AspectRatio) {
    if (mImpl!!.setAspectRatio(ratio)) {
      requestLayout()
    }
  }

  /**
   * Gets the current aspect ratio of camera.
   *
   * @return The current [AspectRatio]. Can be `null` if no camera is opened yet.
   */
  val aspectRatio: AspectRatio?
    get() = mImpl!!.aspectRatio

  /**
   * Gets all the picture sizes for particular ratio supported by the current camera.
   *
   * @param ratio [AspectRatio] for which the available image sizes will be returned.
   */
  fun getAvailablePictureSizes(ratio: AspectRatio): SortedSet<Size> {
    return mImpl!!.getAvailablePictureSizes(ratio)
  }
  /**
   * Gets the size of pictures that will be taken.
   */
  /**
   * Sets the size of taken pictures.
   *
   * @param size The [Size] to be set.
   */
  var pictureSize: Size?
    get() = mImpl!!.pictureSize
    set(size) {
      mImpl!!.pictureSize = size
    }
  /**
   * Returns whether the continuous auto-focus mode is enabled.
   *
   * @return `true` if the continuous auto-focus mode is enabled. `false` if it is
   * disabled, or if it is not supported by the current camera.
   */
  /**
   * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
   * auto-focus, calling this method will be ignored.
   *
   * @param autoFocus `true` to enable continuous auto-focus mode. `false` to
   * disable it.
   */
  var autoFocus: Boolean
    get() = mImpl!!.autoFocus
    set(autoFocus) {
      mImpl!!.autoFocus = autoFocus
    }
  val supportedPreviewFpsRange: ArrayList<IntArray>
    get() = mImpl!!.supportedPreviewFpsRange
  /**
   * Gets the current flash mode.
   *
   * @return The current flash mode.
   */
  /**
   * Sets the flash mode.
   *
   * @param flash The desired flash mode.
   */
  @get:Flash
  var flash: Int
    get() = mImpl!!.flash
    set(flash) {
      mImpl!!.flash = flash
    }
  var exposureCompensation: Float
    get() = mImpl!!.exposureCompensation
    set(exposure) {
      mImpl!!.exposureCompensation = exposure
    }

  /**
   * Gets the camera orientation relative to the devices native orientation.
   *
   * @return The orientation of the camera.
   */
  val cameraOrientation: Int
    get() = mImpl!!.cameraOrientation

  /**
   * Sets the auto focus point.
   *
   * @param x sets the x coordinate for camera auto focus
   * @param y sets the y coordinate for camera auto focus
   */
  fun setAutoFocusPointOfInterest(x: Float, y: Float) {
    mImpl!!.setFocusArea(x, y)
  }

  var focusDepth: Float
    get() = mImpl!!.focusDepth
    set(value) {
      mImpl!!.focusDepth = value
    }
  var zoom: Float
    get() = mImpl!!.zoom
    set(zoom) {
      mImpl!!.zoom = zoom
    }
  var whiteBalance: Int
    get() = mImpl!!.whiteBalance
    set(whiteBalance) {
      mImpl!!.whiteBalance = whiteBalance
    }
  var playSoundOnCapture: Boolean
    get() = mImpl!!.playSoundOnCapture
    set(playSoundOnCapture) {
      mImpl!!.playSoundOnCapture = playSoundOnCapture
    }
  var playSoundOnRecord: Boolean
    get() = mImpl!!.playSoundOnRecord
    set(playSoundOnRecord) {
      mImpl!!.playSoundOnRecord = playSoundOnRecord
    }
  var scanning: Boolean
    get() = mImpl!!.scanning
    set(isScanning) {
      mImpl!!.scanning = isScanning
    }

  /**
   * Take a picture. The result will be returned to
   * [Callback.onPictureTaken].
   */
  fun takePicture(options: ReadableMap) {
    mImpl!!.takePicture(options)
  }

  /**
   * Record a video and save it to file. The result will be returned to
   * [Callback.onVideoRecorded].
   * @param path Path to file that video will be saved to.
   * @param maxDuration Maximum duration of the recording, in seconds.
   * @param maxFileSize Maximum recording file size, in bytes.
   * @param profile Quality profile of the recording.
   *
   * fires [Callback.onRecordingStart] and [Callback.onRecordingEnd].
   */
  fun record(path: String, maxDuration: Int, maxFileSize: Int,
             recordAudio: Boolean, profile: CamcorderProfile, orientation: Int, fps: Int): Boolean {
    return mImpl!!.record(path, maxDuration, maxFileSize, recordAudio, profile, orientation, fps)
  }

  fun stopRecording() {
    mImpl!!.stopRecording()
  }

  fun pauseRecording() {
    mImpl!!.pauseRecording()
  }

  fun resumeRecording() {
    mImpl!!.resumeRecording()
  }

  fun resumePreview() {
    mImpl!!.resumePreview()
  }

  fun pausePreview() {
    mImpl!!.pausePreview()
  }

  fun setPreviewTexture(surfaceTexture: SurfaceTexture) {
    mImpl!!.setPreviewTexture(surfaceTexture)
  }

  val previewSize: Size?
    get() = mImpl!!.previewSize

  private inner class CallbackBridge internal constructor() : CameraViewImpl.Callback {
    private val mCallbacks = ArrayList<Callback>()
    private var mRequestLayoutOnOpen = false
    fun add(callback: Callback) {
      mCallbacks.add(callback)
    }

    fun remove(callback: Callback?) {
      mCallbacks.remove(callback)
    }

    override fun onCameraOpened() {
      if (mRequestLayoutOnOpen) {
        mRequestLayoutOnOpen = false
        requestLayout()
      }
      for (callback in mCallbacks) {
        callback.onCameraOpened(this@CameraView)
      }
    }

    override fun onCameraClosed() {
      for (callback in mCallbacks) {
        callback.onCameraClosed(this@CameraView)
      }
    }

    override fun onPictureTaken(data: ByteArray, deviceOrientation: Int) {
      for (callback in mCallbacks) {
        callback.onPictureTaken(this@CameraView, data, deviceOrientation)
      }
    }

    override fun onRecordingStart(path: String, videoOrientation: Int, deviceOrientation: Int) {
      for (callback in mCallbacks) {
        callback.onRecordingStart(this@CameraView, path, videoOrientation, deviceOrientation)
      }
    }

    override fun onRecordingEnd() {
      for (callback in mCallbacks) {
        callback.onRecordingEnd(this@CameraView)
      }
    }

    override fun onVideoRecorded(path: String?, videoOrientation: Int, deviceOrientation: Int) {
      for (callback in mCallbacks) {
        callback.onVideoRecorded(this@CameraView, path, videoOrientation, deviceOrientation)
      }
    }

    override fun onFramePreview(data: ByteArray, width: Int, height: Int, orientation: Int) {
      for (callback in mCallbacks) {
        callback.onFramePreview(this@CameraView, data, width, height, orientation)
      }
    }

    override fun onMountError() {
      for (callback in mCallbacks) {
        callback.onMountError(this@CameraView)
      }
    }

    fun reserveRequestLayoutOnOpen() {
      mRequestLayoutOnOpen = true
    }
  }

  protected class SavedState : BaseSavedState {
    @Facing
    var facing = 0
    var cameraId: String? = null
    var ratio: AspectRatio? = null
    var autoFocus = false

    @Flash
    var flash = 0
    var exposure = 0f
    var focusDepth = 0f
    var zoom = 0f
    var whiteBalance = 0
    var playSoundOnCapture = false
    var playSoundOnRecord = false
    var scanning = false
    var pictureSize: Size? = null

    constructor(source: Parcel, loader: ClassLoader?) : super(source) {
      facing = source.readInt()
      cameraId = source.readString()
      ratio = source.readParcelable(loader)
      autoFocus = source.readByte().toInt() != 0
      flash = source.readInt()
      exposure = source.readFloat()
      focusDepth = source.readFloat()
      zoom = source.readFloat()
      whiteBalance = source.readInt()
      playSoundOnCapture = source.readByte().toInt() != 0
      playSoundOnRecord = source.readByte().toInt() != 0
      scanning = source.readByte().toInt() != 0
      pictureSize = source.readParcelable(loader)
    }

    constructor(superState: Parcelable?) : super(superState) {}

    override fun writeToParcel(out: Parcel, flags: Int) {
      super.writeToParcel(out, flags)
      out.writeInt(facing)
      out.writeString(cameraId)
      out.writeParcelable(ratio, 0)
      out.writeByte((if (autoFocus) 1 else 0).toByte())
      out.writeInt(flash)
      out.writeFloat(exposure)
      out.writeFloat(focusDepth)
      out.writeFloat(zoom)
      out.writeInt(whiteBalance)
      out.writeByte((if (playSoundOnCapture) 1 else 0).toByte())
      out.writeByte((if (playSoundOnRecord) 1 else 0).toByte())
      out.writeByte((if (scanning) 1 else 0).toByte())
      out.writeParcelable(pictureSize, flags)
    }

    companion object {
      val CREATOR = ParcelableCompat.newCreator<SavedState>(object : ParcelableCompatCreatorCallbacks<SavedState?> {
        override fun createFromParcel(`in`: Parcel, loader: ClassLoader): SavedState? {
          return SavedState(`in`, loader)
        }

        override fun newArray(size: Int): Array<SavedState?> {
          return arrayOfNulls(size)
        }
      })
    }
  }

  /**
   * Callback for monitoring events about [com.google.android.cameraview.CameraView].
   */
  abstract class Callback {
    /**
     * Called when camera is opened.
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     */
    open fun onCameraOpened(cameraView: CameraView) {}

    /**
     * Called when camera is closed.
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     */
    fun onCameraClosed(cameraView: CameraView) {}

    /**
     * Called when a picture is taken.
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     * @param data       JPEG data.
     */
    open fun onPictureTaken(cameraView: CameraView, data: ByteArray, deviceOrientation: Int) {}

    /**
     * Called when a video recording starts
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     * @param path       Path to recoredd video file.
     */
    open fun onRecordingStart(cameraView: CameraView, path: String, videoOrientation: Int, deviceOrientation: Int) {}

    /**
     * Called when a video recording ends, but before video is saved/processed.
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     * @param path       Path to recoredd video file.
     */
    open fun onRecordingEnd(cameraView: CameraView) {}

    /**
     * Called when a video is recorded.
     *
     * @param cameraView The associated [com.google.android.cameraview.CameraView].
     * @param path       Path to recoredd video file.
     */
    open fun onVideoRecorded(cameraView: CameraView, path: String?, videoOrientation: Int, deviceOrientation: Int) {}
    open fun onFramePreview(cameraView: CameraView, data: ByteArray, width: Int, height: Int, orientation: Int) {}
    open fun onMountError(cameraView: CameraView) {}
  }

  companion object {
    /** The camera device faces the opposite direction as the device's screen.  */
    const val FACING_BACK = Constants.FACING_BACK

    /** The camera device faces the same direction as the device's screen.  */
    const val FACING_FRONT = Constants.FACING_FRONT

    /** Flash will not be fired.  */
    const val FLASH_OFF = Constants.FLASH_OFF

    /** Flash will always be fired during snapshot.  */
    const val FLASH_ON = Constants.FLASH_ON

    /** Constant emission of light during preview, auto-focus and snapshot.  */
    const val FLASH_TORCH = Constants.FLASH_TORCH

    /** Flash will be fired automatically when required.  */
    const val FLASH_AUTO = Constants.FLASH_AUTO

    /** Flash will be fired in red-eye reduction mode.  */
    const val FLASH_RED_EYE = Constants.FLASH_RED_EYE
  }

  init {

    // bg hanadler for non UI heavy work
    mBgThread = HandlerThread("RNCamera-Handler-Thread")
    mBgThread!!.start()
    mBgHandler = Handler(mBgThread!!.looper)
    if (isInEditMode) {
      mCallbacks = null
      mDisplayOrientationDetector = null
      mAdjustViewBounds = false
      mContext = null
    } else {
      mAdjustViewBounds = true
      mContext = context

      // Internal setup
      val preview = createPreviewImpl(context)
      val callbacks = CallbackBridge()
      mCallbacks = callbacks
      mImpl = if (fallbackToOldApi || Build.VERSION.SDK_INT < 21 || Camera2.isLegacy(context!!)) {
        Camera1(callbacks, preview, mBgHandler)
      } else if (Build.VERSION.SDK_INT < 23) {
        Camera2(callbacks, preview, context, mBgHandler)
      } else {
        Camera2Api23(callbacks, preview, context, mBgHandler)
      }

      // Display orientation detector
      mDisplayOrientationDetector = object : DisplayOrientationDetector(context) {
        override fun onDisplayOrientationChanged(displayOrientation: Int, deviceOrientation: Int) {
          mImpl!!.setDisplayOrientation(displayOrientation)
          mImpl!!.setDeviceOrientation(deviceOrientation)
        }
      }
    }
  }
}
