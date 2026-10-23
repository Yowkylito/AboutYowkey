package dev.yowkey.portfolio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the card shows when a project has, or lacks, the optional card fields. */
class ProjectCardTextTest {

    private val longSummary = "An offline-first AI travel companion for Android: plan trips, translate through the camera, " +
        "and keep travel memories, with or without a connection."

    @Test
    fun cardLineIsTheTaglineOrTheSummaryCutAtAWord() {
        val project = Project("p", "P", longSummary)
        val line = project.cardLine
        assertTrue(line.length <= Project.MAX_TAGLINE, "\"$line\" is ${line.length} characters")
        assertTrue(line.endsWith("…"))
        assertTrue(longSummary.startsWith(line.removeSuffix("…")), "cut, not rewritten: $line")
        assertTrue(line.removeSuffix("…").last().isLetterOrDigit(), "no dangling comma or colon: $line")
        assertEquals("Short and sweet", project.copy(tagline = "Short and sweet").cardLine)
        assertEquals("Fits.", Project("p", "P", "  Fits.  ").cardLine)
    }

    @Test
    fun truncationNeverExceedsTheLimit() {
        listOf("a".repeat(200), "word ".repeat(40), "x y", "supercalifragilistic expialidocious").forEach { text ->
            for (max in 5..40) assertTrue(truncateAtWord(text, max).length <= max, "\"$text\" at $max")
        }
        assertEquals("aaaa…", truncateAtWord("a".repeat(20), 5), "one long word is cut mid-word")
    }

    @Test
    fun headlineTechIsBuiltWithOrTheFirstOfTheStack() {
        val stack = listOf("Kotlin", "Compose", "Room", "Koin", "WorkManager", "CameraX")
        val project = Project("p", "P", "S", stack = stack)
        assertEquals(stack.take(Engineering.MAX_BUILT_WITH), project.headlineTech)
        assertEquals(stack.take(4), project.copy(engineering = Engineering(platforms = listOf("Android"))).headlineTech)
        assertEquals(listOf("KMP"), project.copy(engineering = Engineering(builtWith = listOf("KMP"))).headlineTech)
    }

    @Test
    fun leadMediaPrefersTheCoverClipThenTheCoverThenTheFirstItem() {
        val shot = { name: String -> Screenshot("$name.webp", name, 9, 16) }
        val bare = Project("p", "P", "S")
        assertNull(bare.leadMedia)
        assertEquals("s1.webp", bare.copy(screenshots = listOf(shot("s1"), shot("s2"))).leadMedia?.shot?.file)
        assertEquals("c1.webp", bare.copy(clips = listOf(shot("c1")), screenshots = listOf(shot("s1"))).leadMedia?.shot?.file)
        val covered = bare.copy(cover = shot("cover"), clips = listOf(shot("c1")))
        assertEquals("cover.webp", covered.leadMedia?.shot?.file)
        assertEquals("intro.webp", covered.copy(coverClip = shot("intro")).leadMedia?.shot?.file)
        assertEquals("project-p", bare.anchor)
    }

    @Test
    fun platformNamesMapToTheirGlyph() {
        mapOf(
            "Android" to PlatformKind.Android, "Wear OS" to PlatformKind.Android, "iOS" to PlatformKind.Ios,
            "Web" to PlatformKind.Web, "Web (Kotlin/Wasm)" to PlatformKind.Web, "Desktop" to PlatformKind.Desktop,
            "macOS" to PlatformKind.Desktop, "Windows" to PlatformKind.Desktop, "Server" to PlatformKind.Server,
            "Smart fridge" to PlatformKind.Other,
        ).forEach { (name, kind) -> assertEquals(kind, PlatformKind.of(name), name) }
    }

    @Test
    fun wordsAreRunsOfNonSpace() {
        assertEquals(0, wordCount("   "))
        assertEquals(3, wordCount(" one  two\nthree "))
        assertEquals(2, wordCount("Kotlin/Wasm, Compose"))
    }
}
