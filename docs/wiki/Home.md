# VisualTasker Blockeditor Wiki

Status: private/pre-public working documentation.

This wiki is an internal staging area for standalone Blockeditor publication
planning. It is not a public product promise, not an API reference, and not a
statement that draft EMScript syntax or future integration points are already
implemented.

## Start Here

- [BlockEditor Overview](BlockEditor-Overview.md)
- [BlockEditor Layout System](BlockEditor-Layout-System.md)
- [BlockEditor Docking And Snapping](BlockEditor-Docking-And-Snapping.md)
- [Flowchart Layout Pipeline](Flowchart-Layout-Pipeline.md)
- [IR Synchronization](IR-Synchronization.md)
- [Roadmap](Roadmap.md)

## Draft EMScript Notes

These pages are planning notes. They describe intended vocabulary and API shape
only where explicitly marked as draft or planned.

- [EMScript Core Types](EMScript-Core-Types.md)
- [EMScript Find API](EMScript-Find-API.md)

## Scope Rules

- Blockeditor and Flowchart are documented first because they define the current
  visual editing and visualization boundaries.
- The Blockeditor edits or preserves a workspace document; it must not imply
  runtime authority by itself.
- Flowchart is treated as a derived visualization pipeline unless a later
  public contract explicitly says otherwise.
- EMScript API pages stay draft/planned and must not claim unfinished syntax is
  implemented.
- Public release steps remain blocked until license, dirty-state, wiki, and
  validation-clone decisions are made.
