package io.dealpoint.kpuppeteer

import org.slf4j.Logger

class WarnReaderClient(private val logger: Logger): StreamReaderClient{
  override fun handleMessage(raw: String, from: Utf8StreamReader) {
    logger.warn(raw)
  }

  override fun streamClosed(from: Utf8StreamReader) {
    logger.info("stream closed")
  }

}