package com.reactnativecamera.utils

/*
  * Replacement for Objects.equals that is only available after Android API 19
  */
fun objectEquals(o1: Any?, o2: Any?): Boolean {
  if (o1 == null && o2 == null) return true
  return if (o1 == null) false else o1 == o2
}
