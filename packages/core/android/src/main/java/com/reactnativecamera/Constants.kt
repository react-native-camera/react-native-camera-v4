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
package com.reactnativecamera

import com.reactnativecamera.view.AspectRatio

interface Constants {
  companion object {
    val DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3)
    const val FACING_BACK = 0
    const val FACING_FRONT = 1
    const val FLASH_OFF = 0
    const val FLASH_ON = 1
    const val FLASH_TORCH = 2
    const val FLASH_AUTO = 3
    const val FLASH_RED_EYE = 4
    const val LANDSCAPE_90 = 90
    const val LANDSCAPE_270 = 270
    const val WB_AUTO = 0
    const val WB_CLOUDY = 1
    const val WB_SUNNY = 2
    const val WB_SHADOW = 3
    const val WB_FLUORESCENT = 4
    const val WB_INCANDESCENT = 5
    const val ORIENTATION_AUTO = 0
    const val ORIENTATION_UP = 1
    const val ORIENTATION_DOWN = 2
    const val ORIENTATION_LEFT = 3
    const val ORIENTATION_RIGHT = 4
  }
}
