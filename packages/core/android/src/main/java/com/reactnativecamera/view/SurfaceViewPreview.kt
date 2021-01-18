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
import android.view.*
import androidx.core.view.ViewCompat
import com.reactnativecamera.R

internal class SurfaceViewPreview(context: Context?, parent: ViewGroup?) : PreviewImpl() {
  val mSurfaceView: SurfaceView
  public override fun getSurface(): Surface {
    return surfaceHolder.surface
  }

  public override fun getSurfaceHolder(): SurfaceHolder {
    return mSurfaceView.getHolder()
  }

  public override fun getView(): View {
    return mSurfaceView
  }

  public override fun getOutputClass(): Class<*> {
    return SurfaceHolder::class.java
  }

  public override fun setDisplayOrientation(displayOrientation: Int) {}
  public override fun isReady(): Boolean {
    return width != 0 && height != 0
  }

  init {
    val view: View = View.inflate(context, R.layout.surface_view, parent)
    mSurfaceView = view.findViewById(R.id.surface_view) as SurfaceView
    val holder: SurfaceHolder = mSurfaceView.getHolder()
    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    holder.addCallback(object : SurfaceHolder.Callback {
      override fun surfaceCreated(h: SurfaceHolder) {}
      override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        setSize(width, height)
        if (!ViewCompat.isInLayout(mSurfaceView)) {
          dispatchSurfaceChanged()
        }
      }

      override fun surfaceDestroyed(h: SurfaceHolder) {
        setSize(0, 0)
      }
    })
  }
}
