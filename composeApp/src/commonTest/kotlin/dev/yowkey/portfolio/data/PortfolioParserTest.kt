package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.validJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortfolioParserTest {

    @Test
    fun parsesValidContentAndIgnoresUnknownKeys() {
        val portfolio = PortfolioParser.parse(validJson)
        assertEquals("Ada", portfolio.profile.name)
        assertEquals(listOf("one", "two"), portfolio.projects.map { it.id })
        assertTrue(portfolio.services.isEmpty(), "missing sections default to empty")
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<IllegalArgumentException> { PortfolioParser.parse("{ not json") }
    }

    @Test
    fun rejectsDuplicateProjectIdsBecauseTheyKeyTheLazyList() {
        val json = validJson.replace("\"id\": \"two\"", "\"id\": \"one\"")
        val error = assertFailsWith<IllegalArgumentException> { PortfolioParser.parse(json) }
        assertTrue("one" in error.message.orEmpty())
    }

    @Test
    fun rejectsBlankName() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("\"name\": \"Ada\"", "\"name\": \"  \""))
        }
    }

    @Test
    fun rejectsNonHttpsLinks() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("https://github.com/a/b", "javascript:alert(1)"))
        }
    }
}
