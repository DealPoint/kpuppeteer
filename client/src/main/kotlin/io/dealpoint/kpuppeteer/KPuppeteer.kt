package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.PageDomain
import io.dealpoint.kpuppeteer.utils.logger
import java.util.concurrent.TimeUnit

class KPuppeteer(val transportManager: TransportManager) : AutoCloseable {

  fun newPage(): PageDomain {
    return goto("about:blank")
  }

  fun goto(url: String): PageDomain {
    log.debug("attempting to load: $url")
    val targetId = this.createTargetId(url)
    val transport = transportManager.newTransport("page", targetId)
    val page = transport.page
    val target = transport.target
    target.getTargetInfo(targetId).get(10000, TimeUnit.MILLISECONDS)
    page.enable().get(10000, TimeUnit.MILLISECONDS)
    return page
  }

  private fun createTargetId(url: String): String {
    val target = transportManager.getCurrentTransport().target
    val contextId = target.createBrowserContext().get().browserContextId
    return target.createTarget(url, browserContextId = contextId).get().targetId
  }

  override fun close() {
    transportManager.close()
  }

  companion object {
    private val log = logger()
    fun launch(): KPuppeteer {
      val rpcManager = ChromeTransportManager(ChromeProcess())
      return KPuppeteer(rpcManager)
    }
  }
}