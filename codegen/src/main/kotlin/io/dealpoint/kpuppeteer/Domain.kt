package io.dealpoint.kpuppeteer

data class Domain(
  val domain: String?,
  private val types: List<Parameter>?,
  val commands: List<Command>?,
  val events: List<Command>?,
  val experimental: Boolean?,
  val dependencies: List<String> = emptyList(),
  val description: String?,
  val deprecated: Boolean?) {

  fun ref(id: String): Parameter {
    return types!!.firstOrNull { id == it.id } ?:
      throw IllegalStateException("Unresolved \$ref: $id in $domain")
  }

  fun simpleName(): String {
    return domain!! + "Domain"
  }

}