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
    fun screenshotFilesMustBePlainWebpNames() {
        fun withShot(file: String) = validJson.replace(
            "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\"",
            "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\", " +
                "\"screenshots\": [{ \"file\": \"$file\", \"caption\": \"c\", \"width\": 10, \"height\": 20 }]",
        )
        assertEquals("ok-shot.webp", PortfolioParser.parse(withShot("ok-shot.webp")).projects[1].screenshots[0].file)
        listOf("../escape.webp", "dir/x.webp", "x.png", "x.thumb.webp").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withShot(bad)) }
        }
    }

    @Test
    fun rejectsNonHttpsLinks() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("https://github.com/a/b", "javascript:alert(1)"))
        }
    }
}
