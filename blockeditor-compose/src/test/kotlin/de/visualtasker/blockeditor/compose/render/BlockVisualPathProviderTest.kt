package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.registry.BlockDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockVisualPathProviderTest {
    @Test
    fun `legacy provider preserves legacy path`() {
        val definition = statementDefinition(isReporter = true)
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
    fun `provider paths do not leak between render passes`() {
        val shared = trianglePath()
        val provider = BlockVisualPathProvider { BlockVisualPathResult.Success(shared) }

        val first = resolveBlockVisualPath(
            statementDefinition(isReporter = true),
            Size(120f, 44f),
            emptyList(),
            provider,
        )
        val second = resolveBlockVisualPath(
            statementDefinition(isReporter = true),
            Size(120f, 44f),
            emptyList(),
            provider,
        )

        assertNotSame(shared, first)
        assertNotSame(shared, second)
        assertNotSame(first, second)
        first.reset()
        assertFalse(second.isEmpty)
    }

    @Test
    fun `successful reporter path is defensively copied`() {
        val supplied = Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 0f)
            lineTo(10f, 10f)
            close()
        }
        val actual = resolveBlockVisualPath(
            statementDefinition(isReporter = true),
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { BlockVisualPathResult.Success(supplied) },
        )

        assertNotSame(supplied, actual)
        assertEquals(supplied.getBounds(), actual.getBounds())

        supplied.reset()

        assertFalse(actual.isEmpty)
    }

    @Test
    fun `provider is not consulted for legacy-rendered shapes`() {
        var calls = 0
        val provider = BlockVisualPathProvider {
            calls += 1
            BlockVisualPathResult.Success(Path())
        }

        val statement = resolveBlockVisualPath(
            statementDefinition(),
            Size(120f, 44f),
            emptyList(),
            provider,
        )
        val containerDefinition = statementDefinition().copy(
            statementInputs = listOf(
                de.visualtasker.blockeditor.registry.StatementInputDefinition("DO", "do"),
            ),
        )
        val container = resolveBlockVisualPath(
            containerDefinition,
            Size(120f, 88f),
            emptyList(),
            provider,
        )

        assertEquals(0, calls)
        assertSame(BlockPathCache.path(statementDefinition(), Size(120f, 44f)), statement)
        assertSame(BlockPathCache.path(containerDefinition, Size(120f, 88f)), container)
    }

    @Test
    fun `empty path and provider failure use legacy fallback`() {
        val definition = statementDefinition()
        val empty = resolveBlockVisualPath(
            definition.copy(isReporter = true),
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { BlockVisualPathResult.Success(Path()) },
        )
        val failed = resolveBlockVisualPath(
            definition.copy(isReporter = true),
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

    private fun trianglePath() = Path().apply {
        moveTo(0f, 0f)
        lineTo(10f, 0f)
        lineTo(10f, 10f)
        close()
    }
}
