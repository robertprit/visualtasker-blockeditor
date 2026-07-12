package de.visualtasker.blockeditor.validation

import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorTest {
    @Test
    fun sampleWorkspace_isValid() {
        val result = Validator.validate(SampleWorkspaceFactory.createDemo())
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun orphanBlock_isReported() {
        val document = SampleWorkspaceFactory.createDemo()
        val orphanId = document.rootBlocks.last()
        val broken = document.copy(
            rootBlocks = document.rootBlocks.dropLast(1),
        )
        val result = Validator.validate(broken)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is OrphanBlock && it.blockId == orphanId })
    }
}
