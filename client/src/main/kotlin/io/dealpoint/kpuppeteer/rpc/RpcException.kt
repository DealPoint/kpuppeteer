package io.dealpoint.kpuppeteer.rpc

data class RpcException(
  val code: Long, override val message: String) : Exception("$message ($code)")