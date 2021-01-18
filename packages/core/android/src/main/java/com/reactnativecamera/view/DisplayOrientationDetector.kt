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
import android.util.SparseIntArray
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Monitors the value returned from [Display.getRotation].
 */
internal abstract class DisplayOrientationDetector(context: Context?) {
  private val mOrientationEventListener: OrientationEventListener

  companion object {
    /** Mapping from Surface.Rotation_n to degrees.  */
    val DISPLAY_ORIENTATIONS = SparseIntArray()

    init {
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0)
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90)
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180)
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270)
    }
  }

  var mDisplay: Display? = null
  var lastKnownDisplayOrientation = 0
    private set
  private var mLastKnownDeviceOrientation = 0
  fun enable(display: Display) {
    mDisplay = display
    mOrientationEventListener.enable()
    // Immediately dispatch the first callback
    dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS[display.rotation])
  }

  fun disable() {
    mOrientationEventListener.disable()
    mDisplay = null
  }

  fun dispatchOnDisplayOrientationChanged(displayOrientation: Int) {
    lastKnownDisplayOrientation = displayOrientation
    onDisplayOrientationChanged(displayOrientation, mLastKnownDeviceOrientation)
  }

  /**
   * Called when display orientation is changed.
   *
   * @param displayOrientation One of 0, 90, 180, and 270.
   * @param deviceOrientation One of 0, 90, 180, and 270.
   */
  abstract fun onDisplayOrientationChanged(displayOrientation: Int, deviceOrientation: Int)

  init {
    mOrientationEventListener = object : OrientationEventListener(context) {
      /** This is either Surface.Rotation_0, _90, _180, _270, or -1 (invalid).  */
      private var mLastKnownRotation = -1
      override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN ||
          mDisplay == null) {
          return
        }
        var hasChanged = false

        /** set device orientation  */
        val deviceOrientation: Int = if (orientation > 315 || orientation < 45) {
          0
        } else if (orientation in 46..134) {
          90
        } else if (orientation in 136..224) {
          180
        } else if (orientation in 226..314) {
          270
        } else {
          0
        }
        if (mLastKnownDeviceOrientation != deviceOrientation) {
          mLastKnownDeviceOrientation = deviceOrientation
          hasChanged = true
        }
        /** set screen orientation  */
        val rotation = mDisplay!!.rotation
        if (mLastKnownRotation != rotation) {
          mLastKnownRotation = rotation
          hasChanged = true
        }
        if (hasChanged) {
          dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS[rotation])
        }
      }
    }
  }
}
