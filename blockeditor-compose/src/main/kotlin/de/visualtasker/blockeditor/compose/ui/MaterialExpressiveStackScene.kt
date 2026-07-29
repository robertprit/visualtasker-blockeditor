package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.visualtasker.blockeditor.compose.layers.EditorCanvasLayer
import de.visualtasker.blockeditor.compose.render.MaterialExpressiveBlockVisualPathProvider
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.createNode

@Composable
fun MaterialExpressiveLinearStackScene(modifier: Modifier = Modifier) {
    val registry = remember { materialExpressiveSceneRegistry() }
    val document = remember { materialExpressiveSceneDocument(registry) }
    val layout = remember(document, registry) { LayoutEngine(registry).build(document) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08070D)),
    ) {
        EditorCanvasLayer(
            document = document,
            layoutCache = layout,
            viewport = ViewportState(scale = 1f, panX = 24f, panY = 24f),
            dragRender = null,
            selectedBlockIds = emptySet(),
            registry = registry,
            gridVisible = false,
            visualPathProvider = MaterialExpressiveBlockVisualPathProvider,
        )
    }
}

private fun materialExpressiveSceneRegistry(): CompositeBlockRegistry =
    CompositeBlockRegistry(DefaultBlockRegistry).apply {
        register(
            BlockDefinition(
                id = SceneScanElementTree,
                label = "Scan Element Tree",
                category = "perception",
                hasPrevious = true,
                hasNext = true,
            ),
        )
    }

private fun materialExpressiveSceneDocument(registry: CompositeBlockRegistry): WorkspaceDocument {
    val startId = BlockId("scene-start")
    val scanId = BlockId("scene-scan")
    val clickId = BlockId("scene-click")
    val startNext = ConnectionId("scene-start:next")
    val scanPrevious = ConnectionId("scene-scan:previous")
    val scanNext = ConnectionId("scene-scan:next")
    val clickPrevious = ConnectionId("scene-click:previous")
    val start = registry.getDefinition(BlockTypes.EVENT_START)!!
        .createNode(startId)
        .withRootOffset(48f, 64f)
        .copy(next = Connection(startNext, startId, ConnectionKind.Next, connectedTo = scanPrevious))
    val scan = registry.getDefinition(SceneScanElementTree)!!
        .createNode(scanId)
        .copy(
            previous = Connection(scanPrevious, scanId, ConnectionKind.Previous, connectedTo = startNext),
            next = Connection(scanNext, scanId, ConnectionKind.Next, connectedTo = clickPrevious),
        )
    val click = registry.getDefinition(BlockTypes.ACTION_CLICK_TEXT)!!
        .createNode(clickId)
        .copy(
            fields = mapOf("text" to FieldValue.Text("Login")),
            previous = Connection(clickPrevious, clickId, ConnectionKind.Previous, connectedTo = scanNext),
        )
    return WorkspaceDocument(
        id = "material-expressive-linear-stack-scene",
        blocks = mapOf(
            startId to start,
            scanId to scan,
            clickId to click,
        ),
        rootBlocks = listOf(startId),
    )
}

private const val SceneScanElementTree = "preview.scanElementTree"
