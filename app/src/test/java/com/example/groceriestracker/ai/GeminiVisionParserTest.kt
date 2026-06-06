package com.example.groceriestracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiVisionParserTest {

    private val parser = GeminiVisionManager()

    @Test
    fun testParseValidJson() {
        val rawJson = """
            [
                {"item": "June Shine", "status": "missing", "action": "add_to_shopping_list"},
                {"item": "Spicy Italian Sausage", "status": "in_stock", "quantity": 2},
                {"item": "Mozz's Cat Food", "status": "low_stock", "quantity": 1}
            ]
        """.trimIndent()

        val results = parser.parseAuditResults(rawJson)
        assertEquals(3, results.size)

        val item1 = results[0]
        assertEquals("June Shine", item1.itemName)
        assertEquals("missing", item1.status)
        assertEquals("add_to_shopping_list", item1.action)
        assertEquals(0, item1.quantity)

        val item2 = results[1]
        assertEquals("Spicy Italian Sausage", item2.itemName)
        assertEquals("in_stock", item2.status)
        assertEquals(2, item2.quantity)

        val item3 = results[2]
        assertEquals("Mozz's Cat Food", item3.itemName)
        assertEquals("low_stock", item3.status)
        assertEquals(1, item3.quantity)
    }

    @Test
    fun testParseJsonWithMarkdownBlocks() {
        val rawJson = """
            ```json
            [
                {"item": "Avocado", "status": "in_stock", "quantity": 4}
            ]
            ```
        """.trimIndent()

        val results = parser.parseAuditResults(rawJson)
        assertEquals(1, results.size)
        assertEquals("Avocado", results[0].itemName)
        assertEquals("in_stock", results[0].status)
        assertEquals(4, results[0].quantity)
    }

    @Test
    fun testParseMalformedJsonThrowsException() {
        val malformedJson = """
            [
                {"item": "Avocado", "status": "in_stock", "quantity": 4
            ]
        """.trimIndent()

        try {
            parser.parseAuditResults(malformedJson)
            fail("Expected parseAuditResults to throw an exception for malformed JSON")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Failed to parse Gemini response as JSON"))
        }
    }
}
