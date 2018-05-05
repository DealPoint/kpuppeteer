package io.dealpoint.kpuppeteer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.dealpoint.kpuppeteer.Codegen.Companion.clientClass
import io.dealpoint.kpuppeteer.Codegen.Companion.domainTypeSpecs
import io.dealpoint.kpuppeteer.Codegen.Companion.generatedAnnotation

data class Domain(
  val domain: String?,
  val commands: List<Command>?,
  val events: List<Command>?,
  val experimental: Boolean?,
  val dependencies: List<String> = emptyList(),
  val description: String?,
  val deprecated: Boolean?,
  private val types: List<Parameter>?) {

  fun ref(id: String): Parameter {
    return types!!.firstOrNull { id == it.id } ?:
      throw IllegalStateException("Unresolved \$ref: $id in $domain")
  }

  fun simpleName(): String {
    return domain!! + "Domain"
  }

  fun classNameForType(typeName: String): ClassName {
    return ClassName(GENERATED_PACKAGE, simpleName(), typeName)
  }

  fun initialize() {
    try {
      val domainName = domain!!
      val domainTypeBuilder = TypeSpec.classBuilder(simpleName())
        .addAnnotation(generatedAnnotation)
        .primaryConstructor(FunSpec.constructorBuilder()
          .addParameter("rpcClient", clientClass)
          .build())
        .addProperty(PropertySpec.builder("rpcClient", clientClass)
          .initializer("rpcClient")
          .build())
      domainTypeSpecs.put(domainName, domainTypeBuilder)
      Codegen.log.info("initialized TypeSpec builder for domain $domainName")
    } catch (ex: Exception) {
      Codegen.log.error("error initializing domain $domain.domain: $ex")
    }

  }
}