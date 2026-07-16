package de.visualtasker.blockeditor.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibleWrappingRowTest {
    @Test
    fun `items wrap without changing their order`() {
        val positions = wrappingPositions(
            sizes = listOf(40 to 10, 50 to 20, 30 to 15),
            availableWidth = 100,
            horizontalSpacing = 8,
            verticalSpacing = 6,
        )

        assertEquals(
            listOf(
                WrappingPosition(index = 0, x = 0, y = 0),
                WrappingPosition(index = 1, x = 48, y = 0),
                WrappingPosition(index = 2, x = 0, y = 26),
            ),
            positions,
        )
    }

    @Test
    fun `oversized first item does not create an empty row`() {
        val positions = wrappingPositions(
            sizes = listOf(120 to 10, 20 to 10),
            availableWidth = 100,
            horizontalSpacing = 4,
            verticalSpacing = 5,
        )

        assertEquals(
            listOf(
                WrappingPosition(index = 0, x = 0, y = 0),
                WrappingPosition(index = 1, x = 0, y = 15),
            ),
            positions,
        )
    }
}
