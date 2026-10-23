package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.ModuleGraph
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.model.wordCount
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
        portfolio.projects.forEach { project ->
            // The id is part of a URL (#/projects/<id>) and an HTML id (project-<id>), so it stays plain.
            require(PROJECT_ID.matches(project.id)) {
                "project id \"${project.id}\" must be lowercase letters, digits and hyphens (it is used in the page's URL)"
            }
        }

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
            // A still or a cover has nothing to play, so a video twin there would ship unused.
            val stray = gallery.filter { it.shot.video && !it.isClip }.map { it.shot.file }
            require(stray.isEmpty()) { "project ${project.id}: only clips and the cover clip take \"video\", not $stray" }
            validateCard(project)
            project.engineering?.let { validateEngineering(project.id, it) }
        }
    }

    private fun validateCard(project: Project) {
        project.tagline?.let { tagline ->
            require(tagline.isNotBlank()) { "project ${project.id}: tagline is blank; leave it out to use the summary" }
            require(tagline.length <= Project.MAX_TAGLINE) {
                "project ${project.id}: tagline is ${tagline.length} characters, at most ${Project.MAX_TAGLINE} (it is the card's one line)"
            }
        }
    }

    /**
     * The card has room for a few chips, the detail screen for one paragraph and a handful of decisions, and the
     * module diagram for a dozen boxes. Beyond that the page would need a different design, so the build says so.
     */
    private fun validateEngineering(id: String, e: Engineering) {
        fun check(condition: Boolean, message: () -> String) = require(condition) { "project $id engineering: ${message()}" }
        fun checkList(name: String, items: List<String>, max: Int) {
            check(items.size <= max) { "$name takes at most $max, got ${items.size}" }
            check(items.none { it.isBlank() }) { "$name has a blank entry" }
            val repeated = items.groupBy { it.lowercase() }.filterValues { it.size > 1 }.keys
            check(repeated.isEmpty()) { "$name repeats $repeated" }
        }
        checkList("builtWith", e.builtWith, Engineering.MAX_BUILT_WITH)
        checkList("platforms", e.platforms, Engineering.MAX_PLATFORMS)
        checkList("quality", e.quality, Engineering.MAX_QUALITY)
        e.architecture?.let { text ->
            check(text.isNotBlank()) { "architecture is blank; leave it out instead" }
            val words = wordCount(text)
            check(words <= Engineering.MAX_ARCHITECTURE_WORDS) {
                "architecture is $words words, at most ${Engineering.MAX_ARCHITECTURE_WORDS}"
            }
        }
        if (e.decisions.isNotEmpty()) {
            check(e.decisions.size in Engineering.MIN_DECISIONS..Engineering.MAX_DECISIONS) {
                "decisions takes ${Engineering.MIN_DECISIONS} to ${Engineering.MAX_DECISIONS}, got ${e.decisions.size}"
            }
        }
        e.decisions.forEach { decision ->
            check(decision.title.isNotBlank() && decision.detail.isNotBlank()) { "a decision needs a title and a detail: $decision" }
            val words = wordCount(decision.detail)
            check(words <= Engineering.MAX_DECISION_WORDS) {
                "decision \"${decision.title}\" is $words words, at most ${Engineering.MAX_DECISION_WORDS}"
            }
        }
        validateModules(e.modules, ::check)
    }

    private fun validateModules(modules: List<Module>, check: (Boolean, () -> String) -> Unit) {
        check(modules.size <= Engineering.MAX_MODULES) { "modules takes at most ${Engineering.MAX_MODULES}, got ${modules.size}" }
        val names = modules.map { it.name }
        check(names.none { it.isBlank() }) { "a module has a blank name" }
        val repeated = names.groupBy { it }.filterValues { it.size > 1 }.keys
        check(repeated.isEmpty()) { "module names repeat: $repeated" }
        modules.forEach { module ->
            check(module.role.isNotBlank()) { "module ${module.name} needs a role" }
            val words = wordCount(module.role)
            check(words <= Engineering.MAX_MODULE_ROLE_WORDS) {
                "module ${module.name}'s role is $words words, at most ${Engineering.MAX_MODULE_ROLE_WORDS}"
            }
            val unknown = module.dependsOn.filter { it !in names }
            check(unknown.isEmpty()) { "module ${module.name} depends on unlisted modules $unknown" }
            check(module.name !in module.dependsOn) { "module ${module.name} depends on itself" }
        }
        val cycle = ModuleGraph.findCycle(modules)
        check(cycle == null) { "modules form a cycle: ${cycle.orEmpty().joinToString(" → ")}" }
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

    private val PROJECT_ID = Regex("[a-z0-9][a-z0-9-]*")

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
