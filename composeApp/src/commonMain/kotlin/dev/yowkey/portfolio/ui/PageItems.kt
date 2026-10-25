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
import dev.yowkey.portfolio.ui.components.contactWhere

/**
 * The page's sections, in page order: what hiring managers weigh first comes first. A recruiter's skim is answered by
 * the hero; a hiring manager then reads the current job, the work they can open, the keywords, the focus areas when
 * there are any, and then the degree and the awards, which credential surveys rank last but a recruiter still looks
 * for, so they keep their place in the bar.
 */
enum class Section(val title: String) {
    Home("Home"),
    Experience("Experience"),
    Projects("Projects"),
    Skills("Skills"),
    Services("Focus areas"),
    Recognition("Education & awards"),
    Contact("Contact"),
}

/** One row of the page's lazy list. Grids are pre-chunked into rows so each row is one cheap lazy item. */
@Immutable
sealed interface PageItem {
    val key: String
    val section: Section

    /** The first row of a section carries its header. */
    val showsHeader: Boolean get() = false

    /** The first screen; [links] are the profile's (LinkedIn, GitHub), beside its buttons. */
    data class Hero(val profile: Profile, val links: List<Link> = emptyList()) : PageItem {
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

    /**
     * The personal projects past the first [FULL_PERSONAL_CARDS], one compact row each under the cards: every project
     * keeps its page, and the page ends sooner (the benchmark's strongest portfolios run about 3.5 screens, and the
     * hiring managers NN/g asked want 3 to 5 projects in depth).
     */
    data class MoreProjects(val projects: List<Project>) : PageItem {
        override val key = "more-projects"
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

    data class Contact(val email: String?, val links: List<Link>, val where: String? = null) : PageItem {
        override val key = "contact"
        override val section = Section.Contact
        override val showsHeader = true
    }

    /**
     * The private message's title, line and button (the form opens over the page), right under the contact block and
     * ahead of everything public (the published notes, then Feedback); only when the content names the site's backend
     * ([Portfolio.api]).
     */
    data object Message : PageItem {
        override val key = "message"
        override val section = Section.Contact
    }

    /**
     * The notes' title and ratings dashboard, between the message and the notes; once the backend has answered (its
     * numbers come with the notes).
     */
    data object Ratings : PageItem {
        override val key = "ratings"
        override val section = Section.Contact
    }

    /** One row of the public notes, under the dashboard. */
    data class Notes(val notes: List<PublicNote>, val columns: Int, val row: Int) : PageItem {
        override val key = "notes-$row"
        override val section = Section.Contact
    }

    /** "Show more notes", under the last row while the backend has more. */
    data object MoreNotes : PageItem {
        override val key = "more-notes"
        override val section = Section.Contact
    }

    /**
     * Feedback's title, line and button (the note form opens over the page), under the notes a note joins; only when
     * the content names the site's backend ([Portfolio.api]).
     */
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
 * at all. With the site's backend, the Contact section continues with the private message, then (once the backend
 * has answered, [notesLoaded]) the ratings dashboard and [notes], the public notes shown so far, chunked like the
 * project cards, with "Show more" when [moreNotes]; then Feedback. Without a backend there is none of them.
 */
fun buildPageItems(
    portfolio: Portfolio,
    serviceColumns: Int,
    projectColumns: Int,
    notes: List<PublicNote> = emptyList(),
    notesLoaded: Boolean = notes.isNotEmpty(),
    moreNotes: Boolean = false,
): List<PageItem> = buildList {
    add(PageItem.Hero(portfolio.profile, portfolio.links))
    portfolio.experience.forEachIndexed { index, job ->
        add(PageItem.Job(job, index, isLast = index == portfolio.experience.lastIndex))
    }
    portfolio.projectGroups.forEachIndexed { index, (group, members) ->
        val note = group.note(members)
        val shown = if (group == ProjectGroup.Personal) FULL_PERSONAL_CARDS else members.size
        members.take(shown).chunked(projectColumns).forEachIndexed { row, chunk ->
            add(PageItem.Projects(chunk, projectColumns, showsHeader = index == 0 && row == 0, group, showsGroupHeader = row == 0, note))
        }
        members.drop(shown).takeIf { it.isNotEmpty() }?.let { add(PageItem.MoreProjects(it)) }
    }
    if (portfolio.skills.isNotEmpty()) add(PageItem.Skills(portfolio.skills))
    portfolio.services.chunked(serviceColumns).forEachIndexed { row, chunk ->
        add(PageItem.Services(chunk, serviceColumns, showsHeader = row == 0, row = row))
    }
    if (portfolio.recognition.isNotEmpty()) add(PageItem.Recognition(portfolio.recognition))
    add(PageItem.Contact(portfolio.profile.email, portfolio.links, contactWhere(portfolio.profile)))
    if (portfolio.api != null) {
        add(PageItem.Message)
        if (notesLoaded) add(PageItem.Ratings)
        notes.chunked(projectColumns).forEachIndexed { row, chunk -> add(PageItem.Notes(chunk, projectColumns, row)) }
        if (moreNotes) add(PageItem.MoreNotes)
        add(PageItem.Feedback)
    }
    add(PageItem.Footer)
}

/** How many personal projects show as full cards; the rest are [PageItem.MoreProjects] rows. */
const val FULL_PERSONAL_CARDS = 4

/** The sections that actually have rows, in page order, with the index of their first row. */
fun sectionStarts(items: List<PageItem>): Map<Section, Int> = buildMap {
    items.forEachIndexed { index, item -> if (item.section !in this) put(item.section, index) }
}
