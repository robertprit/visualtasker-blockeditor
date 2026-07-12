# Blockeditor – Implementierungsstatus (Neuaufbau)

Stand: vollständiger Neuaufbau nach Master-Prompt (Connection-Graph, LayoutCache, TransientState, Render-Layer).

## Module

| Modul | Rolle |
|-------|--------|
| `blockeditor-domain` | WorkspaceDocument, Graph, Reducer, Actions |
| `blockeditor-registry` | BlockDefinition, MVP-Blöcke, SampleWorkspace |
| `blockeditor-layout` | LayoutEngine, LayoutCache, SpatialIndex, HitPrimitives |
| `blockeditor-interaction` | Viewport, Drag, Snap, HitTest (transient) |
| `blockeditor-compose` | WorkspaceLayer, DragLayer, SnapPreview, Chrome |
| `blockeditor-serialization` | JSON Save/Load |
| `blockeditor-validation` | Graph-/Typ-Validator |
| `blockeditor-ir` | VisualTasker IR |
| `blockeditor-emscript` | EMScript-Generator |
| `app` | Demo-App |

## Architektur (erzwungen)

```
WorkspaceDocument → LayoutEngine → LayoutCache
  → WorkspaceLayer / DragLayer / SnapPreviewLayer
  → PointerInput → HitTest / SnapEngine
  → WorkspaceAction → Reducer → WorkspaceDocument
```

- **DragMove** mutiert weder Document noch LayoutCache
- **StatementInput** = Socket only, Stack über Previous/Next
- **Drop** = genau eine WorkspaceAction, danach Layout-Rebuild

## Tests

Alle Unit-Tests grün (`./gradlew test`).

## Noch offen (nach MVP)

- Undo/Redo
- Viewport-Culling
- UI-Tests auf Gerät
- Collapse/Expand UI
- Variable-Editor UI

## Entfernt

Alte Module `blockeditor-model`, `blockeditor-renderer`, `blockeditor-codegen`, alte `blockeditor-interaction` – komplett gelöscht.
