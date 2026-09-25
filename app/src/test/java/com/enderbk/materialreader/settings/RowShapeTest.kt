package com.enderbk.materialreader.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RowShapeTest {

    @Test
    fun aloneIsUniformOuterRadius() {
        assertEquals(RoundedCornerShape(24.dp), rowShape(RowPosition.ALONE))
    }

    @Test
    fun topHasOuterTopAndInnerBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = 24.dp, topEnd = 24.dp,
                bottomStart = 8.dp, bottomEnd = 8.dp
            ),
            rowShape(RowPosition.TOP)
        )
    }

    @Test
    fun middleIsUniformInnerRadius() {
        assertEquals(RoundedCornerShape(8.dp), rowShape(RowPosition.MIDDLE))
    }

    @Test
    fun bottomHasInnerTopAndOuterBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = 8.dp, topEnd = 8.dp,
                bottomStart = 24.dp, bottomEnd = 24.dp
            ),
            rowShape(RowPosition.BOTTOM)
        )
    }

    @Test
    fun pressedOuterRadiusMorphsShape() {
        assertEquals(
            RoundedCornerShape(48.dp),
            rowShape(RowPosition.ALONE, outer = 48.dp)
        )
    }

    @Test
    fun rowPositionsFollowGroupIndex() {
        assertEquals(RowPosition.ALONE, rowPositionFor(0, 1))
        assertEquals(RowPosition.ALONE, rowPositionFor(0, 0))
        assertEquals(RowPosition.TOP, rowPositionFor(0, 3))
        assertEquals(RowPosition.MIDDLE, rowPositionFor(1, 3))
        assertEquals(RowPosition.BOTTOM, rowPositionFor(2, 3))
        assertEquals(RowPosition.BOTTOM, rowPositionFor(1, 2))
    }
}
