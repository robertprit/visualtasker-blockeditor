package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockDesignBlueprint
import de.visualtasker.blockeditor.registry.FieldDefinition
import de.visualtasker.blockeditor.registry.FieldKind

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockDesignFactorySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (BlockDesignBlueprint) -> Unit,
) {
    if (!visible) return

    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BlockCategories.CUSTOM) }
    var hasPrevious by remember { mutableStateOf(true) }
    var hasNext by remember { mutableStateOf(true) }
    var fieldValue by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Block Design Factory",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Eigene Statement-Blöcke entwerfen und direkt in der Palette nutzen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Block-Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("Kategorie", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockCategories.all.forEach { meta ->
                    FilterChip(
                        selected = category == meta.id,
                        onClick = { category = meta.id },
                        label = { Text(meta.label) },
                    )
                }
            }
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                label = { Text("Standard-Feldwert (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            RowToggle(label = "Hat Previous-Notch", checked = hasPrevious, onCheckedChange = { hasPrevious = it })
            RowToggle(label = "Hat Next-Tab", checked = hasNext, onCheckedChange = { hasNext = it })
            Button(
                onClick = {
                    if (label.isBlank()) return@Button
                    onCreate(
                        BlockDesignBlueprint(
                            label = label,
                            category = category,
                            hasPrevious = hasPrevious,
                            hasNext = hasNext,
                            fields = listOf(
                                FieldDefinition(
                                    key = "payload",
                                    label = "value",
                                    kind = FieldKind.TEXT,
                                    defaultValue = fieldValue,
                                ),
                            ),
                        ),
                    )
                    label = ""
                    fieldValue = ""
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = label.isNotBlank(),
            ) {
                Text("Block erstellen")
            }
        }
    }
}

@Composable
private fun RowToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
