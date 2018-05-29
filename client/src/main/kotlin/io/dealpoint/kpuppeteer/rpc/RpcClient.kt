package io.dealpoint.kpuppeteer.rpc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.channels.ClosedChannelException
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

private val emptyListeners = emptyList<Consumer<JsonNode>>()
const val FUTURE_TIMEOUT_SECONDS: Long = 10

class RpcClient(url: String) : AutoCloseable {

  private val idSeq = AtomicLong(0)
  private val methodFutures =
    ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()
  private val eventFutures =
    ConcurrentHashMap<String, MutableList<CompletableFuture<JsonNode>>>()
  private val eventListeners =
    ConcurrentHashMap<String, MutableList<Consumer<JsonNode>>>()
  private val socket: RpcSocket
  private val clientId = ++id
  private val isShuttingDown = AtomicBoolean(false)
  private val log =
    LoggerFactory.getLogger(this::class.java.simpleName + clientId)!!
  
  init {
    log.info("connecting to $url")
    socket = RpcSocket(url)
    socket.connect()
  }

  fun call(method: String, params: Map<String, Any?>): CompletableFuture<JsonNode> {
    return synchronized(socket) {
      if (socket.readyState !== WebSocket.READYSTATE.OPEN) {
        throw IllegalStateException("not connected")
      }
      val request = RpcRequest(idSeq.getAndIncrement(), method, params)
      val future = CompletableFuture<JsonNode>()
      methodFutures[request.id] = future
      val text = objectMapper.writeValueAsString(request)
      log.trace("> {}", text)
      socket.send(text)
      future
    }
  }

  fun <T> call(method: String, params: Map<String, Any?>, resultType: Class<T>): T {
    return call(method, params)
      .thenApply { result -> objectMapper.treeToValue(result, resultType) }
      .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  private fun addEventListener(method: String, listener: Consumer<JsonNode>) {
    var list = eventListeners[method]
    if (list == null) {
      list = Collections.synchronizedList(ArrayList())
      eventListeners[method] = list!!
    }
    list.add(listener)
  }

  fun <T> addEventListener(method: String, listener: Consumer<T>, eventType: Class<T>) {
    addEventListener(method, Consumer { response ->
      listener.accept(objectMapper.treeToValue(response, eventType))
    })
  }

  fun <T> eventFuture(method: String, eventType: Class<T>): CompletableFuture<T> {
    val future = CompletableFuture<JsonNode>()
    var list = eventFutures[method]
    if (list == null) {
      list = Collections.synchronizedList(ArrayList())
      eventFutures[method] = list!!
    }
    list.add(future)
    return future.thenApply { el -> objectMapper.treeToValue(el, eventType) }
  }

  override fun close() {
    cleanup(ClosedChannelException())
  }

  private fun dispatchEvent(method: String, event: JsonNode) {
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

  private fun dispatchResponse(response: RpcResponse) {
    val future = methodFutures.remove(response.id)
    if (future != null) {
      if (response.error != null) {
        log.error(response.error.message)
        future.completeExceptionally(RpcException(response.error.code, response.error.message))
      }
      future.complete(response.result)
    }
  }

  private fun cleanup(reason: Exception?) {
    if (isShuttingDown.getAndSet(true)) {
      return
    }
    log.info("attempting to close socket")
    socket.close()
    log.info("canceling all pending futures")
    completeFutures(methodFutures.values, reason)
    for (futures in eventFutures.values) {
      completeFutures(futures, reason)
    }
    methodFutures.clear()
    eventFutures.clear()
    log.info("RPC client shut down")
  }

  private fun <T, C: Iterable<CompletableFuture<T>>> completeFutures(
    futures: C, reason: Exception?
  ) {
    futures.forEach {
      if (reason === null) {
        it.complete(null)
      } else {
        it.completeExceptionally(reason)
      }
    }
  }

  private inner class RpcSocket(private val url: String)
    : WebSocketClient(URI.create(url), Draft_17())
  {

    private val connectLatch = CountDownLatch(1)

    override fun connect() {
      super.connect()
      connectLatch.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (connectLatch.count > 0) {
        throw TimeoutException("timeout attempting to connect to $url")
      }
    }

    override fun connectBlocking(): Boolean {
      throw NotImplementedError()
    }

    override fun onMessage(message: String) {
      if (log.isTraceEnabled) {
        log.trace("< {}", message.take(255))
      }
      val response = objectMapper.readValue<RpcResponse>(message)
      if (response.method !== null && response.params !==null) {
        dispatchEvent(response.method, response.params)
      } else {
        dispatchResponse(response)
      }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
      cleanup(RpcException(code.toLong(), reason))
    }

    override fun onError(e: Exception) {
      log.error(e.message)
      this@RpcClient.cleanup(e)
    }

    override fun onOpen(serverHandshake: ServerHandshake) {
      connectLatch.countDown()
    }
  }

  private companion object {
    val objectMapper = jacksonObjectMapper()
    private var id = 0
  }

}