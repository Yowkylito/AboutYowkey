package dev.yowkey.portfolio.ui.sections

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingMeasureTest {

    private val plain = Density(density = 1f, fontScale = 1f)

    @Test
    fun bodyTextKeepsToFortyEms() {
        assertEquals(600.dp, readingMeasure(15.sp, plain), "bodyMedium")
        assertEquals(480.dp, readingMeasure(12.sp, plain), "bodySmall")
    }

    @Test
    fun largerTextStopsAtTheMaximum() {
        assertEquals(680.dp, MaxMeasure)
        assertEquals(MaxMeasure, readingMeasure(18.sp, plain), "bodyLarge would be 720dp")
        assertEquals(MaxMeasure, readingMeasure(24.sp, plain), "headlineSmall would be 960dp")
    }

    @Test
    fun theScreenDensityDoesNotChangeIt() {
        assertEquals(readingMeasure(15.sp, plain), readingMeasure(15.sp, Density(density = 2.625f, fontScale = 1f)))
    }

    @Test
    fun aLargerTextScaleWidensTheLineUpToTheMaximum() {
        val scaled = readingMeasure(15.sp, Density(density = 1f, fontScale = 1.1f))
        assertTrue(scaled > readingMeasure(15.sp, plain) && scaled <= MaxMeasure, "was $scaled")
        assertEquals(MaxMeasure, readingMeasure(15.sp, Density(density = 1f, fontScale = 2f)))
    }

    @Test
    fun aSizeItCannotResolveGetsTheMaximum() {
        assertEquals(MaxMeasure, readingMeasure(TextUnit.Unspecified, plain))
        assertEquals(MaxMeasure, readingMeasure(1.2.em, plain))
    }
}
