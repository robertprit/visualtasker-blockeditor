<img width="1100" height="480" alt="Screenshot_20260724_153834_VisualTasker Studio" src="https://github.com/user-attachments/assets/992b482c-9828-4696-a580-8689eacda2de" />

# VisualTasker Logic Editor

> A native Kotlin/Jetpack Compose block editor built specifically for VisualTasker Studio.

## License

VisualTasker Blockeditor is prepared for standalone publication under the
Apache License, Version 2.0. See [LICENSE](LICENSE).

Public-readiness notes live in [docs/PUBLIC_READINESS.md](docs/PUBLIC_READINESS.md).
Third-party and NOTICE status lives in
[docs/THIRD_PARTY_LICENSES.md](docs/THIRD_PARTY_LICENSES.md).

---

# Why another block editor?

VisualTasker originally integrated Google's Blockly as a visual programming interface.

While Blockly is a mature and powerful project, it was designed as a **general-purpose visual programming framework**.

VisualTasker has fundamentally different goals.

The project revolves around a single architectural principle:

> **The Workflow is the truth. Everything else is a projection.**

Blockly does not naturally fit this philosophy.

Instead, Blockly introduces its own XML representation which becomes an additional source of truth.

The conversion chain looked like this:

```text
Blockly XML
        ↓
Generator
        ↓
EMScript
        ↓
Flowchart
        ↓
Runtime
```

Every translation layer introduces complexity, synchronization problems and maintenance costs.

---

# VisualTasker Architecture

The native Logic Editor follows a different architecture.

```text
Workflow Graph (Canonical Truth)
        │
        ├──────── EMScript Projection
        ├──────── Logic Projection
        ├──────── Flowchart Projection
        ├──────── Timeline Projection
        ├──────── Runtime Projection
        └──────── AI Projection
```

The editor is no longer responsible for generating code.

It simply edits the Workflow Graph.

Every other representation is generated from the same underlying model.

---

# Design Goals

The Logic Editor is designed specifically for VisualTasker.

It is **not** intended to become a Blockly clone.

Instead it focuses on:

* Native Android implementation
* Jetpack Compose rendering
* Material 3 Expressive UI
* Workflow-first architecture
* Tight EMScript integration
* Plugin extensibility
* Mobile usability
* Runtime synchronization
* AI integration
* Local-first design

---

# Why replace Blockly?

## Single Source of Truth

Blockly stores workflows as XML.

VisualTasker stores workflows as a Workflow Graph.

This removes unnecessary conversions and synchronization problems.

---

## Native Kotlin

The editor is written entirely in Kotlin.

Advantages include:

* no embedded WebView
* no JavaScript runtime
* native Compose rendering
* shared architecture with the rest of the application
* better debugging
* easier maintenance

---

## Projection-based Architecture

The editor is only one possible projection.

Other projections include:

* EMScript
* Flowchart
* Timeline
* Runtime
* AI Views
* Debug Views

All are synchronized through the Workflow Graph.

---

## Material 3 Integration

Instead of SVG blocks, VisualTasker uses Material 3 Expressive Shapes.

Shapes become semantic elements rather than decorative graphics.

Examples:

* Action
* Condition
* Loop
* Wait
* Vision
* Network
* Audio
* AI

---

## Plugin Architecture

Plugins can register their own:

* Capabilities
* Block Definitions
* Shapes
* Parameter Editors
* Validation Rules
* Providers

The editor automatically renders plugin blocks.

No editor modification is required.

---

## AI Integration

The editor was designed with AI collaboration in mind.

AI can:

* generate workflows
* repair workflows
* explain workflows
* refactor workflows
* suggest optimizations

The AI never edits visual blocks directly.

It edits the Workflow Graph.

The editor simply reflects those changes.

---

## Better Mobile Experience

Blockly was originally optimized for desktop browsers.

VisualTasker targets Android devices first.

The editor is therefore designed for:

* touch interaction
* drag & drop
* snap targets
* inspector panels
* adaptive layouts
* gesture navigation

---

# Core Principles

## Workflow First

The Workflow Graph is the canonical representation.

---

## Projection Driven

Every representation is generated from the same workflow.

---

## Native First

No JavaScript.

No WebView.

No XML dependency.

---

## Capability Driven

Blocks represent Capabilities.

Not programming language syntax.

---

## Plugin First

Everything should be extensible without modifying the core editor.

---

## Local First

The editor works completely offline.

Online AI services are optional and require explicit user consent.

---

# Planned Features

* Native Compose canvas
* Drag & Drop
* Snap Engine
* Type-safe connections
* Workflow Commands
* Undo / Redo
* Material 3 Shapes
* Live Flowchart synchronization
* Live EMScript synchronization
* Runtime inspection
* AI-assisted editing
* Plugin-defined blocks
* Custom parameter editors
* Validation engine
* Diff view
* Timeline synchronization
* Multi-selection
* Search
* Mini Map
* Performance optimized rendering

---

# Architecture

```text
User
    │
    ▼
Logic Projection
    │
    ▼
Workflow Commands
    │
    ▼
Workflow Reducer
    │
    ▼
Workflow Graph
    │
    ├──────── EMScript Projection
    ├──────── Flowchart Projection
    ├──────── Timeline Projection
    ├──────── Runtime Projection
    └──────── AI Projection
```

---

# Philosophy

VisualTasker does not replace Blockly because Blockly is a bad project.

VisualTasker replaces Blockly because it has different architectural requirements.

The goal is not feature parity.

The goal is architectural consistency.

Instead of adapting VisionTasker to Blockly,

the editor adapts itself to VisionTasker's architecture.

---

# Vision

The Logic Editor is intended to become the canonical visual workflow editor of VisualTasker Studio.

It is designed around a single architectural principle:

> **The Workflow is the truth.
> Every editor, diagram, script and AI interaction is merely another projection of that truth.**
