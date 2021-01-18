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

import android.graphics.SurfaceTexture
import android.media.CamcorderProfile
import android.os.Handler
import android.view.View
import com.facebook.react.bridge.ReadableMap
import java.util.*

abstract class CameraViewImpl(
  val mCallback: Callback,
  val mPreview: PreviewImpl,
  // Background handler that the implementation an use to run heavy tasks in background
  // in a thread/looper provided by the view.
  // Most calls should not require this since the view will already schedule it
  // on the bg thread. However, the implementation might need to do some heavy work
  // by itself.
  protected val mBgHandler: Handler) {
  val view: View
    get() = mPreview.view

  /**
   * @return `true` if the implementation was able to start the camera session.
   */
  abstract fun start(): Boolean
  abstract fun stop()
  abstract val isCameraOpened: Boolean
  abstract var facing: Int
  abstract var cameraId: String?
  abstract val supportedAspectRatios: Set<AspectRatio?>?
  abstract val cameraIds: List<Properties?>?
  abstract fun getAvailablePictureSizes(ratio: AspectRatio?): SortedSet<Size?>?
  abstract var pictureSize: Size?

  /**
   * @return `true` if the aspect ratio was changed.
   */
  abstract fun setAspectRatio(ratio: AspectRatio?): Boolean
  abstract val aspectRatio: AspectRatio?
  abstract var autoFocus: Boolean
  abstract var flash: Int
  abstract var exposureCompensation: Float
  abstract fun takePicture(options: ReadableMap?)
  abstract fun record(path: String?, maxDuration: Int, maxFileSize: Int,
                      recordAudio: Boolean, profile: CamcorderProfile?, orientation: Int, fps: Int): Boolean

  abstract fun stopRecording()
  abstract fun pauseRecording()
  abstract fun resumeRecording()
  abstract val cameraOrientation: Int
  abstract fun setDisplayOrientation(displayOrientation: Int)
  abstract fun setDeviceOrientation(deviceOrientation: Int)
  abstract fun setFocusArea(x: Float, y: Float)
  abstract var focusDepth: Float
  abstract var zoom: Float
  abstract val supportedPreviewFpsRange: ArrayList<IntArray?>?
  abstract var whiteBalance: Int
  abstract var playSoundOnCapture: Boolean
  abstract var playSoundOnRecord: Boolean
  abstract var scanning: Boolean
  abstract fun resumePreview()
  abstract fun pausePreview()
  abstract fun setPreviewTexture(surfaceTexture: SurfaceTexture?)
  abstract val previewSize: Size?

  interface Callback {
    fun onCameraOpened()
    fun onCameraClosed()
    fun onPictureTaken(data: ByteArray?, deviceOrientation: Int)
    fun onVideoRecorded(path: String?, videoOrientation: Int, deviceOrientation: Int)
    fun onRecordingStart(path: String?, videoOrientation: Int, deviceOrientation: Int)
    fun onRecordingEnd()
    fun onFramePreview(data: ByteArray?, width: Int, height: Int, orientation: Int)
    fun onMountError()
  }
}
