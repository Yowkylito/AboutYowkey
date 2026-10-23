package dev.yowkey.portfolio.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything the site shows. Loaded from `composeResources/files/portfolio.json`; edit that file, not Kotlin. */
@Immutable
@Serializable
data class Portfolio(
    val siteUrl: String,
    val profile: Profile,
    val services: List<Service> = emptyList(),
    val projects: List<Project> = emptyList(),
    val experience: List<Experience> = emptyList(),
    val recognition: List<Award> = emptyList(),
    val skills: List<SkillGroup> = emptyList(),
    val links: List<Link> = emptyList(),
) {
    /** The projects split into [ProjectGroup]s, in page order, each keeping the content's order. Empty groups are left out. */
    val projectGroups: List<Pair<ProjectGroup, List<Project>>>
        get() = ProjectGroup.entries
            .map { group -> group to projects.filter { ProjectGroup.of(it) == group } }
            .filter { (_, members) -> members.isNotEmpty() }
}

/**
 * The two halves of the Projects section, in page order: the products I build at work, then my own projects. A
 * project is work when it names its [company][Project.company].
 */
enum class ProjectGroup(val title: String) {
    Work("Professional work"),
    Personal("Personal projects"),
    ;

    /** The line under the group's title: why work entries show no code, or why some personal repos are private. */
    fun note(members: List<Project>): String? = when (this) {
        Work -> "Products I build at ${members.mapNotNull { it.company }.distinct().joinToString(" and ")}. Their code " +
            "stays with the company, so each entry covers my part and what it changed."
        Personal -> RepoVisibility.PRIVATE_NOTE.takeIf { members.any { it.visibility == RepoVisibility.Private } }
    }

    companion object {
        fun of(project: Project): ProjectGroup = if (project.isWork) Work else Personal
    }
}

@Immutable
@Serializable
data class Profile(
    val name: String,
    val headline: String,
    val summary: String,
    val location: String? = null,
    val email: String? = null,
    val availability: String? = null,
    /** The hero's few headline numbers, strongest first. The parser caps the count so they stay above the fold. */
    val proof: List<ProofPoint> = emptyList(),
)

/** One hero proof point: a short figure ([value], e.g. "53%") and the plain sentence that backs it. */
@Immutable
@Serializable
data class ProofPoint(
    val value: String,
    val label: String,
)

@Immutable
@Serializable
data class Service(
    val title: String,
    val description: String,
)

@Immutable
@Serializable
data class Project(
    /** Stable, unique: it keys the project's row in the lazy list. */
    val id: String,
    val title: String,
    val summary: String,
    /**
     * The card's one-liner, at most [MAX_TAGLINE] characters. Optional: the card falls back to [summary], cut at a
     * word (see [cardLine]).
     */
    val tagline: String? = null,
    val role: String? = null,
    /**
     * Who it was built for, when it was built at a job. Setting it makes the project work ([ProjectGroup.Work]):
     * listed first, on a card that leads with [figures] instead of media, and told through what I did and what it
     * changed rather than through code the company owns. [figures], [impact] and [optimizations] are for work only.
     */
    val company: String? = null,
    /** Up to [MAX_FIGURES] headline numbers for the card and the detail screen, strongest first. */
    val figures: List<ProofPoint> = emptyList(),
    /** What the work changed for the company, one line each. */
    val impact: List<String> = emptyList(),
    /** The optimizations and audits I made on the product: performance, security, release safety. One line each. */
    val optimizations: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val stack: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val period: String? = null,
    /** Whether the source is on GitHub for anyone to read. Null when not stated: the card then shows no badge. */
    val visibility: RepoVisibility? = null,
    /** The splash screen shown large at the top of the card; its `.thumb.webp` is the card's hero image. */
    val cover: Screenshot? = null,
    /**
     * An animated WebP (with a `.poster.webp` twin, the same files and budgets as [clips]) that plays as the card's
     * cover while the card is on screen. [cover] stays the still: the static page's fallback, and the first page of
     * the viewer.
     */
    val coverClip: Screenshot? = null,
    /**
     * Short animated WebP highlights, each with a `.poster.webp` still for the card.
     *
     * A list of their own rather than a `kind` flag on [screenshots]: the viewer's order is fixed by the product
     * (cover → clips → screenshots, see [gallery]), not chosen per entry, and a clip has different files and
     * budgets (a poster instead of a thumbnail, 700 KB instead of 254 KB). The parser and the tests then know a
     * clip by the list it is in, not by a flag an author can forget.
     */
    val clips: List<Screenshot> = emptyList(),
    val screenshots: List<Screenshot> = emptyList(),
    /** How it is built: the card shows [Engineering.builtWith] and [Engineering.platforms], the detail screen the rest. */
    val engineering: Engineering? = null,
) {
    /** Everything the viewer pages through, in its order. Index 0 is the cover when there is one. */
    val gallery: List<GalleryItem>
        get() = buildList {
            cover?.let { add(GalleryItem(it, GalleryItem.Kind.Cover)) }
            coverClip?.let { add(GalleryItem(it, GalleryItem.Kind.CoverClip)) }
            clips.forEach { add(GalleryItem(it, GalleryItem.Kind.Clip)) }
            screenshots.forEach { add(GalleryItem(it, GalleryItem.Kind.Still)) }
        }

    /**
     * What leads the card and the detail screen: the cover clip, else the cover, else the first gallery item, so
     * a project with only screenshots still opens on a picture. Null for a project with no media at all.
     */
    val leadMedia: GalleryItem?
        get() = gallery.let { items -> items.firstOrNull { it.kind == GalleryItem.Kind.CoverClip } ?: items.firstOrNull() }

    /** The card's one line: the [tagline], or the [summary] cut at a word to fit the same length. */
    val cardLine: String
        get() = tagline ?: truncateAtWord(summary, MAX_TAGLINE)

    /**
     * The card's headline technology: [Engineering.builtWith], or until a project has one, the first few of its
     * [stack], so no card is left without an answer to "what was it built with".
     */
    val headlineTech: List<String>
        get() = engineering?.builtWith?.takeIf { it.isNotEmpty() } ?: stack.take(Engineering.MAX_BUILT_WITH)

    /** The id of the project's article in the static page (`#project-<id>`). */
    val anchor: String get() = "project-$id"

    val isWork: Boolean get() = company != null

    /** Company, role and period, the line under the title on the card, the detail screen and the static page. */
    val meta: String get() = listOfNotNull(company, role, period).joinToString(" · ")

    /** What the detail screen calls [highlights]: on work, my part in a product others built too. */
    val highlightsTitle: String get() = if (isWork) "My contributions" else "Highlights"

    companion object {
        const val MAX_TAGLINE = 90
        const val MAX_FIGURES = 3
        /** A figure sits in a fixed-width column beside its label, so it stays a short number. */
        const val MAX_FIGURE_VALUE = 7
        const val MAX_FIGURE_LABEL = 60
        const val MAX_IMPACT = 4
        const val MAX_OPTIMIZATIONS = 6
        const val MAX_LINE_WORDS = 40
    }
}

/**
 * [text] if it fits in [max] characters, otherwise cut at the last space before the limit with an ellipsis, so the
 * result (ellipsis included) is never longer than [max]. A single word longer than [max] is cut mid-word.
 */
fun truncateAtWord(text: String, max: Int): String {
    val clean = text.trim()
    if (clean.length <= max) return clean
    val room = clean.take(max - 1)
    val cut = room.lastIndexOf(' ').takeIf { it > max / 2 } ?: room.length
    return room.take(cut).trimEnd(' ', ',', ';', ':', '.', '-', '·') + "…"
}

/**
 * How a project is built, for the engineering manager reading the detail screen. Every field is optional; the
 * parser holds them to the limits below so the card and the diagram keep their shape.
 *
 * @property builtWith headline technology, shown as chips on the card
 * @property platforms targets that actually ship, shown with a drawn glyph each ([PlatformKind])
 * @property architecture one paragraph
 * @property decisions the choices worth defending, each with its reason
 * @property modules the module graph, drawn as a layered diagram ([dependsOn][Module.dependsOn] must name listed
 *   modules and form no cycle)
 * @property quality verifiable facts: test counts, CI, budgets
 */
@Immutable
@Serializable
data class Engineering(
    val builtWith: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val architecture: String? = null,
    val decisions: List<Decision> = emptyList(),
    val modules: List<Module> = emptyList(),
    val quality: List<String> = emptyList(),
) {
    companion object {
        const val MAX_BUILT_WITH = 4
        const val MAX_PLATFORMS = 5
        const val MAX_ARCHITECTURE_WORDS = 70
        const val MIN_DECISIONS = 2
        const val MAX_DECISIONS = 4
        const val MAX_DECISION_WORDS = 40
        const val MAX_MODULES = 12
        const val MAX_MODULE_ROLE_WORDS = 10
        const val MAX_QUALITY = 4
    }
}

@Immutable
@Serializable
data class Decision(val title: String, val detail: String)

/** One node of the module diagram. [name] is how it is written in the build (`:core:data`). */
@Immutable
@Serializable
data class Module(
    val name: String,
    val role: String,
    val dependsOn: List<String> = emptyList(),
)

/** Which drawn glyph a platform chip gets. Matched on the platform's name, so authors write plain words. */
enum class PlatformKind {
    Android, Ios, Web, Desktop, Server, Other;

    companion object {
        fun of(platform: String): PlatformKind {
            val name = platform.lowercase()
            return when {
                "android" in name || "wear os" in name -> Android
                "ios" in name || "iphone" in name || "ipad" in name -> Ios
                "web" in name || "browser" in name || "wasm" in name -> Web
                listOf("desktop", "macos", "mac", "windows", "linux", "jvm").any { it in name } -> Desktop
                listOf("server", "backend", "cloud", "api").any { it in name } -> Server
                else -> Other
            }
        }
    }
}

/** Counts the words of [text] the way the parser's limits mean them: runs of non-space characters. */
fun wordCount(text: String): Int = text.trim().split(Whitespace).count { it.isNotEmpty() }

private val Whitespace = Regex("""\s+""")

/** Serialized as `"public"` / `"private"`; any other value fails parsing. */
@Serializable
enum class RepoVisibility(val badge: String) {
    @SerialName("public") Public("Public repo"),
    @SerialName("private") Private("Private repo"),
    ;

    companion object {
        /** Shown once under the Projects header when any project is private, so a missing source link reads as intended. */
        const val PRIVATE_NOTE = "Projects marked Private repo live in private GitHub repositories, so their source " +
            "isn't on my profile. I'm happy to walk through them in an interview."
    }
}

/** One page of the viewer: a [shot] and which of the project's lists it came from, which decides its files. */
@Immutable
data class GalleryItem(val shot: Screenshot, val kind: Kind) {
    enum class Kind { Cover, CoverClip, Clip, Still }

    val isClip: Boolean get() = kind == Kind.Clip || kind == Kind.CoverClip

    /** The cover and the cover clip lead the detail screen; the strip under it shows the rest. */
    val isCoverMedia: Boolean get() = kind == Kind.Cover || kind == Kind.CoverClip

    /** The still the card shows: a clip's poster, otherwise the thumbnail. */
    val cardPath: String get() = if (isClip) shot.posterPath else shot.thumbPath
}

/**
 * An image in `composeResources/files/screens/`: [file] is the full-size WebP shown in the viewer, and a
 * `.thumb.webp` twin beside it is what the card shows. [width] and [height] are the full image's pixels; they
 * reserve the space before it loads, so nothing jumps.
 *
 * Covers have the same files. Clips have the same shape, but [file] is an animated WebP and the card's still is
 * its `.poster.webp` twin.
 */
@Immutable
@Serializable
data class Screenshot(
    val file: String,
    val caption: String,
    val width: Int,
    val height: Int,
    /**
     * Clips only: a 60 fps H.264 `.mp4` twin ships beside the WebP ([videoPath]). The web viewer plays it with the
     * browser's video decoder; the desktop app, the cards, the detail screen and the static page keep the WebP.
     */
    val video: Boolean = false,
) {
    val aspectRatio: Float get() = width.toFloat() / height
    val fullPath: String get() = "$SCREENS_DIR/$file"
    val thumbPath: String get() = "$SCREENS_DIR/${file.removeSuffix(".webp")}.thumb.webp"
    val posterPath: String get() = "$SCREENS_DIR/${file.removeSuffix(".webp")}.poster.webp"
    val videoPath: String get() = "$SCREENS_DIR/${file.removeSuffix(".webp")}.mp4"

    companion object {
        /** Relative to composeResources; `Res.readBytes` takes it as is. */
        const val SCREENS_DIR = "files/screens"
    }
}

@Immutable
@Serializable
data class Award(
    val title: String,
    val detail: String,
    val date: String? = null,
)

@Immutable
@Serializable
data class Experience(
    val role: String,
    val company: String,
    val period: String,
    val location: String? = null,
    /** The tasks and outcomes of the role, one line each. */
    val highlights: List<String> = emptyList(),
)

@Immutable
@Serializable
data class SkillGroup(
    val name: String,
    val items: List<String>,
)

@Immutable
@Serializable
data class Link(
    val label: String,
    val url: String,
)
