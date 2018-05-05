package io.dealpoint.kpuppeteer

data class Command(
  val parameters: List<Parameter> = emptyList(),
  val description: String?,
  val name: String?,
  val experimental: Boolean?,
  val returns: List<Parameter> = emptyList(),
  val redirect: String?,
  val deprecated: Boolean?)
