package de.visualtasker.blockeditor.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoField
import de.visualtasker.blockeditor.compose.viewmodel.BlockInfoSnapshot
import de.visualtasker.blockeditor.domain.BlockId
import de.visualtasker.blockeditor.registry.FieldKind

@Composable
fun EditorBottomPanel(
    code: String,
    blockInfo: BlockInfoSnapshot?,
    onFieldChange: (String, String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Block Info",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (info == null) {
            Text(
                text = "Kein Block ausgewählt.\nTippen zum Markieren, langes Drücken zum Ziehen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        val accent = Color(info.categoryAccentArgb)
        InfoRow("Typ", info.label)
        InfoRow("ID", info.typeId, mono = true)
        CategoryBadge(info.categoryLabel, accent)
        if (info.fields.isNotEmpty()) {
            Text(
                text = "Felder",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            info.fields.forEach { field ->
                BlockFieldEditor(
                    blockId = info.blockId,
                    field = field,
                    onFieldChange = onFieldChange,
                )
            }
        }
        if (info.slotContext != null) {
            InfoRow("Slot", info.slotContext)
        }
        InfoRow("Kette", info.chainSummary)
    }
}

@Composable
private fun BlockFieldEditor(
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
        FieldKind.NUMBER, FieldKind.TEXT -> {
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
            )
        }
    }
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
