package dev.yowkey.portfolio.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class BrandTest {

    @Test
    fun initialsAreTheFirstAndLastWordsLetters() {
        assertEquals("RT", initialsOf("Rodelito Tangonan"))
        assertEquals("MW", initialsOf("Mary Jane Watson"), "a middle name is skipped")
        assertEquals("AL", initialsOf("  ada   lovelace "), "spacing and case do not matter")
        assertEquals("JP", initialsOf("Jean-Luc Picard"), "a hyphen joins words of one name")
        assertEquals("OC", initialsOf("O'Neil Cruz"), "punctuation is skipped for the first letter")
    }

    @Test
    fun oneWordIsOneLetterAndNothingIsNothing() {
        assertEquals("C", initialsOf("Cher"))
        assertEquals("", initialsOf(""))
        assertEquals("", initialsOf("  · "))
    }

    @Test
    fun theRoleIsTheHeadlineBeforeTheSkills() {
        assertEquals(
            "Android Developer at DigiPlus since 2024",
            roleOf("Android Developer at DigiPlus since 2024 · Kotlin, Jetpack Compose, Clean Architecture"),
        )
        assertEquals("Engineer", roleOf("Engineer·Kotlin"), "the separator needs no spaces around it")
        assertEquals("Android Developer", roleOf("  Android Developer  "), "no separator: all of it is the role")
        assertEquals("", roleOf(""))
    }

    @Test
    fun shorterFormsCutAtWholeWordsThatCanEndAPhrase() {
        assertEquals(
            listOf("Open to relocation to any Country", "Open to relocation"),
            shorterForms("Open to relocation to any Country"),
        )
        assertEquals(
            listOf("Open to work · Remote or hybrid", "Open to work · Remote", "Open to work"),
            shorterForms("Open to work · Remote or hybrid"),
            "a stray separator is not a word, and is trimmed off the end",
        )
        assertEquals(
            listOf("Android Developer at DigiPlus since 2024", "Android Developer at DigiPlus", "Android Developer"),
            shorterForms("Android Developer at DigiPlus since 2024"),
            "the bar's role: never left hanging on \"since\"",
        )
    }

    @Test
    fun shorterFormsNeverGoUnderTwoWords() {
        assertEquals(listOf("Available now"), shorterForms("Available now"))
        assertEquals(listOf("Hiring"), shorterForms("  Hiring "))
    }
}
