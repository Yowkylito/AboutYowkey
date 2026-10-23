package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.model.Award
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProjectGroup
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.samplePortfolio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /** Work comes first in its own group whatever the content's order; each group's first row carries its title. */
    @Test
    fun projectsSplitIntoWorkThenPersonalGroups() {
        val mixed = samplePortfolio(
            projects = listOf(
                Project("side-1", "Side", "S"),
                Project("job-1", "Job", "S", company = "Acme"),
                Project("side-2", "Side", "S", visibility = RepoVisibility.Private),
                Project("job-2", "Job", "S", company = "Acme"),
                Project("side-3", "Side", "S"),
            ),
        )
        val rows = buildPageItems(mixed, serviceColumns = 2, projectColumns = 2).filterIsInstance<PageItem.Projects>()
        assertEquals(listOf(listOf("job-1", "job-2"), listOf("side-1", "side-2"), listOf("side-3")), rows.map { row -> row.projects.map { it.id } })
        assertEquals(listOf(ProjectGroup.Work, ProjectGroup.Personal, ProjectGroup.Personal), rows.map { it.group })
        assertEquals(listOf(true, true, false), rows.map { it.showsGroupHeader })
        assertEquals(listOf(true, false, false), rows.map { it.showsHeader }, "one section header, on the first row")
        assertTrue("Acme" in rows[0].note.orEmpty(), "the work note names the company")
        assertEquals(RepoVisibility.PRIVATE_NOTE, rows[1].note)
    }

    @Test
    fun withoutWorkThereIsOnlyThePersonalGroup() {
        val rows = buildPageItems(portfolio, 1, 1).filterIsInstance<PageItem.Projects>()
        assertTrue(rows.all { it.group == ProjectGroup.Personal })
        assertEquals(1, rows.count { it.showsGroupHeader })
        assertNull(rows.first().note, "no private repo, so no note")
    }

    /** The note form sits in the Contact section, after the contact block, only when the content names a backend. */
    @Test
    fun theNoteFormFollowsContactOnlyWithABackend() {
        val without = buildPageItems(portfolio, 1, 1)
        assertFalse(without.any { it is PageItem.Feedback })

        val items = buildPageItems(portfolio.copy(api = "https://api.example"), 1, 1)
        assertEquals(listOf(PageItem.Contact(null, portfolio.links), PageItem.Feedback, PageItem.Footer), items.takeLast(3))
        assertEquals(Section.Contact, PageItem.Feedback.section)
        assertFalse(PageItem.Feedback.showsHeader)
        assertEquals(sectionStarts(without).keys.toList(), sectionStarts(items).keys.toList(), "no section, so no nav entry, of its own")
    }

    @Test
    fun onlyTheLastJobEndsTheTimeline() {
        val jobs = buildPageItems(portfolio, 1, 1).filterIsInstance<PageItem.Job>()
        assertEquals(listOf(false, true), jobs.map { it.isLast })
        assertEquals(listOf(true, false), jobs.map { it.showsHeader })
    }
}
