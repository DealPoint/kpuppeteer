package io.dealpoint.kpuppeteer.rpc

import com.fasterxml.jackson.databind.JsonNode

data class RpcResponse(
  val id: Long = 0,
  val result: JsonNode?,
  val error: RpcError?,
  val method: String?,
  val params: JsonNode?
)
