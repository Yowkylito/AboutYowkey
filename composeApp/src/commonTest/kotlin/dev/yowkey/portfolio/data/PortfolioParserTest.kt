package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.validJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioParserTest {

    @Test
    fun parsesValidContentAndIgnoresUnknownKeys() {
        val portfolio = PortfolioParser.parse(validJson)
        assertEquals("Ada", portfolio.profile.name)
        assertEquals(listOf("one", "two"), portfolio.projects.map { it.id })
        assertTrue(portfolio.services.isEmpty(), "missing sections default to empty")
    }

    @Test
    fun rejectsMalformedJson() {
        assertFailsWith<IllegalArgumentException> { PortfolioParser.parse("{ not json") }
    }

    @Test
    fun rejectsDuplicateProjectIdsBecauseTheyKeyTheLazyList() {
        val json = validJson.replace("\"id\": \"two\"", "\"id\": \"one\"")
        val error = assertFailsWith<IllegalArgumentException> { PortfolioParser.parse(json) }
        assertTrue("one" in error.message.orEmpty())
    }

    @Test
    fun rejectsBlankName() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("\"name\": \"Ada\"", "\"name\": \"  \""))
        }
    }

    @Test
    fun screenshotFilesMustBePlainWebpNames() {
        fun withShot(file: String) = validJson.replace(
            "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\"",
            "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\", " +
                "\"screenshots\": [{ \"file\": \"$file\", \"caption\": \"c\", \"width\": 10, \"height\": 20 }]",
        )
        assertEquals("ok-shot.webp", PortfolioParser.parse(withShot("ok-shot.webp")).projects[1].screenshots[0].file)
        listOf("../escape.webp", "dir/x.webp", "x.png", "x.thumb.webp").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withShot(bad)) }
        }
    }

    @Test
    fun photoIsOptionalAndAPlainWebpName() {
        fun withPhoto(file: String) = validJson.replace("\"summary\": \"Builds things.\"", "\"summary\": \"Builds things.\", \"photo\": \"$file\"")
        assertEquals("files/profile/me.webp", PortfolioParser.parse(withPhoto("me.webp")).profile.photoPath)
        assertEquals(null, PortfolioParser.parse(validJson).profile.photoPath, "the photo is optional")
        listOf("../escape.webp", "dir/x.webp", "x.png", "x.thumb.webp").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withPhoto(bad)) }
        }
    }

    @Test
    fun proofPointsParseAndStayShortEnoughForTheHero() {
        fun withProof(vararg points: Pair<String, String>) = validJson.replace(
            "\"summary\": \"Builds things.\"",
            "\"summary\": \"Builds things.\", \"proof\": [" +
                points.joinToString { (value, label) -> "{ \"value\": \"$value\", \"label\": \"$label\" }" } + "]",
        )
        val parsed = PortfolioParser.parse(withProof("53%" to "of the code", "30 days" to "to launch"))
        assertEquals(listOf("53%", "30 days"), parsed.profile.proof.map { it.value })
        assertTrue(PortfolioParser.parse(validJson).profile.proof.isEmpty(), "proof is optional")

        val tooMany = Array(PortfolioParser.MAX_PROOF_POINTS + 1) { "$it" to "label" }
        listOf(
            withProof(*tooMany),
            withProof(" " to "label"),
            withProof("1" to " "),
            withProof("a figure far too long" to "label"),
            withProof("1" to "x".repeat(PortfolioParser.MAX_PROOF_LABEL + 1)),
        ).forEach { bad -> assertFailsWith<IllegalArgumentException> { PortfolioParser.parse(bad) } }
    }

    @Test
    fun rejectsNonHttpsLinks() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(validJson.replace("https://github.com/a/b", "javascript:alert(1)"))
        }
    }

    private fun withSecondProject(fields: String) = validJson.replace(
        "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\"",
        "\"id\": \"two\", \"title\": \"Second\", \"summary\": \"S\", $fields",
    )

    private fun media(file: String) = "{ \"file\": \"$file\", \"caption\": \"c\", \"width\": 16, \"height\": 9 }"

    @Test
    fun visibilityIsPublicPrivateOrAbsent() {
        val portfolio = PortfolioParser.parse(withSecondProject("\"visibility\": \"private\""))
        assertEquals(listOf(null, RepoVisibility.Private), portfolio.projects.map { it.visibility })
        assertEquals(RepoVisibility.Public, PortfolioParser.parse(withSecondProject("\"visibility\": \"public\"")).projects[1].visibility)
        listOf("\"internal\"", "\"Private\"", "\"\"", "true").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"visibility\": $bad")) }
        }
    }

    @Test
    fun coverAndClipsFollowTheScreenshotFileRules() {
        val ok = PortfolioParser.parse(
            withSecondProject("\"cover\": ${media("splash.webp")}, \"clips\": [${media("ride.webp")}]"),
        ).projects[1]
        assertEquals("splash.webp", ok.cover?.file)
        assertEquals("files/screens/ride.poster.webp", ok.clips.single().posterPath)
        listOf("../x.webp", "x.poster.webp", "x.thumb.webp", "x.gif").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"cover\": ${media(bad)}")) }
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"clips\": [${media(bad)}]")) }
        }
    }

    @Test
    fun iconIsAPlainWebpNameInTheIconsFolder() {
        val portfolio = PortfolioParser.parse(withSecondProject("\"icon\": \"second.webp\""))
        assertEquals(listOf(null, "files/icons/second.webp"), portfolio.projects.map { it.iconPath })
        listOf("../x.webp", "dir/x.webp", "x.png", "x.thumb.webp", "").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"icon\": \"$bad\"")) }
        }
    }

    @Test
    fun aFileCannotBeListedTwiceInOneGallery() {
        val error = assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(withSecondProject("\"clips\": [${media("a.webp")}], \"screenshots\": [${media("a.webp")}]"))
        }
        assertTrue("a.webp" in error.message.orEmpty())
    }

    @Test
    fun galleryRunsCoverThenClipsThenScreenshots() {
        val project = PortfolioParser.parse(
            withSecondProject(
                "\"screenshots\": [${media("s1.webp")}, ${media("s2.webp")}], " +
                    "\"clips\": [${media("c1.webp")}], \"cover\": ${media("cover.webp")}",
            ),
        ).projects[1]
        assertEquals(listOf("cover.webp", "c1.webp", "s1.webp", "s2.webp"), project.gallery.map { it.shot.file })
        assertEquals(
            listOf(GalleryItem.Kind.Cover, GalleryItem.Kind.Clip, GalleryItem.Kind.Still, GalleryItem.Kind.Still),
            project.gallery.map { it.kind },
        )
        assertEquals(
            listOf("cover.thumb.webp", "c1.poster.webp", "s1.thumb.webp", "s2.thumb.webp"),
            project.gallery.map { it.cardPath.substringAfterLast('/') },
        )
    }

    @Test
    fun coverClipLeadsTheClipsAndFollowsTheirFileRules() {
        val project = PortfolioParser.parse(
            withSecondProject(
                "\"cover\": ${media("cover.webp")}, \"coverClip\": ${media("intro.webp")}, \"clips\": [${media("c1.webp")}]",
            ),
        ).projects[1]
        assertEquals(listOf("cover.webp", "intro.webp", "c1.webp"), project.gallery.map { it.shot.file })
        assertEquals(GalleryItem.Kind.CoverClip, project.gallery[1].kind)
        assertTrue(project.gallery[1].isClip && project.gallery[1].isCoverMedia)
        assertEquals("files/screens/intro.poster.webp", project.gallery[1].cardPath)
        assertEquals("intro.webp", project.leadMedia?.shot?.file, "the cover clip leads, ahead of the still cover")
        listOf("../x.webp", "x.poster.webp").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"coverClip\": ${media(bad)}")) }
        }
        assertFailsWith<IllegalArgumentException>("one file cannot be the cover clip and a clip") {
            PortfolioParser.parse(withSecondProject("\"coverClip\": ${media("a.webp")}, \"clips\": [${media("a.webp")}]"))
        }
    }

    @Test
    fun onlyClipsTakeAVideoTwin() {
        fun withVideo(file: String) = media(file).replace(" }", ", \"video\": true }")
        val project = PortfolioParser.parse(
            withSecondProject("\"coverClip\": ${withVideo("intro.webp")}, \"clips\": [${withVideo("c1.webp")}, ${media("c2.webp")}]"),
        ).projects[1]
        assertEquals(listOf(true, true, false), project.gallery.map { it.shot.video })
        assertEquals("files/screens/intro.mp4", project.gallery[0].shot.videoPath)
        assertTrue(PortfolioParser.parse(validJson).projects.flatMap { it.gallery }.none { it.shot.video }, "video is optional")
        listOf("\"cover\": ${withVideo("cover.webp")}", "\"screenshots\": [${withVideo("s.webp")}]").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject(bad)) }
        }
    }

    @Test
    fun projectIdsArePlainBecauseTheyAreInTheUrl() {
        listOf("Two", "two words", "two/three", "-two", "twö").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(validJson.replace("\"id\": \"two\"", "\"id\": \"$bad\"")) }
        }
        assertEquals("two-3", PortfolioParser.parse(validJson.replace("\"id\": \"two\"", "\"id\": \"two-3\"")).projects[1].id)
    }

    @Test
    fun taglineIsOptionalAndFitsTheCard() {
        assertNull(PortfolioParser.parse(validJson).projects[1].tagline)
        val max = "x".repeat(Project.MAX_TAGLINE)
        assertEquals(max, PortfolioParser.parse(withSecondProject("\"tagline\": \"$max\"")).projects[1].tagline)
        listOf("$max!", "  ").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"tagline\": \"$bad\"")) }
        }
    }

    @Test
    fun disclaimerIsOptionalAndShort() {
        assertNull(PortfolioParser.parse(validJson).projects[1].disclaimer)
        val max = words(Project.MAX_DISCLAIMER_WORDS)
        assertEquals(max, PortfolioParser.parse(withSecondProject("\"disclaimer\": \"$max\"")).projects[1].disclaimer)
        listOf("$max more", "  ").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject("\"disclaimer\": \"$bad\"")) }
        }
    }

    @Test
    fun apiIsOptionalHttpsWithoutATrailingSlash() {
        assertNull(PortfolioParser.parse(validJson).api)
        fun with(api: String) = validJson.replaceFirst("\"siteUrl\"", "\"api\": \"$api\", \"siteUrl\"")
        assertEquals("https://api.example", PortfolioParser.parse(with("https://api.example")).api)
        assertEquals("http://localhost:8787", PortfolioParser.parse(with("http://localhost:8787")).api, "a worker running locally")
        listOf("http://api.example", "https://api.example/", "api.example", "").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(with(bad)) }
        }
    }

    private fun words(count: Int) = List(count) { "word" }.joinToString(" ")

    @Test
    fun workTakesFiguresImpactAndOptimizationsWithinTheirCaps() {
        fun figure(value: String = "53%", label: String = "of the code") = "{ \"value\": \"$value\", \"label\": \"$label\" }"
        fun lines(vararg items: String) = items.joinToString { "\"$it\"" }
        val work = PortfolioParser.parse(
            withSecondProject(
                "\"company\": \"Acme\", \"role\": \"Android developer\", \"period\": \"2025\", \"figures\": [${figure()}], " +
                    "\"impact\": [${lines("Shipped it")}], \"optimizations\": [${lines("Faster start")}]",
            ),
        ).projects[1]
        assertTrue(work.isWork)
        assertEquals("Acme · Android developer · 2025", work.meta)
        assertEquals("My contributions", work.highlightsTitle)
        assertEquals(listOf("53%"), work.figures.map { it.value })
        val personal = PortfolioParser.parse(withSecondProject("\"period\": \"2025\"")).projects[1]
        assertEquals("2025" to "Highlights", personal.meta to personal.highlightsTitle)

        listOf(
            "\"company\": \" \"",
            // Only work takes them.
            "\"figures\": [${figure()}]",
            "\"impact\": [${lines("x")}]",
            "\"optimizations\": [${lines("x")}]",
            "\"company\": \"Acme\", \"figures\": [${List(Project.MAX_FIGURES + 1) { figure() }.joinToString()}]",
            "\"company\": \"Acme\", \"figures\": [${figure(value = "x".repeat(Project.MAX_FIGURE_VALUE + 1))}]",
            "\"company\": \"Acme\", \"figures\": [${figure(label = "x".repeat(Project.MAX_FIGURE_LABEL + 1))}]",
            "\"company\": \"Acme\", \"figures\": [${figure(value = " ")}]",
            "\"company\": \"Acme\", \"impact\": [${lines(*Array(Project.MAX_IMPACT + 1) { "x" })}]",
            "\"company\": \"Acme\", \"optimizations\": [${lines(*Array(Project.MAX_OPTIMIZATIONS + 1) { "x" })}]",
            "\"company\": \"Acme\", \"optimizations\": [${lines(words(Project.MAX_LINE_WORDS + 1))}]",
            "\"company\": \"Acme\", \"impact\": [${lines(" ")}]",
        ).forEach { bad -> assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withSecondProject(bad)) } }
    }

    private fun module(name: String, vararg dependsOn: String, role: String = "Does one thing") =
        "{ \"name\": \"$name\", \"role\": \"$role\", \"dependsOn\": [${dependsOn.joinToString { "\"$it\"" }}] }"

    private fun decision(detail: String = "Because it is simpler.") = "{ \"title\": \"A choice\", \"detail\": \"$detail\" }"

    private fun withEngineering(body: String) = withSecondProject("\"engineering\": { $body }")

    private val fullEngineering = """
        "builtWith": ["Kotlin Multiplatform", "Compose Multiplatform"],
        "platforms": ["Android", "iOS", "Web", "Desktop"],
        "architecture": "${words(Engineering.MAX_ARCHITECTURE_WORDS)}",
        "decisions": [${decision()}, ${decision(words(Engineering.MAX_DECISION_WORDS))}],
        "modules": [${module(":app", ":core:data")}, ${module(":core:data", ":core:model")}, ${module(":core:model")}],
        "quality": ["800 unit tests", "CI on GitHub Actions"]
    """.trimIndent()

    @Test
    fun engineeringParsesInTheDocumentedShapeAndIsOptional() {
        assertNull(PortfolioParser.parse(validJson).projects[1].engineering)
        val engineering = PortfolioParser.parse(withEngineering(fullEngineering)).projects[1].engineering!!
        assertEquals(listOf("Kotlin Multiplatform", "Compose Multiplatform"), engineering.builtWith)
        assertEquals(4, engineering.platforms.size)
        assertEquals(listOf(":app", ":core:data", ":core:model"), engineering.modules.map { it.name })
        assertEquals(listOf(":core:model"), engineering.modules[1].dependsOn)
        assertEquals(2, engineering.decisions.size)
        assertTrue(PortfolioParser.parse(withEngineering("")).projects[1].engineering!!.modules.isEmpty(), "every field is optional")
    }

    @Test
    fun engineeringListsKeepTheirCaps() {
        fun strings(n: Int) = List(n) { "\"item $it\"" }.joinToString()
        listOf(
            "\"builtWith\": [${strings(Engineering.MAX_BUILT_WITH + 1)}]",
            "\"platforms\": [${strings(Engineering.MAX_PLATFORMS + 1)}]",
            "\"quality\": [${strings(Engineering.MAX_QUALITY + 1)}]",
            "\"builtWith\": [\"Kotlin\", \" \"]",
            "\"platforms\": [\"Android\", \"android\"]",
        ).forEach { bad -> assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withEngineering(bad)) } }
        PortfolioParser.parse(withEngineering("\"builtWith\": [${strings(Engineering.MAX_BUILT_WITH)}]"))
    }

    @Test
    fun architectureAndDecisionsStayShort() {
        listOf(
            "\"architecture\": \"${words(Engineering.MAX_ARCHITECTURE_WORDS + 1)}\"",
            "\"architecture\": \" \"",
            "\"decisions\": [${decision()}]",
            "\"decisions\": [${List(Engineering.MAX_DECISIONS + 1) { decision() }.joinToString()}]",
            "\"decisions\": [${decision()}, ${decision(words(Engineering.MAX_DECISION_WORDS + 1))}]",
            "\"decisions\": [${decision()}, { \"title\": \" \", \"detail\": \"d\" }]",
        ).forEach { bad -> assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(withEngineering(bad)) } }
    }

    @Test
    fun modulesMustFormAnAcyclicGraphOfListedNames() {
        fun modules(vararg items: String) = withEngineering("\"modules\": [${items.joinToString()}]")
        val cycle = assertFailsWith<IllegalArgumentException> {
            PortfolioParser.parse(modules(module("a", "b"), module("b", "c"), module("c", "a")))
        }
        assertTrue("a → b → c → a" in cycle.message.orEmpty(), cycle.message)
        val unknown = assertFailsWith<IllegalArgumentException> { PortfolioParser.parse(modules(module("a", "ghost"))) }
        assertTrue("ghost" in unknown.message.orEmpty())
        listOf(
            modules(module("a", "a")),
            modules(module("a"), module("a")),
            modules(module("a", role = words(Engineering.MAX_MODULE_ROLE_WORDS + 1))),
            modules(module("a", role = " ")),
            modules(*Array(Engineering.MAX_MODULES + 1) { module("m$it") }),
        ).forEach { bad -> assertFailsWith<IllegalArgumentException>(bad) { PortfolioParser.parse(bad) } }
        PortfolioParser.parse(modules(*Array(Engineering.MAX_MODULES) { module("m$it", *(if (it > 0) arrayOf("m${it - 1}") else emptyArray())) }))
    }
}
