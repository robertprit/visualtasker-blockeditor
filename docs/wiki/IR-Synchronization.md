# IR Synchronization

Status: private/pre-public working documentation.

IR synchronization is the boundary between editor-owned visual documents and
validated semantic workflow representations.

## Principles

- Editing projections must not be treated as accepted runtime authority.
- Conversion into IR should be explicit, typed, and validated.
- Unsupported legacy blocks should remain visible or diagnostic rather than
  guessed.
- Runtime should execute accepted, stable semantic input, not mutable UI state.

## Current Blockeditor State

The Blockeditor workspace document can preserve blocks, fields, connections,
root positions, and metadata. That document is useful for editing and reload,
but it is not enough by itself to prove runtime-safe semantics.

## Synchronization Directions

- Workspace document to IR: requires validator and converter authority.
- IR to visual projection: should preserve enough identity for inspection, but
  must not create hidden editor-only semantics.
- Flowchart projection: should remain derived from semantic input or a clearly
  documented bridge.
- EMScript projection: draft output must be identified as draft until accepted
  by the owning workflow layer.

## Open Questions

- Which package owns final converter diagnostics.
- How rejected conversion attempts are surfaced in hosts.
- Which identifiers survive round trips across Blockeditor, IR, Flowchart, and
  EMScript.
