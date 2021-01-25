package com.reactnativecamera.barcode

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.reactnativecamera.CoreModule


class BarcodePackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
    val module = BarcodeModule(reactContext)
    val coreModule = CoreModule.instance ?: throw Error("Core RNCamera module instance not found")
    coreModule.registerPluginManager(module.manager)
    return listOf(module)
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
    return emptyList()
  }
}
