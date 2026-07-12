package de.visualtasker.blockeditor.registry

object BlockCategories {
    const val EVENT = "event"
    const val ACTION = "action"
    const val CONTROL = "control"
    const val LOGIC = "logic"
    const val DEBUG = "debug"
    const val VARIABLE = "variable"
    const val CUSTOM = "custom"

    data class CategoryMeta(
        val id: String,
        val label: String,
        val accentArgb: Long,
    )

    val all: List<CategoryMeta> = listOf(
        CategoryMeta(EVENT, "Event", 0xFFFFC107),
        CategoryMeta(ACTION, "Action", 0xFF42A5F5),
        CategoryMeta(CONTROL, "Control", 0xFFFF7043),
        CategoryMeta(LOGIC, "Logic", 0xFF66BB6A),
        CategoryMeta(DEBUG, "Debug", 0xFFAB47BC),
        CategoryMeta(VARIABLE, "Variable", 0xFF26A69A),
        CategoryMeta(CUSTOM, "Custom", 0xFF78909C),
    )

    fun metaFor(category: String): CategoryMeta =
        all.find { it.id == category } ?: CategoryMeta(category, category, 0xFF78909C)
}
