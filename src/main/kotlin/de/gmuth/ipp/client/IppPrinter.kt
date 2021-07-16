package de.gmuth.ipp.client

/**
 * Copyright (c) 2020-2021 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.ipp.core.*
import de.gmuth.ipp.core.IppOperation.*
import de.gmuth.ipp.core.IppTag.*
import de.gmuth.ipp.cups.CupsMarker
import de.gmuth.ipp.cups.CupsPrinterType
import de.gmuth.ipp.iana.IppRegistrationsSection2
import de.gmuth.log.Logging
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset

open class IppPrinter(
        val printerUri: URI,
        var attributes: IppAttributesGroup = IppAttributesGroup(Printer), // empty group
        trustAnyCertificate: Boolean = true,
        httpBasicAuth: Http.BasicAuth? = null,
        val ippClient: IppClient = IppClient(httpBasicAuth = httpBasicAuth)
) {

    init {
        if (trustAnyCertificate) ippClient.trustAnyCertificate()
        if (attributes.size == 0) updateAllAttributes()
    }

    constructor(printerAttributes: IppAttributesGroup, ippClient: IppClient = IppClient()) : this(
            printerAttributes.getValues<List<URI>>("printer-uri-supported").first(),
            printerAttributes,
            ippClient = ippClient
    )

    constructor(printerUri: String) : this(URI.create(printerUri))

    fun basicAuth(user: String, password: String) = ippClient.basicAuth(user, password)

    companion object {
        val log = Logging.getLogger {}
    }

    var getJobsRequestedAttributes = listOf(
            "job-id", "job-uri", "job-printer-uri", "job-state", "job-state-message", "job-state-reasons", "job-name", "job-originating-user-name"
    )

    //--------------
    // IppAttributes
    //--------------

    val name: IppString
        get() = attributes.getValue("printer-name")

    val makeAndModel: IppString
        get() = attributes.getValue("printer-make-and-model")

    val isAcceptingJobs: Boolean
        get() = attributes.getValue("printer-is-accepting-jobs")

    val state: IppPrinterState
        get() = IppPrinterState.fromInt(attributes.getValue("printer-state"))

    val stateReasons: List<String>
        get() = attributes.getValues("printer-state-reasons")

    val uriSupported: List<URI>
        get() = attributes.getValues("printer-uri-supported")

    val documentFormatSupported: List<String>
        get() = attributes.getValues("document-format-supported")

    val operationsSupported: List<IppOperation>
        get() = attributes.getValues<List<Int>>("operations-supported").map { IppOperation.fromShort(it.toShort()) }

    // ---------------
    // CUPS Extensions
    // ---------------

    val deviceUri: URI
        get() = attributes.getValue("device-uri")

    val printerType: CupsPrinterType
        get() = CupsPrinterType(attributes.getValue("printer-type"))

    fun hasCapability(capability: CupsPrinterType.Capability) =
            printerType.contains(capability)

    val markers: CupsMarker.List
        get() = CupsMarker.List(attributes)

    fun marker(color: CupsMarker.Color) =
            markers.single { it.color == color }

    //-----------------
    // Identify-Printer
    //-----------------

    fun identify(vararg actions: String) = identify(actions.toList())

    fun identify(actions: List<String>): IppResponse {
        checkIfValueIsSupported("identify-actions-supported", actions)
        val request = ippRequest(IdentifyPrinter).apply {
            operationGroup.attribute("identify-actions", Keyword, actions)
        }
        return exchangeSuccessful(request)
    }

    //-----------------------
    // Printer administration
    //-----------------------

    fun pause() = exchangeSuccessfulIppRequest(PausePrinter)
    fun resume() = exchangeSuccessfulIppRequest(ResumePrinter)

    //-----------------------
    // Get-Printer-Attributes
    //-----------------------

    @JvmOverloads
    fun getPrinterAttributes(requestedAttributes: List<String>? = null) =
            exchangeSuccessfulIppRequest(GetPrinterAttributes, requestedAttributes = requestedAttributes)

    fun updateAllAttributes() {
        attributes = getPrinterAttributes().printerGroup
    }

    //-------------
    // Validate-Job
    //-------------

    @Throws(IppExchangeException::class)
    fun validateJob(vararg attributeBuilders: IppAttributeBuilder): IppResponse {
        val request = attributeBuildersRequest(ValidateJob, attributeBuilders)
        return exchangeSuccessful(request)
    }

    //----------
    // Print-Job
    //----------

    fun printJob(inputStream: InputStream, vararg attributeBuilders: IppAttributeBuilder) =
            printInputStream(inputStream, attributeBuilders)

    fun printJob(file: File, vararg attributeBuilders: IppAttributeBuilder) =
            printInputStream(FileInputStream(file), attributeBuilders)

    private fun printInputStream(inputStream: InputStream, attributeBuilders: Array<out IppAttributeBuilder>): IppJob {
        val request = attributeBuildersRequest(PrintJob, attributeBuilders).apply {
            documentInputStream = inputStream
        }
        return exchangeSuccessfulForIppJob(request)
    }

    //----------
    // Print-URI
    //----------

    fun printUri(documentUri: URI, vararg attributeBuilders: IppAttributeBuilder): IppJob {
        val request = attributeBuildersRequest(PrintURI, attributeBuilders).apply {
            operationGroup.attribute("document-uri", Uri, documentUri)
        }
        return exchangeSuccessfulForIppJob(request)
    }

    //-----------
    // Create-Job
    //-----------

    fun createJob(vararg attributeBuilders: IppAttributeBuilder): IppJob {
        val request = attributeBuildersRequest(CreateJob, attributeBuilders)
        return exchangeSuccessfulForIppJob(request)
    }

    // ---- factory method for operations Validate-Job, Print-Job, Print-Uri, Create-Job

    private fun attributeBuildersRequest(operation: IppOperation, attributeBuilders: Array<out IppAttributeBuilder>) =
            ippRequest(operation).apply {
                for (attributeBuilder in attributeBuilders) {
                    val attribute = attributeBuilder.buildIppAttribute(attributes)
                    checkIfValueIsSupported("${attribute.name}-supported", attribute.values)
                    // put attribute in operation or job group?
                    val groupTag = IppRegistrationsSection2.selectGroupForAttribute(attribute.name)
                    if (getAttributesGroups(groupTag).isEmpty()) createAttributesGroup(groupTag)
                    log.trace { "$groupTag put $attribute" }
                    getSingleAttributesGroup(groupTag).put(attribute)
                }
            }

    //-------------------------------
    // Get-Job-Attributes (as IppJob)
    //-------------------------------

    fun getJob(jobId: Int): IppJob {
        val request = ippRequest(GetJobAttributes, jobId)
        return exchangeSuccessfulForIppJob(request)
    }

    //---------------------------
    // Get-Jobs (as List<IppJob>)
    //---------------------------

    @JvmOverloads
    fun getJobs(
            whichJobs: String? = null,
            requestedAttributes: List<String> = getJobsRequestedAttributes

    ): List<IppJob> {
        val request = ippRequest(GetJobs, requestedAttributes = requestedAttributes)
        if (whichJobs != null) {
            // PWG Job and Printer Extensions Set 2
            checkIfValueIsSupported("which-jobs-supported", whichJobs)
            request.operationGroup.attribute("which-jobs", Keyword, whichJobs)
        }
        return exchangeSuccessful(request) // IppResponse
                .getAttributesGroups(Job)
                .map { IppJob(this, it) }
    }

    //----------------------
    // delegate to IppClient
    //----------------------

    fun ippRequest(operation: IppOperation, jobId: Int? = null, requestedAttributes: List<String>? = null) =
            ippClient.ippRequest(operation, printerUri, jobId, requestedAttributes)

    fun exchangeSuccessful(request: IppRequest): IppResponse {
        checkIfValueIsSupported("ipp-versions-supported", request.version!!)
        checkIfValueIsSupported("operations-supported", request.code!!.toInt())
        checkIfValueIsSupported("charset-supported", request.operationGroup.getValue<Charset>("attributes-charset"))
        return ippClient.exchangeSuccessful(request)
    }

    fun exchangeSuccessfulIppRequest(operation: IppOperation, jobId: Int? = null, requestedAttributes: List<String>? = null) =
            exchangeSuccessful(ippRequest(operation, jobId, requestedAttributes))

    fun exchangeSuccessfulForIppJob(request: IppRequest) =
            IppJob(this, exchangeSuccessful(request).jobGroup)

    // -------
    // Logging
    // -------

    override fun toString() =
            "IppPrinter: name=$name, makeAndModel=$makeAndModel, state=$state, stateReasons=$stateReasons"

    fun logDetails() =
            attributes.logDetails(title = "PRINTER-$name ($makeAndModel), $state $stateReasons")

    // ------------------------------------------------------
    // attribute value checking based on printer capabilities
    // ------------------------------------------------------

    protected fun checkIfValueIsSupported(supportedAttributeName: String, value: Any) {
        if (attributes.size == 0) return

        if (!supportedAttributeName.endsWith("-supported"))
            throw IppException("attribute name not ending with '-supported'")

        if (value is Collection<*>) { // instead of providing another signature just check collections iteratively
            for (collectionValue in value) {
                checkIfValueIsSupported(supportedAttributeName, collectionValue!!)
            }
        } else {
            isAttributeValueSupported(supportedAttributeName, value)
        }
    }

    protected fun isAttributeValueSupported(supportedAttributeName: String, value: Any): Boolean? {
        val supportedAttribute = attributes[supportedAttributeName] ?: return null
        val attributeValueIsSupported = when (supportedAttribute.tag) {
            IppTag.Boolean -> { // e.g. 'page-ranges-supported'
                supportedAttribute.value as Boolean
            }
            IppTag.Enum, Charset, NaturalLanguage, MimeMediaType, Keyword, Resolution -> when (supportedAttributeName) {
                "media-col-supported" -> with(value as IppCollection) {
                    members.filter { !supportedAttribute.values.contains(it.name) }
                            .forEach { log.warn { "member unsupported: $it" } }
                    // all member names must be supported
                    supportedAttribute.values.containsAll(members.map { it.name })
                }
                else -> supportedAttribute.values.contains(value)
            }
            Integer -> {
                if (supportedAttribute.is1setOf()) supportedAttribute.values.contains(value)
                else value is Int && value <= supportedAttribute.value as Int // e.g. 'job-priority-supported'
            }
            RangeOfInteger -> {
                value is Int && value in supportedAttribute.value as IntRange
            }
            else -> null
        }
        when (attributeValueIsSupported) {
            null -> log.warn { "unable to check if value '$value' is supported by $supportedAttribute" }
            true -> log.debug { "$supportedAttributeName: $value" }
            false -> {
                log.warn { "according to printer attributes value '${supportedAttribute.enumNameOrValue(value)}' is not supported." }
                log.warn { "$supportedAttribute" }
            }
        }
        return attributeValueIsSupported
    }

    // -----------------------
    // Save printer attributes
    // -----------------------

    fun savePrinterAttributes() {
        val printerModel: String = makeAndModel.text.replace("\\s+".toRegex(), "_")
        with(getPrinterAttributes()) {
            File("$printerModel.bin").apply {
                saveRawBytes(this)
                log.info { "bin file: $absolutePath" }
            }
            File("$printerModel.txt").apply {
                writeText("# $printerUri\n")
                printerGroup.values.forEach { appendText("$it\n") }
                log.info { "txt file: $absolutePath" }
            }
        }
    }

}