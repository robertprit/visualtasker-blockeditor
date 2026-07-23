# BlockEditor Overview

Status: private/pre-public working documentation.

The VisualTasker Blockeditor is the native visual editor for workflow-shaped
logic. It is designed for Android and Compose, with a workspace document as the
editor-owned state format.

## Role

- Present workflow steps as editable blocks.
- Preserve block identity, fields, connections, statement slots, value inputs,
  and root positions.
- Support mobile-first gestures, docking, snapping, selection, palette actions,
  and visual feedback.
- Keep unsupported or legacy blocks visible instead of silently reinterpreting
  them.

## Boundaries

- The Blockeditor is an editing projection, not the runtime.
- A saved workspace document is not automatically accepted executable workflow
  authority.
- Legacy Blockly XML import, where present, is an import/preservation path and
  not a new semantic source of truth.
- Flowchart and EMScript outputs are derived or planned projections depending
  on the milestone.

## Current Public-Readiness Position

The repository is being prepared for standalone Apache-2.0 publication. The
current working tree still contains uncommitted implementation and documentation
changes, so this wiki remains pre-public until the commit split and validation
clone are completed.
