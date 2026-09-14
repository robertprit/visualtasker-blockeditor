package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.layout.LayoutConstants

object BlockShapes {
    private const val CORNER = 12f
    private const val REPORTER_RADIUS = 13f
    private const val INLINE_REPORTER_RADIUS = 10f
    private const val NOTCH_WIDTH = 24f
    private const val NOTCH_DEPTH = 7f
    private const val TAB_WIDTH = 24f
    private const val TAB_DEPTH = 7f
    private const val BRANCH_SHELF = LayoutConstants.BRANCH_SHELF

    fun statementPath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height
        val corner = CORNER.coerceAtMost(minOf(w, h) / 2f)
        val notchCenter = LayoutConstants.NESTED_INDENT

        moveTo(corner, 0f)
        
        lineTo(notchCenter - NOTCH_WIDTH / 2, 0f)
        cubicTo(
            notchCenter - NOTCH_WIDTH / 4, 0f,
            notchCenter - NOTCH_WIDTH / 4, NOTCH_DEPTH,
            notchCenter, NOTCH_DEPTH
        )
        cubicTo(
            notchCenter + NOTCH_WIDTH / 4, NOTCH_DEPTH,
            notchCenter + NOTCH_WIDTH / 4, 0f,
            notchCenter + NOTCH_WIDTH / 2, 0f
        )
        
        lineTo(w - corner, 0f)
        arcTo(Rect(w - corner * 2, 0f, w, corner * 2), 270f, 90f, false)
        lineTo(w, h - corner)
        arcTo(Rect(w - corner * 2, h - corner * 2, w, h), 0f, 90f, false)
        
        val tabCenter = w / 2f
        lineTo(tabCenter + TAB_WIDTH / 2, h)
        
        cubicTo(
            tabCenter + TAB_WIDTH / 4, h,
            tabCenter + TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter, h - TAB_DEPTH
        )
        cubicTo(
            tabCenter - TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter - TAB_WIDTH / 4, h,
            tabCenter - TAB_WIDTH / 2, h
        )
        
        lineTo(corner, h)
        arcTo(Rect(0f, h - corner * 2, corner * 2, h), 90f, 90f, false)
        lineTo(0f, corner)
        arcTo(Rect(0f, 0f, corner * 2, corner * 2), 180f, 90f, false)
        close()
    }

    fun startStatementPath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height

        moveTo(CORNER, 0f)
        lineTo(w - CORNER, 0f)
        arcTo(Rect(w - CORNER * 2, 0f, w, CORNER * 2), 270f, 90f, false)
        lineTo(w, h - CORNER)
        arcTo(Rect(w - CORNER * 2, h - CORNER * 2, w, h), 0f, 90f, false)

        val tabCenter = w / 2f
        lineTo(tabCenter + TAB_WIDTH / 2, h)

        cubicTo(
            tabCenter + TAB_WIDTH / 4, h,
            tabCenter + TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter, h - TAB_DEPTH
        )
        cubicTo(
            tabCenter - TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter - TAB_WIDTH / 4, h,
            tabCenter - TAB_WIDTH / 2, h
        )

        lineTo(CORNER, h)
        arcTo(Rect(0f, h - CORNER * 2, CORNER * 2, h), 90f, 90f, false)
        lineTo(0f, CORNER)
        arcTo(Rect(0f, 0f, CORNER * 2, CORNER * 2), 180f, 90f, false)
        close()
    }

    fun containerPath(
        size: Size,
        headerHeight: Float,
        footerHeight: Float,
        branchDividers: List<Float> = emptyList(),
    ): Path = Path().apply {
        val w = size.width
        val h = size.height
        val corner = CORNER.coerceAtMost(minOf(w, h) / 2f)
        val bodyBottom = h - footerHeight
        val notchCenter = LayoutConstants.NESTED_INDENT
        val sortedDividers = branchDividers
            .filter { it in headerHeight..bodyBottom }
            .sorted()

        moveTo(corner, 0f)
        
        lineTo(notchCenter - NOTCH_WIDTH / 2, 0f)
        cubicTo(
            notchCenter - NOTCH_WIDTH / 4, 0f,
            notchCenter - NOTCH_WIDTH / 4, NOTCH_DEPTH,
            notchCenter, NOTCH_DEPTH
        )
        cubicTo(
            notchCenter + NOTCH_WIDTH / 4, NOTCH_DEPTH,
            notchCenter + NOTCH_WIDTH / 4, 0f,
            notchCenter + NOTCH_WIDTH / 2, 0f
        )
        
        lineTo(w - corner, 0f)
        arcTo(Rect(w - corner * 2, 0f, w, corner * 2), 270f, 90f, false)
        lineTo(w, headerHeight - corner)
        arcTo(Rect(w - corner * 2, headerHeight - corner * 2, w, headerHeight), 0f, 90f, false)
        lineTo(LayoutConstants.NESTED_INDENT + corner, headerHeight)
        arcTo(
            Rect(
                LayoutConstants.NESTED_INDENT,
                headerHeight,
                LayoutConstants.NESTED_INDENT + corner * 2,
                headerHeight + corner * 2,
            ),
            270f,
            -90f,
            false,
        )

        sortedDividers.forEach { dividerY ->
            lineTo(LayoutConstants.NESTED_INDENT, dividerY - corner)
            arcTo(
                Rect(
                    LayoutConstants.NESTED_INDENT,
                    dividerY - corner * 2,
                    LayoutConstants.NESTED_INDENT + corner * 2,
                    dividerY,
                ),
                180f,
                -90f,
                false,
            )
            
            // Korrektur: Perfekter 180-Grad-Bogen für die rechte Zweigspitze
            val shelfRadius = BRANCH_SHELF / 2f
            lineTo(w - shelfRadius, dividerY)
            arcTo(
                Rect(w - BRANCH_SHELF, dividerY, w, dividerY + BRANCH_SHELF),
                270f,
                180f,
                false
            )
            
            lineTo(LayoutConstants.NESTED_INDENT + corner, dividerY + BRANCH_SHELF)
            arcTo(
                Rect(
                    LayoutConstants.NESTED_INDENT,
                    dividerY + BRANCH_SHELF,
                    LayoutConstants.NESTED_INDENT + corner * 2,
                    dividerY + BRANCH_SHELF + corner * 2,
                ),
                270f,
                -90f,
                false,
            )
        }

        lineTo(LayoutConstants.NESTED_INDENT, bodyBottom - corner)
        arcTo(
            Rect(
                LayoutConstants.NESTED_INDENT,
                bodyBottom - corner * 2,
                LayoutConstants.NESTED_INDENT + corner * 2,
                bodyBottom,
            ),
            180f,
            -90f,
            false,
        )
        
        lineTo(w - corner, bodyBottom)
        // Korrektur: Die Bounding-Box für die Ecke beginnt AB bodyBottom abwärts
        arcTo(
            Rect(w - corner * 2, bodyBottom, w, bodyBottom + corner * 2),
            270f, 
            90f, 
            false
        )
        
        lineTo(w, h - corner)
        arcTo(Rect(w - corner * 2, h - corner * 2, w, h), 0f, 90f, false)
        
        val tabCenter = w / 2f
        lineTo(tabCenter + TAB_WIDTH / 2, h)
        
        cubicTo(
            tabCenter + TAB_WIDTH / 4, h,
            tabCenter + TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter, h - TAB_DEPTH
        )
        cubicTo(
            tabCenter - TAB_WIDTH / 4, h - TAB_DEPTH,
            tabCenter - TAB_WIDTH / 4, h,
            tabCenter - TAB_WIDTH / 2, h
        )
        
        lineTo(corner, h)
        arcTo(Rect(0f, h - corner * 2, corner * 2, h), 90f, 90f, false)
        lineTo(0f, corner)
        arcTo(Rect(0f, 0f, corner * 2, corner * 2), 180f, 90f, false)
        close()
    }

    fun decorativeContainerPath(
        size: Size,
        headerHeight: Float,
        footerHeight: Float,
    ): Path = Path().apply {
        val w = size.width
        val h = size.height
        val bodyBottom = h - footerHeight
        val innerLeft = LayoutConstants.NESTED_INDENT

        moveTo(CORNER, 0f)
        lineTo(w - CORNER, 0f)
        arcTo(Rect(w - CORNER * 2, 0f, w, CORNER * 2), 270f, 90f, false)
        lineTo(w, headerHeight - CORNER)
        arcTo(Rect(w - CORNER * 2, headerHeight - CORNER * 2, w, headerHeight), 0f, 90f, false)
        lineTo(innerLeft + CORNER, headerHeight)
        arcTo(
            Rect(innerLeft, headerHeight, innerLeft + CORNER * 2, headerHeight + CORNER * 2),
            270f,
            -90f,
            false,
        )
        lineTo(innerLeft, bodyBottom - CORNER)
        arcTo(
            Rect(innerLeft, bodyBottom - CORNER * 2, innerLeft + CORNER * 2, bodyBottom),
            180f,
            -90f,
            false,
        )
        lineTo(w - CORNER, bodyBottom)
        arcTo(Rect(w - CORNER * 2, bodyBottom, w, bodyBottom + CORNER * 2), 270f, 90f, false)
        lineTo(w, h - CORNER)
        arcTo(Rect(w - CORNER * 2, h - CORNER * 2, w, h), 0f, 90f, false)
        lineTo(CORNER, h)
        arcTo(Rect(0f, h - CORNER * 2, CORNER * 2, h), 90f, 90f, false)
        lineTo(0f, CORNER)
        arcTo(Rect(0f, 0f, CORNER * 2, CORNER * 2), 180f, 90f, false)
        close()
    }

    fun reporterPath(size: Size, valueType: String? = null): Path =
        reporterSocketPath(size, valueType)

    fun reporterSocketPath(size: Size, valueType: String? = null): Path = when (valueType.reporterSocketShape()) {
        ReporterSocketShape.NumberPill -> pillPath(size)
        ReporterSocketShape.StringRectangle -> roundedRectPath(size, radius = 6f)
        ReporterSocketShape.BooleanTriangle -> booleanTrianglePath(size)
        ReporterSocketShape.AnyDiamond -> diamondPath(size)
        ReporterSocketShape.CustomArch -> archPath(size)
        ReporterSocketShape.FallbackClamShell -> clamShellPath(size)
    }

    private fun pillPath(size: Size): Path = Path().apply {
        val radius = minOf(REPORTER_RADIUS, size.height / 2f, size.width / 2f)
        addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius, radius)))
    }

    private fun roundedRectPath(size: Size, radius: Float): Path = Path().apply {
        val safeRadius = minOf(radius, size.height / 2f, size.width / 2f)
        addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(safeRadius, safeRadius)))
    }

    private fun booleanTrianglePath(size: Size): Path = Path().apply {
        val insetX = (size.width * 0.12f).coerceAtMost(12f)
        val insetY = (size.height * 0.08f).coerceAtMost(4f)
        moveTo(size.width / 2f, insetY)
        lineTo(size.width - insetX, size.height - insetY)
        lineTo(insetX, size.height - insetY)
        close()
    }

    private fun diamondPath(size: Size): Path = Path().apply {
        val insetX = (size.width * 0.06f).coerceAtMost(8f)
        val insetY = (size.height * 0.08f).coerceAtMost(4f)
        moveTo(size.width / 2f, insetY)
        lineTo(size.width - insetX, size.height / 2f)
        lineTo(size.width / 2f, size.height - insetY)
        lineTo(insetX, size.height / 2f)
        close()
    }

    private fun archPath(size: Size): Path = Path().apply {
        val radius = minOf(size.height * 0.7f, size.width / 2f)
        moveTo(radius, 0f)
        lineTo(size.width - radius, 0f)
        arcTo(Rect(size.width - radius * 2f, 0f, size.width, radius * 2f), 270f, 90f, false)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(0f, radius)
        arcTo(Rect(0f, 0f, radius * 2f, radius * 2f), 180f, 90f, false)
        close()
    }

    private fun clamShellPath(size: Size): Path = Path().apply {
        val cut = minOf(size.width, size.height) * 0.2f
        val radius = minOf(size.height / 2f, 8f)
        moveTo(cut, 0f)
        lineTo(size.width - cut, 0f)
        quadraticTo(size.width, 0f, size.width, radius)
        lineTo(size.width, size.height - radius)
        quadraticTo(size.width, size.height, size.width - cut, size.height)
        lineTo(cut, size.height)
        quadraticTo(0f, size.height, 0f, size.height - radius)
        lineTo(0f, radius)
        quadraticTo(0f, 0f, cut, 0f)
        close()
    }

    private enum class ReporterSocketShape {
        NumberPill,
        StringRectangle,
        BooleanTriangle,
        AnyDiamond,
        CustomArch,
        FallbackClamShell,
    }

    private fun String?.reporterSocketShape(): ReporterSocketShape = when (this?.trim()?.lowercase()) {
        "number", "num", "int", "integer", "float", "double", "duration" -> ReporterSocketShape.NumberPill
        "string", "text", "char", "regex" -> ReporterSocketShape.StringRectangle
        "boolean", "bool" -> ReporterSocketShape.BooleanTriangle
        "any", "*", "" -> ReporterSocketShape.AnyDiamond
        "custom" -> ReporterSocketShape.CustomArch
        else -> ReporterSocketShape.FallbackClamShell
    }

    /** Reporter mit Output-Tab links (Blockly-inline). */
    fun inlineReporterPath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height
        val radius = minOf(h / 2f, INLINE_REPORTER_RADIUS, (w - TAB_DEPTH).coerceAtLeast(0f) / 2f)
        val tabCenterY = h / 2f
        val bodyLeft = TAB_DEPTH

        moveTo(bodyLeft + radius, 0f)
        lineTo(w - radius, 0f)
        arcTo(Rect(w - radius * 2, 0f, w, radius * 2), 270f, 90f, false)
        lineTo(w, h - radius)
        arcTo(Rect(w - radius * 2, h - radius * 2, w, h), 0f, 90f, false)
        lineTo(bodyLeft + radius, h)
        arcTo(Rect(bodyLeft, h - radius * 2, bodyLeft + radius * 2, h), 90f, 90f, false)

        lineTo(bodyLeft, tabCenterY + TAB_WIDTH / 2)
        cubicTo(
            bodyLeft, tabCenterY + TAB_WIDTH / 4,
            bodyLeft - TAB_DEPTH * 0.5f, tabCenterY + TAB_WIDTH / 4,
            bodyLeft - TAB_DEPTH, tabCenterY,
        )
        cubicTo(
            bodyLeft - TAB_DEPTH * 0.5f, tabCenterY - TAB_WIDTH / 4,
            bodyLeft, tabCenterY - TAB_WIDTH / 4,
            bodyLeft, tabCenterY - TAB_WIDTH / 2,
        )

        lineTo(bodyLeft, radius)
        arcTo(Rect(bodyLeft, 0f, bodyLeft + radius * 2, radius * 2), 180f, 90f, false)
        close()
    }
}
