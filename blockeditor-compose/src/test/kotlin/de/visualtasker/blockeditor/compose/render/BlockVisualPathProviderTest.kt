package de.visualtasker.blockeditor.compose.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import de.visualtasker.blockeditor.compose.shapes.BlockDockKind
import de.visualtasker.blockeditor.compose.shapes.BlockShapeFamily
import de.visualtasker.blockeditor.compose.shapes.BlockShapeRequest
import de.visualtasker.blockeditor.compose.shapes.BlockShapeTokens
import de.visualtasker.blockeditor.compose.shapes.MaterialExpressiveBlockShapeBuilder
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
        val provider = BlockVisualPathProvider { BlockVisualPathResult.LegacyPath(shared) }

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
            BlockVisualPathProvider { BlockVisualPathResult.LegacyPath(supplied) },
        )

        assertNotSame(supplied, actual)
        assertEquals(supplied.getBounds(), actual.getBounds())

        supplied.reset()

        assertFalse(actual.isEmpty)
    }

    @Test
    fun `provider can override every visual block shape`() {
        var calls = 0
        val provider = BlockVisualPathProvider {
            calls += 1
            BlockVisualPathResult.LegacyPath(trianglePath())
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
        val inlineStatementDefinition = statementDefinition(inputsInline = true)
        val inlineStatement = resolveBlockVisualPath(
            inlineStatementDefinition,
            Size(120f, 44f),
            emptyList(),
            provider,
        )

        assertEquals(3, calls)
        assertEquals(trianglePath().getBounds(), statement.getBounds())
        assertEquals(trianglePath().getBounds(), container.getBounds())
        assertEquals(trianglePath().getBounds(), inlineStatement.getBounds())
    }

    @Test
    fun `empty path and provider failure use legacy fallback`() {
        val definition = statementDefinition()
        val empty = resolveBlockVisualPath(
            definition.copy(isReporter = true),
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { BlockVisualPathResult.LegacyPath(Path()) },
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
            BlockPathCache.shape(statementDefinition(inputsInline = true)) ==
                BlockVisualShape.Statement,
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
        assertTrue(
            BlockPathCache.shape(statementDefinition(id = "em_on_start", hasPrevious = false)) ==
                BlockVisualShape.Statement,
        )
    }

    @Test
    fun `block text color is selected by fill contrast`() {
        assertEquals(Color(0xFF111827), contrastTextColor(Color(0xFFFFC107)))
        assertEquals(Color(0xFFF8FAFC), contrastTextColor(Color(0xFF263238)))
    }

    @Test
    fun `provider definition mutations cannot reach registry definition`() {
        val mutableStatementInputs = mutableListOf(
            de.visualtasker.blockeditor.registry.StatementInputDefinition("DO", "do"),
        )
        val definition = statementDefinition(isReporter = true).copy(
            statementInputs = mutableStatementInputs,
        )
        var received: BlockDefinition? = null

        resolveBlockVisualPath(
            definition,
            Size(120f, 44f),
            emptyList(),
            BlockVisualPathProvider { request ->
                received = request.definition
                BlockVisualPathResult.Unsupported
            },
        )
        mutableStatementInputs.clear()

        assertNotSame(definition, received)
        assertEquals(1, received?.statementInputs?.size)
    }

    @Test
    fun `material expressive geometry has positive finite bounds and path`() {
        val geometry = MaterialExpressiveBlockShapeBuilder.geometry(
            request = BlockShapeRequest(
                blockType = "test.statement",
                size = Size(288f, 60f),
                family = BlockShapeFamily.Statement,
            ),
            tokens = BlockShapeTokens().toPx(Density(1f)),
        )

        assertFalse(geometry.path.isEmpty)
        assertTrue(geometry.visualBounds.width > 0f)
        assertTrue(geometry.visualBounds.height > 0f)
        assertTrue(geometry.contentBounds.left >= geometry.visualBounds.left)
        assertTrue(geometry.contentBounds.right <= geometry.visualBounds.right)
        assertTrue(geometry.interactionBounds.width > 0f)
        assertTrue(geometry.interactionBounds.height > 0f)
    }

    @Test
    fun `material expressive stack docks share a stable x axis`() {
        val geometry = MaterialExpressiveBlockShapeBuilder.geometry(
            request = BlockShapeRequest(
                blockType = "test.statement",
                size = Size(288f, 60f),
                family = BlockShapeFamily.Statement,
            ),
            tokens = BlockShapeTokens().toPx(Density(1f)),
        )
        val previous = geometry.docks.single { it.kind == BlockDockKind.PreviousStack }
        val next = geometry.docks.single { it.kind == BlockDockKind.NextStack }

        assertEquals(64f, previous.center.x)
        assertEquals(previous.center.x, next.center.x)
        assertTrue(previous.bounds.width > 0f)
        assertTrue(next.bounds.width > 0f)
    }

    @Test
    fun `material expressive event has no previous dock`() {
        val geometry = MaterialExpressiveBlockShapeBuilder.geometry(
            request = BlockShapeRequest(
                blockType = "event.start",
                size = Size(288f, 64f),
                family = BlockShapeFamily.Event,
            ),
            tokens = BlockShapeTokens().toPx(Density(1f)),
        )

        assertTrue(geometry.docks.none { it.kind == BlockDockKind.PreviousStack })
        assertTrue(geometry.docks.any { it.kind == BlockDockKind.NextStack })
    }

    @Test
    fun `material expressive provider returns geometry with defensively copied path`() {
        val result = MaterialExpressiveBlockVisualPathProvider.path(
            BlockVisualPathRequest(
                definition = statementDefinition(),
                shape = BlockVisualShape.Statement,
                targetSize = Size(288f, 60f),
            ),
        ) as BlockVisualPathResult.Geometry

        assertFalse(result.geometry.path.isEmpty)
        assertTrue(result.geometry.docks.isNotEmpty())
    }

    @Test
    fun `public compose entry points retain legacy jvm overloads`() {
        assertLegacyAndProviderOverloads(
            "de.visualtasker.blockeditor.compose.host.BlockEditorHostKt",
            "BlockEditorHost",
        )
        assertLegacyAndProviderOverloads(
            "de.visualtasker.blockeditor.compose.ui.BlockEditorScaffoldKt",
            "BlockEditorScaffold",
        )
        assertLegacyAndProviderOverloads(
            "de.visualtasker.blockeditor.compose.layers.EditorCanvasLayerKt",
            "EditorCanvasLayer",
        )
    }

    private fun assertLegacyAndProviderOverloads(className: String, methodName: String) {
        val overloads = Class.forName(className).declaredMethods.filter { it.name == methodName }

        assertTrue(overloads.any { BlockVisualPathProvider::class.java !in it.parameterTypes })
        assertTrue(overloads.any { BlockVisualPathProvider::class.java in it.parameterTypes })
    }

    private fun statementDefinition(
        id: String = "test",
        hasPrevious: Boolean = true,
        isReporter: Boolean = false,
        inputsInline: Boolean = false,
    ) = BlockDefinition(
        id = id,
        label = "Test",
        category = "test",
        hasPrevious = hasPrevious,
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
