package de.gmuth.ipp.core

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.log.Logging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IppAttributesGroupTests {

    private val group = IppAttributesGroup(IppTag.Operation)

    @Test
    fun constructorFails1() {
        assertFailsWith<IppException> { IppAttributesGroup(IppTag.End) }
    }

    @Test
    fun constructorFails2() {
        assertFailsWith<IppException> { IppAttributesGroup(IppTag.Integer) }
    }

    @Test
    fun putWithReplacementAllowed() {
        with(IppAttributesGroup(IppTag.Printer)) {
            attribute("number", IppTag.Integer, 0)
            attribute("number", IppTag.Integer, 1, 2)
            assertEquals(1, size)
            assertEquals(get("number")!!.values.size, 2)
        }
    }

    //@Test
    fun putWithReplacementDenied() {
        IppAttributesGroup.log.logLevel = Logging.LogLevel.INFO
        group.attribute("number", IppTag.Integer, 0)
        group.attribute("number", IppTag.Integer, 1, 2)
        assertEquals(1, group.size)
        assertEquals(group.get("number")!!.values.size, 1)
    }

    @Test
    fun putEmptyValues() {
        group.attribute("empty", IppTag.Integer, listOf())
        assertEquals(1, group.size)
    }

    @Test
    fun toStringValue() {
        assertEquals("'operation-attributes-tag' 0 attributes", group.toString())
    }

    @Test
    fun getValue() {
        group.attribute("foo", IppTag.Keyword, "bar")
        assertEquals("bar", group.getValue("foo") as String)
        assertEquals("bar", group.getValueOrNull("foo") ?: throw NullPointerException())
        assertEquals(null, group.getValueOrNull("invalid-name"))
    }

    @Test
    fun getValues() {
        group.attribute("multiple", IppTag.Integer, 1, 2)
        assertEquals(listOf(1, 2), group.getValues("multiple"))
    }

    @Test
    fun getValueFails() {
        assertFailsWith<IppException> { group.getValue("invalid-name") }
    }

    @Test
    fun getValuesFails() {
        assertFailsWith<IppException> { group.getValues("invalid-name") }
    }

    @Test
    fun logDetails() {
        group.logDetails("|", "title")
    }

    @Test
    fun validate() {
        group.validate()
    }

    // ------------- interface Map methods ------------

    @Test
    fun mapInterfaceMethods() {
        group.remove("a")

        group.attribute("b", IppTag.Unknown)
        assertTrue(group.containsKey("b"))

        val c = IppAttribute<Unit>("c", IppTag.Unknown)
        group.put(c)
        assertTrue(group.containsValue(c))

        group.getOrDefault("d", IppAttribute<Unit>("default", IppTag.Unknown))

        group.remove("b")
        group.remove("c", c)

        assertTrue(group.entries.size == 0)
    }

}