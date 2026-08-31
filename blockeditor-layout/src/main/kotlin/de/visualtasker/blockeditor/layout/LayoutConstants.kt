package de.visualtasker.blockeditor.layout

object LayoutConstants {
    const val HEADER_HEIGHT = 44f
    const val STATEMENT_MIN_HEIGHT = 48f
    const val NESTED_INDENT = 32f
    const val BLOCK_GAP = 8f
    const val SNAP_RADIUS = 42f
    const val PREVIEW_RADIUS = 72f
    const val COLLAPSED_HEIGHT = 44f
    const val COLLAPSED_REPORTER_WIDTH = 36f
    const val COLLAPSED_REPORTER_HEIGHT = 28f
    const val STANDARD_WIDTH = 288f
    const val STACK_DOCK_X = 64f
    const val STACK_CONNECTOR_WIDTH = 28f
    const val STACK_CONNECTOR_DEPTH = 8f
    const val STACK_VERTICAL_GAP = 6f
    const val REPORTER_WIDTH = 74f
    const val REPORTER_HEIGHT = 40f
    const val OUTPUT_TAB = 16f
    const val SLOT_PADDING = 8f
    const val CONTAINER_WIDTH = NESTED_INDENT + STANDARD_WIDTH + SLOT_PADDING
    const val FOOTER_HEIGHT = 24f
    const val ANCHOR_RADIUS = 12f
    const val FIELD_HEIGHT = 32f
    const val ELIF_SECTION_HEIGHT = HEADER_HEIGHT
    /** Horizontaler Mittelsteg zwischen Container-Zweigen – muss mit [BlockShapes] übereinstimmen. */
    const val BRANCH_SHELF = HEADER_HEIGHT
    /** Unterer Innenradius des C-Blocks – zusätzliche Reserve für den letzten Zweig. */
    const val CORNER_RADIUS = 16f
    const val CONTROL_CONTAINER_WIDTH = 164f
    const val INLINE_OUTPUT_TAB = 10f
    const val INLINE_OPERATOR_WIDTH = 32f
    const val INLINE_SLOT_GAP = 6f
    const val INLINE_MIN_SLOT_WIDTH = REPORTER_WIDTH
}
