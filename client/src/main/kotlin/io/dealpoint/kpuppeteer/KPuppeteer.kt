package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.Transport
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class KPuppeteer(pathToChrome: Path) : AutoCloseable {

  private val port = 9292
  private val path = pathToChrome.toRealPath()
  private val chromeOptions = listOf(
    path.toString(),
    "--headless", "--disable-gpu", "--no-sandbox",
    "--remote-debugging-port=$port", "--crash-dumps-dir=/tmp")
  private val process = ProcessBuilder(chromeOptions).start()
  private val connections = ConcurrentHashMap<String, Transport>()

  val browserConnection = try {
    process.inputStream.close()
    process.outputStream.close()
    val browserTargetUrl = getChromeWebSocketUrl(process)
    val newTransport = Transport(browserTargetUrl)
    val target = newTransport.target
    val targetId = target.getTargets().targetInfos.first().targetId
    connections[targetId] = newTransport
    newTransport
  } catch (ex: Exception) {
    process.destroyForcibly()
    throw ex
  }

  val version by lazy { browserConnection.browser.getVersion() }

  init {
    Runtime.getRuntime().addShutdownHook(
      thread(start = false, name = "chrome shutdown hook", isDaemon = true) {
        close()
      })
  }

  fun newPage(uri: URI? = null): Transport {
    val url = uri?.toString() ?: "about:blank"
    val targetId = browserConnection.target.createTarget(url).targetId
    val newTransport = try {
      Transport("ws://127.0.0.1:$port/devtools/page/$targetId")
    } catch (ex: Exception) {
      val attemptedTarget = try {
        browserConnection.target.getTargets().targetInfos
          .firstOrNull { it.targetId == targetId }
      } catch (ignore: Exception) {
        throw ex
      }
      log.error("failed to connect to target $attemptedTarget")
      throw ex
    }
    connections[targetId] = newTransport
    newTransport.target.attachToTarget(targetId)
    log.info("connected new Page Target ID $targetId for '$url'")
    return newTransport
  }

  @Synchronized
  override fun close() {
    log.info("shutting down headless Chrome process")
    connections.entries.forEach {
      try {
        it.value.close()
      } catch (ex: Exception) {
        log.info("failed to close websocket for ${it.key}")
      }
    }
    process.destroyForcibly()
  }

  private fun getChromeWebSocketUrl(process: Process): String {
    log.info("attempting to get web socket url from the Chrome process' stderr")
    val regex = Regex("(ws://\\S+)")
    val errorStreamReader = process.errorStream.bufferedReader()
    do {
      val line = errorStreamReader.readLine()
      log.debug("chrome> {}", line)
      val match = regex.find(line)
      if (match !== null) {
        errorStreamReader.close()
        process.errorStream.close()
        return match.value
      }
      TimeUnit.MILLISECONDS.sleep(100)
    } while (errorStreamReader.ready())
    throw Error("could not find web socket url in stderr")
  }

  private companion object {
    val log = LoggerFactory.getLogger(this::class.java.enclosingClass.simpleName)!!
  }

}