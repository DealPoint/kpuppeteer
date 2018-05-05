package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.PageDomain
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.util.*
import kotlin.test.assertTrue

object TestHelper {

  fun getFileUri(name: String): URI = javaClass.getResource("/$name").toURI()

  fun printAndOpenPdf(page: PageDomain) {
    val data = page.printToPDF().data
    val bytes = decoder.decode(data)
    println("got THIS many bytes of PDF: ${bytes.size}")
    // expect generated test PDFs to have something in them
    assertTrue(bytes.size > 5000)
    val target = Files.createTempFile("test", ".pdf")
    Files.write(target, bytes)
    try {
      Desktop.getDesktop().open(target.toFile())
    } catch (ex: Exception) {
      println("failed to open PDF: ${ex.message}")
    }
  }

  private val decoder = Base64.getDecoder()

}