package com.reactnativecamera.utils

import android.content.Context
import java.io.File

/**
 * Created by jgfidelis on 23/01/18.
 */
class ScopedContext(context: Context?) {
  var cacheDirectory: File? = null
    private set

  private fun createCacheDirectory(context: Context?) {
    cacheDirectory = File(context?.cacheDir.toString() + "/Camera/")
  }

  init {
    createCacheDirectory(context)
  }
}
