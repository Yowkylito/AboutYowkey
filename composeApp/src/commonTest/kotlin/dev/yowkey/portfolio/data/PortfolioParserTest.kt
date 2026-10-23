package dev.yowkey.portfolio.data

import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.validJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
