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

import android.view.Surface
import android.view.SurfaceHolder
import android.view.View

/**
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
abstract class PreviewImpl {
  interface Callback {
    fun onSurfaceChanged()
    fun onSurfaceDestroyed()
  }

  private var mCallback: Callback? = null
  var width = 0
    private set
  var height = 0
    private set

  fun setCallback(callback: Callback?) {
    mCallback = callback
  }

  abstract val surface: Surface
  abstract val view: View
  abstract val outputClass: Class<*>?
  abstract fun setDisplayOrientation(displayOrientation: Int)
  abstract val isReady: Boolean
  protected fun dispatchSurfaceChanged() {
    mCallback!!.onSurfaceChanged()
  }

  protected fun dispatchSurfaceDestroyed() {
    mCallback!!.onSurfaceDestroyed()
  }

  open val surfaceHolder: SurfaceHolder?
    get() = null
  open val surfaceTexture: Any?
    get() = null

  open fun setBufferSize(width: Int, height: Int) {}
  fun setSize(width: Int, height: Int) {
    this.width = width
    this.height = height
  }
}
