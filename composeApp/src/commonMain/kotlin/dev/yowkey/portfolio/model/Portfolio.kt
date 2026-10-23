package dev.yowkey.portfolio.model

import androidx.compose.runtime.Immutable
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
    val screenshots: List<Screenshot> = emptyList(),
)

/**
 * An image in `composeResources/files/screens/`: [file] is the full-size WebP shown in the viewer, and a
 * `.thumb.webp` twin beside it is what the card shows. [width] and [height] are the full image's pixels; they
 * reserve the space before it loads, so nothing jumps.
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
