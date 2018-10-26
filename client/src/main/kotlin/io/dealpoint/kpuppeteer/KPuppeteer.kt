package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.Transport
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class KPuppeteer(pathToChrome: Path, private val port: Int = 9292) : AutoCloseable {
  private val path = pathToChrome.toRealPath()
  private val chromeOptions = listOf(
    path.toString(),
    "--headless", "--disable-gpu", "--no-sandbox",
    "--remote-debugging-port=$port", "--crash-dumps-dir=/tmp")
  private val process = ProcessBuilder(chromeOptions).start()
  private val connections = ConcurrentHashMap<String, Transport>()
  private val isShuttingDown = AtomicBoolean(false)
  private val readerClient = WarnReaderClient(log)
  private var errorReader: Utf8StreamReader? = null

  val browserConnection = try {
    process.outputStream.close()
    val browserTargetUrl = getChromeWebSocketUrl(process)
    val newTransport = Transport(browserTargetUrl)
    val target = newTransport.target
    val targetId = target.getTargets().targetInfos.first().targetId
    connections[targetId] = newTransport
    errorReader = Utf8StreamReader(readerClient, process.errorStream, "\n")
    newTransport
  } catch (ex: Exception) {
    process.destroyForcibly()
    throw ex
  }

  val version by lazy { browserConnection.browser.getVersion() }

  //Only returns actual pid on unix systems
  val pid by lazy {
    var pid: Long = -1
    val processClass = process::class.java
    try {
      if (processClass.name == "java.lang.UNIXProcess") {
        val f: Field = processClass.getDeclaredField("pid")
        f.isAccessible = true
        pid = f.getLong(process)
        f.isAccessible = false
      }
    } catch (e: Exception) {
      pid = -1
    }
    pid
  }

  init {
    Runtime.getRuntime().addShutdownHook(
      thread(start = false, name = "chrome shutdown hook", isDaemon = true) {
        close()
      })
    log.info("KPuppeteer started with pid: $pid")
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

  override fun close() {
    if (isShuttingDown.getAndSet(true)) {
      return
    }
    log.info("disconnecting headless Chrome web sockets")
    connections.entries.forEach {
      try {
        it.value.close()
      } catch (ex: Exception) {
        log.info("failed to close websocket for ${it.key}")
      }
    }
    errorReader?.close()
    log.info("shutting down headless Chrome process")
    process.destroyForcibly().waitFor(2, TimeUnit.SECONDS)
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