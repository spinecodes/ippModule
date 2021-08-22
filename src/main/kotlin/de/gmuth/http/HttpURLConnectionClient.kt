package de.gmuth.http

/**
 * Copyright (c) 2020-2021 Gerhard Muth
 */

import de.gmuth.log.Logging
import de.gmuth.log.Logging.LogLevel.ERROR
import de.gmuth.log.Logging.LogLevel.TRACE
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64.getEncoder
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

class HttpURLConnectionClient(config: Http.Config = Http.Config()) : Http.Client(config) {

    companion object {
        val log = Logging.getLogger {}
    }

    override fun post(uri: URI, contentType: String, writeContent: (OutputStream) -> Unit): Http.Response {
        with(uri.toURL().openConnection() as HttpURLConnection) {
            if (this is HttpsURLConnection && config.sslSocketFactory != null) {
                sslSocketFactory = config.sslSocketFactory
                if (!config.verifySSLHostname) hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            connectTimeout = config.timeout
            readTimeout = config.timeout
            doOutput = true // trigger POST method
            config.basicAuth?.let {
                val basicAuthEncoded = getEncoder().encodeToString("${it.user}:${it.password}".toByteArray())
                setRequestProperty("Authorization", "Basic $basicAuthEncoded")
            }
            setRequestProperty("Content-Type", contentType)
            config.userAgent?.let { setRequestProperty("User-Agent", it) }
            if (config.chunkedTransferEncoding) setChunkedStreamingMode(0)
            writeContent(outputStream)
            for ((key, values) in headerFields) {
                log.log(if (responseCode < 300) TRACE else ERROR) { "$key = $values" }
            }
            val contentResponseStream = try {
                inputStream
            } catch (exception: Exception) {
                log.error { "http exception: $responseCode $responseMessage" }
                errorStream
            }
            return Http.Response(
                    responseCode,
                    getHeaderField("Server"),
                    getHeaderField("Content-Type"),
                    contentResponseStream
            )
        }
    }
}