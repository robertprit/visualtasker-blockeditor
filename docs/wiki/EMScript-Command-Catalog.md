# EMScript Command Catalog

Status: initial implementation contract.

The command catalog is the shared registry for EMScript command semantics. It is intentionally placed in `blockeditor-registry` because the first consumers are block definitions, palette grouping and editor projections. Runtime execution, Flowchart and parser integrations should consume the catalog through the public model instead of duplicating command metadata.

## Current Scope

The first catalog slice covers the existing built-in foundation:

- start event
- `wait`
- `click`
- `findTemplate`
- `beep`
- `vibrate`
- `log`
- `set`
- variable get/reporters
- `repeat`
- `while`
- `if`
- `if` / `else`
- `if` / `else if` / `else`
- boolean, number and string literals
- boolean reporters
- comparison and arithmetic operator reporters

## Catalog Entry Fields

Each entry defines:

- stable id
- canonical EMScript name
- accepted legacy aliases
- command kind
- palette category
- argument schema
- return type
- side-effect class
- required runtime capabilities
- owning plugin
- block binding
- Flowchart binding
- runtime binding

## Migration Rule

Parser import may use `findByAcceptedName`.

Generator export should use `canonicalName`.

Blockeditor and Flowchart should use `findByBlockType`.

Runtime and dry-run should use the entry capability and runtime binding before executing or simulating a command.
