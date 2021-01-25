package com.reactnativecamera.tasks

import com.facebook.react.bridge.WritableMap

interface PictureSavedDelegate {
  fun onPictureSaved(response: WritableMap)
}
