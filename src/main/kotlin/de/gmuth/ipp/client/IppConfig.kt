package de.gmuth.ipp.client

import de.gmuth.http.Http
import de.gmuth.log.Logging
import java.nio.charset.Charset
import javax.net.ssl.SSLSocketFactory

class IppConfig(
        timeout: Int = 30000, // milli seconds
        userAgent: String? = "ipp-client-kotlin/2.2",
        sslSocketFactory: SSLSocketFactory? = null,
        verifySSLHostname: Boolean = false,
        chunkedTransferEncoding: Boolean? = null,
        basicAuth: Http.BasicAuth? = null,
        acceptEncoding: String? = null,
        var userName: String? = basicAuth?.user ?: System.getProperty("user.name"),
        var ippVersion: String = "1.1",
        var charset: Charset = Charsets.UTF_8,
        var naturalLanguage: String = "en",
        var getPrinterAttributesOnInit: Boolean = true

) : Http.Config(
        timeout,
        userAgent,
        basicAuth,
        sslSocketFactory,
        verifySSLHostname,
        chunkedTransferEncoding,
        acceptEncoding
) {
    companion object {
        val log = Logging.getLogger {}
    }

    fun logDetails() {
        log.info { "timeout: $timeout" }
        log.info { "userAgent: $userAgent" }
        log.info { "verifySSLHostname: $verifySSLHostname" }
        log.info { "chunkedTransferEncoding: ${chunkedTransferEncoding ?: "auto"}" }
        log.info { "acceptEncoding: $acceptEncoding" }
        log.info { "userName: $userName" }
        log.info { "ippVersion: $ippVersion" }
        log.info { "charset: ${charset.name().toLowerCase()}" }
        log.info { "naturalLanguage: $naturalLanguage" }
        log.info { "getPrinterAttributesOnInit: $getPrinterAttributesOnInit" }
    }
}