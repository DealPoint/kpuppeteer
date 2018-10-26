package io.dealpoint.kpuppeteer

interface StreamReaderClient {
  fun handleMessage(raw: String, from: Utf8StreamReader)
  fun streamClosed(from: Utf8StreamReader)
}
