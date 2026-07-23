# BlockEditor Layout System

Status: private/pre-public working documentation.

The layout system turns a workspace document into renderable block geometry and
hit-test data for the Compose editor.

## Current Concepts

- Workspace roots define the top-level stacks that should be laid out.
- Root positions keep top-level blocks stable across serialization and reload.
- Block definitions provide shape, category, reporter, field, statement-slot,
  and value-input information.
- The flat layout index is the current render and hit-test surface.

## Layout Artifacts

The working tree introduces separate measure/place artifacts as additional
layout views:

- measure artifacts describe block dimensions and collapsed state.
- place artifacts describe placed bounds, subtree bounds, and z-order.
- the existing flat index remains the compatibility surface for drawing,
  hit-testing, anchors, inline reporters, branch sections, and statement slots.

## Invariants

- Layout should not mutate the workspace document.
- Unknown block definitions should still produce visible fallback geometry where
  possible.
- Root position data should be document state, not transient viewport state.
- Viewport, drag offset, and selection are UI state and should not be serialized
  as canonical workspace data.

## Open Questions

- Whether measure/place artifacts become public API or stay internal.
- How much layout metadata hosts need for testing, accessibility, and previews.
- Whether large workspaces need incremental layout instead of full rebuilds.
