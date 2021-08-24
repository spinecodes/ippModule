package de.gmuth.http

/**
 * Copyright (c) 2021 Gerhard Muth
 */

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import de.gmuth.log.Logging
import de.gmuth.log.Logging.LogLevel.DEBUG
import de.gmuth.log.Logging.LogLevel.ERROR
import java.io.OutputStream
import java.net.URI

class GoogleHttpClient(config: Http.Config = Http.Config()) : Http.Client(config) {

    // http://googleapis.github.io/google-http-java-client/http-transport.html
    var httpTransport: HttpTransport = NetHttpTransport()

    companion object {
        val log = Logging.getLogger {}
    }

    override fun post(uri: URI, contentType: String, writeContent: (OutputStream) -> Unit, chunked: Boolean): Http.Response {
        log.info { "http transport: ${httpTransport.javaClass.simpleName}" }

        val httpContent = object : HttpContent {
            override fun writeTo(outputStream: OutputStream?) = writeContent(outputStream!!)
            override fun getLength() = -1L // length is unknown
            override fun getType() = contentType
            override fun retrySupported() = false
        }

        val requestFactory = httpTransport.createRequestFactory()
        val request = requestFactory.buildPostRequest(GenericUrl(uri), httpContent).apply {
            connectTimeout = config.timeout
            readTimeout = config.timeout
            headers = HttpHeaders().apply {
                userAgent = config.userAgent
                acceptEncoding = config.acceptEncoding
                config.basicAuth?.let { setBasicAuthentication(it.user, it.password) }
            }
        }
        request.execute().run {
            for (key in headers.keys) {
                log.log(if (isSuccessStatusCode) DEBUG else ERROR) { "$key: ${headers[key]}" }
            }
            val server = headers["Server"].toString()
            return Http.Response(statusCode, server, getContentType(), content)
        }
    }

}