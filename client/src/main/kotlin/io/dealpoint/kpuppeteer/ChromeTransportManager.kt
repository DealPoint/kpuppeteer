package io.dealpoint.kpuppeteer

import io.dealpoint.kpuppeteer.generated.Transport

interface TransportManager : AutoCloseable {
  fun getCurrentTransport(): Transport
  fun newTransport(type: String, targetId: String): Transport
}

class ChromeTransportManager(private val chromeProcess: ChromeProcess) : TransportManager {
  private var currentTransport = Transport(chromeProcess.webSocketURL)

  override fun getCurrentTransport(): Transport {
    return currentTransport
  }

  override fun newTransport(type: String, targetId: String): Transport {
    currentTransport.close()
    currentTransport = Transport("${chromeProcess.hostPort}/devtools/$type/$targetId")
    return currentTransport
  }

  override fun close() {
    chromeProcess.close()
    currentTransport.close()
  }
}