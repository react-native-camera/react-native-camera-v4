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

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
class Size
/**
 * Create a new immutable Size instance.
 *
 * @param width  The width of the size, in pixels
 * @param height The height of the size, in pixels
 */(val width: Int, val height: Int) : Comparable<Size>, Parcelable {
  override fun equals(o: Any?): Boolean {
    if (o == null) {
      return false
    }
    if (this === o) {
      return true
    }
    if (o is Size) {
      val size = o
      return width == size.width && height == size.height
    }
    return false
  }

  override fun toString(): String {
    return width.toString() + "x" + height
  }

  override fun hashCode(): Int {
    // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
    return height xor (width shl Integer.SIZE / 2 or (width ushr Integer.SIZE / 2))
  }

  override fun compareTo(another: Size): Int {
    return width * height - another.width * another.height
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(width)
    dest.writeInt(height)
  }

  companion object {
    fun parse(s: String): Size {
      val position = s.indexOf('x')
      require(position != -1) { "Malformed size: $s" }
      return try {
        val width = s.substring(0, position).toInt()
        val height = s.substring(position + 1).toInt()
        Size(width, height)
      } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Malformed size: $s", e)
      }
    }

    val CREATOR: Creator<Size?> = object : Creator<Size?> {
      override fun createFromParcel(source: Parcel): Size? {
        val width = source.readInt()
        val height = source.readInt()
        return Size(width, height)
      }

      override fun newArray(size: Int): Array<Size?> {
        return arrayOfNulls(size)
      }
    }
  }
}
