package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.model.Award
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.samplePortfolio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageItemsTest {

    private val portfolio = samplePortfolio(
        projects = (1..5).map { Project(id = "p$it", title = "P$it", summary = "S") },
    ).copy(
        services = (1..3).map { Service("S$it", "D") },
        experience = listOf(Experience("Role", "A", "2020"), Experience("Role", "B", "2018")),
    )

    @Test
    fun keysAreUniqueAtEveryColumnCount() {
        for (columns in 1..4) {
            val keys = buildPageItems(portfolio, columns, columns).map { it.key }
            assertEquals(keys.size, keys.toSet().size, "duplicate keys at $columns columns: $keys")
        }
    }

    @Test
    fun gridsAreChunkedIntoRowsWithOneHeader() {
        val projects = buildPageItems(portfolio, serviceColumns = 2, projectColumns = 2).filterIsInstance<PageItem.Projects>()
        assertEquals(listOf(2, 2, 1), projects.map { it.projects.size })
        assertEquals(listOf(true, false, false), projects.map { it.showsHeader })
    }

    @Test
    fun emptySectionsProduceNoRowsAndNoNavEntry() {
        val items = buildPageItems(samplePortfolio().copy(services = emptyList(), skills = emptyList()), 2, 2)
        val starts = sectionStarts(items)
        assertFalse(Section.Services in starts)
        assertFalse(Section.Skills in starts)
        assertTrue(Section.Contact in starts)
    }

    @Test
    fun sectionsStartInPageOrder() {
        val starts = sectionStarts(buildPageItems(portfolio, 1, 1))
        assertEquals(starts.values.sorted(), starts.values.toList())
        assertEquals(0, starts[Section.Home])
    }

    /** Evidence before claims: the job, its awards, then work, keywords, and the focus areas last. */
    @Test
    fun sectionsFollowTheHiringReadOrder() {
        val full = portfolio.copy(recognition = listOf(Award("A", "D")), skills = listOf(SkillGroup("G", listOf("K"))))
        val expected = listOf(
            Section.Home, Section.Experience, Section.Recognition, Section.Projects,
            Section.Skills, Section.Services, Section.Contact,
        )
        for (columns in 1..2) assertEquals(expected, sectionStarts(buildPageItems(full, columns, columns)).keys.toList())
        assertEquals(expected, Section.entries, "the enum is the page order; keep them in step")
    }

    @Test
    fun onlyTheLastJobEndsTheTimeline() {
        val jobs = buildPageItems(portfolio, 1, 1).filterIsInstance<PageItem.Job>()
        assertEquals(listOf(false, true), jobs.map { it.isLast })
        assertEquals(listOf(true, false), jobs.map { it.showsHeader })
    }
}
