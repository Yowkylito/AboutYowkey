package dev.yowkey.portfolio.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeRevealTest {

    @Test
    fun theCircleGrowsToTheFarthestCorner() {
        // From near the top-right switch the farthest corner is the bottom-left one: hypot(1300, 900).
        assertEquals(1581.14f, coverRadius(Offset(1300f, 32f), Size(1340f, 932f)), 0.01f)
        assertEquals(500f, coverRadius(Offset(0f, 0f), Size(300f, 400f)), "from a corner it is the diagonal")
    }

    @Test
    fun fromTheMiddleEveryCornerIsAsFar() {
        assertEquals(250f, coverRadius(Offset(150f, 200f), Size(300f, 400f)))
    }
}
