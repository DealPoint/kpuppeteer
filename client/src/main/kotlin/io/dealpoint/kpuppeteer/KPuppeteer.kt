package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.utils.logger
import java.io.File

const val DEFAULT_CHROME_INSTALLATION = "/Applications/Google Chrome.app/Contents/MacOS/"

class KPuppeteer(
  pathToChrome: String? = DEFAULT_CHROME_INSTALLATION) {

  private val port = 9292
  private val chromeOptions = listOf(
    "./Google Chrome", "--headless",
    "--remote-debugging-port=$port", "--crash-dumps-dir=/tmp")
  private val process = ProcessBuilder(chromeOptions)
    .directory(File(pathToChrome))
    .start()
  private val errorStream = process.errorStream

  companion object {
    private val log = logger()
  }
}