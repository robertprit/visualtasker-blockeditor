package de.visualtasker.blockeditor.compose.layers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.visualtasker.blockeditor.compose.icons.BlockIcons
import de.visualtasker.blockeditor.compose.theme.blockEditorColors
import de.visualtasker.blockeditor.domain.WorkspaceAction
import de.visualtasker.blockeditor.registry.BlockDefinition
import de.visualtasker.blockeditor.registry.BlockTypes
import de.visualtasker.blockeditor.registry.DefaultBlockRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorChromeLayer(
    onAction: (WorkspaceAction) -> Unit,
    onClearWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var paletteOpen by remember { mutableStateOf(false) }
    val definitions = remember {
        listOf(
            BlockTypes.ACTION_CLICK_TEXT,
            BlockTypes.CONTROL_REPEAT,
            BlockTypes.CONTROL_IF_ELSEIF_ELSE,
            BlockTypes.LOGIC_BOOLEAN,
        ).mapNotNull { DefaultBlockRegistry.getDefinition(it) }
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text("Block Editor") },
            actions = {
                TextButton(onClick = onClearWorkspace) {
                    Text("Workspace leeren")
                }
                TextButton(onClick = { paletteOpen = true }) {
                    Text("Palette")
                }
            },
        )
    }

    if (paletteOpen) {
        ModalBottomSheet(
            onDismissRequest = { paletteOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PaletteContent(
                definitions = definitions,
                onPick = { definition ->
                    onAction(WorkspaceAction.InstantiateBlock(definition.id, 48f, 48f))
                    paletteOpen = false
                },
            )
        }
    }
}

@Composable
private fun PaletteContent(
    definitions: List<BlockDefinition>,
    onPick: (BlockDefinition) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Blöcke", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(definitions, key = { it.id }) { definition ->
                Surface(
                    modifier = Modifier.clickable { onPick(definition) },
                    color = blockEditorColors(definition.category).copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = BlockIcons.forBlockType(definition.id),
                            contentDescription = definition.label,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = definition.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
