package de.visualtasker.blockeditor.registry

import de.visualtasker.workflow.semantics.CommandCategories

object BlockCategories {
    const val EVENT = CommandCategories.EVENT
    const val ACTION = CommandCategories.ACTION
    const val FEEDBACK = CommandCategories.FEEDBACK
    const val EMSCRIPT = CommandCategories.EMSCRIPT
    const val INPUT = CommandCategories.INPUT
    const val PERCEPTION = CommandCategories.PERCEPTION
    const val VISION = CommandCategories.VISION
    const val SCENE = CommandCategories.SCENE
    const val TEXT = CommandCategories.TEXT
    const val FILE = CommandCategories.FILE
    const val SYSTEM = CommandCategories.SYSTEM
    const val CHROME_TAB = CommandCategories.CHROME_TAB
    const val TASKER = CommandCategories.TASKER
    const val SHIZUKU = CommandCategories.SHIZUKU
    const val TERMUX = CommandCategories.TERMUX
    const val SCRCPY = CommandCategories.SCRCPY
    const val CHARTS = CommandCategories.CHARTS
    const val CONTROL = CommandCategories.CONTROL
    const val LOGIC = CommandCategories.LOGIC
    const val VARIABLES = CommandCategories.VARIABLES
    const val FLOW = CommandCategories.FLOW
    const val REM_FLOW_NODES = CommandCategories.REM_FLOW_NODES
    const val RUNTIME = CommandCategories.RUNTIME
    const val DEBUG = CommandCategories.DEBUG
    const val VARIABLE = CommandCategories.VARIABLE
    const val CUSTOM = CommandCategories.CUSTOM

    data class CategoryMeta(
        val id: String,
        val label: String,
        val accentArgb: Long,
    )

    val all: List<CategoryMeta> = listOf(
        CategoryMeta(EVENT, "Event", 0xFFB78B00),
        CategoryMeta(ACTION, "Action", 0xFF3E6F91),
        CategoryMeta(FEEDBACK, "Feedback", 0xFF8A5F76),
        CategoryMeta(EMSCRIPT, "EMScript", 0xFF56687A),
        CategoryMeta(INPUT, "Input", 0xFF4B6F8F),
        CategoryMeta(PERCEPTION, "Perception", 0xFF3F735F),
        CategoryMeta(VISION, "Vision", 0xFF2E7D78),
        CategoryMeta(SCENE, "Scene", 0xFF5E8CDA),
        CategoryMeta(TEXT, "Text", 0xFF5D6FA8),
        CategoryMeta(FILE, "File", 0xFF8B6F42),
        CategoryMeta(SYSTEM, "System", 0xFF6B7280),
        CategoryMeta(CHROME_TAB, "ChromeTab", 0xFF2F6FDB),
        CategoryMeta(TASKER, "Tasker", 0xFF7C5AC7),
        CategoryMeta(SHIZUKU, "Shizuku", 0xFF8A4F8D),
        CategoryMeta(TERMUX, "Termux", 0xFF207A4B),
        CategoryMeta(SCRCPY, "scrcpy", 0xFF3F7EA3),
        CategoryMeta(CHARTS, "Charts", 0xFFB4772F),
        CategoryMeta(CONTROL, "Control", 0xFF87684A),
        CategoryMeta(LOGIC, "Logic", 0xFF586E4B),
        CategoryMeta(VARIABLES, "Variables", 0xFF6D607E),
        CategoryMeta(FLOW, "Flow", 0xFF7B6750),
        CategoryMeta(REM_FLOW_NODES, "REM-FlowNodes", 0xFF4F7A8A),
        CategoryMeta(RUNTIME, "Runtime", 0xFF686E78),
        CategoryMeta(DEBUG, "Debug", 0xFF75617A),
        CategoryMeta(VARIABLE, "Variable", 0xFF5A716B),
        CategoryMeta(CUSTOM, "Custom", 0xFF66707A),
    )

    fun metaFor(category: String): CategoryMeta =
        all.find { it.id == category } ?: CategoryMeta(category, category, 0xFF66707A)
}
