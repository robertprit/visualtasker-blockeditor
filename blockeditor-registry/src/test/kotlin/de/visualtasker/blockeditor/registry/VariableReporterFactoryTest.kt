package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableReporterFactoryTest {
    @Test
    fun create_reporterDefinitionForVariable() {
        val variable = VariableDefinition(
            id = "score",
            name = "Score",
            type = "Number",
            scope = VariableScope.Global,
        )
        val definition = VariableReporterFactory.create(variable)
        assertEquals("variable.reporter.score", definition.id)
        assertEquals("Score", definition.label)
        assertTrue(definition.isReporter)
        assertEquals("Number", definition.outputType)
        assertEquals("Score", definition.fields.single().defaultValue)
    }
}
