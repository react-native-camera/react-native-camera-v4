package com.reactnativecamera.utils

import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Created by jgfidelis on 23/01/18.
 */
object RNFileUtils {
  @Throws(IOException::class)
  fun ensureDirExists(dir: File): File {
    if (!(dir.isDirectory || dir.mkdirs())) {
      throw IOException("Couldn't create directory '$dir'")
    }
    return dir
  }

  @Throws(IOException::class)
  fun getOutputFilePath(directory: File, extension: String): String {
    ensureDirExists(directory)
    val filename = UUID.randomUUID().toString()
    return directory.toString() + File.separator + filename + extension
  }

  fun uriFromFile(file: File?): Uri {
    return Uri.fromFile(file)
  }
}
