package io.dealpoint.kpuppeteer

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

private const val BUFFER_SIZE = 8 * 1024

class Utf8StreamReader(
  client: StreamReaderClient, stream: InputStream, endMarker: String
) : Closeable {

  override fun close() {
    log.info("shutting down reader")
    receiverThread.interrupt()
    safelyClose(reader)
  }

  private val reader = stream.bufferedReader(StandardCharsets.UTF_8)
  private val endMarkerLength = endMarker.length
  private val self = this

  fun waitForShutdown() {
    receiverThread.join(1000)
  }

  private val receiverThread = thread(
    name = "UtfReader thread",
    isDaemon = true
  ) {
    val myThread = Thread.currentThread()
    log.info("started receiver thread", myThread.id)
    val buffer = CharArray(BUFFER_SIZE)
    val builder = StringBuilder(BUFFER_SIZE)
    var charsRead = reader.read(buffer)
    reader.ready()
    while (charsRead != -1) {
      if (myThread.isInterrupted) {
        log.warn("reader thread has been interrupted!")
        throw InterruptedException()
      }
      // capture builder's current length before updating it, to optimize
      // where we start to search for the end marker
      val builderLength = builder.length
      builder.append(buffer, 0, charsRead)
      var searchFrom =
        if (builderLength > endMarkerLength) builderLength - endMarkerLength
        else 0
      var messageEnd: Int
      do {
        messageEnd = builder.indexOf(endMarker, searchFrom)
        if (messageEnd > -1) {
          val raw = builder.substring(0, messageEnd)
          builder.delete(0, messageEnd + endMarkerLength)
          searchFrom = 0
          client.handleMessage(raw, self)
        }
      } while (messageEnd > -1)
      charsRead = reader.read(buffer)
    }
    client.streamClosed(self)
  }

  private fun safelyClose(closable: Closeable) {
    try {
      closable.close()
    } catch (ex: Exception) {
      log.info("failed to close stream $closable:", ex)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java.enclosingClass.simpleName)!!
  }

}

