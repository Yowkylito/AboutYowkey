package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.Award
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.validJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The crawlable page is also what shows first: it must put the same things above the fold, in the same order. */
class StaticHeroTest {

    private val portfolio = PortfolioParser.parse(validJson)

    @Test
    fun heroCarriesProofAndAContactLinkBeforeAnySection() {
        val hired = portfolio.copy(
            profile = portfolio.profile.copy(
                email = "ada@example.com",
                location = "London",
                availability = "Open to relocation",
                proof = listOf(ProofPoint("53%", "of the code")),
            ),
        )
        val hero = StaticHtml.content(hired).substringBefore("</header>")
        listOf("London · Open to relocation", "<strong>53%</strong> of the code", "href=\"mailto:ada@example.com\"", "href=\"#projects\"")
            .forEach { assertTrue(it in hero, "hero lacks $it") }
    }

    @Test
    fun sectionsFollowTheComposePageOrder() {
        val full = portfolio.copy(
            services = listOf(Service("S", "D")),
            experience = listOf(Experience("R", "C", "2024")),
            recognition = listOf(Award("A", "D")),
            skills = listOf(SkillGroup("G", listOf("K"))),
        )
        val html = StaticHtml.content(full)
        val ids = Regex("""<section id="(\w+)"""").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(listOf("experience", "recognition", "projects", "skills", "services", "contact"), ids)
    }
}
