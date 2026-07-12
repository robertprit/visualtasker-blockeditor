package de.visualtasker.blockeditor.compose.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.icons.BlockIcons
import de.visualtasker.blockeditor.compose.icons.CategoryIcons
import de.visualtasker.blockeditor.registry.BlockCategories
import de.visualtasker.blockeditor.registry.BlockDefinition

@Composable
fun EditorNavigationRail(
    expandedCategory: String?,
    onCategoryClick: (String) -> Unit,
    onOpenBlockFactory: () -> Unit,
    onClearWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = BlockCategories.all.filter { it.id != BlockCategories.CUSTOM }

    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(12.dp))
        categories.forEach { category ->
            val selected = expandedCategory == category.id
            val accent = Color(category.accentArgb)
            NavigationRailItem(
                selected = selected,
                onClick = { onCategoryClick(category.id) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) accent.copy(alpha = 0.28f)
                                else accent.copy(alpha = 0.12f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CategoryIcons.forCategory(category.id),
                            contentDescription = category.label,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                label = null,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = expandedCategory == BlockCategories.CUSTOM,
            onClick = onOpenBlockFactory,
            icon = {
                Icon(
                    imageVector = CategoryIcons.forCategory(BlockCategories.CUSTOM),
                    contentDescription = "Block Factory",
                    modifier = Modifier.size(24.dp),
                )
            },
            label = null,
        )
        NavigationRailItem(
            selected = false,
            onClick = onClearWorkspace,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Workspace leeren",
                    modifier = Modifier.size(22.dp),
                )
            },
            label = null,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryPalettePanel(
    category: String?,
    definitions: List<BlockDefinition>,
    onAddBlock: (BlockDefinition) -> Unit,
    onCreateVariable: ((name: String, type: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (category == null) return
    val meta = BlockCategories.metaFor(category)
    val accent = Color(meta.accentArgb)
    var showCreateVariableDialog by remember { mutableStateOf(false) }
    val variableTypes = remember { listOf("Any", "Number", "Boolean") }

    if (showCreateVariableDialog && onCreateVariable != null) {
        CreateVariableDialog(
            types = variableTypes,
            onDismiss = { showCreateVariableDialog = false },
            onConfirm = { name, type ->
                onCreateVariable(name, type)
                showCreateVariableDialog = false
            },
        )
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                    )
                    Text(
                        text = "Tippe einen Block-Chip zum Hinzufügen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Schließen")
                }
            }
            if (onCreateVariable != null && category == BlockCategories.VARIABLE) {
                FilterChip(
                    selected = false,
                    onClick = { showCreateVariableDialog = true },
                    label = { Text("Neue Variable") },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = accent.copy(alpha = 0.22f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = accent,
                    ),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                definitions.forEach { definition ->
                    FilterChip(
                        selected = false,
                        onClick = { onAddBlock(definition) },
                        label = { Text(definition.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = BlockIcons.forBlockType(definition.id),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = accent.copy(alpha = 0.14f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            iconColor = accent,
                        ),
                    )
                }
            }
            if (definitions.isEmpty()) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Keine Blöcke in dieser Kategorie") },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateVariableDialog(
    types: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(types.first()) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Typ") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false },
                        ) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        typeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedType) },
                enabled = name.isNotBlank(),
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}
