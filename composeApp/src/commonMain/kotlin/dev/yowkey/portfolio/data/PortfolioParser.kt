package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.Screenshot
import kotlinx.serialization.json.Json

/** Parses and validates portfolio.json. Shared by the app and the build-time HTML generator. */
object PortfolioParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** @throws IllegalArgumentException when the content is malformed or breaks a rule the UI relies on. */
    fun parse(text: String): Portfolio {
        val portfolio = try {
            json.decodeFromString(Portfolio.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("portfolio.json is not valid: ${e.message}", e)
        }
        validate(portfolio)
        return portfolio
    }

    private fun validate(portfolio: Portfolio) {
        require(portfolio.profile.name.isNotBlank()) { "profile.name must not be blank" }
        require(portfolio.siteUrl.startsWith("https://")) { "siteUrl must be an https:// URL" }
        validateProof(portfolio.profile.proof)

        val duplicateIds = portfolio.projects.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) { "project ids must be unique, repeated: $duplicateIds" }
        require(portfolio.projects.none { it.id.isBlank() }) { "every project needs an id" }

        val links = portfolio.links + portfolio.projects.flatMap { it.links }
        links.forEach(::validateLink)
        // `visibility` needs no check here: it decodes to an enum, so anything but "public" / "private" (or no key)
        // already failed above, naming the value and its path.
        portfolio.projects.forEach { project ->
            val gallery = project.gallery
            gallery.forEach { validateScreenshot(it.shot) }
            // The viewer's pager keys its pages by file, and one file cannot be both a still and a clip.
            val repeated = gallery.groupBy { it.shot.file }.filterValues { it.size > 1 }.keys
            require(repeated.isEmpty()) { "project ${project.id} lists the same file twice: $repeated" }
        }
    }

    /** Covers, clips and screenshots alike: the file name rules keep every path inside `files/screens/`. */
    private fun validateScreenshot(shot: Screenshot) {
        require(SCREENSHOT_FILE.matches(shot.file)) {
            "screenshot file must be a plain name ending in .webp (no folders, no .thumb or .poster), got ${shot.file}"
        }
        require(shot.width > 0 && shot.height > 0) { "screenshot ${shot.file} needs its pixel width and height" }
        require(shot.caption.isNotBlank()) { "screenshot ${shot.file} needs a caption; it is also the alt text" }
    }

    // No dots before the extension, so `x.thumb.webp` and `x.poster.webp` (the generated twins) are rejected too.
    private val SCREENSHOT_FILE = Regex("[a-z0-9][a-z0-9-]*\\.webp")

    private fun validateLink(link: Link) {
        require(link.label.isNotBlank()) { "a link has a blank label: ${link.url}" }
        require(link.url.startsWith("https://") || link.url.startsWith("mailto:")) {
            "links must be https:// or mailto:, got ${link.url}"
        }
    }

    /** The hero has room for a few short figures above the fold on a phone; more, or longer, would push the CTA down. */
    private fun validateProof(proof: List<ProofPoint>) {
        require(proof.size <= MAX_PROOF_POINTS) { "profile.proof takes at most $MAX_PROOF_POINTS points, got ${proof.size}" }
        proof.forEach { point ->
            require(point.value.isNotBlank() && point.label.isNotBlank()) { "a proof point needs a value and a label: $point" }
            require(point.value.length <= MAX_PROOF_VALUE) {
                "proof value \"${point.value}\" is longer than $MAX_PROOF_VALUE characters; keep the figure short, the label explains it"
            }
            require(point.label.length <= MAX_PROOF_LABEL) {
                "proof label for ${point.value} is longer than $MAX_PROOF_LABEL characters; it must fit two lines on a phone"
            }
        }
    }

    const val MAX_PROOF_POINTS = 3
    const val MAX_PROOF_VALUE = 12
    const val MAX_PROOF_LABEL = 90
}
