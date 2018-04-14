package io.dealpoint.kpuppeteer

import com.squareup.kotlinpoet.*

data class Version(val major: String, val minor: String)

data class Protocol(val version: Version, val domains: MutableList<Domain>?) {
  fun domain(domainName: String): Domain =
    domains!!.firstOrNull { domainName == it.domain }
      ?: throw IllegalArgumentException("No such domain: " + domainName)

  fun merge(other: Protocol): Protocol {
    domains!!.addAll(other.domains!!)
    return this
  }
}

data class Domain(
  val domain: String?,
  val types: List<Parameter>?,
  val commands: List<Command>?,
  val events: List<Command>?,
  val experimental: Boolean?,
  val dependencies: List<String> = emptyList(),
  val description: String?,
  val deprecated: Boolean?)

data class Command(
  val parameters: List<Parameter> = emptyList(),
  val description: String?,
  val name: String?,
  val experimental: Boolean?,
  val returns: List<Parameter> = emptyList(),
  val redirect: String?,
  val deprecated: Boolean?)

data class Parameter(
  val description: String?,
  val type: String?,
  val items: Parameter?,
  val properties: List<Parameter>?,
  var className: ClassName?,
  val id: String?,
  val `$ref`: String?,
  var name: String?,
  var spec: ParameterSpec?,
  var optional: Boolean,
  val enum: List<String>?,
  val experimental: Boolean?,
  val deprecated: Boolean?)

