package de.visualtasker.blockeditor.compose.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import de.visualtasker.blockeditor.layout.LayoutConstants

object BlockShapes {
    private const val CORNER = 16f 
    private const val NOTCH_WIDTH = 28f
    private const val NOTCH_DEPTH = 10f
    private const val TAB_WIDTH = 28f
    private const val TAB_DEPTH = 10f
    private const val BRANCH_SHELF = LayoutConstants.BRANCH_SHELF

    fun statementPath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height
        val notchCenter = LayoutConstants.NESTED_INDENT

        moveTo(CORNER, 0f)
        
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
        val bodyBottom = h - footerHeight
        val notchCenter = LayoutConstants.NESTED_INDENT
        val sortedDividers = branchDividers
            .filter { it in headerHeight..bodyBottom }
            .sorted()

        moveTo(CORNER, 0f)
        
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
        
        lineTo(w - CORNER, 0f)
        arcTo(Rect(w - CORNER * 2, 0f, w, CORNER * 2), 270f, 90f, false)
        lineTo(w, headerHeight - CORNER)
        arcTo(Rect(w - CORNER * 2, headerHeight - CORNER * 2, w, headerHeight), 0f, 90f, false)
        lineTo(LayoutConstants.NESTED_INDENT + CORNER, headerHeight)
        arcTo(
            Rect(
                LayoutConstants.NESTED_INDENT,
                headerHeight,
                LayoutConstants.NESTED_INDENT + CORNER * 2,
                headerHeight + CORNER * 2,
            ),
            270f,
            -90f,
            false,
        )

        sortedDividers.forEach { dividerY ->
            lineTo(LayoutConstants.NESTED_INDENT, dividerY - CORNER)
            arcTo(
                Rect(
                    LayoutConstants.NESTED_INDENT,
                    dividerY - CORNER * 2,
                    LayoutConstants.NESTED_INDENT + CORNER * 2,
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
            
            lineTo(LayoutConstants.NESTED_INDENT + CORNER, dividerY + BRANCH_SHELF)
            arcTo(
                Rect(
                    LayoutConstants.NESTED_INDENT,
                    dividerY + BRANCH_SHELF,
                    LayoutConstants.NESTED_INDENT + CORNER * 2,
                    dividerY + BRANCH_SHELF + CORNER * 2,
                ),
                270f,
                -90f,
                false,
            )
        }

        lineTo(LayoutConstants.NESTED_INDENT, bodyBottom - CORNER)
        arcTo(
            Rect(
                LayoutConstants.NESTED_INDENT,
                bodyBottom - CORNER * 2,
                LayoutConstants.NESTED_INDENT + CORNER * 2,
                bodyBottom,
            ),
            180f,
            -90f,
            false,
        )
        
        lineTo(w - CORNER, bodyBottom)
        // Korrektur: Die Bounding-Box für die Ecke beginnt AB bodyBottom abwärts
        arcTo(
            Rect(w - CORNER * 2, bodyBottom, w, bodyBottom + CORNER * 2), 
            270f, 
            90f, 
            false
        )
        
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

    fun branchStemTabPath(dividerY: Float): Path = Path().apply {
        val center = LayoutConstants.NESTED_INDENT
        moveTo(0f, dividerY - NOTCH_WIDTH / 2)
        cubicTo(
            center * 0.8f, dividerY - NOTCH_WIDTH / 2,
            center, dividerY - NOTCH_DEPTH * 0.5f,
            center, dividerY
        )
        cubicTo(
            center, dividerY + NOTCH_DEPTH * 0.5f,
            center * 0.8f, dividerY + NOTCH_WIDTH / 2,
            0f, dividerY + NOTCH_WIDTH / 2
        )
        close()
    }

    fun reporterPath(size: Size): Path = Path().apply {
        // Korrektur: Verhindert überlappende Radien bei extrem schmalen Blöcken
        val radius = minOf(size.height / 2f, size.width / 2f)
        addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius, radius)))
    }

    /** Reporter mit Output-Tab links (Blockly-inline). */
    fun inlineReporterPath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height
        val radius = minOf(h / 2f, CORNER, (w - TAB_DEPTH) / 2f)
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