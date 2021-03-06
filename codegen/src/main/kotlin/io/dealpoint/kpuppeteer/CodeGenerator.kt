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
import kotlin.reflect.jvm.internal.impl.renderer.KeywordStringsGenerated

const val GENERATED_FILE_INDENT = "  "
const val GENERATED_PACKAGE = "io.dealpoint.kpuppeteer.generated"
const val JS_PROTOCOL =
  "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/json/js_protocol.json"
const val BROWSER_PROTOCOL =
  "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/json/browser_protocol.json"

fun main(args: Array<String>) {
  CodeGenerator(args)
}

/**
 * Generate the protocol classes for the
 * [Chrome devtools protocol](https://chromedevtools.github.io/devtools-protocol/tot)
 */
class CodeGenerator(args: Array<String>) {

  private val objectMapper = jacksonObjectMapper()
  private val outDir = if (args.isNotEmpty()) File(args[0]) else null
  private val pattern = Pattern.compile("([A-Z]+)(.*)")
  private val protocol =
    loadProtocol(BROWSER_PROTOCOL)
      .merge(loadProtocol(JS_PROTOCOL))

  init {
    println("[INFO] initializing ${protocol.domains.size} domains")
    initializeDomains()
    generateCode()
  }

  private fun loadProtocol(url: String): Protocol {
    println("[INFO] fetching protocol: $url")
    val reader = URL(url).openStream().bufferedReader()
    return objectMapper.readValue(reader)
  }

  private fun initializeDomains() {
    for (domain in protocol.domains) {
      domain.initialize()
    }
    // this must wait until after the TypeSpec for each domain has been initialized
    for (domain in protocol.domains) {
      buildDomainType(domain)
    }
  }

  private fun generateCode() {
    protocol.domains.forEach {
      writeToOutDir(FileSpec
        .builder(GENERATED_PACKAGE, it.simpleName())
        .indent(GENERATED_FILE_INDENT)
        .addType(domainTypeSpecs[it.domain]!!.build()).build())
    }
    generateEntryPoint(protocol)
  }

  private fun buildDomainType(domain: Domain) {
    buildCommands(domain)
    if (domain.events == null) {
      return
    }
    for (event in domain.events) {
      val qualifiedEventName = "\"${domain.domain}.${event.name}\""
      try {
        val typeSpec = domainTypeSpecs[domain.domain]!!
        val dataClass = buildDataClass(
          capitalize(event.name!!), event.description, event.parameters, domain)
        val className = dataClass.simpleName() + "::class.java"
        val functionName = "on" + capitalize(event.name)
        val eventListener = FunSpec.builder(functionName)
          .addParameter("listener", ParameterizedTypeName.get(
            Consumer::class.asClassName(), dataClass))
          .addStatement(
            "rpcClient.addEventListener($qualifiedEventName, listener, $className)")
        val eventFuture = FunSpec.builder(functionName)
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
      } catch (ex: Exception) {
        println(
          "[ERROR] failed generating functions for event $qualifiedEventName: $ex")
      }
    }
  }

  private fun buildCommands(domain: Domain) {
    if (domain.commands == null) {
      return
    }
    val domainType = domainTypeSpecs[domain.domain]!!
    for (command in domain.commands) {
      try {
        val funSpec = FunSpec.builder(command.name!!)
        val kDoc = StringBuilder()
        if (command.description != null) {
          kDoc.append(command.description + "\n")
        }
        val resultType = if (command.returns.isEmpty()) {
          Void::class.asClassName()
        } else {
          buildDataClass(
            command.name, command.description, command.returns, domain)
        }
        funSpec.returns(resultType)
          .addStatement("val params = hashMapOf<String, Any>()")
        for (param in command.parameters) {
          val type = typeNameForParameter(param, domain)
          val paramSpec = (if (type == null) {
            ParameterSpec.builder(param.name!!, Any::class)
          } else {
            if (param.optional) {
              ParameterSpec
                .builder(param.name!!, type.asNullable())
                .defaultValue("null")
            } else {
              ParameterSpec.builder(param.name!!, type.asNonNullable())
            }
          }).build()
          val defaultExpression = "params[\"${param.name!!}\"] = ${paramSpec.name}"
          val nullSafetyExpression = "${param.name}?.let { $defaultExpression }"
          funSpec
            .addStatement(
              if (param.optional) nullSafetyExpression else defaultExpression)
            .addParameter(paramSpec)
          if (param.description != null) {
            kDoc.append("@param ").append(param.name).append(" ")
              .append(param.description.replace("%", "%%"))
              .append("\n")
          }
        }
        funSpec.addKdoc(kDoc.toString())
          .addStatement(StringBuilder()
            .append("return rpcClient.call(\"${domain.domain}.${command.name}\", ")
            .append("params, ${resultType.simpleName()}::class.java)").toString())
        domainType.addFunction(funSpec.build())
      } catch (ex: Exception) {
        println(
          "[ERROR] failed generating command ${domain.simpleName()}.${command.name}: $ex")
      }
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
          val typeName = capitalize(parameter.id ?: parameter.name ?: "anonymous")
          parameter.className = domain.classNameForType(typeName)
          buildDataClass(
            typeName, parameter.description, parameter.properties, domain)
        }
        return parameter.className
      }
      "array" -> return ParameterizedTypeName.get(
        List::class.asClassName(),
        typeNameForParameter(parameter.items!!, domain)!!)
      null -> return Any::class.asTypeName()
      else -> return Any::class.asTypeName()
    }
  }

  private fun generateEntryPoint(protocol: Protocol) {
    println("[INFO] generating entry point Transport")
    val transport = TypeSpec.classBuilder("Transport")
        .addSuperinterface(AutoCloseable::class)
      .addProperty(PropertySpec
        .builder("rpcClient", clientClass, KModifier.PRIVATE)
        .initializer("RpcClient(webSocketDebuggerUrl)")
        .build())
      .addAnnotation(generatedAnnotation)

    val constructor = FunSpec.constructorBuilder()
      .addParameter("webSocketDebuggerUrl", String::class)

    for (domain in protocol.domains) {
      val className = ClassName("", domain.simpleName())
      transport.addProperty(PropertySpec
        .builder(unCapitalize(domain.domain), className)
        .initializer(domain.simpleName() + "(rpcClient)")
        .build())
    }

    transport.primaryConstructor(constructor.build())
      .addFunction(FunSpec.builder("close")
      .addModifiers(KModifier.OVERRIDE)
      .addStatement("rpcClient.close()")
      .build())

    writeToOutDir(
      FileSpec.builder(GENERATED_PACKAGE, "Transport")
        .indent(GENERATED_FILE_INDENT)
        .addType(transport.build()).build())
  }

  private fun buildDataClass(
    name: String, description: String?, members: List<Parameter>, domain: Domain)
    : ClassName {
    val typeName = capitalize(name)
    val modifier = if (members.isEmpty())
      KModifier.ABSTRACT else KModifier.DATA
    val typeSpec = TypeSpec.classBuilder(typeName).addModifiers(modifier)
    val constructor = FunSpec.constructorBuilder()
    if (description != null) {
      typeSpec.addKdoc(description.replace("%", "%%") + "\n")
    }
    for (member in members) {
      if (member.name in KeywordStringsGenerated.KEYWORDS) {
        member.name = "`${member.name}`"
      }
      val memberType = typeNameForParameter(member, domain)!!
      val augmentedType = if (member.optional)
        memberType.asNullable() else memberType.asNonNullable()
      constructor.addParameter("val " + member.name!!, augmentedType)
      if (member.description != null) {
        typeSpec.addKdoc(
          member.name!! + ": " + member.description.replace("%", "%%") + "\n")
      }
    }
    if (modifier == KModifier.DATA) {
      typeSpec.primaryConstructor(constructor.build())
    }
    domainTypeSpecs[domain.domain]!!.addType(typeSpec.build())
    println(
      "[INFO] generated ${modifier.name} class ${domain.simpleName()}.$typeName")
    return domain.classNameForType(typeName)
  }

  private fun capitalize(name: String): String =
    name.substring(0, 1).toUpperCase() + name.substring(1)

  private fun unCapitalize(name: String?): String {
    val m = pattern.matcher(name!!)
    return if (m.matches()) {
      m.group(1).toLowerCase() + m.group(2)
    } else {
      name
    }
  }

  private fun writeToOutDir(file: FileSpec) {
    val logMessage = "writing file ${file.name} to package ${file.packageName}"
    println("[INFO] $logMessage")
    try {
      if (outDir == null) {
        file.writeTo(System.out)
      } else {
        file.writeTo(outDir)
      }
    } catch (ex: IOException) {
      println("[ERROR] $logMessage: $ex")
    }
  }

  companion object {
    val domainTypeSpecs = mutableMapOf<String, TypeSpec.Builder>()
    val generatedAnnotation = AnnotationSpec.builder(Generated::class)
      .addMember("value = [%S]", CodeGenerator::class.qualifiedName!!)
      .addMember("date = %S", Instant.now().toString())
      .build()
    val clientClass = ClassName(
      CodeGenerator::class.java.`package`.name + ".rpc", "RpcClient")
  }

}
