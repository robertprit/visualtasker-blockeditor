package de.visualtasker.blockeditor.registry

data class DesignerPortProjection(
    val semanticPropertyId: String,
    val name: String,
    val dataType: String? = null,
    val category: SemanticPropertyCategory,
)

data class DesignerNodeProjection(
    val shapeRole: BlockDesignNodeShapeRole,
    val headerLabel: String,
    val controlFlowInputEnabled: Boolean,
    val controlFlowOutputEnabled: Boolean,
    val valueInputPorts: List<DesignerPortProjection>,
    val valueOutputPort: DesignerPortProjection?,
    val structurePorts: List<DesignerPortProjection>,
    val embeddedFieldKeys: List<String>,
    val collapsedLabel: String,
)

data class DesignerBlockProjection(
    val reporterOutputEnabled: Boolean,
    val valueInputSlots: List<DesignerPortProjection>,
    val statementContainers: List<DesignerPortProjection>,
)

data class DesignerInspectorProjection(
    val propertyOrder: List<String>,
)

data class DesignerProjectionBundle(
    val previewDefinition: BlockDefinition,
    val semanticProperties: List<SemanticPropertyRef>,
    val blockProjection: DesignerBlockProjection,
    val nodeProjection: DesignerNodeProjection,
    val inspectorProjection: DesignerInspectorProjection,
)

object BlockNodeDesignerProjector {
    fun project(blueprint: BlockDesignBlueprint): DesignerProjectionBundle {
        val baseDefinition = BlockDesignFactory.create(blueprint)
        val previewDefinition = applyMutatorLayoutProjection(
            applyMutatorProjection(baseDefinition, blueprint),
            blueprint,
        )
        val ownerId = blueprint.type.ifBlank { previewDefinition.id }
        val semanticProperties = BlockNodePresentationContract.semanticPropertiesForDefinition(
            ownerId = ownerId,
            definition = previewDefinition,
        )
        val semanticByKey = semanticProperties.associateBy { it.key }
        val valueInputs = previewDefinition.valueInputs.map { input ->
            DesignerPortProjection(
                semanticPropertyId = semanticByKey[input.name]?.id
                    ?: BlockNodePresentationContract.valueInputPropertyId(ownerId, input.name),
                name = input.name,
                dataType = input.accepts.firstOrNull(),
                category = SemanticPropertyCategory.INPUT,
            )
        }
        val structurePorts = previewDefinition.statementInputs.map { input ->
            DesignerPortProjection(
                semanticPropertyId = semanticByKey[input.name]?.id
                    ?: BlockNodePresentationContract.statementPropertyId(ownerId, input.name),
                name = input.name,
                category = SemanticPropertyCategory.STRUCTURE,
            )
        }
        val outputPort = previewDefinition.outputType?.let { outputType ->
            DesignerPortProjection(
                semanticPropertyId = BlockNodePresentationContract.outputPropertyId(ownerId),
                name = "output",
                dataType = outputType,
                category = SemanticPropertyCategory.OUTPUT,
            )
        }
        return DesignerProjectionBundle(
            previewDefinition = previewDefinition,
            semanticProperties = semanticProperties,
            blockProjection = DesignerBlockProjection(
                reporterOutputEnabled = outputPort != null,
                valueInputSlots = valueInputs,
                statementContainers = structurePorts,
            ),
            nodeProjection = DesignerNodeProjection(
                shapeRole = blueprint.nodeProjection.shapeRole,
                headerLabel = previewDefinition.label,
                controlFlowInputEnabled = blueprint.nodeProjection.showControlFlowInput && previewDefinition.hasPrevious,
                controlFlowOutputEnabled = blueprint.nodeProjection.showControlFlowOutput && previewDefinition.hasNext,
                valueInputPorts = if (blueprint.nodeProjection.showValueInputPorts) valueInputs else emptyList(),
                valueOutputPort = if (blueprint.nodeProjection.showValueOutputPort) outputPort else null,
                structurePorts = if (blueprint.nodeProjection.showStructurePorts) structurePorts else emptyList(),
                embeddedFieldKeys = blueprint.nodeProjection.embeddedFieldKeys,
                collapsedLabel = previewDefinition.label,
            ),
            inspectorProjection = DesignerInspectorProjection(
                propertyOrder = blueprint.inspectorProjection.propertyOrder.ifEmpty {
                    blueprint.nodeProjection.inspectorPropertyOrder
                },
            ),
        )
    }

    private fun applyMutatorProjection(
        definition: BlockDefinition,
        blueprint: BlockDesignBlueprint,
    ): BlockDefinition {
        val mutatorDefinition = blueprint.mutatorDefinition
        val mutatorState = blueprint.mutatorState
        return when (mutatorDefinition.family) {
            BlockDesignMutatorFamily.NONE -> definition
            BlockDesignMutatorFamily.WHILE -> {
                val condition = ValueInputDefinition(
                    name = "CONDITION",
                    label = "condition",
                    accepts = setOf("Bool", "Boolean"),
                )
                val body = StatementInputDefinition(
                    name = "BODY",
                    label = "body",
                )
                definition.copy(
                    valueInputs = (definition.valueInputs + condition).distinctBy { it.name },
                    statementInputs = (definition.statementInputs + body).distinctBy { it.name },
                )
            }
            BlockDesignMutatorFamily.IF -> {
                val elseIfs = mutatorState.elseIfBranches
                val valueInputs = buildList {
                    add(
                        ValueInputDefinition(
                            name = "CONDITION",
                            label = "condition",
                            accepts = setOf("Bool", "Boolean"),
                        ),
                    )
                    elseIfs.forEach { elseIf ->
                        add(
                            ValueInputDefinition(
                                name = "ELIF_CONDITION_${elseIf.instanceId}",
                                label = elseIf.displayLabel.lowercase(),
                                accepts = setOf("Bool", "Boolean"),
                            ),
                        )
                    }
                }
                val statements = buildList {
                    add(StatementInputDefinition("THEN", "then"))
                    elseIfs.forEach { elseIf ->
                        add(
                            StatementInputDefinition(
                                name = "ELIF_${elseIf.instanceId}",
                                label = elseIf.displayLabel.lowercase(),
                            ),
                        )
                    }
                    if (mutatorState.hasElseBranch && mutatorDefinition.allowElseBranch) {
                        add(StatementInputDefinition("ELSE", "else"))
                    }
                }
                definition.copy(valueInputs = valueInputs, statementInputs = statements)
            }
        }
    }

    private fun applyMutatorLayoutProjection(
        definition: BlockDefinition,
        blueprint: BlockDesignBlueprint,
    ): BlockDefinition {
        if (definition.statementInputs.isEmpty()) return definition
        val existing = definition.metadata.toMutableMap()
        val existingHeaderRows = existing["custom.layout.headerRows"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val existingRowCount = existing["custom.layout.rowCount"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val existingMaxCols = existing["custom.layout.maxRowColumns"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        when (blueprint.mutatorDefinition.family) {
            BlockDesignMutatorFamily.IF -> {
                // Blockly-like IF layout:
                // row0: CONDITION
                // row1: THEN statement
                // row2: ELIF_CONDITION_<id> (if present)
                // row3: ELIF_<id> statement
                // ...
                // last: ELSE statement (if enabled)
                existing["custom.layout.headerRows"] = "1"
                existing["custom.layout.input.CONDITION.row"] = "0"
                existing["custom.layout.input.CONDITION.column"] = "0"

                var rowCursor = 1
                existing["custom.layout.input.THEN.row"] = rowCursor.toString()
                existing["custom.layout.input.THEN.column"] = "0"
                rowCursor += 1

                blueprint.mutatorState.elseIfBranches.forEach { branch ->
                    val conditionName = "ELIF_CONDITION_${branch.instanceId}"
                    val statementName = "ELIF_${branch.instanceId}"
                    existing["custom.layout.input.$conditionName.row"] = rowCursor.toString()
                    existing["custom.layout.input.$conditionName.column"] = "0"
                    rowCursor += 1
                    existing["custom.layout.input.$statementName.row"] = rowCursor.toString()
                    existing["custom.layout.input.$statementName.column"] = "0"
                    rowCursor += 1
                }

                if (definition.statementInputs.any { it.name == "ELSE" }) {
                    existing["custom.layout.input.ELSE.row"] = rowCursor.toString()
                    existing["custom.layout.input.ELSE.column"] = "0"
                    rowCursor += 1
                }

                existing["custom.layout.rowCount"] = maxOf(existingRowCount, rowCursor).toString()
                existing["custom.layout.maxRowColumns"] = maxOf(existingMaxCols, 2).toString()
            }

            BlockDesignMutatorFamily.WHILE -> {
                existing["custom.layout.headerRows"] = "1"
                existing["custom.layout.input.CONDITION.row"] = "0"
                existing["custom.layout.input.CONDITION.column"] = "0"
                existing["custom.layout.input.BODY.row"] = "1"
                existing["custom.layout.input.BODY.column"] = "0"
                existing["custom.layout.rowCount"] = maxOf(existingRowCount, 2).toString()
                existing["custom.layout.maxRowColumns"] = maxOf(existingMaxCols, 2).toString()
            }

            else -> {
                if (existing["custom.layout.designer"] == "true") {
                    // Keep linear factory row metadata untouched so Value/Statement
                    // order stays exactly as arranged in elements[].
                    val currentRows = existing["custom.layout.rowCount"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    existing["custom.layout.rowCount"] = maxOf(existingRowCount, currentRows).toString()
                    existing["custom.layout.maxRowColumns"] = maxOf(existingMaxCols, 1).toString()
                    return definition.copy(metadata = existing)
                }
                val inferredHeaderRows = maxOf(
                    existingHeaderRows,
                    ((definition.valueInputs.size + definition.fields.size + 1) / 2).coerceAtLeast(1),
                )
                existing["custom.layout.headerRows"] = inferredHeaderRows.toString()
                definition.valueInputs.forEachIndexed { index, input ->
                    val keyRow = "custom.layout.input.${input.name}.row"
                    val keyCol = "custom.layout.input.${input.name}.column"
                    if (existing[keyRow] == null) {
                        existing[keyRow] = (index / 2).toString()
                    }
                    if (existing[keyCol] == null) {
                        existing[keyCol] = (index % 2).toString()
                    }
                }
                var branchRow = inferredHeaderRows
                definition.statementInputs.forEach { input ->
                    existing["custom.layout.input.${input.name}.row"] = branchRow.toString()
                    existing["custom.layout.input.${input.name}.column"] = "0"
                    branchRow += 2
                }
                val projectedRows = maxOf(existingRowCount, branchRow.coerceAtLeast(inferredHeaderRows + 1))
                existing["custom.layout.rowCount"] = projectedRows.toString()
                existing["custom.layout.maxRowColumns"] = maxOf(existingMaxCols, 2).toString()
            }
        }

        return definition.copy(metadata = existing)
    }
}
