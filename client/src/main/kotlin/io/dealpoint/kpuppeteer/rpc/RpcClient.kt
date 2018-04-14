package io.dealpoint.kpuppeteer.rpc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.dealpoint.kpuppeteer.utils.logger

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.URI
import java.nio.channels.ClosedChannelException
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

private val emptyListeners = emptyList<Consumer<JsonNode>>()

class RpcClient(url: String) : AutoCloseable {

  private val idSeq = AtomicLong(0)
  private val methodFutures = ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()
  private val eventFutures = ConcurrentHashMap<String, MutableList<CompletableFuture<JsonNode>>>()
  private val eventListeners = ConcurrentHashMap<String, MutableList<Consumer<JsonNode>>>()
  internal var closeReason: Exception? = null
  internal val socket: RpcSocket

  init {

    log.info("connecting to " + url)
    socket = RpcSocket(url)
    try {
      socket.connectBlocking()
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }

    log.info("connected")
    if (closeReason != null) {
      if (closeReason is IOException) {
        throw closeReason as IOException
      } else {
        throw IOException(closeReason)
      }
    }
  }

  fun call(method: String, params: Map<String, Any>): CompletableFuture<JsonNode> {
    if (closeReason != null) {
      throw IllegalStateException("closed", closeReason)
    }
    val request = RpcRequest(idSeq.getAndIncrement(), method, params)
    val future = CompletableFuture<JsonNode>()
    methodFutures.put(request.id, future)
    val text = objectMapper.writeValueAsString(request)
    socket.send(text)
    return future
  }

  fun <T> call(method: String, params: Map<String, Any>, resultType: Class<T>): CompletableFuture<T> {
    return call(method, params).thenApply { result -> objectMapper.treeToValue(result, resultType) }
  }

  @Synchronized
  fun addEventListener(method: String, listener: Consumer<JsonNode>) {
    var list = eventListeners[method]
    if (list == null) {
      list = Collections.synchronizedList(ArrayList())
      eventListeners.put(method, list!!)
    }
    list.add(listener)
  }

  fun <T> addEventListener(method: String, listener: Consumer<T>, eventType: Class<T>) {
    addEventListener(method, Consumer { response ->
      listener.accept(objectMapper.treeToValue(response, eventType))
    })
  }

  @Synchronized
  fun <T> eventFuture(method: String, eventType: Class<T>): CompletableFuture<T> {
    val future = CompletableFuture<JsonNode>()
    var list = eventFutures[method]
    if (list == null) {
      list = Collections.synchronizedList(ArrayList())
      eventFutures.put(method, list!!)
    }
    list.add(future)
    return future.thenApply { el -> objectMapper.treeToValue(el, eventType) }
  }

  @Throws(IOException::class)
  override fun close() {
    close(ClosedChannelException())
  }

  @Synchronized internal fun dispatchEvent(method: String, event: JsonNode) {
    val futures = eventFutures.remove(method)
    if (futures != null) {
      for (future in futures) {
        future.complete(event)
      }
    }
    for (listener in eventListeners[method] ?: emptyListeners) {
      listener.accept(event)
    }
  }

  @Synchronized internal fun dispatchResponse(response: RpcResponse) {
    val future = methodFutures.remove(response.id)
    if (future != null) {
      if (response.error != null) {
        future.completeExceptionally(RpcException(response.error.code, response.error.message))
      }
      future.complete(response.result)
    }
  }

  private fun close(reason: Exception) {
    if (closeReason == null) {
      closeReason = reason
      socket.close()
    }
  }

  @Synchronized private fun cleanup() {
    for (future in methodFutures.values) {
      future.completeExceptionally(closeReason!!)
    }
    methodFutures.clear()

    for (futures in eventFutures.values) {
      for (future in methodFutures.values) {
        future.completeExceptionally(closeReason!!)
      }
    }
    eventFutures.clear()
  }

  internal inner class RpcSocket(url: String) : WebSocketClient(URI.create(url), Draft_17()) {

    override fun onMessage(message: String) {
      println("Message: " + message.substring(0, Math.min(message.length, 2048)))
      val response = objectMapper.readValue<RpcResponse>(message)

      if (response.method == null) {
        dispatchResponse(response)
      } else {
        dispatchEvent(response.method, response.params)
      }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
      if (closeReason != null) {
        closeReason = RpcException(code.toLong(), reason)
        cleanup()
      }
    }

    override fun onError(e: Exception) {
      e.printStackTrace()
      this@RpcClient.close(e)
    }

    override fun onOpen(serverHandshake: ServerHandshake) {

    }
  }

  companion object {
    internal var objectMapper = jacksonObjectMapper()
    internal val log = logger()
  }
}