package de.visualtasker.blockeditor.compose.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import de.visualtasker.blockeditor.compose.theme.defaultBlockEditorColors
import de.visualtasker.blockeditor.interaction.SnapCandidate
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.layout.LayoutConstants

@Composable
fun SnapPreviewLayer(
    layoutCache: LayoutCache,
    snapCandidate: SnapCandidate?,
    modifier: Modifier = Modifier,
) {
    val candidate = snapCandidate ?: return
    val target = layoutCache.flatIndex.connectionAnchors
        .find { it.connectionId == candidate.targetConnectionId }
        ?: return
    val colors = defaultBlockEditorColors()

    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = LayoutConstants.ANCHOR_RADIUS * 2.5f
        drawCircle(color = colors.snapHighlight, radius = radius, center = Offset(target.x, target.y))
        drawCircle(
            color = Color(0xFF1565C0),
            radius = LayoutConstants.ANCHOR_RADIUS,
            center = Offset(target.x, target.y),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
    }
}
