package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.render.drawBlock
import de.visualtasker.blockeditor.compose.theme.darkBlockEditorColors
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignElement
import de.visualtasker.blockeditor.registry.BlockDesignFactory
import de.visualtasker.blockeditor.registry.BlockDesignFieldBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignFieldType
import de.visualtasker.blockeditor.registry.BlockDesignInputDefinition
import de.visualtasker.blockeditor.registry.BlockDesignInputKind
import de.visualtasker.blockeditor.registry.BlockDesignMutatorFamily
import de.visualtasker.blockeditor.registry.BlockDesignMutatorState
import de.visualtasker.blockeditor.registry.BlockDesignNodeShapeRole
import de.visualtasker.blockeditor.registry.BlockDesignValueType
import de.visualtasker.blockeditor.registry.BlockNodeDesignerProjector
import de.visualtasker.blockeditor.registry.addElement
import de.visualtasker.blockeditor.registry.duplicateElement
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.removeElement
import de.visualtasker.blockeditor.registry.reorderElement
import de.visualtasker.blockeditor.registry.updateElement
import de.visualtasker.blockeditor.registry.withAddedElseIf
import de.visualtasker.blockeditor.registry.withElseEnabled
import de.visualtasker.blockeditor.registry.withRemovedElseIf
import de.visualtasker.blockeditor.registry.withEnsuredElementIds
import de.visualtasker.blockeditor.registry.createNode
import kotlin.math.max
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDesignFactorySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (BlockDesignBlueprint) -> Unit,
) {
    if (!visible) return

    var blueprint by remember { mutableStateOf(defaultRenderTestBlueprint()) }
    var activeTab by remember { mutableStateOf(FactoryTab.Definition) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Block Design Factory", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FactoryTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activeTab) {
                    FactoryTab.Definition -> DefinitionEditor(blueprint) { blueprint = it }
                    FactoryTab.Preview -> PreviewEditor(blueprint) { blueprint = it }
                    FactoryTab.Generator -> GeneratorEditor(blueprint) { blueprint = it }
                }
            }
            Button(
                onClick = {
                    if (blueprint.label.isBlank() || blueprint.type.isBlank()) return@Button
                    onCreate(blueprint)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = blueprint.label.isNotBlank() && blueprint.type.isNotBlank(),
            ) {
                Text("Blockdefinition registrieren")
            }
        }
    }
}

@Composable
private fun DefinitionEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = blueprint.type,
            onValueChange = { onBlueprintChange(blueprint.copy(type = it)) },
            label = { Text("type") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = blueprint.label,
            onValueChange = { onBlueprintChange(blueprint.copy(label = it)) },
            label = { Text("label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = blueprint.category,
            onValueChange = { onBlueprintChange(blueprint.copy(category = it)) },
            label = { Text("category") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ColorDropdown(blueprint, onBlueprintChange)
        RowToggle("previousConnection", blueprint.hasPrevious) {
            onBlueprintChange(blueprint.copy(hasPrevious = it))
        }
        RowToggle("nextConnection", blueprint.hasNext) {
            onBlueprintChange(blueprint.copy(hasNext = it))
        }
        RowToggle("Reporter / outputConnection", blueprint.isReporter) {
            onBlueprintChange(
                blueprint.copy(
                    isReporter = it,
                    outputType = if (it) blueprint.outputType ?: "Any" else null,
                    hasPrevious = if (it) false else blueprint.hasPrevious,
                    hasNext = if (it) false else blueprint.hasNext,
                ),
            )
        }
        OutlinedTextField(
            value = blueprint.outputType.orEmpty(),
            onValueChange = { onBlueprintChange(blueprint.copy(outputType = it.ifBlank { null })) },
            label = { Text("outputType") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = blueprint.isReporter,
        )
        OutlinedTextField(
            value = blueprint.description,
            onValueChange = { onBlueprintChange(blueprint.copy(description = it)) },
            label = { Text("description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Text("Block-Typ", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onBlueprintChange(blueprint.asStatementPreset()) }) { Text("Statement") }
            Button(onClick = { onBlueprintChange(blueprint.asLoopPreset()) }) { Text("C-Block (Loop)") }
            Button(onClick = { onBlueprintChange(blueprint.asIfPreset()) }) { Text("Branches (IF)") }
        }
        NodeProjectionControls(blueprint, onBlueprintChange)
        MutatorControls(blueprint, onBlueprintChange)
    }
}

@Composable
private fun PreviewEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    val normalized = remember(blueprint) { blueprint.withEnsuredElementIds() }
    var draggingElementId by remember { mutableStateOf<String?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragDistanceY by remember { mutableStateOf(0f) }
    var paletteDrag by remember { mutableStateOf<PaletteElementSpec?>(null) }
    var paletteDragPosition by remember { mutableStateOf(Offset.Zero) }
    var listBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var deletedSnapshot by remember { mutableStateOf<DeletedElementSnapshot?>(null) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 66.dp.toPx() }

    fun mutate(transform: (BlockDesignBlueprint) -> BlockDesignBlueprint) {
        val next = transform(normalized).withEnsuredElementIds()
        onBlueprintChange(next)
    }

    fun restoreDeleted() {
        val snapshot = deletedSnapshot ?: return
        mutate { state ->
            val restored = state.addElement(snapshot.element)
            restored.reorderElement(snapshot.element.elementId, snapshot.index.coerceIn(0, restored.elements.lastIndex))
        }
        deletedSnapshot = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RenderedDualPreview(normalized)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                deletedSnapshot?.let { snapshot ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Element gelöscht: ${elementDisplayTitle(snapshot.element)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { restoreDeleted() }, modifier = Modifier.height(28.dp)) { Text("Undo") }
                        }
                    }
                }
                BoxWithConstraints {
                    val compact = maxWidth < 900.dp
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PalettePanel(
                                onTapInsert = { spec ->
                                    mutate { it.addElement(spec.toElement(it.nextElementIndex)) }
                                },
                                onPaletteDragStart = { spec, position ->
                                    paletteDrag = spec
                                    paletteDragPosition = position
                                },
                                onPaletteDragMove = { delta -> paletteDragPosition += delta },
                                onPaletteDragEnd = {
                                    val bounds = listBounds
                                    val spec = paletteDrag
                                    if (bounds != null && spec != null && bounds.contains(paletteDragPosition)) {
                                        val relativeY = (paletteDragPosition.y - bounds.top).coerceAtLeast(0f)
                                        val targetIndex = (relativeY / itemHeightPx).toInt().coerceIn(0, normalized.elements.size)
                                        mutate { blueprintState ->
                                            val added = blueprintState.addElement(spec.toElement(blueprintState.nextElementIndex))
                                            val newId = added.elements.last().elementId
                                            added.reorderElement(newId, targetIndex)
                                        }
                                    }
                                    paletteDrag = null
                                },
                            )
                            LinearElementsPanel(
                                blueprint = normalized,
                                onDelete = { id, index, element ->
                                    deletedSnapshot = DeletedElementSnapshot(index = index, element = element)
                                    mutate { it.removeElement(id) }
                                },
                                onDuplicate = { id -> mutate { it.duplicateElement(id) } },
                                onUpdate = { id, transform -> mutate { it.updateElement(id, transform) } },
                                draggingElementId = draggingElementId,
                                dragTargetIndex = dragTargetIndex,
                                onDragStart = { id ->
                                    draggingElementId = id
                                    dragDistanceY = 0f
                                    dragTargetIndex = normalized.elements.indexOfFirst { it.elementId == id }
                                },
                                onDragMove = { delta ->
                                    dragDistanceY += delta
                                    val startIndex = normalized.elements.indexOfFirst { it.elementId == draggingElementId }
                                    if (startIndex < 0) return@LinearElementsPanel
                                    val next = (startIndex + (dragDistanceY / itemHeightPx).toInt()).coerceIn(0, normalized.elements.lastIndex)
                                    dragTargetIndex = next
                                },
                                onDragEnd = {
                                    val id = draggingElementId
                                    val target = dragTargetIndex
                                    if (id != null && target != null) mutate { it.reorderElement(id, target) }
                                    draggingElementId = null
                                    dragTargetIndex = null
                                    dragDistanceY = 0f
                                },
                                onListBounds = { listBounds = it },
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PalettePanel(
                                modifier = Modifier.width(180.dp),
                                onTapInsert = { spec ->
                                    mutate { it.addElement(spec.toElement(it.nextElementIndex)) }
                                },
                                onPaletteDragStart = { spec, position ->
                                    paletteDrag = spec
                                    paletteDragPosition = position
                                },
                                onPaletteDragMove = { delta -> paletteDragPosition += delta },
                                onPaletteDragEnd = {
                                    val bounds = listBounds
                                    val spec = paletteDrag
                                    if (bounds != null && spec != null && bounds.contains(paletteDragPosition)) {
                                        val relativeY = (paletteDragPosition.y - bounds.top).coerceAtLeast(0f)
                                        val targetIndex = (relativeY / itemHeightPx).toInt().coerceIn(0, normalized.elements.size)
                                        mutate { blueprintState ->
                                            val added = blueprintState.addElement(spec.toElement(blueprintState.nextElementIndex))
                                            val newId = added.elements.last().elementId
                                            added.reorderElement(newId, targetIndex)
                                        }
                                    }
                                    paletteDrag = null
                                },
                            )
                            LinearElementsPanel(
                                modifier = Modifier.weight(1f),
                                blueprint = normalized,
                                onDelete = { id, index, element ->
                                    deletedSnapshot = DeletedElementSnapshot(index = index, element = element)
                                    mutate { it.removeElement(id) }
                                },
                                onDuplicate = { id -> mutate { it.duplicateElement(id) } },
                                onUpdate = { id, transform -> mutate { it.updateElement(id, transform) } },
                                draggingElementId = draggingElementId,
                                dragTargetIndex = dragTargetIndex,
                                onDragStart = { id ->
                                    draggingElementId = id
                                    dragDistanceY = 0f
                                    dragTargetIndex = normalized.elements.indexOfFirst { it.elementId == id }
                                },
                                onDragMove = { delta ->
                                    dragDistanceY += delta
                                    val startIndex = normalized.elements.indexOfFirst { it.elementId == draggingElementId }
                                    if (startIndex < 0) return@LinearElementsPanel
                                    val next = (startIndex + (dragDistanceY / itemHeightPx).toInt()).coerceIn(0, normalized.elements.lastIndex)
                                    dragTargetIndex = next
                                },
                                onDragEnd = {
                                    val id = draggingElementId
                                    val target = dragTargetIndex
                                    if (id != null && target != null) mutate { it.reorderElement(id, target) }
                                    draggingElementId = null
                                    dragTargetIndex = null
                                    dragDistanceY = 0f
                                },
                                onListBounds = { listBounds = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderedDualPreview(blueprint: BlockDesignBlueprint) {
    val textMeasurer = rememberTextMeasurer()
    val bundle = remember(blueprint) { runCatching { BlockNodeDesignerProjector.project(blueprint) }.getOrNull() }
    val definition = bundle?.previewDefinition
    val registry = remember(definition) { StaticBlockRegistry(listOfNotNull(definition)) }
    val block = remember(definition) { definition?.createNode(BlockId("factory-preview")) }
    val metadataRows = definition?.metadata?.get("custom.layout.rowCount")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val statementRows = (definition?.statementInputs?.size ?: 0) * 2
    val rows = maxOf(metadataRows, 1 + statementRows)
    val columns = definition?.metadata?.get("custom.layout.maxRowColumns")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val previewWidth = max(288f, 32f + columns * 86f + 16f)
    val canvasHeight = (max(180f, (96f + rows * 54f).coerceAtMost(300f))).dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF11131A), MaterialTheme.shapes.small),
            ) {
                Canvas(Modifier.fillMaxWidth().height(canvasHeight)) {
                    val safeDefinition = definition ?: BlockDefinition(
                        id = blueprint.type.ifBlank { "factory.invalid" },
                        label = blueprint.label.ifBlank { "Invalid Block" },
                        category = blueprint.category.ifBlank { BlockCategories.CUSTOM },
                        hasPrevious = blueprint.hasPrevious,
                        hasNext = blueprint.hasNext,
                    )
                    val safeBlock = block ?: safeDefinition.createNode(BlockId("factory-invalid-preview"))
                    val baseBlockWidth = if (safeDefinition.isReporter) 120f else previewWidth
                    val baseBlockHeight = if (safeDefinition.isReporter) 40f else 44f * rows + 24f
                    val availableWidth = size.width - 20f
                    val availableHeight = size.height - 20f
                    val previewScale = minOf(
                        1f,
                        availableWidth / baseBlockWidth,
                        availableHeight / baseBlockHeight,
                    )
                    val scaledWidth = baseBlockWidth * previewScale
                    val scaledHeight = baseBlockHeight * previewScale
                    val topLabelReserve = 40f
                    val origin = Offset(
                        x = ((size.width - scaledWidth) / 2f).coerceAtLeast(8f),
                        y = ((size.height - scaledHeight) / 2f).coerceAtLeast(topLabelReserve),
                    )
                    withTransform({
                        translate(left = origin.x, top = origin.y)
                        scale(previewScale, previewScale)
                    }) {
                    drawBlock(
                        block = safeBlock,
                        definition = safeDefinition,
                        topLeft = Offset.Zero,
                        width = baseBlockWidth,
                        height = baseBlockHeight,
                        textMeasurer = textMeasurer,
                        colors = darkBlockEditorColors(),
                        registry = registry,
                    )
                    }
                }
                if (definition != null) {
                    DesignerPreviewControls(definition)
                }
                Text(
                    text = "BLOCK PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color(0xFFE0E3EA),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF11131A), MaterialTheme.shapes.small),
            ) {
                NodePreviewCanvas(
                    blueprint = blueprint,
                    bundle = bundle,
                    modifier = Modifier.fillMaxWidth().height(canvasHeight),
                )
                Text(
                    text = "NODE PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color(0xFFE0E3EA),
                )
            }
        }
    }
}

@Composable
private fun NodePreviewCanvas(
    blueprint: BlockDesignBlueprint,
    bundle: de.visualtasker.blockeditor.registry.DesignerProjectionBundle?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val projection = bundle?.nodeProjection
        val nodeWidth = (size.width * 0.6f).coerceIn(120f, 260f)
        val nodeHeight = (size.height * 0.34f).coerceIn(68f, 140f)
        val origin = Offset(
            x = (size.width - nodeWidth) / 2f,
            y = (size.height - nodeHeight) / 2f,
        )
        val shapePath = nodeShapePathForRole(
            role = projection?.shapeRole ?: BlockDesignNodeShapeRole.AUTO,
            topLeft = origin,
            size = Size(nodeWidth, nodeHeight),
        )
        val fill = Color(0xFF4F5B66)
        drawPath(shapePath, color = fill)
        drawPath(shapePath, color = Color(0xFFE7ECFF), style = Stroke(width = 2f))

        val inputPorts = projection?.valueInputPorts.orEmpty()
        val outputPort = projection?.valueOutputPort
        val structurePorts = projection?.structurePorts.orEmpty()

        if ((projection?.controlFlowInputEnabled ?: blueprint.hasPrevious)) {
            drawLine(
                color = Color(0xFF8ED1FF),
                start = Offset(origin.x - 38f, origin.y + nodeHeight / 2f),
                end = Offset(origin.x, origin.y + nodeHeight / 2f),
                strokeWidth = 3f,
            )
        }
        if ((projection?.controlFlowOutputEnabled ?: blueprint.hasNext)) {
            drawLine(
                color = Color(0xFF8ED1FF),
                start = Offset(origin.x + nodeWidth, origin.y + nodeHeight / 2f),
                end = Offset(origin.x + nodeWidth + 42f, origin.y + nodeHeight / 2f),
                strokeWidth = 3f,
            )
        }
        inputPorts.forEachIndexed { index, _ ->
            val y = origin.y + nodeHeight * (index + 1) / (inputPorts.size + 1)
            drawRoundRect(
                color = Color(0xFF63C7FF),
                topLeft = Offset(origin.x - 18f, y - 7f),
                size = Size(18f, 14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
            )
        }
        outputPort?.let {
            drawRoundRect(
                color = Color(0xFF68D391),
                topLeft = Offset(origin.x + nodeWidth, origin.y + nodeHeight / 2f - 7f),
                size = Size(18f, 14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
            )
        }
        structurePorts.forEachIndexed { index, _ ->
            val y = origin.y + nodeHeight + 8f + index * 14f
            drawRoundRect(
                color = Color(0xFFBDA7FF),
                topLeft = Offset(origin.x + 12f, y),
                size = Size(34f, 10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
            )
        }
    }
}

@Composable
private fun DesignerPreviewControls(definition: BlockDefinition) {
    definition.fields.forEach { field ->
        val row = definition.metadata["custom.layout.field.${field.key}.row"]?.toIntOrNull() ?: return@forEach
        val column = definition.metadata["custom.layout.field.${field.key}.column"]?.toIntOrNull() ?: return@forEach
        val fieldType = definition.metadata["custom.layout.field.${field.key}.type"].orEmpty()
        Box(
            modifier = Modifier
                .offset(
                    x = (24 + 32 + column * 86 + 8).dp,
                    y = (24 + row * 44 + 5).dp,
                )
                .width(78.dp)
                .height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (fieldType) {
                "SWITCH" -> Switch(
                    checked = field.defaultValue.equals("true", ignoreCase = true),
                    onCheckedChange = {},
                    modifier = Modifier.size(width = 52.dp, height = 32.dp),
                )
                "CHECKBOX" -> Checkbox(
                    checked = field.defaultValue.equals("true", ignoreCase = true),
                    onCheckedChange = {},
                )
                "SLIDER",
                "THRESHOLD",
                -> Slider(
                    value = field.defaultValue.toFloatOrNull() ?: 0.5f,
                    onValueChange = {},
                    modifier = Modifier.width(72.dp),
                    valueRange = ((field.minValue ?: 0.0).toFloat())..((field.maxValue ?: 1.0).toFloat()),
                )
                "DROPDOWN",
                "RADIO_BUTTONS",
                -> FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(
                            field.options.firstOrNull { it.value == field.defaultValue }?.label
                                ?: field.options.firstOrNull()?.label
                                ?: field.label,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NodeProjectionControls(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Node Projection", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = blueprint.nodeProjection.showControlFlowInput,
                onClick = {
                    onBlueprintChange(
                        blueprint.copy(
                            nodeProjection = blueprint.nodeProjection.copy(
                                showControlFlowInput = !blueprint.nodeProjection.showControlFlowInput,
                            ),
                        ),
                    )
                },
                label = { Text("Control In") },
            )
            FilterChip(
                selected = blueprint.nodeProjection.showControlFlowOutput,
                onClick = {
                    onBlueprintChange(
                        blueprint.copy(
                            nodeProjection = blueprint.nodeProjection.copy(
                                showControlFlowOutput = !blueprint.nodeProjection.showControlFlowOutput,
                            ),
                        ),
                    )
                },
                label = { Text("Control Out") },
            )
            FilterChip(
                selected = blueprint.nodeProjection.showValueOutputPort,
                onClick = {
                    onBlueprintChange(
                        blueprint.copy(
                            nodeProjection = blueprint.nodeProjection.copy(
                                showValueOutputPort = !blueprint.nodeProjection.showValueOutputPort,
                            ),
                        ),
                    )
                },
                label = { Text("Value Out") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockDesignNodeShapeRole.entries.forEach { role ->
                FilterChip(
                    selected = blueprint.nodeProjection.shapeRole == role,
                    onClick = {
                        onBlueprintChange(
                            blueprint.copy(
                                nodeProjection = blueprint.nodeProjection.copy(shapeRole = role),
                            ),
                        )
                    },
                    label = { Text(role.name) },
                )
            }
        }
    }
}

@Composable
private fun MutatorControls(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mutator", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockDesignMutatorFamily.entries.forEach { family ->
                FilterChip(
                    selected = blueprint.mutatorDefinition.family == family,
                    onClick = {
                        val clearedState = when (family) {
                            BlockDesignMutatorFamily.IF -> blueprint.mutatorState.copy(hasElseBranch = false)
                            else -> BlockDesignMutatorState()
                        }
                        onBlueprintChange(
                            blueprint.copy(
                                mutatorDefinition = blueprint.mutatorDefinition.copy(family = family),
                                mutatorState = clearedState,
                            ),
                        )
                    },
                    label = { Text(family.name) },
                )
            }
        }
        if (blueprint.mutatorDefinition.family == BlockDesignMutatorFamily.IF) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onBlueprintChange(blueprint.withAddedElseIf()) }) { Text("+ ELSEIF") }
                Button(
                    onClick = { onBlueprintChange(blueprint.withRemovedElseIf()) },
                    enabled = blueprint.mutatorState.elseIfBranches.isNotEmpty(),
                ) {
                    Text("- ELSEIF")
                }
                FilterChip(
                    selected = blueprint.mutatorState.hasElseBranch,
                    onClick = { onBlueprintChange(blueprint.withElseEnabled(!blueprint.mutatorState.hasElseBranch)) },
                    label = { Text("ELSE") },
                )
            }
            Text(
                text = "ELSEIF IDs: ${blueprint.mutatorState.elseIfBranches.joinToString { it.instanceId }}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun nodeShapePathForRole(
    role: BlockDesignNodeShapeRole,
    topLeft: Offset,
    size: Size,
): Path {
    val x = topLeft.x
    val y = topLeft.y
    val w = size.width
    val h = size.height
    return Path().apply {
        when (role) {
            BlockDesignNodeShapeRole.CONDITION,
            BlockDesignNodeShapeRole.BOOLEAN_VALUE,
            -> {
                moveTo(x + w / 2f, y)
                lineTo(x + w, y + h / 2f)
                lineTo(x + w / 2f, y + h)
                lineTo(x, y + h / 2f)
                close()
            }
            BlockDesignNodeShapeRole.LOOP -> {
                moveTo(x + w * 0.12f, y)
                lineTo(x + w * 0.88f, y)
                lineTo(x + w, y + h * 0.3f)
                lineTo(x + w * 0.88f, y + h)
                lineTo(x + w * 0.12f, y + h)
                lineTo(x, y + h * 0.3f)
                close()
            }
            BlockDesignNodeShapeRole.EVENT -> addOval(
                androidx.compose.ui.geometry.Rect(
                    left = x,
                    top = y,
                    right = x + w,
                    bottom = y + h,
                ),
            )
            else -> addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = androidx.compose.ui.geometry.Rect(x, y, x + w, y + h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                ),
            )
        }
    }
}

private data class PaletteElementSpec(
    val id: String,
    val label: String,
    val group: String,
    val create: (Int) -> BlockDesignElement,
) {
    fun toElement(nextIndex: Int): BlockDesignElement = create(nextIndex)
}

@Composable
private fun PalettePanel(
    modifier: Modifier = Modifier,
    onTapInsert: (PaletteElementSpec) -> Unit,
    onPaletteDragStart: (PaletteElementSpec, Offset) -> Unit,
    onPaletteDragMove: (Offset) -> Unit,
    onPaletteDragEnd: () -> Unit,
) {
    val specs = remember { paletteSpecs() }
    val inputSpecs = remember(specs) { specs.filter { it.group == "INPUTS" } }
    val fieldSpecs = remember(specs) { specs.filter { it.group == "FIELDS" } }
    var selectedInput by remember { mutableStateOf(inputSpecs.firstOrNull()) }
    var selectedField by remember { mutableStateOf(fieldSpecs.firstOrNull()) }
    var inputExpanded by remember { mutableStateOf(false) }
    var fieldExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("PALETTE", style = MaterialTheme.typography.titleSmall)
            Text("INPUTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = { inputExpanded = true },
                    label = { Text(selectedInput?.label ?: "Value") },
                )
                DropdownMenu(expanded = inputExpanded, onDismissRequest = { inputExpanded = false }) {
                    inputSpecs.forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(spec.label) },
                            onClick = {
                                selectedInput = spec
                                inputExpanded = false
                            },
                        )
                    }
                }
                Button(
                    onClick = { selectedInput?.let(onTapInsert) },
                    modifier = Modifier.height(28.dp),
                ) { Text("+") }
            }
            Text("FIELDS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = { fieldExpanded = true },
                    label = { Text(selectedField?.label ?: "Label") },
                )
                DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                    fieldSpecs.forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(spec.label) },
                            onClick = {
                                selectedField = spec
                                fieldExpanded = false
                            },
                        )
                    }
                }
                Button(
                    onClick = { selectedField?.let(onTapInsert) },
                    modifier = Modifier.height(28.dp),
                ) { Text("+") }
            }
            Text("Hinweis: End Row startet immer nur neue Zeile.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LinearElementsPanel(
    blueprint: BlockDesignBlueprint,
    onDelete: (String, Int, BlockDesignElement) -> Unit,
    onDuplicate: (String) -> Unit,
    onUpdate: (String, (BlockDesignElement) -> BlockDesignElement) -> Unit,
    draggingElementId: String?,
    dragTargetIndex: Int?,
    onDragStart: (String) -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onListBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val swipeX = remember { mutableStateMapOf<String, Float>() }
    var swipeHintIndex by remember { mutableIntStateOf(-1) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ELEMENTE", style = MaterialTheme.typography.titleSmall)
            Text("Drag: Handle ≡  •  Swipe links/rechts: Delete", style = MaterialTheme.typography.labelSmall)
            if (draggingElementId != null && dragTargetIndex != null) {
                Text(
                    "Drop bei Position ${dragTargetIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp)
                    .onGloballyPositioned { onListBounds(it.boundsInWindow()) },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(blueprint.elements, key = { _, element -> element.elementId }) { index, element ->
                    if (dragTargetIndex == index && draggingElementId != null && draggingElementId != element.elementId) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(element.elementId) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        swipeX[element.elementId] = ((swipeX[element.elementId] ?: 0f) + dragAmount)
                                            .coerceIn(-240f, 240f)
                                        swipeHintIndex = index
                                    },
                                    onDragEnd = {
                                        val value = swipeX[element.elementId] ?: 0f
                                        if (abs(value) > 140f) {
                                            onDelete(element.elementId, index, element)
                                        }
                                        swipeX[element.elementId] = 0f
                                        swipeHintIndex = -1
                                    },
                                )
                            }
                            .background(
                                when {
                                    (swipeX[element.elementId] ?: 0f) > 40f -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    (swipeX[element.elementId] ?: 0f) < -40f -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                },
                            ),
                        shape = MaterialTheme.shapes.small,
                        color = if (draggingElementId == element.elementId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "≡",
                                modifier = Modifier.pointerInput(element.elementId) {
                                    detectDragGestures(
                                        onDragStart = { onDragStart(element.elementId) },
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragEnd,
                                    ) { change, drag ->
                                        change.consume()
                                        onDragMove(drag.y)
                                        scope.launch { listState.scrollBy(drag.y) }
                                    }
                                },
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(elementDisplayTitle(element), style = MaterialTheme.typography.labelLarge)
                                Text(elementDisplaySubtitle(element), style = MaterialTheme.typography.bodySmall)
                                if (swipeHintIndex == index && abs(swipeX[element.elementId] ?: 0f) > 40f) {
                                    Text("Swipe weiter zum Löschen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                                if (expanded[element.elementId] == true) {
                                    ElementInlineEditor(element = element, onUpdate = { transform ->
                                        onUpdate(element.elementId, transform)
                                    })
                                }
                            }
                            Button(
                                onClick = { expanded[element.elementId] = !(expanded[element.elementId] ?: false) },
                                modifier = Modifier.height(28.dp),
                            ) { Text(if (expanded[element.elementId] == true) "▾" else "▸") }
                            Button(onClick = { onDuplicate(element.elementId) }, modifier = Modifier.height(28.dp)) { Text("Dup") }
                            Button(onClick = { onDelete(element.elementId, index, element) }, modifier = Modifier.height(28.dp)) { Text("Del") }
                        }
                    }
                }
            }
        }
    }
}

private fun BlockDesignBlueprint.asStatementPreset(): BlockDesignBlueprint {
    var idx = 1
    fun nextId() = "el_${idx++}"
    return copy(
        isReporter = false,
        hasPrevious = true,
        hasNext = true,
        outputType = null,
        elements = listOf(
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.FIELD,
                field = BlockDesignFieldBlueprint(
                    name = "label1",
                    label = if (label.isBlank()) "Do" else label,
                    fieldType = BlockDesignFieldType.TEXT,
                ),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(
                    name = "BODY1",
                    label = "BODY1",
                    kind = BlockDesignInputKind.STATEMENT,
                ),
            ),
        ),
        nextElementIndex = idx,
    )
}

private fun BlockDesignBlueprint.asLoopPreset(): BlockDesignBlueprint {
    var idx = 1
    fun nextId() = "el_${idx++}"
    return copy(
        isReporter = false,
        hasPrevious = true,
        hasNext = true,
        outputType = null,
        mutatorDefinition = mutatorDefinition.copy(family = BlockDesignMutatorFamily.WHILE),
        elements = listOf(
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.FIELD,
                field = BlockDesignFieldBlueprint(name = "label1", label = "WHILE", fieldType = BlockDesignFieldType.TEXT),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "CONDITION", label = "CONDITION", kind = BlockDesignInputKind.VALUE, connectionType = "Boolean"),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "endRow1", label = "endRow1", kind = BlockDesignInputKind.END_ROW),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "BODY1", label = "BODY1", kind = BlockDesignInputKind.STATEMENT),
            ),
        ),
        nextElementIndex = idx,
    )
}

private fun BlockDesignBlueprint.asIfPreset(): BlockDesignBlueprint {
    var idx = 1
    fun nextId() = "el_${idx++}"
    return copy(
        isReporter = false,
        hasPrevious = true,
        hasNext = true,
        outputType = null,
        mutatorDefinition = mutatorDefinition.copy(family = BlockDesignMutatorFamily.IF),
        mutatorState = mutatorState.copy(hasElseBranch = false, elseIfBranches = emptyList()),
        elements = listOf(
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.FIELD,
                field = BlockDesignFieldBlueprint(name = "label1", label = "IF", fieldType = BlockDesignFieldType.TEXT),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "CONDITION", label = "CONDITION", kind = BlockDesignInputKind.VALUE, connectionType = "Boolean"),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "endRow1", label = "endRow1", kind = BlockDesignInputKind.END_ROW),
            ),
            BlockDesignElement(
                elementId = nextId(),
                kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
                input = BlockDesignInputDefinition(name = "THEN", label = "THEN", kind = BlockDesignInputKind.STATEMENT),
            ),
        ),
        nextElementIndex = idx,
    )
}

private fun defaultRenderTestBlueprint(): BlockDesignBlueprint {
    var idx = 1
    fun nextId() = "el_${idx++}"
    var valueCounter = 1
    var statementCounter = 1
    var endCounter = 1
    fun valueElement(): BlockDesignElement =
        BlockDesignElement(
            elementId = nextId(),
            kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
            input = BlockDesignInputDefinition(
                name = "value${valueCounter++}",
                label = "value",
                kind = BlockDesignInputKind.VALUE,
                connectionType = "Any",
            ),
        )
    fun statementElement(): BlockDesignElement =
        BlockDesignElement(
            elementId = nextId(),
            kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
            input = BlockDesignInputDefinition(
                name = "BODY${statementCounter++}",
                label = "body",
                kind = BlockDesignInputKind.STATEMENT,
            ),
        )
    fun endRowElement(): BlockDesignElement =
        BlockDesignElement(
            elementId = nextId(),
            kind = de.visualtasker.blockeditor.registry.BlockDesignElementKind.INPUT,
            input = BlockDesignInputDefinition(
                name = "end${endCounter++}",
                label = "layout break",
                kind = BlockDesignInputKind.END_ROW,
            ),
        )
    return BlockDesignBlueprint(
        type = "factory.test.render",
        label = "RENDER TEST",
        category = BlockCategories.CUSTOM,
        color = "blue",
        hasPrevious = true,
        hasNext = true,
        isReporter = false,
        outputType = null,
        mutatorDefinition = de.visualtasker.blockeditor.registry.BlockDesignMutatorDefinition(
            family = BlockDesignMutatorFamily.NONE,
        ),
        elements = listOf(
            valueElement(),
            valueElement(),
            valueElement(),
            endRowElement(),
            endRowElement(),
            endRowElement(),
            valueElement(),
            statementElement(),
            valueElement(),
            endRowElement(),
            endRowElement(),
            statementElement(),
            valueElement(),
        ),
        nextElementIndex = idx,
    )
}

@Composable
private fun ElementInlineEditor(
    element: BlockDesignElement,
    onUpdate: ((BlockDesignElement) -> BlockDesignElement) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        element.input?.let { input ->
            OutlinedTextField(
                value = input.name,
                onValueChange = { value ->
                    onUpdate { current ->
                        current.copy(input = input.copy(name = value, label = value))
                    }
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (input.kind != BlockDesignInputKind.END_ROW) {
                TypeDropdown(input.connectionType) { value ->
                    onUpdate { current -> current.copy(input = input.copy(connectionType = value)) }
                }
            }
        }
        element.field?.let { field ->
            OutlinedTextField(
                value = field.name,
                onValueChange = { value ->
                    onUpdate { current -> current.copy(field = field.copy(name = value, label = value)) }
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = field.defaultValue,
                onValueChange = { value ->
                    onUpdate { current -> current.copy(field = field.copy(defaultValue = value)) }
                },
                label = { Text("Default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (field.fieldType == BlockDesignFieldType.DROPDOWN || field.fieldType == BlockDesignFieldType.RADIO_BUTTONS) {
                val optionsText = field.options.joinToString(",") { "${it.value}:${it.label}" }
                OutlinedTextField(
                    value = optionsText,
                    onValueChange = { raw ->
                        val parsed = raw.split(",")
                            .mapNotNull { entry ->
                                val parts = entry.split(":")
                                val value = parts.getOrNull(0)?.trim().orEmpty()
                                val label = parts.getOrNull(1)?.trim().orEmpty()
                                if (value.isBlank()) null else FieldOption(value = value, label = if (label.isBlank()) value else label)
                            }
                        onUpdate { current -> current.copy(field = field.copy(options = parsed)) }
                    },
                    label = { Text("Optionen value:label,...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            if (field.fieldType == BlockDesignFieldType.NUMBER_INPUT || field.fieldType == BlockDesignFieldType.SLIDER) {
                NumberPropertyField("Min", field.min) { min ->
                    onUpdate { current -> current.copy(field = field.copy(min = min)) }
                }
                NumberPropertyField("Max", field.max) { max ->
                    onUpdate { current -> current.copy(field = field.copy(max = max)) }
                }
                NumberPropertyField("Step", field.step) { step ->
                    onUpdate { current -> current.copy(field = field.copy(step = step)) }
                }
            }
        }
    }
}

private data class DeletedElementSnapshot(
    val index: Int,
    val element: BlockDesignElement,
)

@Composable
private fun PropertiesPanel(
    blueprint: BlockDesignBlueprint,
    selectedElementId: String?,
    onUpdate: (String, (BlockDesignElement) -> BlockDesignElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = blueprint.elements.firstOrNull { it.elementId == selectedElementId }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("PROPERTIES", style = MaterialTheme.typography.titleSmall)
            if (selected == null) {
                Text("Element auswählen", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            selected.input?.let { input ->
                OutlinedTextField(
                    value = input.name,
                    onValueChange = { value ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(input = input.copy(name = value, label = value))
                        }
                    },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (input.kind != BlockDesignInputKind.END_ROW) {
                    TypeDropdown(input.connectionType) { value ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(input = input.copy(connectionType = value))
                        }
                    }
                }
            }
            selected.field?.let { field ->
                OutlinedTextField(
                    value = field.name,
                    onValueChange = { value ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(field = field.copy(name = value, label = value))
                        }
                    },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = field.defaultValue,
                    onValueChange = { value ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(field = field.copy(defaultValue = value))
                        }
                    },
                    label = { Text("Default") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (field.fieldType in setOf(BlockDesignFieldType.NUMBER_INPUT, BlockDesignFieldType.SLIDER)) {
                    NumberPropertyField("Min", field.min) { min ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(field = field.copy(min = min))
                        }
                    }
                    NumberPropertyField("Max", field.max) { max ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(field = field.copy(max = max))
                        }
                    }
                    NumberPropertyField("Step", field.step) { step ->
                        onUpdate(selected.elementId) { element ->
                            element.copy(field = field.copy(step = step))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPropertyField(
    label: String,
    value: Double?,
    onValue: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it.toDoubleOrNull())
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

private fun elementDisplayTitle(element: BlockDesignElement): String =
    element.input?.let { input ->
        when (input.kind) {
            BlockDesignInputKind.VALUE -> "Value Input"
            BlockDesignInputKind.STATEMENT -> "Statement Input"
            BlockDesignInputKind.END_ROW -> "End Row"
        }
    } ?: element.field?.let { field ->
        when (field.fieldType) {
            BlockDesignFieldType.TEXT -> "Label"
            BlockDesignFieldType.DROPDOWN -> "Dropdown"
            BlockDesignFieldType.CHECKBOX -> "Checkbox"
            BlockDesignFieldType.IMAGE,
            BlockDesignFieldType.IMAGE_CAROUSEL,
            -> "Image"
            BlockDesignFieldType.VARIABLE -> "Variable"
            BlockDesignFieldType.TEXT_INPUT -> "Text"
            BlockDesignFieldType.NUMBER_INPUT -> "Number"
            BlockDesignFieldType.SLIDER -> "Slider"
            else -> field.fieldType.name
        }
    } ?: "Element"

private fun elementDisplaySubtitle(element: BlockDesignElement): String =
    element.input?.let { input ->
        if (input.kind == BlockDesignInputKind.END_ROW) "layout break"
        else "${input.name} · ${input.connectionType}"
    } ?: element.field?.let { field ->
        "${field.name} · ${field.fieldType.name}"
    } ?: "-"

private fun paletteSpecs(): List<PaletteElementSpec> = listOf(
    PaletteElementSpec("input.value", "Value", "INPUTS") { index ->
        BlockDesignElement.input(
            BlockDesignInputDefinition(
                name = "value$index",
                label = "value$index",
                kind = BlockDesignInputKind.VALUE,
                connectionType = "Any",
            ),
        )
    },
    PaletteElementSpec("input.statement", "Statement", "INPUTS") { index ->
        BlockDesignElement.input(
            BlockDesignInputDefinition(
                name = "BODY$index",
                label = "BODY$index",
                kind = BlockDesignInputKind.STATEMENT,
            ),
        )
    },
    PaletteElementSpec("input.endrow", "End Row", "INPUTS") { index ->
        BlockDesignElement.input(
            BlockDesignInputDefinition(
                name = "endRow$index",
                label = "endRow$index",
                kind = BlockDesignInputKind.END_ROW,
            ),
        )
    },
    PaletteElementSpec("field.label", "Label", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "label$index", label = "label$index", fieldType = BlockDesignFieldType.TEXT))
    },
    PaletteElementSpec("field.dropdown", "Dropdown", "FIELDS") { index ->
        BlockDesignElement.field(
            BlockDesignFieldBlueprint(
                name = "dropdown$index",
                label = "dropdown$index",
                fieldType = BlockDesignFieldType.DROPDOWN,
                options = listOf(FieldOption("option1", "Option 1")),
                defaultValue = "option1",
            ),
        )
    },
    PaletteElementSpec("field.checkbox", "Checkbox", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "checkbox$index", label = "checkbox$index", fieldType = BlockDesignFieldType.CHECKBOX, defaultValue = "false"))
    },
    PaletteElementSpec("field.image", "Image", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "image$index", label = "image$index", fieldType = BlockDesignFieldType.IMAGE))
    },
    PaletteElementSpec("field.variable", "Variable", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "variable$index", label = "variable$index", fieldType = BlockDesignFieldType.VARIABLE))
    },
    PaletteElementSpec("field.text", "Text", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "text$index", label = "text$index", fieldType = BlockDesignFieldType.TEXT_INPUT))
    },
    PaletteElementSpec("field.number", "Number", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "number$index", label = "number$index", fieldType = BlockDesignFieldType.NUMBER_INPUT, valueType = BlockDesignValueType.NUMBER, defaultValue = "0"))
    },
    PaletteElementSpec("field.slider", "Slider", "FIELDS") { index ->
        BlockDesignElement.field(BlockDesignFieldBlueprint(name = "slider$index", label = "slider$index", fieldType = BlockDesignFieldType.SLIDER, valueType = BlockDesignValueType.NUMBER, defaultValue = "0", min = 0.0, max = 100.0, step = 1.0))
    },
)

@Composable
private fun DesignElementCards(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Block sequence", style = MaterialTheme.typography.titleSmall)
        blueprint.elements.forEachIndexed { index, element ->
            val input = element.input
            val field = element.field
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("::", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        val title = input?.kind?.name ?: field?.fieldType?.name ?: "FIELD"
                        Text(title.lowercase(), style = MaterialTheme.typography.labelLarge)
                        when {
                            input != null && input.kind != BlockDesignInputKind.END_ROW -> Text(
                                "${input.name} · ${input.label}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            field != null -> FieldControlPreview(field)
                        }
                    }
                    if (input != null && input.kind != BlockDesignInputKind.END_ROW) {
                        TypeDropdown(input.connectionType) {
                            onBlueprintChange(
                                blueprint.copy(
                                    elements = blueprint.elements.replace(index, BlockDesignElement.input(input.copy(connectionType = it))),
                                ),
                            )
                        }
                    }
                    Button(onClick = { onBlueprintChange(blueprint.copy(elements = blueprint.elements.removeAtIndex(index))) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldControlPreview(field: BlockDesignFieldBlueprint) {
    when (field.fieldType) {
        BlockDesignFieldType.SWITCH,
        BlockDesignFieldType.CHECKBOX,
        -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${field.name} · ${field.label}", style = MaterialTheme.typography.bodySmall)
            Switch(checked = field.defaultValue.equals("true", ignoreCase = true), onCheckedChange = null)
        }
        BlockDesignFieldType.DROPDOWN,
        BlockDesignFieldType.RADIO_BUTTONS,
        -> Text(
            "${field.name} · ${field.options.joinToString("/") { it.label }}",
            style = MaterialTheme.typography.bodySmall,
        )
        else -> Text("${field.name} · ${field.label}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GeneratorEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    val bundle = remember(blueprint) { runCatching { BlockNodeDesignerProjector.project(blueprint) }.getOrNull() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Block JSON", style = MaterialTheme.typography.titleSmall)
        CodePanel(BlockDesignFactory.toJson(blueprint))
        OutlinedTextField(
            value = blueprint.generatorTemplate,
            onValueChange = { onBlueprintChange(blueprint.copy(generatorTemplate = it)) },
            label = { Text("Generator Stub") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Text("Generiertes Script", style = MaterialTheme.typography.titleSmall)
        CodePanel(BlockDesignFactory.generatorPreview(blueprint))
        Text("Node Projection Stub", style = MaterialTheme.typography.titleSmall)
        CodePanel(
            buildString {
                appendLine("shapeRole=${blueprint.nodeProjection.shapeRole}")
                appendLine("controlIn=${bundle?.nodeProjection?.controlFlowInputEnabled}")
                appendLine("controlOut=${bundle?.nodeProjection?.controlFlowOutputEnabled}")
                appendLine("valueInputs=${bundle?.nodeProjection?.valueInputPorts?.joinToString { it.name }}")
                appendLine("valueOutput=${bundle?.nodeProjection?.valueOutputPort?.name ?: "-"}")
                appendLine("structure=${bundle?.nodeProjection?.structurePorts?.joinToString { it.name }}")
                appendLine("inspectorOrder=${bundle?.inspectorProjection?.propertyOrder?.joinToString().orEmpty()}")
            }.trimEnd(),
        )
    }
}

@Composable
private fun ColorDropdown(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("colour", style = MaterialTheme.typography.labelMedium)
        FilterChip(selected = true, onClick = { expanded = true }, label = { Text(blueprint.color) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            colorOptions.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text("$label $value") },
                    onClick = {
                        onBlueprintChange(blueprint.copy(color = value))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TypeDropdown(value: String, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(selected = true, onClick = { expanded = true }, label = { Text(value.ifBlank { "Any" }) })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("Any", "Boolean", "Number", "String", "Array", "Object").forEach { type ->
            DropdownMenuItem(
                text = { Text(type) },
                onClick = {
                    onValue(type)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun CodePanel(value: String) {
    Text(
        text = value,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun RowToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun BlockDesignBlueprint.withAddedInput(kind: BlockDesignInputKind): BlockDesignBlueprint {
    if (kind == BlockDesignInputKind.END_ROW) {
        return copy(elements = elements + BlockDesignElement.input(BlockDesignInputDefinition(kind = kind, name = "endRow${elements.size + 1}")))
    }
    val index = elements.mapNotNull { it.input }.count { it.kind == kind } + 1
    val name = if (kind == BlockDesignInputKind.VALUE) "value$index" else "BODY$index"
    return copy(
        elements = elements + BlockDesignElement.input(
            BlockDesignInputDefinition(
                kind = kind,
                name = name,
                label = if (kind == BlockDesignInputKind.VALUE) "value" else "statement",
                connectionType = "Any",
            ),
        ),
    )
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> =
    mapIndexed { currentIndex, current -> if (currentIndex == index) value else current }

private fun <T> List<T>.removeAtIndex(index: Int): List<T> =
    filterIndexed { currentIndex, _ -> currentIndex != index }

private enum class FactoryTab(val label: String) {
    Definition("Definition"),
    Preview("Preview"),
    Generator("Generator"),
}

private data class FieldPreset(
    val label: String,
    val create: (Int) -> BlockDesignFieldBlueprint,
)

private val fieldPresets = listOf(
    FieldPreset("Text") { index -> BlockDesignFieldBlueprint("text$index", "Text $index", BlockDesignFieldType.TEXT) },
    FieldPreset("Text input") { index -> BlockDesignFieldBlueprint("input$index", "Input $index", BlockDesignFieldType.TEXT_INPUT) },
    FieldPreset("Dropdown") { index ->
        BlockDesignFieldBlueprint(
            name = "dropdown$index",
            label = "Dropdown $index",
            fieldType = BlockDesignFieldType.DROPDOWN,
            options = listOf(FieldOption("option1", "Option 1"), FieldOption("option2", "Option 2")),
            defaultValue = "option1",
        )
    },
    FieldPreset("Radio") { index ->
        BlockDesignFieldBlueprint(
            name = "radio$index",
            label = "Radio $index",
            fieldType = BlockDesignFieldType.RADIO_BUTTONS,
            options = listOf(FieldOption("option1", "Option 1"), FieldOption("option2", "Option 2")),
            defaultValue = "option1",
        )
    },
    FieldPreset("Switch") { index ->
        BlockDesignFieldBlueprint("switch$index", "Switch $index", BlockDesignFieldType.SWITCH, BlockDesignValueType.BOOL, "false")
    },
    FieldPreset("Checkbox") { index ->
        BlockDesignFieldBlueprint("checkbox$index", "Checkbox $index", BlockDesignFieldType.CHECKBOX, BlockDesignValueType.BOOL, "false")
    },
    FieldPreset("Number") { index ->
        BlockDesignFieldBlueprint("number$index", "Number $index", BlockDesignFieldType.NUMBER_INPUT, BlockDesignValueType.NUMBER, "0")
    },
    FieldPreset("Slider") { index ->
        BlockDesignFieldBlueprint("slider$index", "Slider $index", BlockDesignFieldType.SLIDER, BlockDesignValueType.NUMBER, "0")
    },
    FieldPreset("Image") { index ->
        BlockDesignFieldBlueprint("image$index", "Image $index", BlockDesignFieldType.IMAGE, BlockDesignValueType.IMAGE)
    },
)

private val colorOptions = listOf(
    "Input" to "#4B6F8F",
    "Perception" to "#3F735F",
    "Logic" to "#586E4B",
    "Variables" to "#6D607E",
    "Control" to "#87684A",
    "Runtime" to "#686E78",
    "Debug" to "#75617A",
    "Custom" to "#66707A",
)
