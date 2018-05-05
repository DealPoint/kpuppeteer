package io.dealpoint.kpuppeteer

data class Version(val major: String, val minor: String)

data class Protocol(val version: Version, val domains: MutableList<Domain>) {
  fun domain(domainName: String): Domain =
    domains.firstOrNull { domainName == it.domain }
      ?: throw IllegalArgumentException("No such domain: " + domainName)

  fun merge(other: Protocol): Protocol {
    domains.addAll(other.domains)
    return this
  }
}