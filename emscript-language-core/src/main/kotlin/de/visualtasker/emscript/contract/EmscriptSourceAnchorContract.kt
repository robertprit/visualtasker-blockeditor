package de.visualtasker.emscript.contract

/** Runtime-inert EMScript projection directives used to preserve semantic identity. */
object EmscriptSourceAnchorContract {
    const val ENTITY_DIRECTIVE: String = "source.entity"
    const val RELATION_DIRECTIVE: String = "source.relation"

    const val BLOCK_LOCATOR: String = "block"
    const val INPUT_LOCATOR_PREFIX: String = "input:"
    const val BRANCH_LOCATOR_PREFIX: String = "branch:"

    private val stableIdPattern = Regex("[A-Za-z][A-Za-z0-9_.:-]{0,255}")
    private val inputLocatorPattern = Regex("input:[A-Za-z0-9_.:-]+(?:/[A-Za-z0-9_.:-]+)*")
    private val branchLocatorPattern = Regex("branch:[A-Za-z0-9_.:-]+")

    fun isValidStableId(value: String): Boolean = stableIdPattern.matches(value)

    fun isValidEntityLocator(value: String): Boolean =
        value == BLOCK_LOCATOR || inputLocatorPattern.matches(value) || branchLocatorPattern.matches(value)

    fun inputLocator(path: List<String>): String {
        require(path.isNotEmpty()) { "Source-anchor input path must not be empty." }
        require(path.all { it.matches(Regex("[A-Za-z0-9_.:-]+")) }) {
            "Source-anchor input path contains an invalid slot."
        }
        return INPUT_LOCATOR_PREFIX + path.joinToString("/")
    }

    fun branchLocator(slotName: String): String {
        require(slotName.matches(Regex("[A-Za-z0-9_.:-]+"))) {
            "Source-anchor branch slot is invalid."
        }
        return BRANCH_LOCATOR_PREFIX + slotName
    }
}
