package de.gmuth.ipp.iana

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.csv.CSVReader
import de.gmuth.ipp.core.IppTag

class IppRegistrations {

    data class Attribute(
            val collection: String,
            val name: String,
            val memberAttribute: String? = null,
            val subMemberAttribute: String? = null,
            val syntax: String,
            val reference: String
    ) {
        companion object RowMapper : CSVReader.RowMapper<Attribute> {
            override fun mapRow(columns: List<String>, rowNum: Int): Attribute {
                return Attribute(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5])
            }
        }

        override fun toString() = "$name: syntax = $syntax"

        fun tag() = when {
            syntax.contains("charset") -> IppTag.Charset
            syntax.contains("naturalLanguage") -> IppTag.NaturalLanguage
            syntax.contains("mimeMediaType") -> IppTag.MimeMediaType
            syntax.contains("uri") -> IppTag.Uri
            syntax.contains("uriScheme") -> IppTag.UriScheme
            syntax.contains("keyword") -> IppTag.Keyword
            syntax.contains("name") -> IppTag.NameWithoutLanguage
            syntax.contains("text") -> IppTag.TextWithoutLanguage
            syntax.contains("integer") -> IppTag.Integer
            syntax.contains("enum") -> IppTag.Enum
            syntax.contains("boolean") -> IppTag.Boolean
            syntax.contains("rangeOfInteger") -> IppTag.RangeOfInteger
            syntax.contains("dateTime") -> IppTag.DateTime
            syntax.contains("resolution") -> IppTag.Resolution
            syntax.isEmpty() -> null
            else -> throw NotImplementedError("unknown syntax '$syntax'")
        }

        fun is1SetOf() = syntax.contains("1setOf")

        // key for map, because name is not unique
        fun key(): String {
            val key = StringBuffer(name)
            if (memberAttribute?.isNotBlank()!!) key.append("/$memberAttribute")
            if (subMemberAttribute?.isNotBlank()!!) key.append("/$subMemberAttribute")
            return key.toString()
        }
    }

    companion object {

        // https://www.iana.org/assignments/ipp-registrations/ipp-registrations.xml#ipp-registrations-2
        private fun ippRegistrationsCsvInputStream() = IppRegistrations::class.java.getResourceAsStream("/ipp-registrations-2.csv")

        private val allAttributes = CSVReader<Attribute>(Attribute.RowMapper).read(ippRegistrationsCsvInputStream(), true)

        private val attributesMap = allAttributes.associateBy(Attribute::key)

        fun attributeNameIsRegistered(name: String) = attributesMap[name] != null

        fun attributeByName(name: String) = attributesMap[name]
                ?: throw IllegalArgumentException(String.format("attribute name '%s' not found", name))

        fun tagForAttribute(name: String) = attributeByName(name).tag()
                ?: throw IllegalArgumentException("tag for attribute '$name' not found")

        fun attributeIs1setOf(name: String) = attributeByName(name).is1SetOf()

        fun checkSyntaxOfAttribute(name: String, tag: IppTag) {
            try {
                val syntax = attributeByName(name).syntax
                if (syntax.isNotEmpty() && !syntax.contains(tag.registeredSyntax())) println("WARN: '$name' uses '$tag' instead of '$syntax'")
            } catch (exception: Exception) {
            }
        }

//        fun prettyPrintCSV(outputStream: OutputStream) {
//            val csvReader = ListOfStringCSVReader()
//            csvReader.read(ippRegistrationsCsvInputStream(), false)
//            csvReader.prettyPrint(outputStream)
//        }

        // issues with: cover-back, cover-front, insert-sheet, job-accounting-sheets
        fun printTagMappingsForRFC8011Attributes() {
            allAttributes.stream()
                    .filter { it.reference.contains("RFC8011") }
                    .forEach { attribute ->
                        try {
                            val tag = tagForAttribute(attribute.name)
                            println("${attribute.name} -> ${tag}")
                        } catch (exception: Exception) {
                            println(exception.message)
                        }
                    }
        }

    }
}

fun main() {
    //IppRegistrations.prettyPrintCSV(System.out)
    //IppRegistrations.printTagMappingsForRFC8011Attributes()
}