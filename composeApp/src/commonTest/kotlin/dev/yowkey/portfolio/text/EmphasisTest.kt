package dev.yowkey.portfolio.text

import kotlin.test.Test
import kotlin.test.assertEquals

class EmphasisTest {

    private fun marked(text: String) = Emphasis.spans(text).map { text.substring(it.start, it.end) to it.kind }

    @Test
    fun winsAreStrong() {
        val text = "wrote 53% of its code (+270K lines), 2.4x the team average, #1 of 40, Rank #2, Top 9 Awardee"
        assertEquals(
            listOf(
                "53%" to EmphasisKind.Strong,
                "+270K" to EmphasisKind.Strong,
                "2.4x" to EmphasisKind.Strong,
                "#1" to EmphasisKind.Strong,
                "40" to EmphasisKind.Figure,
                "Rank #2" to EmphasisKind.Strong,
                "Top 9" to EmphasisKind.Strong,
            ),
            marked(text),
        )
    }

    @Test
    fun plainCountsAreFigures() {
        assertEquals(
            listOf("12", "16", "450", "366", "72.8", "13").map { it to EmphasisKind.Figure },
            marked("12 feature areas, 16 screens, about 450 composables in 366 Kotlin files; 72.8 points; 13 P0 tests"),
        )
    }

    @Test
    fun yearsVersionsAndIdentifiersAreNotFacts() {
        val text = "In August 2026 ArenaPlus V2 moved to :v2 modules, Adjust SDK 5.8, ESP8266, P0, a 3D town and 2020 – 2024."
        assertEquals(emptyList(), marked(text))
    }

    @Test
    fun segmentsRebuildTheText() {
        val text = "53% of 413 commits in 2026."
        val segments = Emphasis.segments(text)
        assertEquals(text, segments.joinToString("") { it.first })
        assertEquals(listOf(EmphasisKind.Strong, EmphasisKind.Figure), segments.mapNotNull { it.second })
    }

    @Test
    fun textWithoutNumbersIsOneSegment() {
        assertEquals(listOf("Ships Compose apps." to null), Emphasis.segments("Ships Compose apps."))
        assertEquals(emptyList(), Emphasis.segments(""))
    }
}
