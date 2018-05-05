package io.dealpoint.kpuppeteer

import com.squareup.kotlinpoet.*

data class Parameter(
  val description: String?,
  val type: String?,
  val items: Parameter?,
  val properties: List<Parameter>?,
  var className: ClassName?,
  val id: String?,
  val `$ref`: String?,
  var name: String?,
  var optional: Boolean,
  val enum: List<String>?,
  val experimental: Boolean?,
  val deprecated: Boolean?)