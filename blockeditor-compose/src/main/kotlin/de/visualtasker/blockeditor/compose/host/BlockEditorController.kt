package de.visualtasker.blockeditor.compose.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.BlockNode
import de.visualtasker.blockeditor.domain.Connection
import de.visualtasker.blockeditor.domain.ConnectionId
import de.visualtasker.blockeditor.domain.ConnectionKind
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.StatementInput
import de.visualtasker.blockeditor.domain.ValueInput
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.WorkspaceState
import de.visualtasker.blockeditor.domain.allConnections
import de.visualtasker.blockeditor.domain.newBlockId
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.domain.withConnectionUpdated
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.interaction.BlockTouchZone
import de.visualtasker.blockeditor.emscript.EmscriptGenerator
import de.visualtasker.blockeditor.emscript.WorkspaceCodeGenerator
import de.visualtasker.blockeditor.interaction.DragLayoutPreview
import de.visualtasker.blockeditor.interaction.DragOperations
import de.visualtasker.blockeditor.interaction.DragPullMode
import de.visualtasker.blockeditor.interaction.HitResult
import de.visualtasker.blockeditor.interaction.HitTest
import de.visualtasker.blockeditor.interaction.SnapEngine
import de.visualtasker.blockeditor.interaction.TransientEditorState
import de.visualtasker.blockeditor.interaction.ViewportConstraints
import de.visualtasker.blockeditor.interaction.ViewportState
import de.visualtasker.blockeditor.ir.IrGenerator
import de.visualtasker.blockeditor.layout.LayoutCache
import de.visualtasker.blockeditor.layout.LayoutEngine
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignFactory
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.CompositeBlockRegistry
import de.visualtasker.blockeditor.registry.ParameterSourceKind
import de.visualtasker.blockeditor.registry.VariableReporterFactory
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.registry.createNode
import de.visualtasker.blockeditor.serialization.WorkspaceSerializer
import de.visualtasker.blockeditor.validation.Validator
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.CommonBlockInfoFields
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import de.visualtasker.blockeditor.compose.viewmodel.parameterSourceFieldKey
import de.visualtasker.blockeditor.compose.viewmodel.parseInfoValue
import de.visualtasker.blockeditor.compose.viewmodel.toBlockInfoField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Host-independent block editor controller.
 *
 * After [close], all methods are no-ops and no callbacks are emitted.
 */
class BlockEditorController(
    initialDocument: WorkspaceDocument,
    private val callbacks: BlockEditorHostCallbacks = BlockEditorHostCallbacks.NoOp,
    override val registry: CompositeBlockRegistry = CompositeBlockRegistry(),
    private val layoutEngine: LayoutEngine = LayoutEngine(registry),
    private val snapEngine: SnapEngine = SnapEngine(),
    private val workspaceCodeGenerator: WorkspaceCodeGenerator = EmscriptGenerator(IrGenerator(registry)),
    private val debounceMillis: Long = DEFAULT_DERIVED_OUTPUT_DEBOUNCE_MS,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BlockEditorControllerState, AutoCloseable {
    private val disposed = AtomicBoolean(false)
    private var debounceJob: Job? = null
    private var lastValidDraft: String = ""

    override var document by mutableStateOf(initialDocument)
        private set

    override var layoutCache by mutableStateOf(layoutEngine.build(initialDocument))
        private set

    override var viewport by mutableStateOf(ViewportState())
        private set

    private var canvasSize by mutableStateOf<Offset2?>(null)

    private var selectedBlockId by mutableStateOf<BlockId?>(null)
    private var infoPanelBlockId by mutableStateOf<BlockId?>(null)
    private var workspaceState: WorkspaceState = WorkspaceState(initialDocument)
    private var pendingFocusBlockId: BlockId? = null
    private var pendingFocusSelect: Boolean = false
    private var initialCanvasFitApplied: Boolean = false

    override var selectedBlockIds by mutableStateOf<Set<BlockId>>(emptySet())
        private set

    override var dragRender by mutableStateOf<DragRenderState?>(null)
        private set

    override var expandedCategory by mutableStateOf<String?>(null)
        private set

    override var showBottomPanel by mutableStateOf(true)
        private set

    override var showBlockFactory by mutableStateOf(false)
        private set

    override val codePreview: String
        get() = if (disposed.get()) {
            ""
        } else {
            generateDraft(reportFailure = false) ?: lastValidDraft
        }

    val isDisposed: Boolean
        get() = disposed.get()

    val historySize: Int
        get() = workspaceState.history.undoStack.size

    val redoSize: Int
        get() = workspaceState.history.redoStack.size

    init {
        syncVariableReporters(initialDocument.variables)
        emitInitialDerivedOutputs()
    }

    fun onAction(action: WorkspaceAction) {
        if (disposed.get()) return
        val reduced = WorkspaceReducer.reduce(document, action, registry.asFactory())
        applyPersistentDocumentChange(reduced, previousDocument = document)
    }

    fun onTap(screenPoint: Offset2) {
        if (disposed.get()) return
        val zoneHit = blockTouchZoneAt(screenPoint)
        if (zoneHit == null) {
            clearSelection()
            return
        }
        val (blockId, _) = zoneHit
        selectSingle(blockId)
        infoPanelBlockId = blockId
    }

    fun onDoubleTap(screenPoint: Offset2) {
        if (disposed.get()) return
        val (blockId, zone) = blockTouchZoneAt(screenPoint) ?: return
        if (zone == BlockTouchZone.CenterLabel) {
            duplicateBlock(blockId)
        }
    }

    fun onLongPressDragStart(screenPoint: Offset2): Boolean {
        if (disposed.get()) return false
        val (blockId, zone) = blockTouchZoneAt(screenPoint) ?: return false
        return when (zone) {
            BlockTouchZone.LeftGroup -> beginBlockDrag(screenPoint, blockId, DragPullMode.StackBelow)
            BlockTouchZone.RightSingle -> beginBlockDrag(screenPoint, blockId, DragPullMode.Single)
            BlockTouchZone.CenterLabel -> beginBlockDrag(screenPoint, blockId, DragPullMode.Single)
        }
    }

    fun onPointerMove(screenPoint: Offset2) {
        if (disposed.get()) return
        val render = dragRender ?: return
        val transient = TransientEditorState(
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
        val session = updated.dragSession?.let(::clampDragSessionToCanvas) ?: return
        render.runtimeState.update(session.dragOffset, updated.activeSnapCandidate)
        dragRender = render.copy(
            session = session,
            snapCandidate = updated.activeSnapCandidate,
        )
    }

    fun onPointerUp(screenPoint: Offset2) {
        if (disposed.get()) return
        if (dragRender == null) return
        val transient = TransientEditorState(
            viewport = viewport,
            dragSession = dragRender!!.session,
            activeSnapCandidate = dragRender!!.snapCandidate,
            selectedBlockId = selectedBlockId,
        )
        val activeDrag = dragRender!!
        val (droppedDocument, newTransient) = DragOperations.endDrag(transient, document)
        val positionedDocument = preserveMissingRootOffsets(droppedDocument)
        val newDocument = clampDroppedRootToCanvas(positionedDocument, activeDrag)
        dragRender = null
        selectedBlockId = newTransient.selectedBlockId
        if (newDocument != document) {
            applyPersistentDocumentChange(newDocument)
        } else {
            layoutCache = layoutEngine.build(newDocument)
        }
    }

    fun onViewportChange(newViewport: ViewportState) {
        if (disposed.get()) return
        val previousScale = viewport.scale
        val candidate = if (newViewport.scale != previousScale) {
            constrainStartBlockVisible(newViewport)
        } else {
            newViewport
        }
        viewport = constrainWorkspaceVisible(candidate)
    }

    fun onCanvasSizeChange(size: Offset2) {
        if (disposed.get()) return
        canvasSize = size
        val pending = pendingFocusBlockId
        if (pending != null && focusBlockInCanvas(pending, selectFocusedBlock = pendingFocusSelect)) {
            pendingFocusBlockId = null
            pendingFocusSelect = false
            initialCanvasFitApplied = true
        } else if (!initialCanvasFitApplied) {
            fitWorkspaceToCanvas(force = true)
            initialCanvasFitApplied = true
        }
    }

    fun fitWorkspaceToCanvas(
        margin: Float = 32f,
        force: Boolean = false,
    ) {
        if (disposed.get()) return
        val size = canvasSize ?: return
        if (size.x <= 0f || size.y <= 0f) return
        val blocks = layoutCache.flatIndex.visibleBlocks
        if (blocks.isEmpty()) return
        if (!force && blocks.any { it.subtreeBounds.isVisibleIn(viewport, size, margin) }) return
        val left = blocks.minOf { it.subtreeBounds.x }
        val top = blocks.minOf { it.subtreeBounds.y }
        val right = blocks.maxOf { it.subtreeBounds.right }
        val bottom = blocks.maxOf { it.subtreeBounds.bottom }
        val contentWidth = max(1f, right - left)
        val contentHeight = max(1f, bottom - top)
        val availableWidth = max(1f, size.x - margin * 2f)
        val availableHeight = max(1f, size.y - margin * 2f)
        val scale = min(availableWidth / contentWidth, availableHeight / contentHeight)
            .coerceIn(0.5f, 1.25f)
        val panX = (size.x - contentWidth * scale) / 2f - left * scale
        val panY = (size.y - contentHeight * scale) / 2f - top * scale
        if (!panX.isFinite() || !panY.isFinite() || !scale.isFinite()) return
        viewport = ViewportState(panX = panX, panY = panY, scale = scale)
    }

    fun zoomIn() {
        zoomBy(1.2f)
    }

    fun zoomOut() {
        zoomBy(1f / 1.2f)
    }

    fun undo(): Boolean {
        if (disposed.get()) return false
        val state = workspaceState.undo() ?: return false
        applyWorkspaceState(state)
        return true
    }

    fun redo(): Boolean {
        if (disposed.get()) return false
        val state = workspaceState.redo() ?: return false
        applyWorkspaceState(state)
        return true
    }

    fun deleteSelectedBlock(): Boolean {
        if (disposed.get()) return false
        val activeDrag = dragRender
        if (activeDrag != null) {
            val toDelete = activeDrag.session.includedBlocks.filter { it in document.blocks }
            if (toDelete.isEmpty()) return false
            dragRender = null
            clearSelection()
            val updated = deleteDragGroup(document, toDelete.toSet())
            val changed = updated != document
            applyPersistentDocumentChange(updated, previousDocument = document)
            return changed
        }
        val selected = selectedBlockId ?: return false
        if (selected !in document.blocks) {
            clearSelection()
            return false
        }
        dragRender = null
        clearSelection()
        val updated = deleteSingleBlockPreservingChain(document, selected)
        applyPersistentDocumentChange(updated, previousDocument = document)
        return true
    }

    fun onCategoryClick(category: String) {
        if (disposed.get()) return
        expandedCategory = if (expandedCategory == category) null else category
        showBlockFactory = false
    }

    fun dismissCategory() {
        if (disposed.get()) return
        expandedCategory = null
    }

    override fun definitionsForExpandedCategory(): List<BlockDefinition> {
        if (disposed.get()) return emptyList()
        val category = expandedCategory ?: return emptyList()
        return registry.definitionsByCategory(category)
            .filter { definition ->
                definition.paletteVisible &&
                    (category != BlockCategories.VARIABLE || definition.id != BlockTypes.VARIABLE_GET)
            }
            .sortedWith(
                compareBy<BlockDefinition> {
                    when {
                        it.id == BlockTypes.VARIABLE_SET -> 0
                        it.id.startsWith(BlockTypes.VARIABLE_REPORTER_PREFIX) -> 1
                        else -> 2
                    }
                }.thenBy(BlockDefinition::paletteOrder).thenBy { it.label.lowercase() },
            )
    }

    fun createVariable(name: String, type: String) {
        if (disposed.get()) return
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
        if (disposed.get()) return
        val y = 120f + (document.rootBlocks.size * 24f)
        onAction(WorkspaceAction.InstantiateBlock(definition.id, 96f, y))
    }

    fun toggleBottomPanel() {
        if (disposed.get()) return
        showBottomPanel = !showBottomPanel
    }

    /** Explicitly regenerates the derived code with the same generator used by all other paths. */
    fun regenerateCode(): String? {
        if (disposed.get()) return null
        return generateDraft(reportFailure = true)?.also(callbacks::onEmscriptDraftChanged)
    }

    override fun selectedBlockInfo(): BlockInfoSnapshot? {
        if (disposed.get()) return null
        val blockId = infoPanelBlockId ?: return null
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
            branchCount = block.ifBranchCount().takeIf { block.statementInputs.isNotEmpty() } ?: 0,
        )
    }

    fun updateBlockField(fieldKey: String, rawValue: String) {
        if (disposed.get()) return
        val blockId = selectedBlockId ?: return
        val block = document.blocks[blockId] ?: return
        val fieldDef = (registry.getDefinition(block.type)?.fields.orEmpty() + CommonBlockInfoFields)
            .find { it.key == fieldKey }
            ?: return
        val parsed = fieldDef.parseInfoValue(rawValue) ?: return
        onAction(WorkspaceAction.UpdateField(blockId, fieldKey, parsed))
    }

    fun updateBlockFieldSource(fieldKey: String, rawSource: String) {
        if (disposed.get()) return
        val blockId = selectedBlockId ?: return
        val block = document.blocks[blockId] ?: return
        val fieldDef = (registry.getDefinition(block.type)?.fields.orEmpty() + CommonBlockInfoFields)
            .find { it.key == fieldKey }
            ?: return
        val source = ParameterSourceKind.entries.firstOrNull { it.name == rawSource } ?: return
        if (source !in fieldDef.sourceOptions) return
        onAction(WorkspaceAction.UpdateField(blockId, parameterSourceFieldKey(fieldKey), FieldValue.Text(source.name)))
    }

    fun replaceSelectedBlockType(targetType: String): Boolean {
        if (disposed.get()) return false
        val blockId = selectedBlockId ?: return false
        return replaceBlockType(blockId, targetType)
    }

    fun addSelectedIfBranch(
        ifType: String,
        ifElseType: String,
        maxBranches: Int = 8,
    ): Boolean {
        if (disposed.get()) return false
        val blockId = selectedBlockId ?: return false
        val block = document.blocks[blockId] ?: return false
        if (block.type != ifType && block.type != ifElseType) return false
        val nextCount = (block.ifBranchCount() + 1).coerceAtMost(maxBranches)
        if (nextCount == block.ifBranchCount()) return false
        return replaceIfBranchShape(blockId, ifType, ifElseType, nextCount)
    }

    fun removeSelectedIfBranch(
        ifType: String,
        ifElseType: String,
    ): Boolean {
        if (disposed.get()) return false
        val blockId = selectedBlockId ?: return false
        val block = document.blocks[blockId] ?: return false
        if (block.type != ifType && block.type != ifElseType) return false
        val nextCount = (block.ifBranchCount() - 1).coerceAtLeast(1)
        if (nextCount == block.ifBranchCount()) return false
        return replaceIfBranchShape(blockId, ifType, ifElseType, nextCount)
    }

    fun openBlockFactory() {
        if (disposed.get()) return
        showBlockFactory = true
        expandedCategory = BlockCategories.CUSTOM
    }

    fun dismissBlockFactory() {
        if (disposed.get()) return
        showBlockFactory = false
    }

    fun createCustomBlock(blueprint: BlockDesignBlueprint) {
        if (disposed.get()) return
        val definition = BlockDesignFactory.create(blueprint)
        registry.register(definition)
        expandedCategory = definition.category
        addBlockFromPalette(definition)
    }

    fun clearWorkspace() {
        if (disposed.get()) return
        dragRender = null
        clearSelection()
        expandedCategory = null
        showBlockFactory = false
        applyPersistentDocumentChange(WorkspaceBootstrap.starter())
        viewport = ViewportState()
        initialCanvasFitApplied = false
    }

    fun replaceWorkspaceDocument(
        newDocument: WorkspaceDocument,
        recordHistory: Boolean = true,
        focusBlockId: BlockId? = newDocument.rootBlocks.firstOrNull(),
        selectFocusedBlock: Boolean = false,
    ) {
        if (disposed.get()) return
        dragRender = null
        clearSelection()
        applyPersistentDocumentChange(newDocument, recordHistory = recordHistory)
        initialCanvasFitApplied = false
        val focused = focusBlockId?.takeIf { it in document.blocks } ?: return
        if (focusBlockInCanvas(focused, selectFocusedBlock)) {
            initialCanvasFitApplied = true
        } else {
            if (selectFocusedBlock) {
                selectSingle(focused)
            }
            pendingFocusBlockId = focused
            pendingFocusSelect = selectFocusedBlock
        }
    }

    override fun close() {
        if (!disposed.compareAndSet(false, true)) return
        debounceJob?.cancel()
        debounceJob = null
        coroutineScope.cancel()
    }

    private fun applyPersistentDocumentChange(
        newDocument: WorkspaceDocument,
        previousDocument: WorkspaceDocument? = null,
        recordHistory: Boolean = true,
    ) {
        if (disposed.get()) return
        if (newDocument == document) return
        val nextState = if (recordHistory) {
            previousDocument?.let { baseline ->
                workspaceState.copy(document = baseline).record(newDocument)
            } ?: workspaceState.record(newDocument)
        } else {
            workspaceState.replaceWithoutHistory(newDocument)
        }
        applyWorkspaceState(nextState)
    }

    private fun applyWorkspaceState(nextState: WorkspaceState) {
        if (disposed.get()) return
        workspaceState = nextState
        val newDocument = nextState.document
        document = newDocument
        syncVariableReporters(newDocument.variables)
        layoutCache = layoutEngine.build(newDocument)
        callbacks.onWorkspaceDocumentChanged(WorkspaceSerializer.serialize(newDocument))
        scheduleDerivedOutputs()
    }

    private fun deleteSingleBlockPreservingChain(
        source: WorkspaceDocument,
        blockId: BlockId,
    ): WorkspaceDocument {
        val block = source.blocks[blockId] ?: return source
        val previousId = WorkspaceGraph.previousChain(source, blockId)
        val nextId = WorkspaceGraph.nextChain(source, blockId)
        val directStatementSlot = if (previousId == null) {
            WorkspaceGraph.slotContaining(source, blockId)?.takeIf { (parentId, slotName) ->
                WorkspaceGraph.statementStackHead(source, parentId, slotName) == blockId
            }
        } else {
            null
        }
        val nestedToRemove = collectOwnedNestedBlocks(source, blockId)
        val toRemove = nestedToRemove + blockId
        var blocks = source.blocks.toMutableMap()
        var promotedRootId: BlockId? = null

        toRemove.forEach { removedId ->
            source.blocks[removedId]?.allConnections().orEmpty().forEach { connection ->
                val partnerId = connection.connectedTo ?: return@forEach
                val (partnerBlockId, _) = WorkspaceGraph.findConnection(source, partnerId) ?: return@forEach
                if (partnerBlockId !in toRemove) {
                    blocks[partnerBlockId] = blocks[partnerBlockId]
                        ?.withConnectionUpdated(partnerId) { it.copy(connectedTo = null) }
                        ?: return@forEach
                }
            }
        }

        val roots = source.rootBlocks.toMutableList()
        when {
            previousId != null -> {
                val previousNext = source.blocks[previousId]?.next?.id
                val nextPrevious = nextId?.let { source.blocks[it]?.previous?.id }
                if (previousNext != null) {
                    blocks[previousId]?.let { previous ->
                        blocks[previousId] = previous.withConnectionUpdated(previousNext) {
                            it.copy(connectedTo = nextPrevious)
                        }
                    }
                }
                if (nextId != null && nextPrevious != null) {
                    blocks[nextId]?.let { next ->
                        blocks[nextId] = next.withConnectionUpdated(nextPrevious) {
                            it.copy(connectedTo = previousNext)
                        }
                    }
                }
            }
            directStatementSlot != null -> {
                val (parentId, slotName) = directStatementSlot
                val slotConnection = source.blocks[parentId]
                    ?.statementInputs
                    ?.find { it.name == slotName }
                    ?.connection
                    ?.id
                val nextPrevious = nextId?.let { source.blocks[it]?.previous?.id }
                if (slotConnection != null) {
                    blocks[parentId]?.let { parent ->
                        blocks[parentId] = parent.withConnectionUpdated(slotConnection) {
                            it.copy(connectedTo = nextPrevious)
                        }
                    }
                }
                if (nextId != null && nextPrevious != null) {
                    blocks[nextId]?.let { next ->
                        blocks[nextId] = next.withConnectionUpdated(nextPrevious) {
                            it.copy(connectedTo = slotConnection)
                        }
                    }
                    roots.remove(nextId)
                }
            }
            nextId != null -> {
                val nextPrevious = source.blocks[nextId]?.previous?.id
                if (nextPrevious != null) {
                    blocks[nextId]?.let { next ->
                        blocks[nextId] = next.withConnectionUpdated(nextPrevious) {
                            it.copy(connectedTo = null)
                        }
                    }
                }
                val selectedRootIndex = roots.indexOf(blockId)
                if (selectedRootIndex >= 0) {
                    roots[selectedRootIndex] = nextId
                    promotedRootId = nextId
                } else if (nextId !in roots) {
                    roots += nextId
                    promotedRootId = nextId
                }
            }
        }

        toRemove.forEach(blocks::remove)
        val reduced = source.copy(
            version = source.version + 1,
            blocks = blocks,
            rootPositions = source.rootPositions - toRemove,
        )
        val withNextRootPosition = promotedRootId?.let { id ->
            source.rootOffset(blockId)?.let { offset ->
                reduced.withRootOffset(id, offset.x, offset.y)
            }
        } ?: reduced
        return withNextRootPosition.copy(
            rootBlocks = WorkspaceGraph.pruneRootBlocks(
                withNextRootPosition,
                roots.filter { it !in toRemove },
            ),
            rootPositions = withNextRootPosition.rootPositions.filterKeys {
                it in roots.filter { root -> root !in toRemove }
            },
        )
    }

    private fun deleteDragGroup(
        source: WorkspaceDocument,
        blockIds: Set<BlockId>,
    ): WorkspaceDocument {
        if (blockIds.isEmpty()) return source
        val toRemove = blockIds.filterTo(mutableSetOf()) { it in source.blocks }
        if (toRemove.isEmpty()) return source
        var blocks = source.blocks.toMutableMap()
        toRemove.forEach { removedId ->
            source.blocks[removedId]?.allConnections().orEmpty().forEach { connection ->
                val partnerId = connection.connectedTo ?: return@forEach
                val (partnerBlockId, _) = WorkspaceGraph.findConnection(source, partnerId) ?: return@forEach
                if (partnerBlockId !in toRemove) {
                    blocks[partnerBlockId] = blocks[partnerBlockId]
                        ?.withConnectionUpdated(partnerId) { it.copy(connectedTo = null) }
                        ?: return@forEach
                }
            }
        }
        toRemove.forEach(blocks::remove)
        val reduced = source.copy(
            version = source.version + 1,
            blocks = blocks,
            rootBlocks = source.rootBlocks.filter { it !in toRemove },
            rootPositions = source.rootPositions - toRemove,
        )
        return reduced.copy(
            rootBlocks = WorkspaceGraph.pruneRootBlocks(reduced, reduced.rootBlocks),
            rootPositions = reduced.rootPositions.filterKeys { it in reduced.rootBlocks },
        )
    }

    private fun collectOwnedNestedBlocks(
        source: WorkspaceDocument,
        blockId: BlockId,
    ): Set<BlockId> {
        val result = mutableSetOf<BlockId>()
        fun walk(id: BlockId) {
            val block = source.blocks[id] ?: return
            block.statementInputs.forEach { slot ->
                WorkspaceGraph.statementStack(source, id, slot.name).forEach { childId ->
                    if (result.add(childId)) walk(childId)
                }
            }
            block.valueInputs.forEach { input ->
                val connected = input.connection.connectedTo ?: return@forEach
                val (childId, connection) = WorkspaceGraph.findConnection(source, connected) ?: return@forEach
                if (connection.kind == de.visualtasker.blockeditor.domain.ConnectionKind.Output && result.add(childId)) {
                    walk(childId)
                }
            }
        }
        walk(blockId)
        return result
    }

    private fun zoomBy(factor: Float) {
        if (disposed.get()) return
        val size = canvasSize ?: return
        val centroid = Offset2(size.x / 2f, size.y / 2f)
        onViewportChange(
            viewport.withTransform(
                centroid = centroid,
                panDelta = Offset2(0f, 0f),
                zoomFactor = factor,
            ),
        )
    }

    private fun focusBlockInCanvas(
        blockId: BlockId,
        selectFocusedBlock: Boolean,
        margin: Float = 32f,
    ): Boolean {
        val size = canvasSize ?: return false
        if (size.x <= 0f || size.y <= 0f) return false
        val layout = layoutCache.flatIndex.visibleBlocks.find { it.blockId == blockId }
            ?: return false
        val bounds = layout.subtreeBounds
        val contentWidth = max(1f, bounds.width)
        val contentHeight = max(1f, bounds.height)
        val availableWidth = max(1f, size.x - margin * 2f)
        val availableHeight = max(1f, size.y - margin * 2f)
        val scale = min(availableWidth / contentWidth, availableHeight / contentHeight)
            .coerceIn(0.5f, 1.25f)
        val panX = (size.x - contentWidth * scale) / 2f - bounds.x * scale
        val panY = (size.y - contentHeight * scale) / 2f - bounds.y * scale
        if (!panX.isFinite() || !panY.isFinite() || !scale.isFinite()) return false
        viewport = ViewportState(panX = panX, panY = panY, scale = scale)
        if (selectFocusedBlock) {
            selectSingle(blockId)
        }
        return true
    }

    private fun de.visualtasker.blockeditor.domain.Rect.isVisibleIn(
        viewport: ViewportState,
        canvasSize: Offset2,
        margin: Float,
    ): Boolean {
        val left = x * viewport.scale + viewport.panX
        val top = y * viewport.scale + viewport.panY
        val right = this.right * viewport.scale + viewport.panX
        val bottom = this.bottom * viewport.scale + viewport.panY
        return right >= margin &&
            bottom >= margin &&
            left <= canvasSize.x - margin &&
            top <= canvasSize.y - margin
    }

    private fun constrainWorkspaceVisible(
        candidate: ViewportState,
        margin: Float = 32f,
    ): ViewportState {
        val size = canvasSize ?: return candidate
        if (size.x <= 0f || size.y <= 0f) return candidate
        val blocks = layoutCache.flatIndex.visibleBlocks
        if (blocks.isEmpty()) return candidate
        if (blocks.any { it.subtreeBounds.isVisibleIn(candidate, size, margin) }) return candidate

        val left = blocks.minOf { it.subtreeBounds.x }
        val top = blocks.minOf { it.subtreeBounds.y }
        val right = blocks.maxOf { it.subtreeBounds.right }
        val bottom = blocks.maxOf { it.subtreeBounds.bottom }
        val contentWidth = max(1f, right - left)
        val contentHeight = max(1f, bottom - top)
        val scale = candidate.scale
        val panX = (size.x - contentWidth * scale) / 2f - left * scale
        val panY = (size.y - contentHeight * scale) / 2f - top * scale
        if (!panX.isFinite() || !panY.isFinite()) return candidate
        return candidate.copy(panX = panX, panY = panY)
    }

    private fun emitInitialDerivedOutputs() {
        if (disposed.get()) return
        emitValidationImmediate()
        emitEmscriptImmediate()
    }

    private fun scheduleDerivedOutputs() {
        if (disposed.get()) return
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(debounceMillis)
            if (disposed.get()) return@launch
            emitValidationImmediate()
            emitEmscriptImmediate()
        }
    }

    private fun emitValidationImmediate() {
        if (disposed.get()) return
        val result = Validator.validate(document, registry)
        callbacks.onValidationErrors(result.errors)
    }

    private fun emitEmscriptImmediate() {
        if (disposed.get()) return
        generateDraft(reportFailure = true)?.also(callbacks::onEmscriptDraftChanged)
    }

    private fun generateDraft(reportFailure: Boolean): String? = runCatching {
        workspaceCodeGenerator.generate(document)
    }.fold(
        onSuccess = { draft ->
            lastValidDraft = draft
            draft
        },
        onFailure = { error ->
            if (reportFailure) {
                val reason = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: "Unknown error"
                callbacks.onEmscriptGenerationFailed("EMScript generation failed: $reason")
            }
            null
        },
    )

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

    private fun hitAt(screenPoint: Offset2): HitResult {
        val workspacePoint = viewport.localToWorkspace(screenPoint)
        return HitTest.hitTest(layoutCache.flatIndex, workspacePoint)
    }

    private fun blockTouchZoneAt(screenPoint: Offset2): Pair<BlockId, BlockTouchZone>? {
        val workspacePoint = viewport.localToWorkspace(screenPoint)
        val hit = hitAt(screenPoint)
        val hitBlockId = selectableBlockId(hit)
        val fallbackBlock = layoutCache.flatIndex.visibleBlocks
            .asSequence()
            .sortedByDescending { it.zIndex }
            .firstOrNull { it.bounds.contains(workspacePoint.x, workspacePoint.y) }
        val blockId = hitBlockId ?: fallbackBlock?.blockId ?: return null
        val bounds = layoutCache.flatIndex.visibleBlocks.find { it.blockId == blockId }?.bounds
        val zone = DragOperations.detectTouchZone(bounds, workspacePoint)
        return blockId to zone
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
        infoPanelBlockId = null
    }

    private fun duplicateBlock(blockId: BlockId): Boolean {
        val block = document.blocks[blockId] ?: return false
        val definition = registry.getDefinition(block.type) ?: return false
        val duplicateId = newBlockId()
        val duplicate = definition.createNode(duplicateId).copy(
            fields = block.fields,
            collapsed = block.collapsed,
            metadata = block.metadata.filterKeys { key ->
                !key.startsWith("macro.runtime.")
            },
        )
        val sourceBounds = layoutCache.flatIndex.visibleBlocks.find { it.blockId == blockId }?.bounds
        val x = (sourceBounds?.x ?: document.rootOffset(blockId)?.x ?: 96f) + 36f
        val y = (sourceBounds?.y ?: document.rootOffset(blockId)?.y ?: 120f) + 36f
        val withDuplicateBlock = document.copy(
            version = document.version + 1,
            blocks = document.blocks + (duplicateId to duplicate),
        ).withRootOffset(duplicateId, x, y)
        val updated = withDuplicateBlock.copy(
            rootBlocks = WorkspaceGraph.pruneRootBlocks(withDuplicateBlock, withDuplicateBlock.rootBlocks + duplicateId),
        )
        applyPersistentDocumentChange(updated, previousDocument = document)
        selectSingle(duplicateId)
        infoPanelBlockId = duplicateId
        return true
    }

    private fun replaceBlockType(blockId: BlockId, targetType: String): Boolean {
        val source = document.blocks[blockId] ?: return false
        if (source.type == targetType) return false
        val targetDefinition = registry.getDefinition(targetType) ?: return false
        val targetTemplate = targetDefinition.createNode(blockId)
        val targetConnectionIds = targetTemplate.allConnections().map { it.id }.toSet()
        val promotedRoots = mutableListOf<BlockId>()
        var blocks = document.blocks.toMutableMap()

        source.allConnections()
            .filter { it.id !in targetConnectionIds }
            .forEach { removedConnection ->
                val partnerId = removedConnection.connectedTo ?: return@forEach
                val (partnerBlockId, partnerConnection) = WorkspaceGraph.findConnection(document, partnerId)
                    ?: return@forEach
                blocks[partnerBlockId] = blocks[partnerBlockId]
                    ?.withConnectionUpdated(partnerConnection.id) { it.copy(connectedTo = null) }
                    ?: return@forEach
                if (removedConnection.kind == de.visualtasker.blockeditor.domain.ConnectionKind.StatementInput &&
                    partnerConnection.kind == de.visualtasker.blockeditor.domain.ConnectionKind.Previous
                ) {
                    promotedRoots += partnerBlockId
                }
            }

        val sourceValueInputs = source.valueInputs.associateBy { it.name }
        val sourceStatementInputs = source.statementInputs.associateBy { it.name }
        val replacement = targetTemplate.copy(
            fields = targetTemplate.fields + source.fields,
            previous = source.previous?.takeIf { targetTemplate.previous != null },
            next = source.next?.takeIf { targetTemplate.next != null },
            output = source.output?.takeIf { targetTemplate.output != null },
            valueInputs = targetTemplate.valueInputs.map { input ->
                sourceValueInputs[input.name]?.let { existing ->
                    input.copy(connection = existing.connection)
                } ?: input
            },
            statementInputs = targetTemplate.statementInputs.map { input ->
                sourceStatementInputs[input.name]?.let { existing ->
                    input.copy(connection = existing.connection)
                } ?: input
            },
            collapsed = source.collapsed,
            metadata = source.metadata,
        )
        blocks[blockId] = replacement
        val updated = document.copy(
            version = document.version + 1,
            blocks = blocks,
            rootBlocks = WorkspaceGraph.pruneRootBlocks(document.copy(blocks = blocks), document.rootBlocks + promotedRoots),
        )
        applyPersistentDocumentChange(updated, previousDocument = document)
        selectSingle(blockId)
        infoPanelBlockId = blockId
        return true
    }

    private fun replaceIfBranchShape(
        blockId: BlockId,
        ifType: String,
        ifElseType: String,
        branchCount: Int,
    ): Boolean {
        val targetType = if (branchCount <= 1) ifType else ifElseType
        return replaceBlockType(
            blockId = blockId,
            targetType = targetType,
            branchCount = branchCount.coerceIn(1, 8),
        )
    }

    private fun replaceBlockType(
        blockId: BlockId,
        targetType: String,
        branchCount: Int,
    ): Boolean {
        val source = document.blocks[blockId] ?: return false
        val targetDefinition = registry.getDefinition(targetType) ?: return false
        val targetTemplate = targetDefinition.createNode(blockId)
        val dynamicTemplate = targetTemplate.withIfBranches(branchCount)
        val targetConnectionIds = dynamicTemplate.allConnections().map { it.id }.toSet()
        val promotedRoots = mutableListOf<BlockId>()
        var blocks = document.blocks.toMutableMap()

        source.allConnections()
            .filter { it.id !in targetConnectionIds }
            .forEach { removedConnection ->
                val partnerId = removedConnection.connectedTo ?: return@forEach
                val (partnerBlockId, partnerConnection) = WorkspaceGraph.findConnection(document, partnerId)
                    ?: return@forEach
                blocks[partnerBlockId] = blocks[partnerBlockId]
                    ?.withConnectionUpdated(partnerConnection.id) { it.copy(connectedTo = null) }
                    ?: return@forEach
                if (removedConnection.kind == ConnectionKind.StatementInput &&
                    partnerConnection.kind == ConnectionKind.Previous
                ) {
                    promotedRoots += partnerBlockId
                }
            }

        val sourceValueInputs = source.valueInputs.associateBy { it.name }
        val sourceStatementInputs = source.statementInputs.associateBy { it.name }
        val replacement = dynamicTemplate.copy(
            fields = dynamicTemplate.fields + source.fields,
            previous = source.previous?.takeIf { dynamicTemplate.previous != null },
            next = source.next?.takeIf { dynamicTemplate.next != null },
            output = source.output?.takeIf { dynamicTemplate.output != null },
            valueInputs = dynamicTemplate.valueInputs.map { input ->
                sourceValueInputs[input.name]?.let { existing ->
                    input.copy(connection = existing.connection)
                } ?: input
            },
            statementInputs = dynamicTemplate.statementInputs.map { input ->
                sourceStatementInputs[input.name]?.let { existing ->
                    input.copy(connection = existing.connection)
                } ?: input
            },
            collapsed = source.collapsed,
            metadata = source.metadata + ("if.branchCount" to branchCount.toString()),
        )
        blocks[blockId] = replacement
        val updated = preserveMissingRootOffsets(
            document.copy(
                version = document.version + 1,
                blocks = blocks,
                rootBlocks = WorkspaceGraph.pruneRootBlocks(document.copy(blocks = blocks), document.rootBlocks + promotedRoots),
            ),
        )
        applyPersistentDocumentChange(updated, previousDocument = document)
        selectSingle(blockId)
        infoPanelBlockId = blockId
        return true
    }

    private fun clampDroppedRootToCanvas(
        source: WorkspaceDocument,
        render: DragRenderState,
    ): WorkspaceDocument {
        val size = canvasSize ?: return source
        if (size.x <= 0f || size.y <= 0f || viewport.scale <= 0f) return source
        val rootId = render.session.rootBlockId
        if (rootId !in source.rootBlocks) return source
        val rootOffset = source.rootOffset(rootId) ?: return source
        val rootLayout = render.dragLayoutCache.flatIndex.visibleBlocks
            .find { it.blockId == rootId }
            ?: return source
        val visibleLeft = -viewport.panX / viewport.scale
        val visibleTop = -viewport.panY / viewport.scale
        val visibleRight = (size.x - viewport.panX) / viewport.scale
        val visibleBottom = (size.y - viewport.panY) / viewport.scale
        val subtreeLeftFromRoot = rootLayout.subtreeBounds.x - rootLayout.bounds.x
        val subtreeTopFromRoot = rootLayout.subtreeBounds.y - rootLayout.bounds.y
        val subtreeRightFromRoot = rootLayout.subtreeBounds.right - rootLayout.bounds.x
        val subtreeBottomFromRoot = rootLayout.subtreeBounds.bottom - rootLayout.bounds.y
        val clampedX = rootOffset.x.clampToVisibleRange(
            min = visibleLeft - subtreeLeftFromRoot,
            max = visibleRight - subtreeRightFromRoot,
        )
        val clampedY = rootOffset.y.clampToVisibleRange(
            min = visibleTop - subtreeTopFromRoot,
            max = visibleBottom - subtreeBottomFromRoot,
        )
        if (clampedX == rootOffset.x && clampedY == rootOffset.y) return source
        return source.withRootOffset(rootId, clampedX, clampedY)
    }

    private fun clampDragSessionToCanvas(
        session: de.visualtasker.blockeditor.interaction.DragSession,
    ): de.visualtasker.blockeditor.interaction.DragSession {
        val render = dragRender ?: return session
        val size = canvasSize ?: return session
        if (size.x <= 0f || size.y <= 0f || viewport.scale <= 0f) return session
        val rootLayout = render.dragLayoutCache.flatIndex.visibleBlocks
            .find { it.blockId == session.rootBlockId }
            ?: return session

        val visibleLeft = -viewport.panX / viewport.scale
        val visibleTop = -viewport.panY / viewport.scale
        val visibleRight = (size.x - viewport.panX) / viewport.scale
        val visibleBottom = (size.y - viewport.panY) / viewport.scale
        val origin = session.originalLayoutPosition
        val subtreeLeftFromOrigin = rootLayout.subtreeBounds.x - origin.x
        val subtreeTopFromOrigin = rootLayout.subtreeBounds.y - origin.y
        val subtreeRightFromOrigin = rootLayout.subtreeBounds.right - origin.x
        val subtreeBottomFromOrigin = rootLayout.subtreeBounds.bottom - origin.y
        val minX = visibleLeft - origin.x - subtreeLeftFromOrigin
        val maxX = visibleRight - origin.x - subtreeRightFromOrigin
        val minY = visibleTop - origin.y - subtreeTopFromOrigin
        val maxY = visibleBottom - origin.y - subtreeBottomFromOrigin
        val clampedOffset = Offset2(
            session.dragOffset.x.clampToVisibleRange(minX, maxX),
            session.dragOffset.y.clampToVisibleRange(minY, maxY),
        )
        return if (clampedOffset == session.dragOffset) {
            session
        } else {
            session.copy(dragOffset = clampedOffset)
        }
    }

    private fun Float.clampToVisibleRange(min: Float, max: Float): Float {
        if (!isFinite() || !min.isFinite() || !max.isFinite()) return this
        val clamped = if (min <= max) coerceIn(min, max) else min
        return if (abs(clamped) < 0.0001f) 0f else clamped
    }

    private fun preserveMissingRootOffsets(source: WorkspaceDocument): WorkspaceDocument {
        var updated = source
        source.rootBlocks.forEach { rootId ->
            if (updated.rootOffset(rootId) != null) return@forEach
            val layout = layoutCache.flatIndex.visibleBlocks.find { it.blockId == rootId } ?: return@forEach
            updated = updated.withRootOffset(rootId, layout.bounds.x, layout.bounds.y)
        }
        return updated
    }

    private fun BlockNode.ifBranchCount(): Int {
        val explicit = metadata["if.branchCount"]?.toIntOrNull()
        if (explicit != null) return explicit.coerceIn(1, 8)
        return statementInputs.count { input ->
            input.name == BlockTypes.SLOT_THEN ||
                input.name == BlockTypes.SLOT_ELSE ||
                input.name.startsWith("ELIF_")
        }.coerceAtLeast(1)
    }

    private fun BlockNode.withIfBranches(branchCount: Int): BlockNode {
        val count = branchCount.coerceIn(1, 8)
        if (count <= 1) return this
        val elifCount = (count - 2).coerceAtLeast(0)
        val valueInputs = buildList {
            addAll(this@withIfBranches.valueInputs.filterNot { it.name.startsWith("ELIF_CONDITION_") })
            repeat(elifCount) { index ->
                val number = index + 1
                add(
                    ValueInput(
                        name = "ELIF_CONDITION_$number",
                        connection = Connection(
                            id = ConnectionId("${id.value}:ELIF_CONDITION_$number"),
                            owner = id,
                            kind = ConnectionKind.ValueInput,
                            accepts = setOf("Bool", "Boolean"),
                            slotName = "ELIF_CONDITION_$number",
                        ),
                    ),
                )
            }
        }
        val statementInputs = buildList {
            add(statementInput(BlockTypes.SLOT_THEN))
            repeat(elifCount) { index ->
                add(statementInput("ELIF_${index + 1}"))
            }
            add(statementInput(BlockTypes.SLOT_ELSE))
        }
        return copy(valueInputs = valueInputs, statementInputs = statementInputs)
    }

    private fun BlockNode.statementInput(name: String): StatementInput =
        statementInputs.find { it.name == name } ?: StatementInput(
            name = name,
            connection = Connection(
                id = ConnectionId("${id.value}:$name:stmt"),
                owner = id,
                kind = ConnectionKind.StatementInput,
                slotName = name,
            ),
        )

    private fun beginBlockDrag(screenPoint: Offset2, blockId: BlockId, pullMode: DragPullMode? = null): Boolean {
        if (blockId !in selectedBlockIds) {
            selectSingle(blockId)
        }
        infoPanelBlockId = null
        val transient = DragOperations.beginDrag(
            document = document,
            layoutCache = layoutCache,
            blockId = blockId,
            pointer = screenPoint,
            viewport = viewport,
            pullMode = pullMode,
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
            layoutDoc = preserveMissingRootOffsets(layoutDoc)
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
            ).let(::preserveMissingRootOffsets)
            val staticLayout = if (session.pullMode == DragPullMode.Single) {
                preLiftLayout
            } else {
                layoutEngine.build(layoutDoc)
            }
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
        }
        return transient.dragSession != null
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

    companion object {
        const val DEFAULT_DERIVED_OUTPUT_DEBOUNCE_MS = 200L

        /** Controller seeded with [WorkspaceBootstrap.starter]. */
        fun starter(
            callbacks: BlockEditorHostCallbacks = BlockEditorHostCallbacks.NoOp,
        ): BlockEditorController = BlockEditorController(
            initialDocument = WorkspaceBootstrap.starter(),
            callbacks = callbacks,
        )
    }
}
