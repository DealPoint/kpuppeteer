package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.utils.logger
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ChromeProcess(pathToChrome: String) : AutoCloseable {
  private val port = 9292
  private val path = Paths.get(pathToChrome).toAbsolutePath()
  private val chromeOptions = listOf(
    path.toString(), "--headless", "--disable-gpu", "--no-sandbox",
    "--remote-debugging-port=$port", "--crash-dumps-dir=/tmp")
  private val process = ProcessBuilder(chromeOptions)
    .start()

  private val errorStream = process.errorStream

  val webSocketURL = this.getChromeWebSocketURL()
  val hostPort = webSocketURL.substringBefore("/devtools/browser/")

  private val closingThread = thread(
    start = false,
    name = "chrome shutdown hook",
    isDaemon = true
  ) {
    this.close()
  }

  init {
    Runtime.getRuntime().addShutdownHook(closingThread)
  }

  override fun close() {
    log.info("shutting down chrome")
    this.errorStream.close()
    this.process.destroyForcibly()
  }

  private fun getChromeWebSocketURL(): String {
    log.info("attempting to get chrome web socket url from stderr")
    val regex = Regex("(ws://.*)")
    val errorStreamReader = errorStream.bufferedReader()
    do {
      val line = errorStreamReader.readLine()
      log.info(line)
      val match = regex.find(line)
      if (match !== null) {
        errorStreamReader.close()
        return match.value
      }
      TimeUnit.SECONDS.sleep(5)
    } while (errorStreamReader.ready())
    this.close()
    throw Error("could not find web socket url in stderr")
  }

  companion object {
    private val log = logger()
  }
}