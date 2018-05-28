package io.dealpoint.kpuppeteer

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.util.function.Consumer
import kotlin.test.assertTrue

@NoCi
internal class KPuppeteerTest {

  @Test
  fun `starts browser and gets version`() {
    println("version: ${puppeteer.version}")
    assertTrue(puppeteer.version.product.contains("headless", ignoreCase = true))
    puppeteer.browserConnection.target.getTargets()
      .targetInfos.forEach { println(it) }
  }

  @Test
  fun `opens new page`() {
    val transport = puppeteer.newPage(TestHelper.getFileUri("test.html"))
    val runtime = transport.runtime
    runtime.enable()
    val page = transport.page
    val start = System.nanoTime()
    runtime.onConsoleAPICalled(Consumer {
      val arguments = it.args.mapNotNull { it.value?.toString() }.joinToString(", ")
      println("${System.nanoTime() - start} console.log > $arguments")
      if (arguments == "__renderComplete__") {
        println("${System.nanoTime() - start} page loaded")
      }
    })
    page.enable()
    page.onFrameStoppedLoading().get()
    TestHelper.printAndOpenPdf(page)
    println("${System.nanoTime() - start} done")
    page.navigate(TestHelper.getFileUri("test2.html").toString())
    page.onFrameStoppedLoading().get()
    println("${System.nanoTime() - start} reloaded")
    TestHelper.printAndOpenPdf(page)
  }

  private companion object {
    private val puppeteer by lazy {
      KPuppeteer(
        Paths.get("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
    }
  }

}