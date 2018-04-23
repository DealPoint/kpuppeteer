package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.PageDomain
import io.dealpoint.kpuppeteer.utils.logger

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
    target.getTargetInfo(targetId)
    page.enable()
    return page
  }

  private fun createTargetId(url: String): String {
    val target = transportManager.getCurrentTransport().target
    val contextId = target.createBrowserContext().browserContextId
    return target.createTarget(url, browserContextId = contextId).targetId
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