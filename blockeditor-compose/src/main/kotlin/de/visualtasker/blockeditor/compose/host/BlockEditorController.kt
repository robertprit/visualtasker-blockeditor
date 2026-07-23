package de.visualtasker.blockeditor.compose.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.domain.FieldValue
import de.visualtasker.blockeditor.domain.Offset2
import de.visualtasker.blockeditor.domain.VariableDefinition
import de.visualtasker.blockeditor.domain.VariableRegistry
import de.visualtasker.blockeditor.domain.VariableScope
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.domain.WorkspaceDocument
import de.visualtasker.blockeditor.domain.WorkspaceGraph
import de.visualtasker.blockeditor.domain.WorkspaceReducer
import de.visualtasker.blockeditor.domain.WorkspaceState
import de.visualtasker.blockeditor.domain.asString
import de.visualtasker.blockeditor.domain.allConnections
import de.visualtasker.blockeditor.domain.rootOffset
import de.visualtasker.blockeditor.domain.withConnectionUpdated
import de.visualtasker.blockeditor.domain.withRootOffset
import de.visualtasker.blockeditor.emscript.EmscriptGenerator
import de.visualtasker.blockeditor.emscript.WorkspaceCodeGenerator
import de.visualtasker.blockeditor.interaction.DragLayoutPreview
import de.visualtasker.blockeditor.interaction.DragOperations
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
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.VariableReporterFactory
import de.visualtasker.blockeditor.registry.WorkspaceBootstrap
import de.visualtasker.blockeditor.registry.asFactory
import de.visualtasker.blockeditor.serialization.WorkspaceSerializer
import de.visualtasker.blockeditor.validation.Validator
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoField
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.DragRenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
    private var workspaceState: WorkspaceState = WorkspaceState(initialDocument)
    private var pendingFocusBlockId: BlockId? = null
    private var pendingFocusSelect: Boolean = false

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
        val hit = hitAt(screenPoint)
        when (val blockId = selectableBlockId(hit)) {
            null -> clearSelection()
            else -> selectSingle(blockId)
        }
    }

    fun onDoubleTap(screenPoint: Offset2) {
        if (disposed.get()) return
        val hit = hitAt(screenPoint)
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
        if (disposed.get()) return false
        val hit = hitAt(screenPoint)
        if (hit !is HitResult.BlockHit) return false
        return beginBlockDrag(screenPoint, hit.blockId)
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
        val session = updated.dragSession ?: return
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
        val (newDocument, newTransient) = DragOperations.endDrag(transient, document)
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
        } else {
            fitWorkspaceToCanvas(force = true)
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
            fields = definition.fields.map { field ->
                BlockInfoField(
                    key = field.key,
                    label = field.label.ifEmpty { field.key },
                    kind = field.kind,
                    value = block.fields[field.key]?.asString() ?: field.defaultValue,
                    options = field.options,
                )
            },
            slotContext = slotContext,
            chainSummary = chainPart,
        )
    }

    fun updateBlockField(fieldKey: String, rawValue: String) {
        if (disposed.get()) return
        val blockId = selectedBlockId ?: return
        val block = document.blocks[blockId] ?: return
        val fieldDef = registry.getDefinition(block.type)?.fields?.find { it.key == fieldKey } ?: return
        val parsed = when (fieldDef.kind) {
            FieldKind.NUMBER -> rawValue.toDoubleOrNull()?.let { FieldValue.Number(it) }
                ?: FieldValue.Number(0.0)
            FieldKind.BOOLEAN -> FieldValue.Bool(rawValue.equals("true", ignoreCase = true))
            FieldKind.CHOICE -> {
                if (fieldDef.options.none { it.value == rawValue }) return
                FieldValue.Text(rawValue)
            }
            FieldKind.TEXT -> FieldValue.Text(rawValue)
        }
        onAction(WorkspaceAction.UpdateField(blockId, fieldKey, parsed))
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
        val focused = focusBlockId?.takeIf { it in document.blocks } ?: return
        if (!focusBlockInCanvas(focused, selectFocusedBlock)) {
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
        fitWorkspaceToCanvas()
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
