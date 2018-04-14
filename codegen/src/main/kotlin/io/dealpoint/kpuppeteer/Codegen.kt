package io.dealpoint.kpuppeteer

import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*


fun main(args: Array<String>) {
  Codegen(args)
}

class Codegen(args: Array<String>) {

  private val jsProtocol =
    "https://chromium.googlesource.com/v8/v8/+/master/src/inspector/js_protocol.json?format=text"
  private val browserProtocol =
    "https://chromium.googlesource.com/chromium/src/+/lkcr/third_party/WebKit/Source/core/inspector/browser_protocol.json?format=text"
  private val objectMapper = jacksonObjectMapper()


  init {
    val protocol = loadProtocol(browserProtocol).merge(loadProtocol(jsProtocol))
  }

  private fun loadProtocol(url: String): Protocol {
    Base64.getDecoder()
      .wrap(URL(url).openStream())
      .use { stream -> InputStreamReader(stream, StandardCharsets.UTF_8)
      .use { reader -> return objectMapper.readValue(reader) } }
  }

}