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
    fun proofPointsParseAndStayShortEnoughForTheHero() {
        fun withProof(vararg points: Pair<String, String>) = validJson.replace(
            "\"summary\": \"Builds things.\"",
            "\"summary\": \"Builds things.\", \"proof\": [" +
                points.joinToString { (value, label) -> "{ \"value\": \"$value\", \"label\": \"$label\" }" } + "]",
        )
        val parsed = PortfolioParser.parse(withProof("53%" to "of the code", "#1 of ~40" to "engineers"))
        assertEquals(listOf("53%", "#1 of ~40"), parsed.profile.proof.map { it.value })
        assertTrue(PortfolioParser.parse(validJson).profile.proof.isEmpty(), "proof is optional")

        val tooMany = Array(PortfolioParser.MAX_PROOF_POINTS + 1) { "$it" to "label" }
        listOf(
            withProof(*tooMany),
            withProof(" " to "label"),
            withProof("1" to " "),
            withProof("a figure far too long" to "label"),
            withProof("1" to "x".repeat(PortfolioParser.MAX_PROOF_LABEL + 1)),
        ).forEach { bad -> assertFailsWith<IllegalArgumentException> { PortfolioParser.parse(bad) } }
    }

    @Test
    fun rejectsNonHttpsLinks() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("https://github.com/a/b", "javascript:alert(1)"))
        }
    }
}
