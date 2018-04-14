package io.dealpoint.kpuppeteer.rpc

import com.fasterxml.jackson.databind.JsonNode

data class RpcResponse(
  val id: Long = 0,
  val result: JsonNode? = null,
  val error: RpcError? = null,
  val method: String? = null,
  val params: JsonNode
)
