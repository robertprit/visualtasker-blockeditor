package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.registry.BlockDefinition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockVisualPathProviderTest {
    @Test
    fun `legacy provider preserves legacy path`() {
        val definition = statementDefinition()
        val expected = BlockPathCache.path(definition, Size(120f, 44f))

        val actual = resolveBlockVisualPath(
            definition,
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider.Legacy,
        )

        assertSame(expected, actual)
    }

    @Test
    fun `successful provider path is presentation only`() {
        val supplied = Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 10f)
            close()
        }
        val actual = resolveBlockVisualPath(
            statementDefinition(),
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { BlockVisualPathResult.Success(supplied) },
        )

        assertSame(supplied, actual)
    }

    @Test
    fun `empty path and provider failure use legacy fallback`() {
        val definition = statementDefinition()
        val empty = resolveBlockVisualPath(
            definition,
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { BlockVisualPathResult.Success(Path()) },
        )
        val failed = resolveBlockVisualPath(
            definition,
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { error("presentation failure") },
        )

        assertFalse(empty.isEmpty)
        assertFalse(failed.isEmpty)
    }

    @Test
    fun `shape inference is deterministic and containers stay distinct`() {
        assertTrue(BlockPathCache.shape(statementDefinition()) == BlockVisualShape.Statement)
        assertTrue(
            BlockPathCache.shape(statementDefinition(isReporter = true)) == BlockVisualShape.Reporter,
        )
        assertTrue(
            BlockPathCache.shape(statementDefinition(isReporter = true, inputsInline = true)) ==
                BlockVisualShape.InlineReporter,
        )
        assertTrue(
            BlockPathCache.shape(
                statementDefinition().copy(
                    statementInputs = listOf(
                        de.visualtasker.blockeditor.registry.StatementInputDefinition("DO", "do"),
                    ),
                ),
            ) == BlockVisualShape.Container,
        )
    }

    private fun statementDefinition(
        isReporter: Boolean = false,
        inputsInline: Boolean = false,
    ) = BlockDefinition(
        id = "test",
        label = "Test",
        category = "test",
        hasPrevious = true,
        hasNext = true,
        isReporter = isReporter,
        inputsInline = inputsInline,
    )
}
