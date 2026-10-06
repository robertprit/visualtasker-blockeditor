package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.registry.SampleWorkspaceFactory
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.workflow.core.WorkspaceAction
import de.visualtasker.workflow.core.WorkspaceReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineIncrementalUpdateTest {
    private val engine = LayoutEngine(DefaultBlockRegistry)
    private val factory = DefaultBlockRegistry.asFactory()

    @Test
    fun `free root move reuses all measurements and matches full layout geometry`() {
        val before = SampleWorkspaceFactory.createWithStatementSlot()
        val beforeCache = engine.build(before)
        val rootId = SampleWorkspaceFactory.mainChain(before).first()
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.MoveRoot(rootId, 220f, 310f),
            factory,
        )

        val incremental = engine.update(before, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(LayoutUpdatePath.ROOT_TRANSLATION, incremental.updateDiagnostics.path)
        assertEquals(0, incremental.updateDiagnostics.measuredBlocks)
        assertEquals(after.blocks.size, incremental.updateDiagnostics.reusedLayoutBlocks)
        assertEquals(after.blocks.size, incremental.updateDiagnostics.repositionedBlocks)
        assertEquals(0, incremental.updateDiagnostics.fullBuilds)
        assertGeometryEquals(oracle, incremental)
    }

    @Test
    fun `translation refreshes hit and anchor indexes`() {
        val before = SampleWorkspaceFactory.createDemo()
        val beforeCache = engine.build(before)
        val rootId = SampleWorkspaceFactory.mainChain(before).first()
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.MoveRoot(rootId, 360f, 420f),
            factory,
        )

        val incremental = engine.update(before, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)
        val movedBlock = incremental.flatIndex.visibleBlocks.first { it.blockId == rootId }
        val pointHits = incremental.flatIndex.hitIndex.queryPoint(
            movedBlock.bounds.x + movedBlock.bounds.width / 2f,
            movedBlock.bounds.y + movedBlock.bounds.height / 2f,
        )
        assertTrue(pointHits.any { it.blockId == rootId })

        incremental.flatIndex.connectionAnchors.forEach { anchor ->
            val hits = incremental.flatIndex.anchorIndex.queryPoint(anchor.x, anchor.y, anchor.radius)
            assertTrue(anchor in hits)
        }
        assertEquals(oracle.flatIndex.hitPrimitives, incremental.flatIndex.hitPrimitives)
        assertEquals(oracle.flatIndex.connectionAnchors, incremental.flatIndex.connectionAnchors)
    }

    @Test
    fun `semantic mutation uses conservative full layout fallback`() {
        val before = SampleWorkspaceFactory.createDemo()
        val beforeCache = engine.build(before)
        val chain = SampleWorkspaceFactory.mainChain(before)
        val upper = before.blocks.getValue(chain[0])
        val lower = before.blocks.getValue(chain[1])
        val detached = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.Disconnect(upper.next!!.id),
            factory,
        )
        assertNotNull(lower.previous)

        val updated = engine.update(before, detached, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(detached)

        assertEquals(LayoutUpdatePath.FULL_BUILD, updated.updateDiagnostics.path)
        assertEquals(2, updated.updateDiagnostics.dirtyBlocks)
        assertEquals(detached.blocks.size, updated.updateDiagnostics.measuredBlocks)
        assertEquals(1, updated.updateDiagnostics.fullBuilds)
        assertGeometryEquals(oracle, updated)
    }

    @Test
    fun `simple tail connect translates rigid root and matches full layout`() {
        var before = SampleWorkspaceFactory.create()
        before = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 40f, 220f),
            factory,
        )
        val startId = before.rootBlocks.first()
        val waitId = before.rootBlocks.last()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.Connect(
                before.blocks.getValue(startId).next!!.id,
                before.blocks.getValue(waitId).previous!!.id,
            ),
            factory,
        )

        val incremental = engine.update(before, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(LayoutUpdatePath.RIGID_CHAIN_RELINK, incremental.updateDiagnostics.path)
        assertEquals(2, incremental.updateDiagnostics.dirtyBlocks)
        assertEquals(0, incremental.updateDiagnostics.measuredBlocks)
        assertEquals(after.blocks.size, incremental.updateDiagnostics.reusedLayoutBlocks)
        assertGeometryEquals(oracle, incremental)
    }

    @Test
    fun `simple tail detach translates rigid stack and matches full layout`() {
        var disconnected = SampleWorkspaceFactory.create()
        disconnected = WorkspaceReducer.reduce(
            disconnected,
            WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 40f, 220f),
            factory,
        )
        val startId = disconnected.rootBlocks.first()
        val waitId = disconnected.rootBlocks.last()
        val connected = WorkspaceReducer.reduce(
            disconnected,
            WorkspaceAction.Connect(
                disconnected.blocks.getValue(startId).next!!.id,
                disconnected.blocks.getValue(waitId).previous!!.id,
            ),
            factory,
        )
        val beforeCache = engine.build(connected)
        val unlinked = WorkspaceReducer.reduce(
            connected,
            WorkspaceAction.Disconnect(connected.blocks.getValue(startId).next!!.id),
            factory,
        )
        val after = WorkspaceReducer.reduce(
            unlinked,
            WorkspaceAction.MoveRoot(waitId, 280f, 260f),
            factory,
        )

        val incremental = engine.update(connected, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(LayoutUpdatePath.RIGID_CHAIN_RELINK, incremental.updateDiagnostics.path)
        assertEquals(2, incremental.updateDiagnostics.dirtyBlocks)
        assertEquals(0, incremental.updateDiagnostics.measuredBlocks)
        assertGeometryEquals(oracle, incremental)
    }

    @Test
    fun `reporter attach uses conservative fallback and matches full layout`() {
        var before = instantiate(BlockTypes.CONTROL_IF, 40f, 40f)
        before = instantiate(before, BlockTypes.LOGIC_BOOLEAN, 280f, 40f)
        val controlId = before.rootBlocks.first()
        val reporterId = before.rootBlocks.last()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.Connect(
                before.blocks.getValue(reporterId).output!!.id,
                before.blocks.getValue(controlId).valueInputs.single().connection.id,
            ),
            factory,
        )

        assertFullFallbackEqualsOracle(before, after, beforeCache, expectedDirtyBlocks = 2)
    }

    @Test
    fun `reporter detach uses conservative fallback and matches full layout`() {
        var disconnected = instantiate(BlockTypes.CONTROL_IF, 40f, 40f)
        disconnected = instantiate(disconnected, BlockTypes.LOGIC_BOOLEAN, 280f, 40f)
        val controlId = disconnected.rootBlocks.first()
        val reporterId = disconnected.rootBlocks.last()
        val connected = WorkspaceReducer.reduce(
            disconnected,
            WorkspaceAction.Connect(
                disconnected.blocks.getValue(reporterId).output!!.id,
                disconnected.blocks.getValue(controlId).valueInputs.single().connection.id,
            ),
            factory,
        )
        val beforeCache = engine.build(connected)
        val after = WorkspaceReducer.reduce(
            connected,
            WorkspaceAction.Disconnect(connected.blocks.getValue(reporterId).output!!.id),
            factory,
        )

        assertFullFallbackEqualsOracle(connected, after, beforeCache, expectedDirtyBlocks = 2)
    }

    @Test
    fun `nested reporter mutation uses conservative fallback and matches full layout`() {
        var before = instantiate(BlockTypes.LOGIC_AND, 40f, 40f)
        before = instantiate(before, BlockTypes.LOGIC_BOOLEAN, 280f, 40f)
        val parentId = before.rootBlocks.first()
        val childId = before.rootBlocks.last()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.Connect(
                before.blocks.getValue(childId).output!!.id,
                before.blocks.getValue(parentId).valueInputs.first().connection.id,
            ),
            factory,
        )

        assertFullFallbackEqualsOracle(before, after, beforeCache, expectedDirtyBlocks = 2)
    }

    @Test
    fun `statement attach uses conservative fallback and matches full layout`() {
        var before = instantiate(BlockTypes.CONTROL_REPEAT, 40f, 40f)
        before = instantiate(before, BlockTypes.ACTION_WAIT, 280f, 40f)
        val controlId = before.rootBlocks.first()
        val statementId = before.rootBlocks.last()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.Connect(
                before.blocks.getValue(controlId).statementInputs.single().connection.id,
                before.blocks.getValue(statementId).previous!!.id,
            ),
            factory,
        )

        assertFullFallbackEqualsOracle(before, after, beforeCache, expectedDirtyBlocks = 2)
    }

    @Test
    fun `statement detach uses conservative fallback and matches full layout`() {
        var disconnected = instantiate(BlockTypes.CONTROL_REPEAT, 40f, 40f)
        disconnected = instantiate(disconnected, BlockTypes.ACTION_WAIT, 280f, 40f)
        val controlId = disconnected.rootBlocks.first()
        val statementId = disconnected.rootBlocks.last()
        val connected = WorkspaceReducer.reduce(
            disconnected,
            WorkspaceAction.Connect(
                disconnected.blocks.getValue(controlId).statementInputs.single().connection.id,
                disconnected.blocks.getValue(statementId).previous!!.id,
            ),
            factory,
        )
        val beforeCache = engine.build(connected)
        val after = WorkspaceReducer.reduce(
            connected,
            WorkspaceAction.Disconnect(connected.blocks.getValue(statementId).previous!!.id),
            factory,
        )

        assertFullFallbackEqualsOracle(connected, after, beforeCache, expectedDirtyBlocks = 2)
    }

    @Test
    fun `control field mutation uses conservative fallback and matches full layout`() {
        val before = instantiate(BlockTypes.CONTROL_REPEAT, 40f, 40f)
        val controlId = before.rootBlocks.single()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.UpdateField(
                controlId,
                "times",
                de.visualtasker.workflow.core.FieldValue.Number(12.0),
            ),
            factory,
        )

        assertFullFallbackEqualsOracle(before, after, beforeCache, expectedDirtyBlocks = 1)
    }

    @Test
    fun `large 181 block subtree move reuses every measurement`() {
        val before = largeTwoChainFixture()
        val movedRoot = before.rootBlocks.last()
        val beforeCache = engine.build(before)
        val after = WorkspaceReducer.reduce(
            before,
            WorkspaceAction.MoveRoot(movedRoot, 720f, 480f),
            factory,
        )

        val incremental = engine.update(before, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(181, after.blocks.size)
        assertEquals(LayoutUpdatePath.ROOT_TRANSLATION, incremental.updateDiagnostics.path)
        assertEquals(0, incremental.updateDiagnostics.measuredBlocks)
        assertEquals(181, incremental.updateDiagnostics.reusedLayoutBlocks)
        assertEquals(98, incremental.updateDiagnostics.repositionedBlocks)
        assertGeometryEquals(oracle, incremental)
    }

    @Test
    fun `large 181 block simple reconnect and detach stay local`() {
        val disconnected = largeTwoChainFixture()
        val firstRoot = disconnected.rootBlocks.first()
        val movedRoot = disconnected.rootBlocks.last()
        val firstTail = de.visualtasker.workflow.core.WorkspaceGraph.chainFrom(disconnected, firstRoot).last()
        val disconnectedCache = engine.build(disconnected)
        val connected = WorkspaceReducer.reduce(
            disconnected,
            WorkspaceAction.Connect(
                disconnected.blocks.getValue(firstTail).next!!.id,
                disconnected.blocks.getValue(movedRoot).previous!!.id,
            ),
            factory,
        )
        val connectedIncremental = engine.update(disconnected, connected, disconnectedCache)
        val connectedOracle = LayoutEngine(DefaultBlockRegistry).build(connected)

        assertEquals(LayoutUpdatePath.RIGID_CHAIN_RELINK, connectedIncremental.updateDiagnostics.path)
        assertEquals(0, connectedIncremental.updateDiagnostics.measuredBlocks)
        assertEquals(181, connectedIncremental.updateDiagnostics.reusedLayoutBlocks)
        assertEquals(98, connectedIncremental.updateDiagnostics.repositionedBlocks)
        assertGeometryEquals(connectedOracle, connectedIncremental)

        val unlinked = WorkspaceReducer.reduce(
            connected,
            WorkspaceAction.Disconnect(connected.blocks.getValue(firstTail).next!!.id),
            factory,
        )
        val after = WorkspaceReducer.reduce(
            unlinked,
            WorkspaceAction.MoveRoot(movedRoot, 420f, 80f),
            factory,
        )
        val detachedIncremental = engine.update(connected, after, connectedIncremental)
        val detachedOracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(LayoutUpdatePath.RIGID_CHAIN_RELINK, detachedIncremental.updateDiagnostics.path)
        assertEquals(0, detachedIncremental.updateDiagnostics.measuredBlocks)
        assertEquals(181, detachedIncremental.updateDiagnostics.reusedLayoutBlocks)
        assertEquals(98, detachedIncremental.updateDiagnostics.repositionedBlocks)
        assertGeometryEquals(detachedOracle, detachedIncremental)
    }

    private fun instantiate(type: String, x: Float, y: Float) = instantiate(
        de.visualtasker.workflow.core.WorkspaceDocument(id = "incremental-layout"),
        type,
        x,
        y,
    )

    private fun instantiate(
        document: de.visualtasker.workflow.core.WorkspaceDocument,
        type: String,
        x: Float,
        y: Float,
    ) = WorkspaceReducer.reduce(
        document,
        WorkspaceAction.InstantiateBlock(type, x, y),
        factory,
    )

    private fun largeTwoChainFixture(): de.visualtasker.workflow.core.WorkspaceDocument {
        var document = de.visualtasker.workflow.core.WorkspaceDocument(id = "large-incremental-layout")
        val chains = listOf(83 to 40f, 98 to 420f)
        chains.forEach { (size, x) ->
            val ids = buildList {
                repeat(size) { index ->
                    document = instantiate(document, BlockTypes.ACTION_WAIT, x, 40f + index * 48f)
                    add(document.rootBlocks.last())
                }
            }
            ids.zipWithNext().forEach { (upperId, lowerId) ->
                document = WorkspaceReducer.reduce(
                    document,
                    WorkspaceAction.Connect(
                        document.blocks.getValue(upperId).next!!.id,
                        document.blocks.getValue(lowerId).previous!!.id,
                    ),
                    factory,
                )
            }
        }
        return document
    }

    private fun assertFullFallbackEqualsOracle(
        before: de.visualtasker.workflow.core.WorkspaceDocument,
        after: de.visualtasker.workflow.core.WorkspaceDocument,
        beforeCache: LayoutCache,
        expectedDirtyBlocks: Int,
    ) {
        val updated = engine.update(before, after, beforeCache)
        val oracle = LayoutEngine(DefaultBlockRegistry).build(after)

        assertEquals(LayoutUpdatePath.FULL_BUILD, updated.updateDiagnostics.path)
        assertEquals(expectedDirtyBlocks, updated.updateDiagnostics.dirtyBlocks)
        assertEquals(after.blocks.size, updated.updateDiagnostics.measuredBlocks)
        assertEquals(0, updated.updateDiagnostics.reusedLayoutBlocks)
        assertEquals(1, updated.updateDiagnostics.fullBuilds)
        assertGeometryEquals(oracle, updated)
    }

    private fun assertGeometryEquals(expected: LayoutCache, actual: LayoutCache) {
        assertEquals(expected.documentVersion, actual.documentVersion)
        assertEquals(expected.flatIndex.visibleBlocks, actual.flatIndex.visibleBlocks)
        assertEquals(expected.flatIndex.hitPrimitives, actual.flatIndex.hitPrimitives)
        assertEquals(expected.flatIndex.connectionAnchors, actual.flatIndex.connectionAnchors)
        assertEquals(expected.flatIndex.statementSlots, actual.flatIndex.statementSlots)
        assertEquals(expected.flatIndex.branchSections, actual.flatIndex.branchSections)
        assertEquals(expected.flatIndex.inlineReporterLayouts, actual.flatIndex.inlineReporterLayouts)
        assertEquals(expected.measuredLayoutTree, actual.measuredLayoutTree)
        assertEquals(expected.placedLayoutTree, actual.placedLayoutTree)
    }
}
