package io.dealpoint.kpuppeteer.utils

import org.slf4j.LoggerFactory

inline fun <reified T : Any> T.logger(): org.slf4j.Logger {
  val k = this::class
  return LoggerFactory.getLogger(
    if (k.isCompanion) k.java.enclosingClass.simpleName
    else k.java.simpleName)
}
