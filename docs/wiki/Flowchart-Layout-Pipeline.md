# Flowchart Layout Pipeline

Status: private/pre-public working documentation.

This page documents the intended relationship between Blockeditor workspace
state and Flowchart visualization. It does not claim that every conversion path
is implemented in this repository.

## Role

Flowchart is the read-oriented visualization pipeline for workflow structure. It
should help users inspect order, branching, and relationships without becoming
an alternate hidden source of truth.

## Pipeline Shape

Planned or external pipeline boundaries:

- accepted workflow or IR input
- graph extraction
- node and edge classification
- layout pass
- routing pass
- render pass
- diagnostics for unsupported or incomplete semantics

## Blockeditor Relationship

- Blockeditor workspace documents can preserve visual editing state.
- Flowchart should consume a semantic graph or explicit bridge output, not raw
  transient editor UI state.
- Legacy or tolerant Blockeditor imports should not silently become strict
  Flowchart graphs without validation.

## Public-Readiness Notes

- Public docs must distinguish visual layout from executable semantics.
- If a bridge is added later, it needs tests that prove unsupported blocks fail
  visibly or remain non-semantic.
- Flowchart routing and layout decisions belong in the Flowchart package or a
  clear adapter boundary.
