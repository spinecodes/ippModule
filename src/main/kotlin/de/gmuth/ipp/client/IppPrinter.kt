package de.gmuth.ipp.client

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.ipp.core.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI

class IppPrinter(val printerUri: URI) {

    companion object {
        var checkValueSupported: Boolean = true
    }

    val ippClient = IppClient()

    val attributes: IppAttributesGroup = getPrinterAttributes()

    var httpAuth: Http.Auth?
        get() = ippClient.httpAuth
        set(value) {
            ippClient.httpAuth = value
        }

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
        get() = IppPrinterState.fromInt(attributes.getValue("printer-state") as Int)

    val stateReasons: List<String>
        get() = attributes.getValues("printer-state-reasons")

    //-----------------
    // Identify-Printer
    //-----------------

    fun identify(action: String) {
        checkValueSupported("identify-actions-supported", action)
        val request = ippRequest(IppOperation.IdentifyPrinter).apply {
            operationGroup.attribute("identify-actions", IppTag.Keyword, action)
        }
        exchangeSuccessful(request)
    }

    //--------------
    // Pause-Printer
    //--------------

    fun pause() {
        exchangeSuccessfulIppRequest(IppOperation.PausePrinter)
    }

    //---------------
    // Resume-Printer
    //---------------

    fun resume() {
        exchangeSuccessfulIppRequest(IppOperation.ResumePrinter)
    }

    //-----------------------
    // Get-Printer-Attributes
    //-----------------------

    fun getPrinterAttributes(requestedAttributes: List<String> = listOf()): IppAttributesGroup {
        val request = ippRequest(IppOperation.GetPrinterAttributes).apply {
            if (requestedAttributes.isNotEmpty()) {
                operationGroup.attribute("requested-attributes", IppTag.Keyword, requestedAttributes)
            }
        }
        return exchangeSuccessful(request).printerGroup
    }

    //----------
    // Print-Job
    //----------

    fun printJob(
            file: File,
            vararg attributeHolders: IppAttributeHolder,
            waitForTermination: Boolean = false

    ): IppJob {
        val request = attributeHoldersRequest(IppOperation.PrintJob, attributeHolders)
        val response = exchangeSuccessful(request, FileInputStream(file))
        return handlePrintResponse(response, waitForTermination)
    }

    //----------
    // Print-Uri
    //----------

    fun printUri(
            documentUri: URI,
            vararg attributeHolders: IppAttributeHolder,
            waitForTermination: Boolean = false

    ): IppJob {
        val request = attributeHoldersRequest(IppOperation.PrintUri, attributeHolders).apply {
            operationGroup.attribute("document-uri", IppTag.Uri, documentUri)
        }
        val response = exchangeSuccessful(request)
        return handlePrintResponse(response, waitForTermination)
    }

    //-------------
    // Validate-Job
    //-------------

    fun validateJob(vararg attributeHolders: IppAttributeHolder): IppResponse {
        val request = attributeHoldersRequest(IppOperation.ValidateJob, attributeHolders)
        return exchangeSuccessful(request)
    }

    //-----------
    // Create-Job
    //-----------

    fun createJob(vararg attributeHolders: IppAttributeHolder): IppJob {
        val request = attributeHoldersRequest(IppOperation.CreateJob, attributeHolders)
        val response = exchangeSuccessful(request)
        return IppJob(this, response.jobGroup)
    }

    // ---- factory method for IppRequest with Operation Print-Job, Print-Uri, Validate-Job, Create-Job

    private fun attributeHoldersRequest(operation: IppOperation, attributeHolders: Array<out IppAttributeHolder>) =
            ippRequest(operation).apply {
                with(ippAttributesGroup(IppTag.Job)) {
                    for (attributeHolder in attributeHolders) {
                        val attribute = attributeHolder.getIppAttribute(attributes)
                        for (value in attribute.values) {
                            checkValueSupported("${attribute.name}-supported", value!!)
                        }
                        put(attribute)
                    }
                }
            }

    private fun handlePrintResponse(printResponse: IppResponse, wait: Boolean = false): IppJob {
        val job = IppJob(this, printResponse.jobGroup)
        if (wait) {
            job.waitForTermination()
        }
        job.logDetails()
        return job
    }

    //---------------------------
    // Get-Jobs (as List<IppJob>)
    //---------------------------

    fun getJobs(whichJobs: String? = null): List<IppJob> {
        val request = ippRequest(IppOperation.GetJobs)
        if (whichJobs != null) {
            // PWG Job and Printer Extensions Set 2
            checkValueSupported("which-jobs-supported", whichJobs)
            request.operationGroup.attribute("which-jobs", IppTag.Keyword, whichJobs)
        }
        val response = exchangeSuccessful(request)
        val jobGroups = response.getAttributesGroups(IppTag.Job)
        return jobGroups.map {
            IppJob(this, it)
        }
    }

    //-------------------------------
    // Get-Job-Attributes (as IppJob)
    //-------------------------------

    fun getJob(jobId: Int): IppJob {
        val response = exchangeSuccessfulIppJobRequest(IppOperation.GetJobAttributes, jobId)
        return IppJob(this, response.jobGroup)
    }

    //----------------------
    // delegate to IppClient
    //----------------------

    fun ippRequest(operation: IppOperation) =
            ippClient.ippRequest(operation, printerUri)

    fun ippJobRequest(operation: IppOperation, jobId: Int) =
            ippClient.ippJobRequest(operation, printerUri, jobId)

    fun exchangeSuccessfulIppRequest(operation: IppOperation) =
            exchangeSuccessful(ippRequest(operation))

    fun exchangeSuccessfulIppJobRequest(operation: IppOperation, jobId: Int) =
            exchangeSuccessful(ippJobRequest(operation, jobId))

    fun exchangeSuccessful(request: IppRequest, documentInputStream: InputStream? = null): IppResponse {
        checkValueSupported("ipp-versions-supported", ippClient.ippVersion)
        checkValueSupported("operations-supported", request.code!!.toInt())
        checkValueSupported("charset-supported", request.attributesCharset)
        return ippClient.exchangeSuccessful(printerUri, request, documentInputStream)
    }

    // -------
    // Logging
    // -------

    override fun toString() =
            "IppPrinter: name = $name, makeAndModel = $makeAndModel, state = $state, stateReasons = ${stateReasons.joinToString(",")}"

    fun logDetails() =
            attributes.logDetails(title = "PRINTER-$name ($makeAndModel), $state (${stateReasons.joinToString(",")})")

    // -------------------------------------------------------
    // ipp spec checking method, based on printer capabilities
    // -------------------------------------------------------

    private fun checkValueSupported(supportedAttributeName: String, value: Any) {
        // condition is NOT always false, because this method is used during class initialization
        if (attributes == null) {
            return
        }
        if (!supportedAttributeName.endsWith("-supported")) {
            throw IppException("expected attribute name ending with '-supported' but found: '$supportedAttributeName'")
        }
        val supportedAttribute = attributes.get(supportedAttributeName)
        if (supportedAttribute == null || !checkValueSupported) {
            return
        }
        with(supportedAttribute) {
            val valueIsSupported = when (tag) {
                IppTag.Boolean -> {
                    //e.g. 'page-ranges-supported'
                    supportedAttribute.value as Boolean
                }
                IppTag.Charset,
                IppTag.NaturalLanguage,
                IppTag.MimeMediaType,
                IppTag.Enum,
                IppTag.Resolution -> {
                    values.contains(value)
                }
                IppTag.Keyword -> {
                    if (value is IppVersion) {
                        values.contains(value.toString())
                    } else {
                        values.contains(value)
                    }
                }
                IppTag.Integer -> {
                    if (is1setOf()) {
                        values.contains(value)
                    } else {
                        // e.g. 'job-priority-supported'
                        value is Int && value <= supportedAttribute.value as Int
                    }
                }
                IppTag.RangeOfInteger -> {
                    value is Int && value in supportedAttribute.value as IntRange
                }
                else -> {
                    println("WARN: unable to check if value '$value' is supported by $this")
                    true
                }
            }
            if (valueIsSupported) {
                //println("'${enumValueNameOrValue(value)}' supported by printer. $this")
            } else {
                println("ERROR: unsupported: $value")
                println("ERROR: supported: ${values.joinToString(",")}")
                throw IppException("value '${enumValueNameOrValue(value)}' not supported by printer. $this")
            }
        }
    }
}