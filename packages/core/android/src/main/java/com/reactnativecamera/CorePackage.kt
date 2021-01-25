package com.reactnativecamera

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.reactnativecamera.view.RNCameraViewManager


class CorePackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
    val module = CoreModule(reactContext)
    mModule = module
    CoreModule.instance = module
    mViewManager?.mModule = module
    return listOf(module)
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
    val viewManager = RNCameraViewManager()
    mViewManager = viewManager
    if (mModule != null) {
      viewManager.mModule = mModule
    }
    return listOf(viewManager)
  }

  var mViewManager: RNCameraViewManager? = null
  var mModule: CoreModule? = null
}
