package de.visualtasker.blockeditor.registry

object BlockCategories {
    const val EVENT = "event"
    const val ACTION = "action"
    const val FEEDBACK = "feedback"
    const val EMSCRIPT = "emscript"
    const val INPUT = "input"
    const val PERCEPTION = "perception"
    const val CONTROL = "control"
    const val LOGIC = "logic"
    const val VARIABLES = "variables"
    const val FLOW = "flow"
    const val RUNTIME = "runtime"
    const val DEBUG = "debug"
    const val VARIABLE = "variable"
    const val CUSTOM = "custom"

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
        CategoryMeta(CONTROL, "Control", 0xFF87684A),
        CategoryMeta(LOGIC, "Logic", 0xFF586E4B),
        CategoryMeta(VARIABLES, "Variables", 0xFF6D607E),
        CategoryMeta(FLOW, "Flow", 0xFF7B6750),
        CategoryMeta(RUNTIME, "Runtime", 0xFF686E78),
        CategoryMeta(DEBUG, "Debug", 0xFF75617A),
        CategoryMeta(VARIABLE, "Variable", 0xFF5A716B),
        CategoryMeta(CUSTOM, "Custom", 0xFF66707A),
    )

    fun metaFor(category: String): CategoryMeta =
        all.find { it.id == category } ?: CategoryMeta(category, category, 0xFF66707A)
}
