package io.dealpoint.kpuppeteer

import com.fasterxml.jackson.module.kotlin.*
import com.squareup.kotlinpoet.*
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.annotation.Generated

const val BASE_PACKAGE = "kpuppeteer"
const val jsProtocol =
  "https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json?format=text"
const val browserProtocol =
  "https://chromium.googlesource.com/chromium/src/+/lkcr/third_party/WebKit/Source/core/inspector/browser_protocol.json?format=text"

fun main(args: Array<String>) {
  Codegen(args)
}

class Codegen(args: Array<String>) {

  private val clientClass = ClassName(Codegen::class.java.`package`.name + ".rpc", "RpcClient")
  private val objectMapper = jacksonObjectMapper()
  private val outDir = if (args.isNotEmpty()) File(args[0]) else null
  private val pattern = Pattern.compile("([A-Z]+)(.*)")
  private val domainTypeSpecs = mutableMapOf<String, TypeSpec.Builder>()
  private val protocol = loadProtocol(browserProtocol).merge(loadProtocol(jsProtocol))
  lateinit var generatedAnnotation: AnnotationSpec

  init { gencode() }

  private fun loadProtocol(url: String): Protocol {
    Base64.getDecoder()
      .wrap(URL(url).openStream())
      .use { stream -> InputStreamReader(stream, StandardCharsets.UTF_8)
      .use { reader -> return objectMapper.readValue(reader) } }
  }

  private fun gencode() {
    generatedAnnotation = AnnotationSpec.builder(Generated::class)
      .addMember("value = %S", Codegen::class.qualifiedName!!)
      .addMember("date = %S", Instant.now().toString())
      .build()

    initializeDomains()

    protocol.domains.forEach {
      val file = FileSpec.builder(BASE_PACKAGE, it.simpleName())
        .addType(domainTypeSpecs[it.domain]!!.build()).build()
      writeToOutDir(file)
    }
    genEntrypoint(protocol)
  }

  private fun initializeDomains() {
    for (domain in protocol.domains) {
      val domainTypeBuilder = TypeSpec.classBuilder(domain.simpleName())
        .addAnnotation(generatedAnnotation)
        .primaryConstructor(FunSpec.constructorBuilder()
          .addParameter("rpcClient", clientClass)
          .build())
        .addProperty(PropertySpec.builder("rpcClient", clientClass)
          .initializer("rpcClient")
          .build())

      domainTypeSpecs.put(domain.domain!!, domainTypeBuilder)
    }
    for (domain in protocol.domains) {
      buildDomainType(domain)
    }
  }

  private fun buildDomainType(domain: Domain) {
    buildCommands(domain)
    if (domain.events != null) {
      for (event in domain.events) {
        val typeSpec = domainTypeSpecs[domain.domain]!!
        val dataClass = buildDataClass(
          cap(event.name!!), event.description, event.parameters, domain)
        val qualifiedEventName = "\"${domain.domain}.${event.name}\""
        val className = dataClass.simpleName() + "::class.java"
        val eventListener = FunSpec.builder("on" + cap(event.name))
          .addParameter("listener", ParameterizedTypeName.get(
            Consumer::class.asClassName(), dataClass))
          .addStatement(
            "rpcClient.addEventListener($qualifiedEventName, listener, $className)")
        val eventFuture = FunSpec.builder("on" + cap(event.name))
          .returns(ParameterizedTypeName.get(
            CompletableFuture::class.asClassName(), dataClass))
          .addStatement(
            "return rpcClient.eventFuture($qualifiedEventName, $className)")
        if (event.description != null) {
          val description = event.description.replace("%", "%%") + "\n"
          eventListener.addKdoc(description)
          eventFuture.addKdoc(description)
        }
        typeSpec.addFunction(eventListener.build())
        typeSpec.addFunction(eventFuture.build())
      }
    }
  }

  private fun buildCommands(domain: Domain) {
    val domainType = domainTypeSpecs[domain.domain]!!
    for (command in domain.commands!!) {
      val funSpec = FunSpec.builder(command.name!!)
      val kDoc = StringBuilder()
      if (command.description != null) {
        kDoc.append(command.description + "\n")
      }
      val resultType = if (command.returns.isEmpty()) {
        Void::class.asClassName()
      } else {
        buildDataClass(command.name, command.description, command.returns, domain)
      }
      val returnType = ParameterizedTypeName.get(
        CompletableFuture::class.asClassName(), resultType)
      funSpec.returns(returnType)
        .addStatement("val params = hashMapOf<String, Any>()")
      for (param in command.parameters) {
        val type = typeNameForParameter(param, domain)
        val paramSpec = if (type != null) {
          val augmentedType = if (param.optional) {
            type.asNullable()
          } else type.asNonNullable()
          ParameterSpec.builder(param.name!!, augmentedType).build()
        } else {
          ParameterSpec.builder(param.name!!, Any::class).build()
        }
        val body = StringBuilder()
        body.append("params.put(\"${param.name!!}\", ${paramSpec.name}")
          .append(if (param.optional) "!!)" else ")")
        funSpec.addStatement(body.toString())
          .addParameter(paramSpec)

        if (param.description != null) {
          kDoc.append("@param ").append(param.name).append(" ")
            .append(param.description.replace("%", "%%"))
            .append("\n")
        }
      }
      funSpec.addKdoc(kDoc.toString())
        .addStatement(StringBuilder()
          .append("return rpcClient.call(\"${domain.domain}.${command.name}\"")
          .append(", params, ${resultType.simpleName()}::class.java)").toString())
      domainType.addFunction(funSpec.build())
    }
  }

  private fun typeNameForParameter(parameter: Parameter, domain: Domain)
    : TypeName? {
    val ref = parameter.`$ref`
    if (ref != null) {
      return if (ref.contains(".")) {
        val parts = ref
          .split("\\.".toRegex())
          .dropLastWhile { it.isEmpty() }
          .toTypedArray()
        val refDomain = protocol.domain(parts[0])
        typeNameForParameter(refDomain.ref(parts[1]), refDomain)
      } else {
        typeNameForParameter(domain.ref(ref), domain)
      }
    }
    when (parameter.type) {
      "string" -> return String::class.asTypeName()
      "integer" -> return Int::class.asTypeName()
      "boolean" -> return Boolean::class.asTypeName()
      "number" -> return Double::class.asTypeName()
      "object" -> {
        if (parameter.properties == null) {
          return ParameterizedTypeName.get(Map::class, String::class, Any::class)
        }
        if (parameter.className == null) {
          val typeName = cap(coalesce(parameter.id, parameter.name, "anonymous")!!)
          parameter.className = ClassName(BASE_PACKAGE, domain.simpleName(), typeName)
          buildDataClass(typeName, parameter.description, parameter.properties, domain)
        }
        return parameter.className
      }
      "array" -> return ParameterizedTypeName.get(
        List::class.asClassName(), typeNameForParameter(parameter.items!!, domain)!!)
      null -> return Any::class.asTypeName()
      else -> return Any::class.asTypeName()
    }
  }

  private fun genEntrypoint(protocol: Protocol) {
    val kpuppeteer = TypeSpec.classBuilder("KPuppeteer")
        .addSuperinterface(Closeable::class)
      .addProperty(PropertySpec.builder("rpcClient", clientClass)
        .initializer("RpcClient(webSocketDebuggerUrl)")
        .build())
      .addAnnotation(generatedAnnotation)

    val constructor = FunSpec.constructorBuilder()
      .addParameter("webSocketDebuggerUrl", String::class)

    for (domain in protocol.domains) {
      val className = ClassName("", domain.simpleName())
      val field = PropertySpec
        .builder(uncap(domain.domain), className).build()
      constructor.addStatement("$field = $className(rpcClient)")
    }

    kpuppeteer.primaryConstructor(constructor.build())
      .addFunction(FunSpec.builder("close")
      .addModifiers(KModifier.OVERRIDE)
      .addStatement("rpcClient.close()")
      .build())

    writeToOutDir(
      FileSpec.builder(BASE_PACKAGE, "KPuppeteer")
      .addType(kpuppeteer.build()).build())
  }

  private fun buildDataClass(name: String, description: String?, members: List<Parameter>, domain: Domain)
    : ClassName {
    val typeName = cap(name)
    val typeSpec = TypeSpec.classBuilder(typeName).addModifiers(
      if (members.isEmpty()) KModifier.ABSTRACT else KModifier.DATA)
    val constructor = FunSpec.constructorBuilder()
    if (description != null) {
      typeSpec.addKdoc(description.replace("%", "%%") + "\n")
    }
    for (member in members) {
      if (member.name == "this") {
        member.name = "this_"
      }
      if (member.name == "object") {
        member.name = "object_"
      }
      constructor.addParameter("val " + member.name!!,
        typeNameForParameter(member, domain)!!)
      if (member.description != null) {
        typeSpec.addKdoc(
          member.name!! + ": " + member.description.replace("%", "%%") + "\n")
      }
    }
    typeSpec.primaryConstructor(constructor.build())
    domainTypeSpecs[domain.domain]!!.addType(typeSpec.build())
    return ClassName(BASE_PACKAGE, domain.simpleName(), typeName)
  }

  private fun coalesce(vararg strs: String?): String? {
    strs.forEach { if (it != null) return it }
    return null
  }

  private fun cap(name: String): String =
    name.substring(0, 1).toUpperCase() + name.substring(1)

  private fun uncap(name: String?): String {
    val m = pattern.matcher(name!!)
    return if (m.matches()) {
      m.group(1).toLowerCase() + m.group(2)
    } else {
      name
    }
  }

  private fun writeToOutDir(file: FileSpec) {
    if (outDir == null) {
      file.writeTo(System.out)
    } else {
      file.writeTo(outDir)
    }
  }

}