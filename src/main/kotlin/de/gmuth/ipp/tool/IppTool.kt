package de.gmuth.ipp.tool

import de.gmuth.ipp.client.IppClient
import de.gmuth.ipp.core.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.Reader
import java.net.URI

class IppTool() {
    var uri: URI? = null
    var filename: String? = null
    var verbose: Boolean = false

    fun runResource(resource: String) = run(javaClass.getResourceAsStream(resource))
    fun runFile(path: String) = runFile(File(path))
    fun runFile(file: File) = run(FileInputStream(file))
    fun run(inputStream: InputStream) = run(inputStream.reader())
    fun run(reader: Reader) = run(reader.readLines())
    fun run(vararg lines: String) = if (lines.size == 1) run(lines[0].reader()) else run(lines.toList())

    fun run(lines: List<String>) {
        var fileInputStream: FileInputStream? = null
        lateinit var currentGroup: IppAttributesGroup
        val request = IppRequest()

        // parse commands and build ipp request
        for (line in lines) {
            val lineItems = line.trim().split("\\s+".toRegex())
            val command = lineItems.first()
            if (lineItems.size < 2 || command.startsWith("#")) continue
            if (verbose) println("| ${line.trim()}")
            val firstArgument = lineItems[1]
            when (command) {
                "OPERATION" -> {
                    val operation = IppOperation.fromRegisteredName(firstArgument)
                    request.code = operation.code
                }
                "GROUP" -> when (firstArgument) {
                    "operation-attributes-tag" -> currentGroup = request.operationGroup
                    else -> throw IppException("unsupported group '$firstArgument'")
                }
                "ATTR" -> {
                    val tag = IppTag.fromRegisteredName(
                            if (firstArgument == "language") "naturalLanguage" else firstArgument
                    )
                    val name = lineItems[2]
                    val value: Any = with(lineItems[3]) {
                        when {
                            this == "\$uri" -> uri ?: throw IppException("\$uri undefined")
                            name.contains("-uri") -> URI.create(this)
                            else -> this
                        }
                    }
                    currentGroup.put(IppAttribute(name, tag, value))
                }
                "FILE" -> {
                    if (firstArgument == "\$file" || firstArgument == "\$filename") {
                        if (filename == null) throw IppException("$firstArgument undefined")
                    } else {
                        filename = firstArgument
                    }
                    fileInputStream = FileInputStream(File(filename))
                }
                else -> println("ignore unknown command '$command'")
            }
        }

        with(IppClient()) {
            verbose = true
            if (uri == null) throw IppException("uri missing")
            exchangeSuccessful(uri as URI, request, "FAILED", fileInputStream)
        }
    }
}