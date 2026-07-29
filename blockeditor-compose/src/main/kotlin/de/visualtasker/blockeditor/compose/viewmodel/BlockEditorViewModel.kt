package de.visualtasker.blockeditor.compose.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import de.visualtasker.blockeditor.compose.debug.EditorDebugLog
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.interaction.DragLayoutPreview
import de.visualtasker.blockeditor.interaction.DragOperations
import de.visualtasker.blockeditor.interaction.DragRuntimeState
import de.visualtasker.blockeditor.interaction.DragSession
import de.visualtasker.blockeditor.interaction.HitResult
import de.visualtasker.blockeditor.interaction.HitTest
import de.visualtasker.blockeditor.interaction.SnapCandidate
import de.visualtasker.blockeditor.interaction.SnapEngine
import de.visualtasker.blockeditor.interaction.ViewportConstraints
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.emscript.EmscriptGenerator
import de.visualtasker.blockeditor.ir.IrGenerator
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignFactory
import de.visualtasker.blockeditor.registry.BlockRegistry
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.registry.ParameterSourceKind
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.VariableReporterFactory
import de.visualtasker.blockeditor.registry.asFactory

data class BlockInfoSnapshot(
    val blockId: BlockId,
    val typeId: String,
    val label: String,
    val categoryLabel: String,
    val categoryAccentArgb: Long,
    val fields: List<BlockInfoField>,
    val slotContext: String?,
    val chainSummary: String,
    val branchCount: Int = 0,
)

/** Visuelle Drag-Daten inkl. Layout-Vorschau ohne gezogene Blöcke. */
data class DragRenderState(
    val session: DragSession,
    val snapCandidate: SnapCandidate?,
    /** Gelöstes Dokument für Snap/Layout (Kette/Slot temporär offen). */
    val previewDocument: WorkspaceDocument,
    /** Hintergrund ohne gezogenen Block (Kette/Slot gelöst). */
    val staticLayoutCache: LayoutCache,
    /** Volles Layout für Drag-Positionen der gezogenen Blöcke (gleiche Topologie wie static). */
    val dragLayoutCache: LayoutCache,
    val runtimeState: DragRuntimeState = DragRuntimeState(),
)

class BlockEditorViewModel(
    initialDocument: WorkspaceDocument = WorkspaceBootstrap.starter(),
    private val registry: CompositeBlockRegistry = CompositeBlockRegistry(),
    private val layoutEngine: LayoutEngine = LayoutEngine(registry),
    private val snapEngine: SnapEngine = SnapEngine(),
    private val emscriptGenerator: EmscriptGenerator = EmscriptGenerator(IrGenerator(registry)),
) : ViewModel() {
    var document by mutableStateOf(initialDocument)
        private set

    var layoutCache by mutableStateOf(layoutEngine.build(initialDocument))
        private set

    var viewport by mutableStateOf(ViewportState())
        private set

    var canvasSize by mutableStateOf<Offset2?>(null)
        private set

    var selectedBlockId by mutableStateOf<BlockId?>(null)
        private set

    var selectedBlockIds by mutableStateOf<Set<BlockId>>(emptySet())
        private set

    /** Null = kein Drag. Nur DragLayer/SnapPreview lesen das – Workspace bleibt stabil. */
    var dragRender by mutableStateOf<DragRenderState?>(null)
        private set

    var expandedCategory by mutableStateOf<String?>(null)
        private set

    var showBottomPanel by mutableStateOf(true)
        private set

    var showBlockFactory by mutableStateOf(false)
        private set

    val codePreview: String
        get() = runCatching { emscriptGenerator.generate(document) }
            .getOrElse { "// Code preview error: ${it.message}" }

    init {
        syncVariableReporters(document.variables)
        EditorDebugLog.i("Session", "ViewModel gestartet chain=${chainSummary(document)}")
    }

    fun onAction(action: WorkspaceAction) {
        EditorDebugLog.i("Action", actionSummary(action))
        document = WorkspaceReducer.reduce(document, action, registry.asFactory())
        syncVariableReporters(document.variables)
        layoutCache = layoutEngine.build(document)
        EditorDebugLog.d("Workspace", "v${document.version} chain=${chainSummary(document)}")
    }

    fun onTap(screenPoint: Offset2) {
        val hit = hitAt(screenPoint)
        EditorDebugLog.d("Pointer", "tap screen=$screenPoint hit=${hitSummary(hit)}")
        when (val blockId = selectableBlockId(hit)) {
            null -> clearSelection()
            else -> selectSingle(blockId)
        }
    }

    fun onDoubleTap(screenPoint: Offset2) {
        val hit = hitAt(screenPoint)
        EditorDebugLog.d("Pointer", "doubleTap screen=$screenPoint hit=${hitSummary(hit)}")
        val blockId = selectableBlockId(hit) ?: return
        selectedBlockIds = if (blockId in selectedBlockIds) {
            selectedBlockIds - blockId
        } else {
            selectedBlockIds + blockId
        }
        selectedBlockId = blockId.takeIf { it in selectedBlockIds }
        if (selectedBlockIds.isEmpty()) {
            selectedBlockId = null
        }
    }

    fun onLongPressDragStart(screenPoint: Offset2): Boolean {
        val hit = hitAt(screenPoint)
        EditorDebugLog.d("Pointer", "longPress screen=$screenPoint hit=${hitSummary(hit)}")
        if (hit !is HitResult.BlockHit) return false
        return beginBlockDrag(screenPoint, hit.blockId)
    }

    fun onPointerMove(screenPoint: Offset2) {
        val render = dragRender ?: return
        val transient = de.visualtasker.blockeditor.interaction.TransientEditorState(
            viewport = viewport,
            dragSession = render.session,
            activeSnapCandidate = render.snapCandidate,
            selectedBlockId = selectedBlockId,
        )
        val (updated, _) = DragOperations.updateDrag(
            transient = transient,
            pointer = screenPoint,
            snapEngine = snapEngine,
            layoutCache = render.staticLayoutCache,
            document = render.previewDocument,
        )
        val session = updated.dragSession ?: return
        render.runtimeState.update(session.dragOffset, updated.activeSnapCandidate)
        val previousSnap = render.snapCandidate?.targetConnectionId
        val nextSnap = updated.activeSnapCandidate?.targetConnectionId
        if (previousSnap != nextSnap) {
            EditorDebugLog.d(
                "Snap",
                if (nextSnap == null) {
                    "cleared offset=${session.dragOffset}"
                } else {
                    "candidate source=${updated.activeSnapCandidate!!.sourceConnectionId.value} " +
                        "target=${nextSnap.value} dist=${updated.activeSnapCandidate!!.distance}"
                },
            )
        }
        dragRender = render.copy(
            session = session,
            snapCandidate = updated.activeSnapCandidate,
        )
    }

    fun onPointerUp(screenPoint: Offset2) {
        if (dragRender == null) return
        val snap = dragRender!!.snapCandidate
        EditorDebugLog.i(
            "Drag",
            "end snap=${snap?.targetConnectionId?.value ?: "none"} offset=${dragRender!!.session.dragOffset}",
        )
        val transient = de.visualtasker.blockeditor.interaction.TransientEditorState(
            viewport = viewport,
            dragSession = dragRender!!.session,
            activeSnapCandidate = dragRender!!.snapCandidate,
            selectedBlockId = selectedBlockId,
        )
        val (newDocument, newTransient) = DragOperations.endDrag(transient, document)
        document = newDocument
        layoutCache = layoutEngine.build(newDocument)
        dragRender = null
        selectedBlockId = newTransient.selectedBlockId
        EditorDebugLog.d("Workspace", "v${document.version} chain=${chainSummary(document)}")
    }

    fun onViewportChange(newViewport: ViewportState) {
        val previousScale = viewport.scale
        viewport = if (newViewport.scale != previousScale) {
            constrainStartBlockVisible(newViewport)
        } else {
            newViewport
        }
    }

    fun onCanvasSizeChange(size: Offset2) {
        canvasSize = size
    }

    fun fitWorkspaceToCanvas() {
        viewport = ViewportState()
    }

    fun zoomIn() {
        viewport = viewport.withTransform(
            centroid = canvasSize?.let { Offset2(it.x / 2f, it.y / 2f) } ?: Offset2(0f, 0f),
            panDelta = Offset2(0f, 0f),
            zoomFactor = 1.2f,
        )
    }

    fun zoomOut() {
        viewport = viewport.withTransform(
            centroid = canvasSize?.let { Offset2(it.x / 2f, it.y / 2f) } ?: Offset2(0f, 0f),
            panDelta = Offset2(0f, 0f),
            zoomFactor = 1f / 1.2f,
        )
    }

    fun undo(): Boolean = false

    fun redo(): Boolean = false

    private fun constrainStartBlockVisible(candidate: ViewportState): ViewportState {
        val size = canvasSize ?: return candidate
        val startId = document.rootBlocks.firstOrNull {
            document.blocks[it]?.type == BlockTypes.EVENT_START
        } ?: return candidate
        val bounds = layoutCache.flatIndex.visibleBlocks.find { it.blockId == startId }?.bounds
            ?: return candidate
        return ViewportConstraints.keepBlockVisible(
            viewport = candidate,
            blockBounds = bounds,
            viewportWidth = size.x,
            viewportHeight = size.y,
        )
    }

    fun deleteSelected() {
        val selected = selectedBlockId ?: return
        onAction(WorkspaceAction.DeleteBlock(selected))
        clearSelection()
    }

    fun deleteSelectedBlock(): Boolean {
        val selected = selectedBlockId ?: return false
        deleteSelected()
        return true
    }

    fun onCategoryClick(category: String) {
        expandedCategory = if (expandedCategory == category) null else category
        showBlockFactory = false
    }

    fun dismissCategory() {
        expandedCategory = null
    }

    fun definitionsForExpandedCategory(): List<BlockDefinition> {
        val category = expandedCategory ?: return emptyList()
        return registry.definitionsByCategory(category)
            .filter { definition ->
                category != BlockCategories.VARIABLE || definition.id != BlockTypes.VARIABLE_GET
            }
            .sortedWith(
                compareBy<BlockDefinition> {
                    when {
                        it.id == BlockTypes.VARIABLE_SET -> 0
                        it.id.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) -> 1
                        else -> 2
                    }
                }.thenBy { it.label.lowercase() },
            )
    }

    fun createVariable(name: String, type: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val variable = VariableDefinition(
            id = generateVariableId(trimmed),
            name = trimmed,
            type = type.ifBlank { "Any" },
            scope = VariableScope.Global,
        )
        onAction(WorkspaceAction.CreateVariable(variable))
        onAction(
            WorkspaceAction.InstantiateBlock(
                VariableReporterFactory.reporterId(variable.id),
                96f,
                120f + (document.rootBlocks.size * 24f),
            ),
        )
    }

    fun addBlockFromPalette(definition: BlockDefinition) {
        val y = 120f + (document.rootBlocks.size * 24f)
        onAction(WorkspaceAction.InstantiateBlock(definition.id, 96f, y))
    }

    fun toggleBottomPanel() {
        showBottomPanel = !showBottomPanel
    }

    fun selectedBlockInfo(): BlockInfoSnapshot? {
        val blockId = selectedBlockId ?: return null
        val block = document.blocks[blockId] ?: return null
        val definition = registry.getDefinition(block.type) ?: return null
        val category = BlockCategories.metaFor(definition.category)
        val slot = WorkspaceGraph.slotContaining(document, blockId)
        val slotContext = slot?.let { (parent, name) ->
            val parentDef = registry.getDefinition(document.blocks[parent]?.type ?: "")?.label ?: parent.value
            "$parentDef → $name"
        }
        val chainPart = buildString {
            if (selectedBlockIds.size > 1) {
                append("${selectedBlockIds.size} Blöcke ausgewählt")
            } else {
                when {
                    WorkspaceGraph.previousChain(document, blockId) != null -> append("verbunden oben")
                    WorkspaceGraph.nextChain(document, blockId) != null -> append("verbunden unten")
                    slot != null -> append("im Slot")
                    blockId in document.rootBlocks -> append("Root-Block")
                    else -> append("frei")
                }
            }
        }
        return BlockInfoSnapshot(
            blockId = blockId,
            typeId = block.type,
            label = definition.label,
            categoryLabel = category.label,
            categoryAccentArgb = category.accentArgb,
            fields = (definition.fields + CommonBlockInfoFields).map { it.toBlockInfoField(block) },
            slotContext = slotContext,
            chainSummary = chainPart,
        )
    }

    fun updateBlockField(fieldKey: String, rawValue: String) {
        val blockId = selectedBlockId ?: return
        val block = document.blocks[blockId] ?: return
        val fieldDef = (registry.getDefinition(block.type)?.fields.orEmpty() + CommonBlockInfoFields)
            .find { it.key == fieldKey }
            ?: return
        val parsed = fieldDef.parseInfoValue(rawValue) ?: return
        onAction(WorkspaceAction.UpdateField(blockId, fieldKey, parsed))
    }

    fun updateBlockFieldSource(fieldKey: String, rawSource: String) {
        val blockId = selectedBlockId ?: return
        val block = document.blocks[blockId] ?: return
        val fieldDef = (registry.getDefinition(block.type)?.fields.orEmpty() + CommonBlockInfoFields)
            .find { it.key == fieldKey }
            ?: return
        val source = ParameterSourceKind.entries.firstOrNull { it.name == rawSource } ?: return
        if (source !in fieldDef.sourceOptions) return
        onAction(WorkspaceAction.UpdateField(blockId, parameterSourceFieldKey(fieldKey), FieldValue.Text(source.name)))
    }

    fun openBlockFactory() {
        showBlockFactory = true
        expandedCategory = BlockCategories.CUSTOM
    }

    fun dismissBlockFactory() {
        showBlockFactory = false
    }

    fun createCustomBlock(blueprint: BlockDesignBlueprint) {
        val definition = BlockDesignFactory.create(blueprint)
        registry.register(definition)
        expandedCategory = definition.category
        addBlockFromPalette(definition)
        EditorDebugLog.i("Factory", "custom block ${definition.id}")
    }

    fun clearWorkspace() {
        EditorDebugLog.i("Reset", "workspace geleert")
        dragRender = null
        clearSelection()
        expandedCategory = null
        showBlockFactory = false
        document = WorkspaceBootstrap.starter()
        syncVariableReporters(document.variables)
        layoutCache = layoutEngine.build(document)
        viewport = ViewportState()
        EditorDebugLog.d("Workspace", "v${document.version} chain=${chainSummary(document)}")
    }

    fun clearDebugLog() {
        EditorDebugLog.clear()
    }

    private fun chainSummary(doc: WorkspaceDocument): String {
        val startId = doc.rootBlocks.firstOrNull { doc.blocks[it]?.type == BlockTypes.EVENT_START } ?: return "-"
        return WorkspaceGraph.chainFrom(doc, startId)
            .joinToString(" -> ") { blockLabel(doc, it) }
    }

    private fun blockLabel(doc: WorkspaceDocument, id: BlockId): String {
        val type = doc.blocks[id]?.type ?: return id.value.take(8)
        return type.substringAfterLast('.')
    }

    private fun actionSummary(action: WorkspaceAction): String = when (action) {
        is WorkspaceAction.InstantiateBlock -> "Instantiate ${action.definitionId} @ (${action.x}, ${action.y})"
        is WorkspaceAction.Connect -> "Connect ${action.source.value} -> ${action.target.value}"
        is WorkspaceAction.DeleteBlock -> "Delete ${action.blockId.value}"
        is WorkspaceAction.MoveRoot -> "MoveRoot ${action.blockId.value} -> (${action.x}, ${action.y})"
        is WorkspaceAction.Disconnect -> "Disconnect ${action.connection.value}"
        is WorkspaceAction.DetachBlock -> "Detach ${action.blockId.value}"
        is WorkspaceAction.Collapse -> "Collapse ${action.blockId.value}"
        is WorkspaceAction.Expand -> "Expand ${action.blockId.value}"
        is WorkspaceAction.UpdateField -> "UpdateField ${action.blockId.value}.${action.key}"
        is WorkspaceAction.CreateVariable -> "CreateVariable ${action.variable.name}"
        is WorkspaceAction.DeleteVariable -> "DeleteVariable ${action.variableId}"
    }

    private fun hitAt(screenPoint: Offset2): HitResult {
        val workspacePoint = viewport.localToWorkspace(screenPoint)
        return HitTest.hitTest(layoutCache.flatIndex, workspacePoint)
    }

    private fun selectableBlockId(hit: HitResult): BlockId? = when (hit) {
        is HitResult.BlockHit -> hit.blockId
        is HitResult.FieldHit -> hit.blockId
        is HitResult.StatementSlotHit -> hit.blockId
        else -> null
    }

    private fun selectSingle(blockId: BlockId) {
        selectedBlockIds = setOf(blockId)
        selectedBlockId = blockId
    }

    private fun clearSelection() {
        selectedBlockIds = emptySet()
        selectedBlockId = null
    }

    private fun beginBlockDrag(screenPoint: Offset2, blockId: BlockId): Boolean {
        if (blockId !in selectedBlockIds) {
            selectSingle(blockId)
        }
        val transient = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutCache,
            blockId = blockId,
            pointer = screenPoint,
            viewport = viewport,
        )
        transient.dragSession?.let { session ->
            val preLiftLayout = layoutEngine.build(document)
            val rootBounds = preLiftLayout.flatIndex.visibleBlocks
                .find { it.blockId == blockId }
                ?.bounds
            var layoutDoc = DragLayoutPreview.layoutDocument(
                document,
                blockId,
                session.includedBlocks,
            )
            if (rootBounds != null) {
                val liftedRoot = layoutDoc.blocks[blockId]
                if (liftedRoot != null) {
                    val roots = if (blockId in layoutDoc.rootBlocks) {
                        layoutDoc.rootBlocks
                    } else {
                        WorkspaceGraph.pruneRootBlocks(layoutDoc, layoutDoc.rootBlocks + blockId)
                    }
                    layoutDoc = layoutDoc.copy(
                        rootBlocks = roots,
                    ).withRootOffset(blockId, rootBounds.x, rootBounds.y)
                }
            }
            val snapDoc = DragLayoutPreview.snapDocument(
                document,
                blockId,
                session.includedBlocks,
            )
            val staticLayout = layoutEngine.build(layoutDoc)
            val dragLayout = layoutEngine.build(layoutDoc)
            val rootLayout = staticLayout.flatIndex.visibleBlocks
                .find { it.blockId == blockId }
            val liftedSession = session.copy(
                originalAnchors = staticLayout.flatIndex.connectionAnchors
                    .filter { it.ownerBlockId == blockId },
                originalLayoutPosition = if (rootLayout != null) {
                    Offset2(rootLayout.bounds.x, rootLayout.bounds.y)
                } else {
                    session.originalLayoutPosition
                },
            )
            dragRender = DragRenderState(
                session = liftedSession,
                snapCandidate = null,
                previewDocument = snapDoc,
                staticLayoutCache = staticLayout,
                dragLayoutCache = dragLayout,
            )
            EditorDebugLog.i(
                "Drag",
                "begin root=${blockLabel(document, session.rootBlockId)} " +
                    "pull=${session.pullMode} included=${session.includedBlocks.size}",
            )
        }
        return transient.dragSession != null
    }

    private fun hitSummary(hit: HitResult): String = when (hit) {
        is HitResult.BlockHit -> "Block(${hit.blockId.value.take(8)})"
        is HitResult.FieldHit -> "Field(${hit.blockId.value.take(8)}.${hit.fieldName})"
        is HitResult.StatementSlotHit -> "Slot(${hit.blockId.value.take(8)}.${hit.inputName})"
        is HitResult.ConnectionHit -> "Connection(${hit.connectionId.value})"
        HitResult.Empty -> "Empty"
    }

    private fun syncVariableReporters(variables: VariableRegistry) {
        val registeredReporterIds = registry.customDefinitions()
            .map { it.id }
            .filter { it.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) }
            .toSet()
        val desiredReporterIds = variables.variables.keys
            .map { VariableReporterFactory.reporterId(it) }
            .toSet()
        (registeredReporterIds - desiredReporterIds).forEach(registry::unregister)
        variables.variables.values.forEach { variable ->
            registry.register(VariableReporterFactory.create(variable))
        }
    }

    private fun generateVariableId(name: String): String {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .trim('_')
            .ifEmpty { "var" }
        var candidate = base
        var suffix = 1
        while (candidate in document.variables.variables) {
            candidate = "${base}_$suffix"
            suffix++
        }
        return candidate
    }
}
