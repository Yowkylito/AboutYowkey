package dev.yowkey.portfolio.ui

import androidx.compose.runtime.Immutable
import dev.yowkey.portfolio.model.Award
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProjectGroup
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup

/**
 * The page's sections, in page order: evidence before claims. A recruiter's skim is answered by the hero; a hiring
 * manager then reads the current job, the awards that back it, the work they can open, the keywords, and only then
 * the focus areas, which summarise rather than prove.
 */
enum class Section(val title: String) {
    Home("Home"),
    Experience("Experience"),
    Recognition("Recognition"),
    Projects("Projects"),
    Skills("Skills"),
    Services("Focus areas"),
    Contact("Contact"),
}

/** One row of the page's lazy list. Grids are pre-chunked into rows so each row is one cheap lazy item. */
@Immutable
sealed interface PageItem {
    val key: String
    val section: Section

    /** The first row of a section carries its header. */
    val showsHeader: Boolean get() = false

    data class Hero(val profile: Profile) : PageItem {
        override val key = "hero"
        override val section = Section.Home
    }

    data class Services(val services: List<Service>, val columns: Int, override val showsHeader: Boolean, val row: Int) : PageItem {
        override val key = "services-$row"
        override val section = Section.Services
    }

    /**
     * One row of a [ProjectGroup]'s cards. The first row of each group carries the group's title and [note]; only
     * the first row of the first group carries the section header.
     */
    data class Projects(
        val projects: List<Project>,
        val columns: Int,
        override val showsHeader: Boolean,
        val group: ProjectGroup,
        val showsGroupHeader: Boolean,
        val note: String? = null,
    ) : PageItem {
        override val key = "projects-${projects.first().id}"
        override val section = Section.Projects
    }

    data class Job(val experience: Experience, val index: Int, val isLast: Boolean) : PageItem {
        override val key = "job-$index"
        override val section = Section.Experience
        override val showsHeader get() = index == 0
    }

    data class Recognition(val awards: List<Award>) : PageItem {
        override val key = "recognition"
        override val section = Section.Recognition
        override val showsHeader = true
    }

    data class Skills(val groups: List<SkillGroup>) : PageItem {
        override val key = "skills"
        override val section = Section.Skills
        override val showsHeader = true
    }

    data class Contact(val email: String?, val links: List<Link>) : PageItem {
        override val key = "contact"
        override val section = Section.Contact
        override val showsHeader = true
    }

    /** One row of the published notes, between the contact block and the form; the first row carries the title. */
    data class Notes(val notes: List<PublicNote>, val columns: Int, val row: Int) : PageItem {
        override val key = "notes-$row"
        override val section = Section.Contact
    }

    /** The note form, under the contact block; only when the content names the site's backend ([Portfolio.api]). */
    data object Feedback : PageItem {
        override val key = "feedback"
        override val section = Section.Contact
    }

    data object Footer : PageItem {
        override val key = "footer"
        override val section = Section.Contact
    }
}

/**
 * Flattens the portfolio into rows for the current column counts, in [Section] order. Empty sections produce no rows
 * at all. [notes], the published notes from the site's backend, are chunked like the project cards and sit between
 * the contact block and the form; without a backend there are neither.
 */
fun buildPageItems(
    portfolio: Portfolio,
    serviceColumns: Int,
    projectColumns: Int,
    notes: List<PublicNote> = emptyList(),
): List<PageItem> = buildList {
    add(PageItem.Hero(portfolio.profile))
    portfolio.experience.forEachIndexed { index, job ->
        add(PageItem.Job(job, index, isLast = index == portfolio.experience.lastIndex))
    }
    if (portfolio.recognition.isNotEmpty()) add(PageItem.Recognition(portfolio.recognition))
    portfolio.projectGroups.forEachIndexed { index, (group, members) ->
        val note = group.note(members)
        members.chunked(projectColumns).forEachIndexed { row, chunk ->
            add(PageItem.Projects(chunk, projectColumns, showsHeader = index == 0 && row == 0, group, showsGroupHeader = row == 0, note))
        }
    }
    if (portfolio.skills.isNotEmpty()) add(PageItem.Skills(portfolio.skills))
    portfolio.services.chunked(serviceColumns).forEachIndexed { row, chunk ->
        add(PageItem.Services(chunk, serviceColumns, showsHeader = row == 0, row = row))
    }
    add(PageItem.Contact(portfolio.profile.email, portfolio.links))
    if (portfolio.api != null) {
        notes.chunked(projectColumns).forEachIndexed { row, chunk -> add(PageItem.Notes(chunk, projectColumns, row)) }
        add(PageItem.Feedback)
    }
    add(PageItem.Footer)
}

/** The sections that actually have rows, in page order, with the index of their first row. */
fun sectionStarts(items: List<PageItem>): Map<Section, Int> = buildMap {
    items.forEachIndexed { index, item -> if (item.section !in this) put(item.section, index) }
}
