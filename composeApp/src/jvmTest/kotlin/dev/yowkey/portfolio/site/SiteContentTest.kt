package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.MessageCopy
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.trackingKey
import dev.yowkey.portfolio.ui.image.AnimatedClip
import dev.yowkey.portfolio.ui.image.ClipFrames
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs against the real content and template, so what ships is what is tested. */
class SiteContentTest {

    private val resources = File("src/commonMain/composeResources")
    private val portfolio = PortfolioParser.parse(File(resources, "files/portfolio.json").readText())
    private val build = "949ee26"
    private val page = StaticHtml.page(portfolio, File("web/index.template.html").readText(), build)
    private val gallery = portfolio.projects.flatMap { it.gallery }
    private val stills = gallery.filterNot { it.isClip }
    private val clips = gallery.filter { it.isClip }

    @Test
    fun everyProjectAndJobIsInTheCrawlablePage() {
        portfolio.projects.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing project ${it.title}") }
        portfolio.experience.forEach { assertTrue(StaticHtml.escape(it.company) in page, "missing job at ${it.company}") }
        portfolio.services.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing service ${it.title}") }
        portfolio.recognition.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing award ${it.title}") }
    }

    /** The detail screen's engineering content is in the crawlable page too, every word of it. */
    @Test
    fun everyProjectsEngineeringIsInTheCrawlablePage() {
        portfolio.projects.forEach { project ->
            val article = page.substringAfter("<article id=\"${project.anchor}\">").substringBefore("</article>")
            project.tagline?.let { assertTrue(StaticHtml.escape(it) in article, "${project.id}: tagline") }
            val e = project.engineering ?: return@forEach
            (e.builtWith + e.platforms + e.quality + listOfNotNull(e.architecture) + e.decisions.flatMap { listOf(it.title, it.detail) } +
                e.modules.flatMap { listOf(it.name, it.role) + it.dependsOn })
                .forEach { assertTrue(StaticHtml.escape(it) in article, "${project.id}: missing \"$it\"") }
        }
    }

    /** Work leads the projects, and everything its detail screen says is in the crawlable page too. */
    @Test
    fun workLeadsTheProjectsAndIsCrawlable() {
        val work = portfolio.projects.filter { it.isWork }
        assertTrue(work.isNotEmpty(), "no work projects")
        val section = page.substringAfter("<section id=\"projects\">").substringBefore("</section>")
        val firstPersonal = section.indexOf("<article id=\"${portfolio.projects.first { !it.isWork }.anchor}\">")
        work.forEach { project ->
            assertTrue(section.indexOf("<article id=\"${project.anchor}\">") in 0 until firstPersonal, "${project.id} is not ahead of the personal projects")
            val article = page.substringAfter("<article id=\"${project.anchor}\">").substringBefore("</article>")
            (project.figures.flatMap { listOf(it.value, it.label) } + project.impact + project.highlights + project.optimizations + project.meta)
                .forEach { assertTrue(StaticHtml.escape(it) in article, "${project.id}: missing \"$it\"") }
        }
    }

    /** The seven-second skim: every point is in the hero, ahead of the first section. */
    @Test
    fun heroShowsProofAndContact() {
        val hero = page.substringAfter("<header class=\"hero\">").substringBefore("</header>")
        val proof = portfolio.profile.proof
        assertTrue(proof.size >= 2, "the hero needs at least two proof points")
        proof.forEach { assertTrue(StaticHtml.escape(it.label) in hero, "hero lacks proof ${it.value}") }
        portfolio.profile.email?.let { assertTrue(StaticHtml.escape(it) in hero, "hero lacks $it") }
    }

    /** The page is light unless the visitor switched; a saved choice is applied before the styles, so it never flashes. */
    @Test
    fun themeIsLightUnlessTheVisitorSwitched() {
        assertFalse("prefers-color-scheme: dark" in page, "the theme must not follow the OS")
        assertTrue("<meta name=\"color-scheme\" content=\"light\">" in page)
        assertTrue(":root[data-theme=\"dark\"]" in page, "dark is keyed on the html element's data-theme")
        val head = page.substringBefore("<style>")
        assertTrue("localStorage.getItem(\"theme\")" in head, "the saved theme is applied before the styles")
        assertTrue("window.applyTheme = function" in head, "the app switches through the page's applyTheme")
    }

    /**
     * The bar leads the scrolling page, ahead of the content, and every link in it lands on the page; the button
     * follows the content. The app replaces both: at hand-off they hide, the bar keeping its place so nothing under
     * the canvas shifts.
     */
    @Test
    fun theBarAndTheButtonFrameTheContentAndGiveWayToTheApp() {
        val scroll = page.substringAfter("<div id=\"static-scroll\">").substringBefore("<div id=\"app\"")
        val bar = StaticHtml.bar(portfolio)
        assertTrue(scroll.trimStart().startsWith(bar), "the bar opens the scrolling page")
        assertTrue(scroll.indexOf("<main id=\"static-content\">") > scroll.indexOf("</header>"), "the bar is outside the main content")
        assertTrue(scroll.indexOf(StaticHtml.backToTop()) > scroll.indexOf("</main>"), "the button follows the content")
        Regex("href=\"#([\\w-]+)\"").findAll(bar).map { it.groupValues[1] }.toSet().forEach {
            assertTrue("id=\"$it\"" in page, "the bar links to #$it, which is not on the page")
        }
        listOf("body.app-ready .bar { visibility: hidden; }", "body.app-ready .to-top { display: none; }")
            .forEach { assertTrue(it in page, "the app does not replace the static bar: $it") }
    }

    /**
     * Jumps glide only where motion is welcome, land below the bar, and the switch keeps the app's "theme" key, so the
     * app opens in the theme picked on the static page.
     */
    @Test
    fun jumpsGlideOnlyWithoutReducedMotionAndLandBelowTheBar() {
        val css = page.substringAfter("<style>").substringBefore("</style>")
        assertEquals(1, Regex("scroll-behavior: smooth").findAll(css).count())
        assertTrue("@media (prefers-reduced-motion: no-preference) { #static-scroll { scroll-behavior: smooth; } }" in css)
        assertTrue("#static-content [id] { scroll-margin-top: calc(var(--bar-offset) + 24px); }" in css)
        assertFalse("scroll-padding-top" in css, "it would scroll the page whenever the keyboard reached the bar's own controls")
        assertTrue("localStorage.setItem(\"theme\", next)" in page && "window.applyTheme(next)" in page)
    }

    /** The CV carries a personal phone number; the site must never. */
    @Test
    fun noPhoneNumberIsPublished() {
        val phone = Regex("""(\+?63|\b0)[\s-]?9\d{2}[\s-]?\d{3}[\s-]?\d{4}""")
        val json = File(resources, "files/portfolio.json").readText()
        listOf(json, page).forEach { assertFalse(phone.containsMatchIn(it), "a phone number: ${phone.find(it)?.value}") }
    }

    @Test
    fun pageHasDiscoverabilityTags() {
        assertFalse("{{" in page)
        listOf("<title>", "name=\"description\"", "property=\"og:title\"", "rel=\"canonical\"", "application/ld+json")
            .forEach { assertTrue(it in page, "index.html lacks $it") }
    }

    /**
     * GitHub Pages caches every file for ten minutes, so the app script's URL carries the build id: a freshly deployed
     * index.html then never loads the previous deploy's composeApp.js, which points at wasm files that are gone.
     */
    @Test
    fun appScriptIsLoadedPerBuild() {
        assertTrue("script.src = \"composeApp.js?v=$build\";" in page, "the app script's URL does not carry the build id")
        assertFalse("\"composeApp.js\"" in page, "an unversioned composeApp.js reference")
    }

    /** Budgets keep the first visit fast: a card shows thumbnails only; full images load when opened. */
    @Test
    fun everyScreenshotAndCoverShipsWithinBudget() {
        stills.forEach { item ->
            val shot = item.shot
            val full = File(resources, shot.fullPath)
            val thumb = File(resources, shot.thumbPath)
            assertTrue(full.isFile, "missing ${full.path}")
            assertTrue(thumb.isFile, "missing ${thumb.path}")
            assertTrue(full.length() <= 260_000, "${full.name} is ${full.length() / 1024} KB, budget 254 KB")
            assertTrue(thumb.length() <= 64_000, "${thumb.name} is ${thumb.length() / 1024} KB, budget 62 KB")
            assertTrue(StaticHtml.resourceUrl(shot.thumbPath) in page, "${shot.file} is not in the static page")
        }
    }

    /** The viewer plays clips; the card and reduced motion show the poster; the static page animates the file. */
    @Test
    fun everyClipShipsWithItsPosterWithinBudget() {
        clips.forEach { item ->
            val shot = item.shot
            val clip = File(resources, shot.fullPath)
            val poster = File(resources, shot.posterPath)
            assertTrue(clip.isFile, "missing ${clip.path}")
            assertTrue(poster.isFile, "missing ${poster.path}")
            assertTrue(clip.length() <= 700L * 1024, "${clip.name} is ${clip.length() / 1024} KB, budget 700 KB")
            assertTrue(poster.length() <= 64_000, "${poster.name} is ${poster.length() / 1024} KB, budget 62 KB")
            assertTrue(StaticHtml.resourceUrl(shot.fullPath) in page, "${shot.file} is not in the static page")
            assertTrue(StaticHtml.resourceUrl(shot.posterPath) in page, "${shot.file}'s poster is not in the static page")
        }
    }

    /** Decodes each clip with the same Skia Codec the app plays it with, and holds it to the script's limits. */
    @Test
    fun everyClipDecodesWithinItsLimits() {
        clips.forEach { item ->
            val shot = item.shot
            val codec = Codec.makeFromData(Data.makeFromBytes(File(resources, shot.fullPath).readBytes()))
            assertTrue(codec.frameCount > 1, "${shot.file} is not animated")
            assertEquals(shot.width to shot.height, codec.width to codec.height, "${shot.file}: width/height in the JSON")
            assertTrue(maxOf(codec.width, codec.height) <= 720, "${shot.file} is over 720 px on its long edge")
            val millis = codec.framesInfo.sumOf { ClipFrames.delayMillis(it.duration) }
            assertTrue(millis <= 6_000, "${shot.file} runs ${millis} ms, at most 6 s")
            assertTrue(codec.frameCount * 1000 <= millis * 12 + 1000, "${shot.file} is over 12 fps")

            // And the player's path: every frame of a full loop, decoded in turn into the one bitmap.
            val player = AnimatedClip.decode(File(resources, shot.fullPath).readBytes())
            repeat(player.frameCount * 2) { player.show(ClipFrames.next(player.heldFrame, player.frameCount)) }
            assertEquals(0, player.heldFrame, "two loops end back on frame 0")
        }
    }

    /**
     * The web viewer's 60 fps twins, held to `prepare_screenshots.py --video`: H.264 (every browser decodes it), the
     * clip's shape (the viewer sizes the box from the JSON), silent, index first so it plays while it downloads.
     */
    @Test
    fun everyClipVideoIsA60FpsH264WithinBudget() {
        clips.filter { it.shot.video }.forEach { item ->
            val shot = item.shot
            val file = File(resources, shot.videoPath)
            assertTrue(file.isFile, "missing ${file.path}")
            assertTrue(file.length() <= 1536L * 1024, "${file.name} is ${file.length() / 1024} KB, budget 1536 KB")
            val video = Mp4Info.read(file.readBytes())
            assertEquals("avc1", video.codec, "${file.name} is not H.264")
            assertEquals(60.0, video.framesPerSecond, 0.5, "${file.name}'s frame rate")
            assertTrue(video.seconds <= 6.05, "${file.name} runs ${video.seconds} s, at most 6 s")
            assertTrue(maxOf(video.width, video.height) <= 1440, "${file.name} is over 1440 px on its long edge")
            val ratio = video.width.toFloat() / video.height
            assertTrue(abs(ratio / shot.aspectRatio - 1) < 0.02, "${file.name} is not the shape of ${shot.file}")
            assertFalse(video.hasAudio, "${file.name} has a sound track")
            assertTrue(video.indexFirst, "${file.name} has its index after its frames, so it cannot play until fully loaded")
        }
    }

    @Test
    fun noScreenshotIsLeftUnlisted() {
        val listed = gallery.flatMap { item ->
            listOfNotNull(
                File(item.shot.fullPath).name,
                File(item.cardPath).name,
                File(item.shot.videoPath).name.takeIf { item.shot.video },
            )
        }.toSet()
        val shipped = File(resources, "files/screens").listFiles().orEmpty().map { it.name }.toSet()
        assertTrue((shipped - listed).isEmpty(), "shipped but unused, remove them: ${shipped - listed}")
    }

    /**
     * Each app icon ships square and small (the page draws it at 40 to 64 dp), sharp enough for a 3x phone at the
     * card's size, and sits right before its title in the static article.
     */
    @Test
    fun everyIconShipsSquareWithinBudgetBeforeItsTitle() {
        val withIcon = portfolio.projects.filter { it.iconPath != null }
        assertTrue(withIcon.isNotEmpty(), "no project has an icon")
        withIcon.forEach { project ->
            val file = File(resources, project.iconPath!!)
            assertTrue(file.isFile, "missing ${file.path}")
            assertTrue(file.length() <= 16 * 1024, "${file.name} is ${file.length() / 1024} KB, budget 16 KB")
            val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
            assertEquals(codec.width, codec.height, "${file.name} is not square")
            assertTrue(codec.width >= 120, "${file.name} is ${codec.width} px, at least 120 for a 40 dp icon at 3x")
            val article = page.substringAfter("<article id=\"${project.anchor}\">").substringBefore("</article>")
            val icon = article.indexOf(StaticHtml.resourceUrl(project.iconPath!!))
            assertTrue(icon in 0 until article.indexOf("<h4>${StaticHtml.escape(project.title)}</h4>"), "${project.id}: the icon is not before its title")
        }
    }

    /**
     * The portrait ships small and cut out (its top corners clear, so the stage shows through) and leads the static
     * hero, ahead of the name, as in the app's narrow layout. Nothing else sits in its folder unlisted.
     */
    @Test
    fun portraitShipsCutOutWithinBudgetAndLeadsTheHero() {
        val path = assertNotNull(portfolio.profile.photoPath, "the profile has no photo")
        val file = File(resources, path)
        assertTrue(file.isFile, "missing ${file.path}")
        assertTrue(file.length() <= 48 * 1024, "${file.name} is ${file.length() / 1024} KB, budget 48 KB")
        val bitmap = Bitmap.makeFromImage(Image.makeFromEncoded(file.readBytes()))
        listOf(0 to 0, bitmap.width - 1 to 0).forEach { (x, y) ->
            assertEquals(0, bitmap.getColor(x, y) ushr 24, "${file.name} is not cut out: its corner ($x, $y) is opaque")
        }
        val hero = page.substringAfter("<header class=\"hero\">").substringBefore("</header>")
        assertTrue(hero.indexOf(StaticHtml.resourceUrl(path)) in 0 until hero.indexOf("<h1>"), "the portrait does not lead the hero")
        val shipped = File(resources, Profile.PHOTO_DIR).listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(setOf(portfolio.profile.photo), shipped, "shipped but unused, remove them")
    }

    /** The shipped page's styles carry the hero's entrance, and the colour its split needs in either theme. */
    @Test
    fun thePageCarriesTheHerosEntrance() {
        val styles = page.substringAfter("<style>").substringBefore("</style>")
        assertTrue(StaticHtml.heroMotion() in styles, "the entrance is not in the page's styles")
        assertEquals(2, Regex("--fringe: #[0-9A-F]{6};").findAll(styles).count(), "the split's fringe, light and dark")
    }

    @Test
    fun noIconIsLeftUnlisted() {
        val listed = portfolio.projects.mapNotNull { it.icon }.toSet()
        val shipped = File(resources, Project.ICONS_DIR).listFiles().orEmpty().map { it.name }.toSet()
        assertTrue((shipped - listed).isEmpty(), "shipped but unused, remove them: ${shipped - listed}")
    }

    @Test
    fun repositoryVisibilityIsInTheCrawlablePage() {
        portfolio.projects.mapNotNull { it.visibility }.toSet().forEach {
            assertTrue(">${it.badge}</span>" in page, "missing badge ${it.badge}")
        }
        val anyPrivate = portfolio.projects.any { it.visibility == RepoVisibility.Private }
        assertEquals(anyPrivate, StaticHtml.escape(RepoVisibility.PRIVATE_NOTE) in page)
    }

    @Test
    fun everyUnofficialConceptSaysSoInItsArticle() {
        portfolio.projects.filter { it.disclaimer != null }.forEach { project ->
            val article = page.substringAfter("<article id=\"${project.anchor}\">").substringBefore("</article>")
            assertTrue(">${Project.UNOFFICIAL_BADGE}</span>" in article, "${project.id}: no unofficial badge")
            assertTrue("<p class=\"disclaimer\">${StaticHtml.escape(project.disclaimer!!)}</p>" in article, "${project.id}: no disclaimer")
        }
    }

    /**
     * Once the content names the backend, the page carries the note form, the notes' place (`#notes`) and the bar's
     * proof chip, which jumps there; the hero no longer has a live line of its own.
     */
    @Test
    fun theNoteFormTheNotesAndTheChipFollowTheContentsBackend() {
        val api = portfolio.api
        assertEquals(api != null, "<form class=\"feedback\"" in page)
        assertEquals(api != null, "<div class=\"visitor-notes\" id=\"notes\"" in page)
        assertEquals(api != null, "<a class=\"proof\" href=\"#notes\"" in page)
        assertFalse("live-stats" in page, "the hero does not repeat the chip's numbers")
        api?.let {
            assertTrue("action=\"${StaticHtml.escape(it)}/feedback\"" in page)
            assertTrue("<a class=\"proof\" href=\"#notes\" data-api=\"${StaticHtml.escape(it)}\" hidden>" in page)
        }
    }

    /**
     * The chip's script speaks the contract's words, the app's chip's: the average to one decimal and "· 1 rating"
     * (or "· 14 ratings"), the visits grouped ("1,234") and "visits", and the label that puts them together. The
     * words are read out of the script itself and spelled as it spells them. The numbers count up once, over 900ms,
     * easing out, and not at all with reduced motion, where nothing in the chip moves.
     */
    @Test
    fun theChipsScriptSpeaksTheContractsWords() {
        val script = page.substringAfter("var proof = document.querySelector(\".bar .proof[data-api]\")").substringBefore("</script>")
        fun words(what: String, pattern: String) = assertNotNull(Regex(pattern).find(script), "the chip's script lacks $what").groupValues
        // The notes' plural, which the chip shares: "1 rating", "14 ratings".
        assertTrue("function plural(n, one, many) { return n + \" \" + (n === 1 ? one : many); }" in page)
        val rated = words("the rating's part", """parts\.push\("([^"]*)" \+ chip\.average\.toFixed\(1\) \+ "([^"]*)" \+ plural\(count, "([^"]*)", "([^"]*)"\)\)""")
        val seen = words("the visits' part", """parts\.push\(chip\.visits\.toLocaleString\("en"\) \+ \(chip\.visits === 1 \? "([^"]*)" : "([^"]*)"\)\)""")
        val label = words("the label", """parts\.join\("([^"]*)"\) \+ "([^"]*)"\)""")
        val shownRated = words("the rating's words", """\.rate \.words"\)\.textContent = "([^"]*)" \+ plural\(count, "([^"]*)", "([^"]*)"\)""")
        val shownSeen = words("the visits' word", """\.seen \.words"\)\.textContent = chip\.visits === 1 \? "([^"]*)" : "([^"]*)"""")
        fun labelOf(average: String, ratings: Int, visits: String, one: Boolean) = listOf(
            rated[1] + average + rated[2] + ratings + " " + (if (ratings == 1) rated[3] else rated[4]),
            visits + (if (one) seen[1] else seen[2]),
        ).joinToString(label[1]) + label[2]
        assertEquals("Rated 5.0 out of 5 from 1 rating, 35 visits. Read the reviews.", labelOf("5.0", 1, "35", one = false))
        assertEquals("Rated 4.6 out of 5 from 14 ratings, 1,234 visits. Read the reviews.", labelOf("4.6", 14, "1,234", one = false))
        assertEquals(listOf("· ", "rating", "ratings"), shownRated.drop(1))
        assertEquals(listOf("visit", "visits"), shownSeen.drop(1))
        assertEquals(listOf(" visit", " visits"), seen.drop(1))
        listOf("average.toFixed(1)", "Math.round(chip.visits * k).toLocaleString(\"en\")")
            .forEach { assertTrue(it in script, "the count-up's numbers are the label's: $it") }
        assertTrue("(now - start) / 900" in script && "drawChip(1 - Math.pow(1 - t, 3))" in script, "900ms, easing out")
        assertTrue("window.matchMedia(\"(prefers-reduced-motion: reduce)\").matches" in script)
        assertTrue("if (chip.started || stillness()) { chip.started = true; proof.classList.add(\"on\"); return; }" in script, "once, and never with reduced motion")
        val css = page.substringAfter("<style>").substringBefore("</style>")
        assertTrue(Regex("@media \\(prefers-reduced-motion: reduce\\) \\{\\s*\\.brand > \\*, \\.bar \\.proof,").containsMatchIn(css), "the chip fades only with motion")
        assertFalse("visibility: visible" in css, "the bar hidden at hand-off hides the chip too")
    }

    /**
     * Likewise the private message form, and the beacon on the profile's LinkedIn and GitHub links; the template's
     * script sends both, and thanks the visitor in the app's words.
     */
    @Test
    fun theMessageFormAndTheTrackedLinksFollowTheContentsBackend() {
        val api = portfolio.api
        assertEquals(api != null, "<form class=\"message\"" in page)
        assertEquals(api != null, "data-track=\"" in page)
        val tracked = portfolio.links.mapNotNull { it.trackingKey }
        assertTrue(tracked.isNotEmpty(), "the profile links to LinkedIn or GitHub")
        api?.let {
            assertTrue("action=\"${StaticHtml.escape(it)}/messages\"" in page)
            tracked.forEach { key -> assertTrue("data-track=\"$key\" data-api=\"${StaticHtml.escape(it)}\"" in page, "$key is not tracked") }
        }
        assertTrue("navigator.sendBeacon(link.dataset.api + \"/clicks\", JSON.stringify({ link: name }))" in page)
        assertTrue("addEventListener(\"auxclick\"" in page, "a middle click counts too")
        assertTrue(MessageCopy.THANKS in page, "the static form thanks the visitor as the app does")
    }

    /**
     * The template's script draws the note cards and the star sheet in the model's words, which it copies: each
     * relation's label and plural, the empty sheet's "from colleagues" and the rest, the badge and the buttons.
     */
    @Test
    fun theNotesScriptSpeaksTheModelsWords() {
        Relation.entries.forEach {
            assertTrue("[\"${it.wire}\", \"${it.label}\", \"${it.plural}\"]" in page, "the script's ${it.wire}")
            val from = FeedbackCopy.noneInSheet(it, 5).removePrefix("No notes").substringBefore(" with ")
            assertTrue("${it.wire}: \"$from\"" in page, "the script's \"$from\"")
        }
        listOf(FeedbackCopy.PINNED, FeedbackCopy.MORE, FeedbackCopy.LOADING_MORE, PublicNote.ANONYMOUS).forEach {
            assertTrue("\"$it\"" in page, "the script lacks \"$it\"")
        }
        assertTrue("\" to read\"" in page && "\"-star notes\"" in page, "the sheet's title and summary")
    }

    /** The card's lead (the cover clip, else the cover) opens its article, ahead of the title. */
    @Test
    fun coversComeFirstInTheirArticle() {
        portfolio.projects.forEach { project ->
            val lead = project.coverClip?.fullPath ?: project.cover?.thumbPath ?: return@forEach
            val article = page.substringAfter("<article id=\"${project.anchor}\">", missingDelimiterValue = "")
            assertTrue(article.isNotEmpty(), "${project.id}: no article with its id")
            val title = article.indexOf("<h4>${StaticHtml.escape(project.title)}</h4>")
            assertTrue(article.indexOf(StaticHtml.resourceUrl(lead)) in 0 until title, "${project.id}: the cover is not before its title")
        }
    }

    @Test
    fun everyProjectHasAnArticleTheDeepLinkCanReach() {
        portfolio.projects.forEach { assertTrue("<article id=\"project-${it.id}\">" in page, "no article for ${it.id}") }
        assertTrue("getElementById(\"project-\" + match[1])" in page, "the static page scrolls to a deep-linked project")
    }

    /**
     * Every project's animated splash cover: it leads the card and the static article, keeps the still cover and its
     * poster for reduced motion, and decodes and loops in the card's one reusable bitmap.
     */
    @Test
    fun everyCoverClipLeadsItsProjectAndPlays() {
        val withClip = portfolio.projects.filter { it.coverClip != null }
        assertTrue(withClip.isNotEmpty(), "no project has an animated cover")
        val reparsed = PortfolioParser.parse(Json.encodeToString(Portfolio.serializer(), portfolio))
        val html = StaticHtml.content(reparsed)
        withClip.forEach { project ->
            val clip = project.coverClip!!
            assertEquals(GalleryItem.Kind.CoverClip, project.leadMedia?.kind, project.id)
            if (project.cover != null) {
                assertEquals(listOf(GalleryItem.Kind.Cover, GalleryItem.Kind.CoverClip), project.gallery.take(2).map { it.kind }, project.id)
            }
            val article = html.substringAfter("<article id=\"project-${project.id}\">").substringBefore("</article>")
            assertTrue(article.indexOf(StaticHtml.resourceUrl(clip.fullPath)) < article.indexOf("<h4>"), "${project.id}: the clip leads the article")
            assertTrue(StaticHtml.resourceUrl(clip.posterPath) in article, "${project.id}: its poster for reduced motion")

            val player = AnimatedClip.decode(File(resources, clip.fullPath).readBytes())
            repeat(player.frameCount) { player.show(ClipFrames.next(player.heldFrame, player.frameCount)) }
            assertEquals(0, player.heldFrame, "${project.id}: one loop ends back on frame 0")
            assertTrue(File(resources, clip.posterPath).isFile, "${project.id}: poster missing")
        }
    }

    /** The cover clip budget holds the clips that ship today, decoded the way the cards hold them. */
    @Test
    fun coverClipBudgetFitsFourOfTodaysLargestClips() {
        val largest = clips.map { AnimatedClip.decode(File(resources, it.shot.fullPath).readBytes()).byteCount }.sortedDescending()
        assertTrue(largest.isNotEmpty())
        assertTrue(largest.take(4).sum() <= ScreenshotImages.COVER_CLIP_BUDGET, "four clips weigh ${largest.take(4).sum()} bytes")
    }
}
