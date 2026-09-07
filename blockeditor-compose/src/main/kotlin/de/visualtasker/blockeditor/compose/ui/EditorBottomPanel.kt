package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.model.ReporterVisualMode
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoField
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.compose.viewmodel.label
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.registry.FieldKind
import de.visualtasker.blockeditor.registry.ParameterSourceKind

@Composable
fun EditorBottomPanel(
    code: String,
    blockInfo: BlockInfoSnapshot?,
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit,
    onSetReporterVisualMode: (ReporterVisualMode) -> Unit = {},
    onToggleBlockActive: () -> Boolean = { false },
    onToggleBlockCollapse: () -> Boolean = { false },
    onReplaceBlockType: (String) -> Boolean = { false },
    onAddBranch: () -> Boolean = { false },
    onRemoveBranch: () -> Boolean = { false },
    onUpdateBlockNote: (String) -> Boolean = { false },
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Inspector",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                IconButton(onClick = onToggleVisible) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = "Panel einklappen")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                BlockInfoCard(
                    info = blockInfo,
                    onFieldChange = onFieldChange,
                    onFieldSourceChange = onFieldSourceChange,
                    onSetReporterVisualMode = onSetReporterVisualMode,
                    onToggleBlockActive = onToggleBlockActive,
                    onToggleBlockCollapse = onToggleBlockCollapse,
                    onReplaceBlockType = onReplaceBlockType,
                    onAddBranch = onAddBranch,
                    onRemoveBranch = onRemoveBranch,
                    onUpdateBlockNote = onUpdateBlockNote,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 6.dp, bottom = 12.dp),
                )
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                CodePreviewPanel(
                    code = code,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 6.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
fun BlockInfoCard(
    info: BlockInfoSnapshot?,
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit,
    onSetReporterVisualMode: (ReporterVisualMode) -> Unit = {},
    onToggleBlockActive: () -> Boolean = { false },
    onToggleBlockCollapse: () -> Boolean = { false },
    onReplaceBlockType: (String) -> Boolean = { false },
    onAddBranch: () -> Boolean = { false },
    onRemoveBranch: () -> Boolean = { false },
    onUpdateBlockNote: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (info == null) {
            Text(
                text = "Kein Block ausgewählt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        val accent = Color(info.categoryAccentArgb)
        BlockInspectorHeader(
            info = info,
            accent = accent,
            onReplaceBlockType = onReplaceBlockType,
        )
        InspectorActions(
            info = info,
            onToggleBlockActive = onToggleBlockActive,
            onToggleBlockCollapse = onToggleBlockCollapse,
            onReplaceBlockType = onReplaceBlockType,
            onAddBranch = onAddBranch,
            onRemoveBranch = onRemoveBranch,
        )
        NoteEditor(
            blockId = info.blockId,
            note = info.note,
            onUpdateBlockNote = onUpdateBlockNote,
        )
        if (info.isReporter) {
            Text(
                text = "Reporter-Darstellung",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = info.reporterVisualMode == ReporterVisualMode.COMPACT,
                    onClick = { onSetReporterVisualMode(ReporterVisualMode.COMPACT) },
                    label = { Text("Kompakt") },
                )
                FilterChip(
                    selected = info.reporterVisualMode == ReporterVisualMode.DETAILED,
                    onClick = { onSetReporterVisualMode(ReporterVisualMode.DETAILED) },
                    label = { Text("Ausführlich") },
                )
            }
        }
        val parameterFields = info.fields.filterNot { it.key == "note" }
        if (parameterFields.isNotEmpty()) {
            Text(
                text = "Parameter",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            parameterFields.forEach { field ->
                BlockFieldEditor(
                    blockId = info.blockId,
                    field = field,
                    onFieldChange = onFieldChange,
                    onFieldSourceChange = onFieldSourceChange,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockInspectorHeader(
    info: BlockInfoSnapshot,
    accent: Color,
    onReplaceBlockType: (String) -> Boolean,
) {
    var typeMenuExpanded by remember(info.blockId, info.typeId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = info.label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Anzeige-Label") },
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.86f),
                tonalElevation = 2.dp,
            ) {}
        }
        Box {
            OutlinedTextField(
                value = info.typeOptions.firstOrNull { it.typeId == info.typeId }?.let {
                    "${it.label} · ${it.categoryLabel}"
                } ?: "${info.label} · ${info.categoryLabel}",
                onValueChange = {},
                readOnly = true,
                enabled = info.typeOptions.size > 1,
                singleLine = true,
                label = { Text("Typ ändern") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = info.typeOptions.size > 1) { typeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                info.typeOptions
                    .groupBy { it.categoryLabel }
                    .forEach { (category, options) ->
                        DropdownMenuItem(
                            text = { Text(category, color = MaterialTheme.colorScheme.primary) },
                            enabled = false,
                            onClick = {},
                        )
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                enabled = option.typeId != info.typeId,
                                onClick = {
                                    typeMenuExpanded = false
                                    onReplaceBlockType(option.typeId)
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun InspectorActions(
    info: BlockInfoSnapshot,
    onToggleBlockActive: () -> Boolean,
    onToggleBlockCollapse: () -> Boolean,
    onReplaceBlockType: (String) -> Boolean,
    onAddBranch: () -> Boolean,
    onRemoveBranch: () -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = info.active,
                onClick = { onToggleBlockActive() },
                label = { Text(if (info.active) "Aktiv" else "Inaktiv") },
            )
            FilterChip(
                selected = info.collapsed,
                onClick = { onToggleBlockCollapse() },
                label = { Text(if (info.collapsed) "Ausklappen" else "Einklappen") },
            )
        }
        if (info.branchCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { onAddBranch() },
                    enabled = info.canAddBranch,
                    label = { Text("Branch +") },
                )
                FilterChip(
                    selected = false,
                    onClick = { onRemoveBranch() },
                    enabled = info.canRemoveBranch,
                    label = { Text("Branch -") },
                )
            }
        }
    }
}

@Composable
private fun NoteEditor(
    blockId: BlockId,
    note: String,
    onUpdateBlockNote: (String) -> Boolean,
) {
    var draft by remember(blockId) { mutableStateOf(note) }
    LaunchedEffect(blockId, note) {
        draft = note
    }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            onUpdateBlockNote(it)
        },
        label = { Text("Notiz") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 1,
        maxLines = 3,
    )
}

@Composable
private fun BlockFieldEditor(
    blockId: BlockId,
    field: BlockInfoField,
    onFieldChange: (String, String) -> Unit,
    onFieldSourceChange: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (field.sourceOptions.size > 1) {
            SourceFieldEditor(field, onFieldSourceChange)
        } else {
            Text(
                text = sourceSummary(field),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (field.reporterAllowed || field.variableAllowed) {
            Text(
                text = buildList {
                    if (field.reporterAllowed) add("Reporter erlaubt")
                    if (field.variableAllowed) add("Variable erlaubt")
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValueFieldEditor(blockId, field, onFieldChange)
        field.diagnostic?.let { diagnostic ->
            Text(
                text = diagnostic,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ValueFieldEditor(
    blockId: BlockId,
    field: BlockInfoField,
    onFieldChange: (String, String) -> Unit,
) {
    when (field.kind) {
        FieldKind.BOOLEAN -> {
            val checked = field.value.equals("true", ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = checked,
                    onCheckedChange = { onFieldChange(field.key, it.toString()) },
                )
            }
        }
        FieldKind.CHOICE -> ChoiceFieldEditor(field, onFieldChange)
        FieldKind.NUMBER,
        FieldKind.TEXT,
        FieldKind.VARIABLE_REF,
        FieldKind.FILE_PATH,
        FieldKind.IMAGE_TEMPLATE,
        FieldKind.REGION,
        FieldKind.TIMEOUT_MS,
        FieldKind.RETRY_COUNT,
        FieldKind.THRESHOLD,
        -> {
            var draft by remember(blockId, field.key) { mutableStateOf(field.value) }
            LaunchedEffect(blockId, field.key) {
                draft = field.value
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onFieldChange(field.key, it)
                },
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = field.diagnostic != null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceFieldEditor(
    field: BlockInfoField,
    onFieldChange: (String, String) -> Unit,
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    val selected = field.options.firstOrNull { it.value == field.value }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.label ?: field.value,
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onFieldChange(field.key, option.value)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceFieldEditor(
    field: BlockInfoField,
    onFieldSourceChange: (String, String) -> Unit,
) {
    var expanded by remember(field.key, field.source) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = field.source.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("${field.label} Quelle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            field.sourceOptions.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.label()) },
                    onClick = {
                        expanded = false
                        onFieldSourceChange(field.key, source.name)
                    },
                )
            }
        }
    }
}

private fun sourceSummary(field: BlockInfoField): String {
    val mode = when (field.source) {
        ParameterSourceKind.MANUAL -> "manuell gesetzt"
        ParameterSourceKind.REPORTER -> "durch Reporter gespeist"
        ParameterSourceKind.VARIABLE -> "durch Variable gespeist"
        ParameterSourceKind.PRESET -> "per Preset gesetzt"
        ParameterSourceKind.FILE -> "per Datei/Image gesetzt"
        ParameterSourceKind.REGION_MANUAL -> "Region manuell gesetzt"
        ParameterSourceKind.REGION_REPORTER -> "Region per Reporter gespeist"
    }
    return "Quelle: $mode"
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun CategoryBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
