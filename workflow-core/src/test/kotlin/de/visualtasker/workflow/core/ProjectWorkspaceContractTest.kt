package de.visualtasker.workflow.core

import de.visualtasker.workflow.serialization.ProjectDecodeResult
import de.visualtasker.workflow.serialization.ProjectSerializer
import de.visualtasker.workflow.serialization.WorkflowSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspaceContractTest {
    @Test
    fun newProjectHasStableIdentityAndActiveRoot() {
        val project = project()

        assertEquals(ProjectId("project:test"), project.id)
        assertEquals(ROOT_A, project.manifest.activeRootId)
        assertEquals("workspace-a", project.activeRoot()!!.workspace.id)
        assertTrue(ProjectValidator.validate(project).isEmpty())
    }

    @Test
    fun legacySingleRootMigrationIsDeterministicAndIdempotent() {
        val legacy = WorkflowSerializer.serialize(workspace("legacy", "start"))
        val first = ProjectSerializer.decode(legacy) as ProjectDecodeResult.Decoded
        val persisted = ProjectSerializer.serialize(first.project)
        val second = ProjectSerializer.decode(persisted) as ProjectDecodeResult.Decoded

        assertTrue(first.migratedFromWorkspace)
        assertFalse(second.migratedFromWorkspace)
        assertEquals(first.project, second.project)
        assertEquals(persisted, ProjectSerializer.serialize(second.project))
    }

    @Test
    fun twoRootsRemainIndependentWhenOneWorkspaceIsReplaced() {
        val before = twoRootProject()
        val rootB = before.roots.getValue(ROOT_B)
        val replacement = workspace("workspace-a", "changed")
        val result = success(
            before,
            ProjectOperation.ReplaceRootWorkspace(op("replace"), ROOT_A, before.roots.getValue(ROOT_A).workspace.version, replacement),
        )

        assertEquals(replacement, result.project.roots.getValue(ROOT_A).workspace)
        assertEquals(rootB, result.project.roots.getValue(ROOT_B))
    }

    @Test
    fun rootSwitchAndRenameDoNotMutateWorkflowDocuments() {
        val before = twoRootProject()
        val switched = success(before, ProjectOperation.SetActiveRoot(op("switch"), ROOT_B)).project
        val renamed = success(switched, ProjectOperation.RenameRoot(op("rename"), ROOT_B, "Secondary")).project

        assertEquals(ROOT_B, renamed.manifest.activeRootId)
        assertEquals("Secondary", renamed.roots.getValue(ROOT_B).name)
        assertEquals(before.roots.getValue(ROOT_A).workspace, renamed.roots.getValue(ROOT_A).workspace)
        assertEquals(before.roots.getValue(ROOT_B).workspace, renamed.roots.getValue(ROOT_B).workspace)
    }

    @Test
    fun missingRootAndRevisionConflictRollbackExactly() {
        val before = project()
        val missing = failure(before, ProjectOperation.RenameRoot(op("missing"), WorkflowRootId("missing"), "No"))
        val stale = ProjectOperationExecutor.execute(
            before,
            ProjectTransaction(op("stale"), emptyList(), ProjectRevision(before.revision.value + 1)),
        ) as ProjectOperationResult.Failure

        assertEquals(ProjectOperationDiagnosticCode.RootNotFound, missing.diagnostic.code)
        assertEquals(ProjectOperationDiagnosticCode.RevisionMismatch, stale.diagnostic.code)
        assertEquals(before, missing.project)
        assertEquals(before, stale.project)
    }

    @Test
    fun crossRootRelationIsDiagnosed() {
        val project = twoRootProject()
        val rootA = project.roots.getValue(ROOT_A)
        val rootBStatement = project.roots.getValue(ROOT_B).workspace.canonical!!.entities.single { it.ref.kind == SemanticEntityKind.Statement }.ref
        val rootAStatement = rootA.workspace.canonical!!.entities.single { it.ref.kind == SemanticEntityKind.Statement }.ref
        val cross = SemanticRelation(
            SemanticRelationId("cross"),
            SemanticRelationKind.Sequence,
            rootAStatement,
            rootBStatement,
            SemanticRelationRole(SemanticRelationRoleKind.Next),
        )
        val invalid = project.copy(
            roots = project.roots + (ROOT_A to rootA.copy(workspace = rootA.workspace.copy(canonical = rootA.workspace.canonical.copy(relations = rootA.workspace.canonical.relations + cross)))),
        )

        assertTrue(ProjectValidator.validate(invalid).any { it.code == ProjectValidationCode.CrossRootRelation })
    }

    @Test
    fun definitionsUseStableReferencesAndWhereUsed() {
        val definition = ProjectDefinition(DefinitionId("definition:login"), "Login", ProjectDefinitionKind.Subworkflow)
        val created = success(project(), ProjectOperation.CreateDefinition(op("definition"), definition)).project
        val reference = ProjectReference(
            ProjectReferenceId("reference:login"),
            ProjectReferenceSource.Root(ROOT_A),
            ProjectReferenceTarget.Definition(definition.id),
            ProjectReferenceKind.Call,
        )
        val bound = success(created, ProjectOperation.BindReference(op("bind"), reference)).project

        assertEquals(listOf(reference), bound.whereUsed(definition.id))
        val blocked = failure(bound, ProjectOperation.DeleteDefinition(op("delete"), definition.id))
        assertEquals(ProjectOperationDiagnosticCode.ReferencedItem, blocked.diagnostic.code)
        assertEquals(bound, blocked.project)
    }

    @Test
    fun definitionWithoutWorkspaceStillValidatesItsReferenceTarget() {
        val definitionId = DefinitionId("definition:helper")
        val referenceId = ProjectReferenceId("reference:missing-resource")
        val candidate = project().copy(
            definitions = mapOf(
                definitionId to ProjectDefinition(definitionId, "Helper", ProjectDefinitionKind.Function),
            ),
            references = mapOf(
                referenceId to ProjectReference(
                    id = referenceId,
                    source = ProjectReferenceSource.Definition(definitionId),
                    target = ProjectReferenceTarget.Resource(ResourceId("resource:missing")),
                    kind = ProjectReferenceKind.Resource,
                ),
            ),
        )

        assertTrue(
            ProjectValidator.validate(candidate).any {
                it.code == ProjectValidationCode.ReferenceTargetMissing && it.referenceId == referenceId
            },
        )
    }

    @Test
    fun resourceReferencesBlockRemovalAndNeverEmbedBytes() {
        val resource = ProjectResource(ResourceId("resource:screen"), "Screen", ProjectResourceType.Image, ProjectResourceScope.ProjectLocal, "images/screen.png", "sha256:abc")
        val added = success(project(), ProjectOperation.AddResource(op("resource"), resource)).project
        val reference = ProjectReference(ProjectReferenceId("reference:screen"), ProjectReferenceSource.Root(ROOT_A), ProjectReferenceTarget.Resource(resource.id), ProjectReferenceKind.Resource)
        val bound = success(added, ProjectOperation.BindReference(op("bind"), reference)).project

        assertEquals(resource, bound.resources.getValue(resource.id))
        assertEquals(ProjectOperationDiagnosticCode.ReferencedItem, failure(bound, ProjectOperation.RemoveResource(op("remove"), resource.id)).diagnostic.code)
        assertFalse(ProjectSerializer.serialize(bound).contains("PNG"))
    }

    @Test
    fun resourceUpsertAndMetadataPatchAreAtomicAndRoundtrip() {
        val resource = ProjectResource(ResourceId("resource:screen"), "Screen", ProjectResourceType.Image, ProjectResourceScope.ProjectLocal, "images/screen.png")
        val updated = resource.copy(name = "Updated Screen", metadata = mapOf("opaque" to "preserved"))
        val result = success(
            project(),
            ProjectTransaction(
                op("inventory"),
                listOf(
                    ProjectOperation.PutResource(op("put-resource"), resource),
                    ProjectOperation.PutResource(op("update-resource"), updated),
                    ProjectOperation.PutMetadata(op("metadata"), mapOf("inventory.version" to "1")),
                ),
                ProjectRevision(0L),
            ),
        ).project

        assertEquals(ProjectRevision(1L), result.revision)
        assertEquals(updated, result.resources.getValue(resource.id))
        assertEquals("1", result.metadata["inventory.version"])
        assertEquals(result, ProjectSerializer.deserialize(ProjectSerializer.serialize(result)))
    }

    @Test
    fun invalidMetadataPatchRollsBackEarlierResourceUpsert() {
        val before = project()
        val resource = ProjectResource(ResourceId("resource:screen"), "Screen", ProjectResourceType.Image, ProjectResourceScope.ProjectLocal, "images/screen.png")
        val result = ProjectOperationExecutor.execute(
            before,
            ProjectTransaction(
                op("invalid-inventory"),
                listOf(
                    ProjectOperation.PutResource(op("put-resource"), resource),
                    ProjectOperation.PutMetadata(op("invalid-metadata"), mapOf("" to "invalid")),
                ),
                before.revision,
            ),
        ) as ProjectOperationResult.Failure

        assertEquals(before, result.project)
        assertEquals(ProjectOperationDiagnosticCode.ValidationFailed, result.diagnostic.code)
    }

    @Test
    fun variableScopesSeparateLifetimePersistenceAndProviderBinding() {
        val external = ProjectVariable(VariableDefinition("tasker", "tasker", "String", VariableScope.External), "tasker.variable")
        val stored = success(project(), ProjectOperation.PutVariable(op("variable"), external)).project

        assertTrue(ProjectValidator.validate(stored).isEmpty())
        assertTrue(ProjectVariableScopes.contract(VariableScope.External).providerRequired)
        assertFalse(ProjectVariableScopes.contract(VariableScope.Project).runtimeImplemented)
        assertTrue(ProjectVariableScopes.contract(VariableScope.Local).runtimeImplemented)
        assertEquals(ProjectVariablePersistence.AppStore, ProjectVariableScopes.contract(VariableScope.AppShared).persistence)
    }

    @Test
    fun externalVariableWithoutProviderIsRejectedAtomically() {
        val invalid = ProjectVariable(VariableDefinition("external", "external", "String", VariableScope.External))
        val result = failure(project(), ProjectOperation.PutVariable(op("invalid-variable"), invalid))

        assertEquals(ProjectOperationDiagnosticCode.ValidationFailed, result.diagnostic.code)
        assertTrue(result.project.variables.isEmpty())
    }

    @Test
    fun transactionRollsBackEarlierOperationsWhenLaterOperationFails() {
        val before = project()
        val definition = ProjectDefinition(DefinitionId("definition:new"), "New", ProjectDefinitionKind.Function)
        val result = ProjectOperationExecutor.execute(
            before,
            ProjectTransaction(
                op("transaction"),
                listOf(
                    ProjectOperation.CreateDefinition(op("create"), definition),
                    ProjectOperation.DeleteRoot(op("delete-missing"), WorkflowRootId("missing")),
                ),
                before.revision,
            ),
        ) as ProjectOperationResult.Failure

        assertEquals(before, result.project)
        assertFalse(definition.id in result.project.definitions)
    }

    @Test
    fun deterministicSaveReloadPreservesIdsMetadataAndUnknownMetadataEntries() {
        val original = twoRootProject().copy(metadata = mapOf("plugin.future" to "opaque"))
        val first = ProjectSerializer.serialize(original)
        val restored = ProjectSerializer.deserialize(first)

        assertEquals(original, restored)
        assertEquals("opaque", restored.metadata["plugin.future"])
        assertEquals(first, ProjectSerializer.serialize(restored))
        assertEquals(original.roots.keys, restored.roots.keys)
    }

    @Test
    fun duplicateIdsInPersistedProjectAreRejectedInsteadOfCollapsed() {
        val encoded = ProjectSerializer.serialize(twoRootProject())
            .replace("\"id\":\"root:b\"", "\"id\":\"root:a\"")

        val result = ProjectSerializer.decode(encoded)

        assertTrue(result is ProjectDecodeResult.Malformed)
        assertTrue((result as ProjectDecodeResult.Malformed).reason.contains("Duplicate project root ID root:a"))
    }

    @Test
    fun deleteActiveRootSelectsStableRemainingRoot() {
        val project = twoRootProject()
        val deleted = success(project, ProjectOperation.DeleteRoot(op("delete-a"), ROOT_A)).project

        assertEquals(ROOT_B, deleted.manifest.activeRootId)
        assertEquals(setOf(ROOT_B), deleted.roots.keys)
    }

    @Test
    fun duplicateOperationIdsAndResourceIdsAreRejected() {
        val before = project()
        val resource = ProjectResource(ResourceId("resource:x"), "X", ProjectResourceType.Shape, ProjectResourceScope.ProjectLocal, "shapes/x.ema")
        val duplicateOps = ProjectOperationExecutor.execute(
            before,
            ProjectTransaction(op("tx"), listOf(ProjectOperation.AddResource(op("same"), resource), ProjectOperation.AddResource(op("same"), resource)), before.revision),
        ) as ProjectOperationResult.Failure

        assertEquals(ProjectOperationDiagnosticCode.DuplicateOperationId, duplicateOps.diagnostic.code)
        val added = success(before, ProjectOperation.AddResource(op("add"), resource)).project
        assertEquals(ProjectOperationDiagnosticCode.DuplicateId, failure(added, ProjectOperation.AddResource(op("again"), resource)).diagnostic.code)
    }

    @Test
    fun projectRevisionAdvancesOncePerAtomicTransaction() {
        val before = project()
        val after = success(
            before,
            ProjectTransaction(
                op("tx"),
                listOf(
                    ProjectOperation.RenameRoot(op("rename"), ROOT_A, "Renamed"),
                    ProjectOperation.SetActiveRoot(op("active"), ROOT_A),
                ),
                before.revision,
            ),
        ).project

        assertEquals(before.revision.value + 1, after.revision.value)
    }

    @Test
    fun projectHistoryRestoresRootMutationAndActiveRoot() {
        val before = twoRootProject()
        val after = success(before, ProjectOperation.SetActiveRoot(op("switch"), ROOT_B)).project
        val history = ProjectHistoryController()

        assertTrue(history.recordAcceptedTransition(before, after))
        val undone = history.undo(after)!!.project
        val redone = history.redo(undone)!!.project

        assertEquals(ROOT_A, undone.manifest.activeRootId)
        assertEquals(after, redone)
    }

    @Test
    fun projectHistoryCanBeClearedWhenActivatingAnotherProject() {
        val before = twoRootProject()
        val after = success(before, ProjectOperation.SetActiveRoot(op("switch-clear"), ROOT_B)).project
        val history = ProjectHistoryController()
        history.recordAcceptedTransition(before, after)

        history.clear()

        assertEquals(0, history.undoDepth)
        assertEquals(0, history.redoDepth)
        assertNull(history.undo(after))
        assertNull(history.redo(after))
    }

    private fun project() = ProjectDocument(
        id = ProjectId("project:test"),
        manifest = ProjectManifest("Test", ROOT_A),
        roots = mapOf(ROOT_A to ProjectWorkflowRoot(ROOT_A, "Main", workspace("workspace-a", "start-a"))),
    )

    private fun twoRootProject(): ProjectDocument {
        val first = project()
        return first.copy(roots = first.roots + (ROOT_B to ProjectWorkflowRoot(ROOT_B, "Second", workspace("workspace-b", "start-b"))))
    }

    private fun workspace(id: String, blockId: String): WorkspaceDocument = WorkflowSerializer.deserialize(
        """{"id":"$id","version":0,"blocks":[{"id":"$blockId","node":{"type":"event.start","fields":[],"previous":null,"next":{"id":"$blockId.next","owner":"$blockId","kind":"Next","accepts":[],"provides":null,"connectedTo":null,"slotName":null},"output":null,"valueInputs":[],"statementInputs":[],"collapsed":false,"metadata":[]}}],"rootBlocks":["$blockId"],"rootPositions":[],"variables":[]}""",
    )

    private fun success(project: ProjectDocument, operation: ProjectOperation) =
        ProjectOperationExecutor.execute(project, operation) as ProjectOperationResult.Success

    private fun success(project: ProjectDocument, transaction: ProjectTransaction) =
        ProjectOperationExecutor.execute(project, transaction) as ProjectOperationResult.Success

    private fun failure(project: ProjectDocument, operation: ProjectOperation) =
        ProjectOperationExecutor.execute(project, operation) as ProjectOperationResult.Failure

    private fun op(value: String) = ProjectOperationId(value)

    private companion object {
        val ROOT_A = WorkflowRootId("root:a")
        val ROOT_B = WorkflowRootId("root:b")
    }
}
