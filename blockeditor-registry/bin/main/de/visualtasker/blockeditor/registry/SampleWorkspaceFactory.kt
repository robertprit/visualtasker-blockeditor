package de.visualtasker.blockeditor.registry

import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer

object SampleWorkspaceFactory {
    /** Leeres Arbeitsblatt: nur Script Start. */
    fun create(): WorkspaceDocument {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "workspace")
        document = reduce(
            document,
            WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 40f, 40f),
            factory,
        )
        return document
    }

    /** Demo-Kette für Tests: start → click → repeat → click → repeat → click → click */
    fun createDemo(): WorkspaceDocument {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "demo-workspace")

        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 40f, 40f), factory)
        val chainTypes = listOf(
            BlockTypes.ACTION_CLICK_TEXT,
            BlockTypes.CONTROL_REPEAT,
            BlockTypes.ACTION_CLICK_TEXT,
            BlockTypes.CONTROL_REPEAT,
            BlockTypes.ACTION_CLICK_TEXT,
            BlockTypes.ACTION_CLICK_TEXT,
        )

        val chainIds = mutableListOf(document.rootBlocks.first())
        var y = 120f
        chainTypes.forEach { type ->
            document = reduce(document, WorkspaceAction.InstantiateBlock(type, 40f, y), factory)
            chainIds += document.rootBlocks.last()
            y += 80f
        }

        for (i in 0 until chainIds.lastIndex) {
            val upper = document.blocks[chainIds[i]]!!
            val lower = document.blocks[chainIds[i + 1]]!!
            document = reduce(
                document,
                WorkspaceAction.Connect(upper.next!!.id, lower.previous!!.id),
                factory,
            )
        }

        return document
    }

    /** Altes Mini-Demo mit Wait + Click im Repeat-Slot (nur Tests). */
    fun createWithStatementSlot(): WorkspaceDocument {
        val factory = DefaultBlockRegistry.asFactory()
        var document = WorkspaceDocument(id = "slot-test-workspace")

        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.EVENT_START, 40f, 40f), factory)
        val startId = document.rootBlocks.first()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_WAIT, 40f, 120f), factory)
        val waitId = document.rootBlocks.last()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.CONTROL_REPEAT, 40f, 220f), factory)
        val repeatId = document.rootBlocks.last()
        document = reduce(document, WorkspaceAction.InstantiateBlock(BlockTypes.ACTION_CLICK_TEXT, 72f, 320f), factory)
        val clickId = document.rootBlocks.last()

        val start = document.blocks[startId]!!
        val wait = document.blocks[waitId]!!
        val repeat = document.blocks[repeatId]!!
        val click = document.blocks[clickId]!!

        document = reduce(document, WorkspaceAction.Connect(start.next!!.id, wait.previous!!.id), factory)
        document = reduce(document, WorkspaceAction.Connect(wait.next!!.id, repeat.previous!!.id), factory)
        document = reduce(
            document,
            WorkspaceAction.Connect(repeat.statementInputs.first().connection.id, click.previous!!.id),
            factory,
        )
        return document
    }

    fun mainChain(document: WorkspaceDocument): List<de.visualtasker.blockeditor.domain.BlockId> {
        val startId = document.rootBlocks.first {
            document.blocks[it]?.type == BlockTypes.EVENT_START
        }
        return WorkspaceGraph.chainFrom(document, startId)
    }

    private fun reduce(
        document: WorkspaceDocument,
        action: WorkspaceAction,
        factory: de.visualtasker.blockeditor.domain.BlockFactory,
    ): WorkspaceDocument = WorkspaceReducer.reduce(document, action, factory)
}
