package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
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
import de.visualtasker.blockeditor.registry.BlockDesignValueType
import de.visualtasker.blockeditor.registry.FieldOption
import de.visualtasker.blockeditor.registry.StaticBlockRegistry
import de.visualtasker.blockeditor.registry.createNode
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDesignFactorySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (BlockDesignBlueprint) -> Unit,
) {
    if (!visible) return

    var blueprint by remember { mutableStateOf(BlockDesignFactory.findTemplateBlueprint()) }
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
    }
}

@Composable
private fun PreviewEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RenderedBlockPreview(blueprint)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onBlueprintChange(blueprint.withAddedInput(BlockDesignInputKind.VALUE)) }) { Text("+ Value") }
            Button(onClick = { onBlueprintChange(blueprint.withAddedInput(BlockDesignInputKind.STATEMENT)) }) { Text("+ Statement") }
            Button(onClick = { onBlueprintChange(blueprint.withAddedInput(BlockDesignInputKind.END_ROW)) }) { Text("+ End Row") }
            fieldPresets.forEach { preset ->
                Button(
                    onClick = {
                        onBlueprintChange(
                            blueprint.copy(
                                elements = blueprint.elements + BlockDesignElement.field(preset.create(blueprint.elements.size + 1)),
                            ),
                        )
                    },
                ) { Text("+ ${preset.label}") }
            }
        }
        DesignElementCards(blueprint, onBlueprintChange)
    }
}

@Composable
private fun RenderedBlockPreview(blueprint: BlockDesignBlueprint) {
    val textMeasurer = rememberTextMeasurer()
    val definition = remember(blueprint) { runCatching { BlockDesignFactory.create(blueprint) }.getOrNull() }
    val registry = remember(definition) { StaticBlockRegistry(listOfNotNull(definition)) }
    val block = remember(definition) { definition?.createNode(BlockId("factory-preview")) }
    val rows = definition?.metadata?.get("custom.layout.rowCount")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val columns = definition?.metadata?.get("custom.layout.maxRowColumns")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val previewWidth = max(288f, 32f + columns * 86f + 16f)
    val canvasHeight = (96 + rows * 54).dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .padding(14.dp)
                .background(Color(0xFF11131A), MaterialTheme.shapes.small),
            contentAlignment = Alignment.CenterStart,
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
                drawBlock(
                    block = safeBlock,
                    definition = safeDefinition,
                    topLeft = Offset(24f, 24f),
                    width = if (safeDefinition.isReporter) 120f else previewWidth,
                    height = if (safeDefinition.isReporter) 40f else 44f * rows + 24f,
                    textMeasurer = textMeasurer,
                    colors = darkBlockEditorColors(),
                    registry = registry,
                )
            }
            if (definition != null) {
                DesignerPreviewControls(definition)
            }
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
