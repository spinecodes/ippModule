package de.gmuth.ipp.core

/**
 * Copyright (c) 2020-2021 Gerhard Muth
 */

import de.gmuth.io.ByteArraySavingInputStream
import de.gmuth.io.ByteArraySavingOutputStream
import de.gmuth.log.Logging
import java.io.*

abstract class IppMessage {

    var code: Short? = null
    var requestId: Int? = null
    var version: String? = null
        set(value) { // validate version
            if (Regex("""^\d\.\d$""").matches(value!!)) field = value else throw IppException("invalid version string: $value")
        }
    val attributesGroups = mutableListOf<IppAttributesGroup>()
    var documentInputStream: InputStream? = null
    var documentInputStreamIsConsumed: Boolean = false
    var rawBytes: ByteArray? = null

    abstract val codeDescription: String // request operation or response status

    companion object {
        val log = Logging.getLogger {}
    }

    val operationGroup: IppAttributesGroup
        get() = getSingleAttributesGroup(IppTag.Operation)

    val jobGroup: IppAttributesGroup
        get() = getSingleAttributesGroup(IppTag.Job)

    fun getAttributesGroups(tag: IppTag) =
            attributesGroups.filter { it.groupTag == tag }

    fun getSingleAttributesGroup(tag: IppTag) = with(getAttributesGroups(tag)) {
        if (isEmpty()) throw IppException("no group found with tag '$tag' in $attributesGroups")
        single()
    }

    fun containsGroup(tag: IppTag) =
            attributesGroups.map { it.groupTag }.contains(tag)

    // factory method for IppAttributesGroup
    fun createAttributesGroup(tag: IppTag) =
            IppAttributesGroup(tag).apply { attributesGroups.add(this) }

    fun hasDocument() = documentInputStream != null

    // --------
    // ENCODING
    // --------

    fun write(outputStream: OutputStream) {
        val byteArraySavingOutputStream = ByteArraySavingOutputStream(outputStream)
        try {
            IppOutputStream(byteArraySavingOutputStream).writeMessage(this)
        } finally {
            rawBytes = byteArraySavingOutputStream.toByteArray()
            log.debug { "wrote ${rawBytes!!.size} raw bytes" }
            byteArraySavingOutputStream.saveBytes = false // stop saving document bytes
        }
        copyDocumentStream(byteArraySavingOutputStream)
    }

    fun write(file: File) = write(FileOutputStream(file))

    fun encode(): ByteArray = with(ByteArrayOutputStream()) {
        write(this)
        log.debug { "ByteArrayOutputStream size = ${this.size()}" }
        toByteArray()
    }

    // --------
    // DECODING
    // --------

    fun read(inputStream: InputStream) {
        val byteArraySavingInputStream = ByteArraySavingInputStream(inputStream.buffered())
        try {
            IppInputStream(byteArraySavingInputStream).readMessage(this)
            documentInputStream = byteArraySavingInputStream
        } finally {
            rawBytes = byteArraySavingInputStream.toByteArray()
            log.debug { "read ${rawBytes!!.size} raw bytes" }
            byteArraySavingInputStream.saveBytes = false // stop saving document bytes
        }
    }

    fun read(file: File) {
        log.debug { "read file ${file.absolutePath} (${file.length()} bytes)" }
        read(FileInputStream(file))
    }

    fun decode(byteArray: ByteArray) {
        log.debug { "decode ${byteArray.size} bytes" }
        read(ByteArrayInputStream(byteArray))
    }

    // ------------------------
    // DOCUMENT and IPP-MESSAGE
    // ------------------------

    private fun copyDocumentStream(outputStream: OutputStream) {
        if (documentInputStreamIsConsumed) log.warn { "documentInputStream is consumed" }
        documentInputStream?.copyTo(outputStream)
        log.debug { "consumed documentInputStream" }
        documentInputStreamIsConsumed = true
    }

    fun saveDocumentStream(file: File) {
        copyDocumentStream(file.outputStream())
        log.info { "saved ${file.length()} document bytes to file ${file.path}" }
    }

    fun saveRawBytes(file: File) = file.apply {
        writeBytes(rawBytes ?: throw RuntimeException("missing raw bytes. you must call read/decode or write/encode before."))
        log.info { "saved: $path" }
    }

    // -------
    // LOGGING
    // -------

    override fun toString() = "%s %s%s".format(
            codeDescription,
            attributesGroups.map { "${it.values.size} ${it.groupTag}" },
            if (rawBytes == null) "" else " (${rawBytes!!.size} bytes)"
    )

    fun logDetails(prefix: String = "") {
        if (rawBytes != null) log.info { "${prefix}${rawBytes!!.size} raw ipp bytes" }
        log.info { "${prefix}version = $version" }
        log.info { "${prefix}$codeDescription" }
        log.info { "${prefix}request-id = $requestId" }
        for (group in attributesGroups) {
            group.logDetails(prefix)
        }
    }

}