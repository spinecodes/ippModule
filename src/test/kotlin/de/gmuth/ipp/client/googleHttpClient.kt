package de.gmuth.ipp.client

import com.google.api.client.http.apache.v2.ApacheHttpTransport
import de.gmuth.http.GoogleHttpClient
import de.gmuth.ipp.core.IppOperation
import de.gmuth.log.Logging
import java.net.URI

fun main() {
    //Logging.defaultLogLevel = Logging.LogLevel.DEBUG
    //IppClient.log.logLevel = Logging.LogLevel.TRACE
    GoogleHttpClient.log.logLevel = Logging.LogLevel.DEBUG
    val log = Logging.getLogger {}

    var uri = URI.create("ipp://localhost:8632/printers/laser")

    try {
        val ippConfig = IppConfig()
        val googleHttpClient = GoogleHttpClient(ippConfig).apply {
            // http://googleapis.github.io/google-http-java-client/http-transport.html
            //httpTransport = NetHttpTransport() // java url connection
            httpTransport = ApacheHttpTransport() // apache http client
            //httpTransport = UrlFetchTransport() // google app engine -> ApiProxy$CallNotFoundException
        }
        IppClient(config = ippConfig, httpClient = googleHttpClient).run {
            val request = ippRequest(IppOperation.GetPrinterAttributes, uri)
            val response = exchange(request)
            response.logDetails()
        }
    } catch (exception: Exception) {
        log.error(exception) { "failed" }
    }
}