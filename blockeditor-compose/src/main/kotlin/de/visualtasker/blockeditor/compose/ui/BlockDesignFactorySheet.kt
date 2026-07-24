package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignFactory
import de.visualtasker.blockeditor.registry.BlockDesignFieldBlueprint
import de.visualtasker.blockeditor.registry.BlockDesignFieldType
import de.visualtasker.blockeditor.registry.BlockDesignInputDefinition
import de.visualtasker.blockeditor.registry.BlockDesignInputKind
import de.visualtasker.blockeditor.registry.BlockDesignValueType
import de.visualtasker.blockeditor.registry.ParameterSourceKind

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
            Text(
                text = "Block Design Factory",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Definiert Blocktypen als Datenmodell. Keine Runtime-Ausführung, keine Runtime-Autorität.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    FactoryTab.Definition -> DefinitionEditor(
                        blueprint = blueprint,
                        onBlueprintChange = { blueprint = it },
                    )
                    FactoryTab.Preview -> FactoryPreview(blueprint)
                    FactoryTab.Json -> CodePanel(BlockDesignFactory.toJson(blueprint))
                    FactoryTab.Generator -> CodePanel(BlockDesignFactory.generatorPreview(blueprint))
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
        OutlinedTextField(
            value = blueprint.color,
            onValueChange = { onBlueprintChange(blueprint.copy(color = it)) },
            label = { Text("color") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        RowToggle(
            label = "previousConnection",
            checked = blueprint.hasPrevious,
            onCheckedChange = { onBlueprintChange(blueprint.copy(hasPrevious = it)) },
        )
        RowToggle(
            label = "nextConnection",
            checked = blueprint.hasNext,
            onCheckedChange = { onBlueprintChange(blueprint.copy(hasNext = it)) },
        )
        RowToggle(
            label = "Reporter / outputConnection",
            checked = blueprint.isReporter,
            onCheckedChange = {
                onBlueprintChange(
                    blueprint.copy(
                        isReporter = it,
                        outputType = if (it) blueprint.outputType ?: "Any" else null,
                        hasPrevious = if (it) false else blueprint.hasPrevious,
                        hasNext = if (it) false else blueprint.hasNext,
                    ),
                )
            },
        )
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
        InputListEditor(blueprint, onBlueprintChange)
        FieldListEditor(blueprint, onBlueprintChange)
        OutlinedTextField(
            value = blueprint.generatorTemplate,
            onValueChange = { onBlueprintChange(blueprint.copy(generatorTemplate = it)) },
            label = { Text("Generator Preview Template") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }
}

@Composable
private fun InputListEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Text("Inputs", style = MaterialTheme.typography.titleSmall)
    blueprint.inputs.forEachIndexed { index, input ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${input.kind.name.lowercase()} · ${input.name}", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = input.name,
                    onValueChange = {
                        onBlueprintChange(blueprint.copy(inputs = blueprint.inputs.replace(index, input.copy(name = it))))
                    },
                    label = { Text("name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = input.connectionType,
                    onValueChange = {
                        onBlueprintChange(
                            blueprint.copy(inputs = blueprint.inputs.replace(index, input.copy(connectionType = it))),
                        )
                    },
                    label = { Text("connectionType") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = input.kind == BlockDesignInputKind.VALUE,
                )
                RowToggle(
                    label = "required",
                    checked = input.required,
                    onCheckedChange = {
                        onBlueprintChange(blueprint.copy(inputs = blueprint.inputs.replace(index, input.copy(required = it))))
                    },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val next = BlockDesignInputDefinition(
                    kind = BlockDesignInputKind.VALUE,
                    name = "value${blueprint.inputs.size + 1}",
                    connectionType = "Any",
                )
                onBlueprintChange(blueprint.copy(inputs = blueprint.inputs + next))
            },
        ) { Text("+ Value") }
        Button(
            onClick = {
                val next = BlockDesignInputDefinition(
                    kind = BlockDesignInputKind.STATEMENT,
                    name = "BODY${blueprint.inputs.size + 1}",
                    label = "body",
                )
                onBlueprintChange(blueprint.copy(inputs = blueprint.inputs + next))
            },
        ) { Text("+ Statement") }
    }
}

@Composable
private fun FieldListEditor(
    blueprint: BlockDesignBlueprint,
    onBlueprintChange: (BlockDesignBlueprint) -> Unit,
) {
    Text("InfoPanel Fields", style = MaterialTheme.typography.titleSmall)
    blueprint.infoFields.forEachIndexed { index, field ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${field.fieldType.name.lowercase()} · ${field.name}", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = field.name,
                    onValueChange = {
                        onBlueprintChange(blueprint.copy(infoFields = blueprint.infoFields.replace(index, field.copy(name = it))))
                    },
                    label = { Text("name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = field.defaultValue,
                    onValueChange = {
                        onBlueprintChange(
                            blueprint.copy(infoFields = blueprint.infoFields.replace(index, field.copy(defaultValue = it))),
                        )
                    },
                    label = { Text("defaultValue") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    text = "type=${field.valueType.name.lowercase()} · sources=${field.allowedSources.joinToString { it.name.lowercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Button(
        onClick = {
            val next = BlockDesignFieldBlueprint(
                name = "field${blueprint.infoFields.size + 1}",
                fieldType = BlockDesignFieldType.TEXT_INPUT,
                valueType = BlockDesignValueType.STRING,
                allowedSources = listOf(ParameterSourceKind.MANUAL, ParameterSourceKind.REPORTER, ParameterSourceKind.VARIABLE),
            )
            onBlueprintChange(blueprint.copy(infoFields = blueprint.infoFields + next))
        },
    ) { Text("+ Field") }
}

@Composable
private fun FactoryPreview(blueprint: BlockDesignBlueprint) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(BlockDesignFactory.previewLabel(blueprint), style = MaterialTheme.typography.titleLarge)
            Text(
                text = buildString {
                    append("connections: ")
                    append(if (blueprint.hasPrevious) "previous " else "")
                    append(if (blueprint.hasNext) "next " else "")
                    append(blueprint.outputType?.let { "output:$it" }.orEmpty())
                }.ifBlank { "connections: none" },
                style = MaterialTheme.typography.bodySmall,
            )
            blueprint.inputs.forEach { input ->
                Text(
                    text = "${input.kind.name.lowercase()} ${input.name}: ${input.connectionType}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Werte erscheinen im InfoPanel, nicht in der Blockform.", style = MaterialTheme.typography.labelMedium)
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
private fun RowToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private enum class FactoryTab(val label: String) {
    Definition("Definition"),
    Preview("Preview"),
    Json("JSON"),
    Generator("Generator"),
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> =
    mapIndexed { currentIndex, current -> if (currentIndex == index) value else current }
