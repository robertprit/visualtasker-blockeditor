# Blockeditor Public Host API

This document describes the Studio-agnostic reusable surface of the native Kotlin/Compose
Blockeditor. VisualTasker Studio consumes it through a Gradle composite build and a thin
plugin adapter (M207B). This repository does not depend on `studio-core`.

## Future Studio identities (documentation only)

| Concept | Value |
|---------|-------|
| PluginId | `blockeditor` |
| PanelId | `blockeditor-panel` |
| EditorId | `blockeditor-editor` |
| Workspace media type | `application/vnd.visualtasker.blockeditor+json` |
| EMScript media type | `text/x-emscript` |

## Module graph

```
blockeditor-compose          (public UI + controller)
├── blockeditor-serialization (public document format)
├── blockeditor-validation
├── blockeditor-emscript
├── blockeditor-ir
├── blockeditor-interaction
├── blockeditor-layout
├── blockeditor-registry
└── blockeditor-domain
```

### Gradle coordinates

All modules share:

```
group:    de.visualtasker.blockeditor
version:  0.1.0-SNAPSHOT
```

Consumable modules for Studio integration:

| Module | Artifact ID |
|--------|-------------|
| `blockeditor-compose` | `blockeditor-compose` |
| `blockeditor-serialization` | `blockeditor-serialization` |
| `blockeditor-validation` | `blockeditor-validation` |

Transitive modules (via `blockeditor-compose`):

- `blockeditor-domain`
- `blockeditor-registry`
- `blockeditor-layout`
- `blockeditor-interaction`
- `blockeditor-ir`
- `blockeditor-emscript`

The `app` module is demo-only and must not be consumed by Studio.

### Composite build example

In VisualTasker Studio `settings.gradle.kts`:

```kotlin
includeBuild("../blockeditordemo")
```

In `studio-plugin-blockeditor/build.gradle.kts`:

```kotlin
dependencies {
    implementation("de.visualtasker.blockeditor:blockeditor-compose:0.1.0-SNAPSHOT")
    implementation("de.visualtasker.blockeditor:blockeditor-serialization:0.1.0-SNAPSHOT")
    implementation("de.visualtasker.blockeditor:blockeditor-validation:0.1.0-SNAPSHOT")
}
```

Gradle resolves these to the included build projects automatically when group/version match.

## Public entry points

### Document format

```kotlin
// blockeditor-serialization
object BlockEditorDocumentFormats {
    const val WORKSPACE_JSON = "application/vnd.visualtasker.blockeditor+json"
}

object WorkspaceSerializer {
    fun serialize(document: WorkspaceDocument): String
    fun deserialize(raw: String): WorkspaceDocument
}
```

### Workspace bootstrap

```kotlin
// blockeditor-registry
object WorkspaceBootstrap {
    fun empty(): WorkspaceDocument      // no blocks
    fun starter(): WorkspaceDocument    // single EVENT_START
}
```

`SampleWorkspaceFactory` remains demo/test-only. Host code must not depend on it.

### Host callbacks

```kotlin
// blockeditor-compose
interface BlockEditorHostCallbacks {
    fun onWorkspaceDocumentChanged(serializedJson: String)
    fun onEmscriptDraftChanged(emscript: String)
    fun onValidationErrors(errors: List<ValidationError>)
    fun onEmscriptGenerationFailed(message: String) {}
}
```

`onEmscriptGenerationFailed` is a source-compatible optional callback for generator
failures. Hosts can map this failure to diagnostics (for example to Studio ERROR
diagnostics) without replacing the last valid EMScript draft.

Not exposed: dirty state, persistence, save acknowledgment, Runtime, Workflow mutation,
or any Studio types. Dirty state is host-owned and derived from canonical serialized content.

### UI configuration

```kotlin
data class BlockEditorHostUiConfig(
    val showBottomPanel: Boolean = true,
    val showBlockFactory: Boolean = true,
    val allowClearWorkspace: Boolean = false,
)
```

### Controller

```kotlin
class BlockEditorController(
    initialDocument: WorkspaceDocument,
    callbacks: BlockEditorHostCallbacks = BlockEditorHostCallbacks.NoOp,
    ...
) : BlockEditorControllerState, AutoCloseable

// Convenience factory for hosts that want the minimal EVENT_START workspace:
BlockEditorController.starter(callbacks = BlockEditorHostCallbacks.NoOp)
```

### Compose host entry

```kotlin
@Composable
fun BlockEditorHost(
    controller: BlockEditorController,
    uiConfig: BlockEditorHostUiConfig = BlockEditorHostUiConfig(),
    modifier: Modifier = Modifier,
)
```

`BlockEditorScreen` and `BlockEditorViewModel` are demo-internal. Do not use them as the
public host entry point.

## Controller lifecycle

- Construct with an initial `WorkspaceDocument` (typically from `WorkspaceBootstrap` or
  `WorkspaceSerializer.deserialize`).
- On construction: emits one validation result and one EMScript draft. Does **not** emit
  `onWorkspaceDocumentChanged` (the host already owns the opening baseline).
- `close()` / `AutoCloseable`: cancels debounce jobs, marks disposed.
- After disposal: all methods are no-ops; no callbacks are emitted.

`BlockEditorHost` calls `controller.close()` in `DisposableEffect.onDispose`.

## Callback semantics

### Persistent document changes

Trigger `onWorkspaceDocumentChanged` immediately with canonical JSON from
`WorkspaceSerializer.serialize`:

- block create/delete
- committed block move (drag end)
- connection create/delete
- field/property change
- variable create/delete
- clear workspace
- undo/redo (when implemented)

### Transient interaction (no document callback)

- pointer move during drag
- snap/hover preview
- selection changes
- viewport pan/zoom

### Derived outputs (debounced 200 ms default)

After each persistent change, validation and EMScript are coalesced:

- `onValidationErrors`
- `onEmscriptDraftChanged`

When EMScript generation fails:

- `onEmscriptGenerationFailed` is emitted with a stable message
- no empty draft is emitted through `onEmscriptDraftChanged`
- the previous valid draft remains unchanged on the host side
- workspace document validity and dirty semantics remain unchanged

Initial construction emits derived outputs once without debounce.

## Canonical document format

- Schema version field: `"schemaVersion": 1` (`WORKSPACE_SCHEMA_VERSION`)
- Compact JSON (no pretty-print)
- Stable ordering:
  - blocks sorted by id
  - variables sorted by id
  - fields/metadata sorted by key
  - value/statement inputs sorted by name
  - connection `accepts` sorted
- No transient editor state, timestamps, or unstable map iteration
- Single domain model: `WorkspaceDocument` (no duplicate workspace model)

Unsupported `schemaVersion` or malformed JSON throws `WorkspaceSerializationException`.

## Ownership boundaries

| Concern | Owner |
|---------|-------|
| Dirty state | Studio host |
| Persistence / save | Studio host |
| Workflow authority | Studio host |
| Runtime execution | Studio host |
| Workspace document JSON | Blockeditor serializer |
| EMScript draft | Blockeditor generator (non-authoritative) |
| Validation errors | Blockeditor validator |

## Demo app

The demo uses `BlockEditorHost` + `BlockEditorController` with `WorkspaceBootstrap.starter()`.
`BlockEditorViewModel` remains available for demo-internal use only.
