package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.utils.logger
import java.io.File

const val DEFAULT_CHROME_INSTALLATION = "/Applications/Google Chrome.app/Contents/MacOS/"

class ChromeProcess(pathToChrome: String? = DEFAULT_CHROME_INSTALLATION) : AutoCloseable {
  private val port = 9292
  private val chromeOptions = listOf(
    "./Google Chrome", "--headless", "--disable-gpu",
    "--remote-debugging-port=$port", "--crash-dumps-dir=/tmp")
  private val process = ProcessBuilder(chromeOptions)
    .directory(File(pathToChrome))
    .start()

  private val errorStream = process.errorStream

  val webSocketURL = this.getChromeWebSocketURL()
  val hostPort = webSocketURL.substringBefore("/devtools/browser/")

  override fun close() {
    this.errorStream.close()
    this.process.destroyForcibly()
  }

  private fun getChromeWebSocketURL(): String {
    log.info("attempting to get chrome web socket url from stderr")
    val regex = Regex("(ws:\\/\\/.*)")
    val errorStreamReader = errorStream.bufferedReader()
    do {
      val line = errorStreamReader.readLine()
      log.info(line)
      val match = regex.find(line)
      if (match !== null) {
        errorStreamReader.close()
        return match.value
      }
    } while (errorStreamReader.ready())
    errorStreamReader.close()
    throw Error("could not find web socket url in stderr")
  }

  companion object {
    private val log = logger()
  }
}