package io.dealpoint.kpuppeteer.rpc

data class RpcRequest (
  val id: Long,
  val method: String,
  val params: Map<String, Any?>)
