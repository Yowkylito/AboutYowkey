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
)

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
    val role: String? = null,
    val highlights: List<String> = emptyList(),
    val stack: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val period: String? = null,
    /** Whether the source is on GitHub for anyone to read. Null when not stated: the card then shows no badge. */
    val visibility: RepoVisibility? = null,
    /** The splash screen shown large at the top of the card; its `.thumb.webp` is the card's hero image. */
    val cover: Screenshot? = null,
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
) {
    /** Everything the viewer pages through, in its order. Index 0 is the cover when there is one. */
    val gallery: List<GalleryItem>
        get() = buildList {
            cover?.let { add(GalleryItem(it, GalleryItem.Kind.Cover)) }
            clips.forEach { add(GalleryItem(it, GalleryItem.Kind.Clip)) }
            screenshots.forEach { add(GalleryItem(it, GalleryItem.Kind.Still)) }
        }
}

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
    enum class Kind { Cover, Clip, Still }

    val isClip: Boolean get() = kind == Kind.Clip

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
) {
    val aspectRatio: Float get() = width.toFloat() / height
    val fullPath: String get() = "$SCREENS_DIR/$file"
    val thumbPath: String get() = "$SCREENS_DIR/${file.removeSuffix(".webp")}.thumb.webp"
    val posterPath: String get() = "$SCREENS_DIR/${file.removeSuffix(".webp")}.poster.webp"

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
