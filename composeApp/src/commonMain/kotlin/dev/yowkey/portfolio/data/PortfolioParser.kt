package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
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

        val duplicateIds = portfolio.projects.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) { "project ids must be unique, repeated: $duplicateIds" }
        require(portfolio.projects.none { it.id.isBlank() }) { "every project needs an id" }

        val links = portfolio.links + portfolio.projects.flatMap { it.links }
        links.forEach(::validateLink)
        portfolio.projects.flatMap { it.screenshots }.forEach(::validateScreenshot)
    }

    private fun validateScreenshot(shot: Screenshot) {
        require(SCREENSHOT_FILE.matches(shot.file)) {
            "screenshot file must be a plain name ending in .webp (no folders, no .thumb), got ${shot.file}"
        }
        require(shot.width > 0 && shot.height > 0) { "screenshot ${shot.file} needs its pixel width and height" }
        require(shot.caption.isNotBlank()) { "screenshot ${shot.file} needs a caption; it is also the alt text" }
    }

    private val SCREENSHOT_FILE = Regex("[a-z0-9][a-z0-9-]*(?<!\\.thumb)\\.webp")

    private fun validateLink(link: Link) {
        require(link.label.isNotBlank()) { "a link has a blank label: ${link.url}" }
        require(link.url.startsWith("https://") || link.url.startsWith("mailto:")) {
            "links must be https:// or mailto:, got ${link.url}"
        }
    }
}
