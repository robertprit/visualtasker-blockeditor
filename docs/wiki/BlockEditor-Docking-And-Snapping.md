# BlockEditor Docking And Snapping

Status: private/pre-public working documentation.

Docking and snapping connect blocks through typed connection anchors while drag
state remains transient until a drop is committed.

## Concepts

- Previous/next connections build linear statement chains.
- Statement inputs own nested stacks.
- Value inputs accept reporter/output blocks.
- Snap candidates are visual suggestions until pointer release.
- Drag preview layout is separate from persisted workspace state.

## Expected Behavior

- Pointer movement during drag should not emit persistent document changes.
- Dropping on a valid target creates one committed workspace change.
- Dropping without a valid target should preserve or update root position
  depending on the drag operation.
- Deleting a selected block can promote or preserve its next chain where the
  controller explicitly supports that behavior.

## Safety Boundaries

- Snapping must not invent block semantics.
- Type checks should be enforced at connection boundaries.
- Unsupported legacy blocks may be visible but should not become executable
  commands through docking alone.
- Runtime execution remains outside the docking system.

## Open Questions

- Larger physical-device drag fixtures are still needed for end-to-end gesture
  validation.
- Accessibility affordances for anchors and drop targets need a public contract
  before release.
