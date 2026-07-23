# EMScript Find API

Status: draft/planned.

This page sketches a possible future Find API for EMScript-facing automation
workflows. It is not implemented syntax and not a runtime contract.

## Draft Goal

Find operations would locate UI, accessibility, document, or dataset targets in
a typed way before a workflow uses them. The intent is to avoid stringly-typed
lookups becoming hidden runtime authority.

## Draft Concepts

- Find target: the thing being searched for.
- Find scope: where the search is allowed to run.
- Selector: stable description of the desired target.
- Result: typed match data plus diagnostics.
- Timeout and retry policy: explicit execution controls.

## Planned Safety Rules

- Find operations should require explicit capability and permission checks.
- Failed matches should produce diagnostics, not guessed commands.
- A find result should be an input to a later accepted workflow step, not an
  editor-only side effect.
- Legacy imported blocks should not become Find API calls without explicit
  converter support.

## Non-Claims

- No parser syntax is defined here.
- No Android service behavior is defined here.
- No Tasker, Accessibility, Flowchart, or EMScript runtime implementation is
  added by this page.
