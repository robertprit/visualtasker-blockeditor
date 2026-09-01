# EMScript Language Spec

Status: draft architecture contract for VisualTasker Studio WSS.

This document defines the canonical VisualTasker EMScript syntax. The parser may accept legacy or Macrorify-compatible forms, but every editor and generator should converge on the canonical form described here.

## Goals

- One canonical text syntax for Blockeditor, Flowchart, TextEditor, IRGraph, dry-run and runtime.
- A tolerant import layer for existing Macrorify-style and legacy VisualTasker scripts.
- A normalized semantic IR that is independent from textual spelling.
- A command catalog that can map one command to parser signatures, block blueprints, flowchart nodes, runtime capabilities and plugin ownership.

## Language Layers

| Layer | Purpose | Rule |
|---|---|---|
| Accepted syntax | Import and compatibility | Broad; may accept legacy uppercase commands and Macrorify-like forms. |
| Canonical syntax | Save/export/generate | Narrow; exactly one spelling and formatting style. |
| IR semantics | Runtime/editor truth | Text-format independent and stable across editors. |

The Blockeditor and Flowchart must not depend on source spelling. They consume `WorkspaceDocument` and semantic IR projections.

## Canonical Style

Canonical EMScript uses a C-/JavaScript-like surface:

```js
var count = 0;
set count = count + 1;

wait(500);
click("Start");
beep(1000, 200, 80);
vibrate(40, 80, 40);

if (count > 3) {
    log("high");
} else if (count == 3) {
    log("equal");
} else {
    log("low");
}

repeat (10) {
    wait(100);
}

while (count < 10) {
    set count = count + 1;
}
```

Canonical formatting rules:

- Commands are function calls: `wait(500)`, not `WAIT 500`.
- Command names are lowercase unless they name a type or constructor.
- Keywords are lowercase: `if`, `else if`, `else`, `while`, `repeat`, `var`, `set`.
- Statement semicolons are emitted by generators for simple statements.
- Semicolons are optional for parsing, but canonical export includes them.
- Blocks use braces.
- Indentation is four spaces.
- Strings use double quotes.
- Booleans are `true` and `false`.
- Null is `null`.
- `else if` is canonical; `elseif` is accepted only as compatibility syntax.

## Variables

Canonical declaration:

```js
var name;
var count = 3;
var enabled = true;
```

Canonical mutation:

```js
set count = count + 1;
```

Compatibility parser input may accept the current simplified forms:

```txt
LET count = 0
SET count = count + 1
```

The generator should emit `var` for first declaration and `set` for mutation once declaration tracking is available. Until then, existing `LET`/`SET` import should normalize to the same IR assignment nodes.

## Expressions

Minimum canonical expression support:

- number, string, boolean and null literals
- variable references
- arithmetic: `+`, `-`, `*`, `/`, `%`
- comparison: `==`, `!=`, `<`, `<=`, `>`, `>=`
- boolean logic: `&&`, `||`, `!`
- grouped expressions: `(a + b) * c`

Planned expression support:

- arrays: `[1, "two", true]`
- objects: `{ name: "test", enabled: true }`
- index access: `values[0]`
- member access: `region.findText("OK")`
- method calls and chains: `Touch.single().down(Point(10, 20)).up().dispatch()`
- ternary: `condition ? a : b`
- lambdas: `value => value * 2`

## Control Flow

Canonical condition:

```js
if (condition) {
    log("then");
} else if (otherCondition) {
    log("else-if");
} else {
    log("else");
}
```

Compatibility parser input may accept:

```txt
IF condition
    log("then")
ELSEIF otherCondition
    log("else-if")
ELSE
    log("else")
END IF
```

Canonical loops:

```js
repeat (10) {
    wait(100);
}

while (condition) {
    wait(100);
}
```

Planned loops:

```js
for (var i = 0; i < 10; i = i + 1) {
    log(i);
}

for (var item : values) {
    log(item);
}

do {
    wait(100);
} while (condition);
```

## Functions

Planned canonical function syntax:

```js
fun add(a, b) {
    return a + b;
}

var operation = add;
var doubleValue = value => value * 2;
```

Functions are planned as first-class values, but runtime execution should remain blocked until scope handling, return propagation and source mapping are complete.

## Types And Constructors

Type and constructor names use PascalCase:

```js
var p = Point(100, 200);
var region = Region(0, 0, 300, 200);
```

Static members and methods use the documented type name:

```js
var left = Point.LEFT;
var scaled = Point.scale(0.5, 0.5, Point.LEFT | Point.TOP);
```

Method chains are allowed for object-like APIs:

```js
Touch.single()
    .down(Point(100, 200))
    .up()
    .dispatch();
```

## Commands

Commands are catalog entries, not hard-coded parser branches in the long term.

Each command entry should define:

- canonical name
- accepted aliases
- namespace/category
- argument signatures
- default values
- return type
- side-effect type
- runtime capability
- plugin owner
- block blueprint
- flowchart node kind
- dry-run behavior

Canonical examples:

```js
wait(500);
click("Start");
click(Point(100, 200));
swipe([SwipePoint(Point(100, 400)), SwipePoint(Point(100, 100))]);
beep(1000, 200, 80);
vibrate(40, 80, 40);
log("ready");
```

Compatibility parser input may accept currently used forms such as:

```txt
WAIT 500
CLICK "Start"
BEEP 1000, 200, 80
```

Export must not emit compatibility forms.

## Capability Model

Every command is assigned to one or more runtime capabilities:

| Capability | Examples |
|---|---|
| `CORE` | variables, expressions, arrays, strings, math |
| `TIMING` | `wait` |
| `FEEDBACK` | `beep`, `vibrate` |
| `A11Y` | text click, element tree, accessibility gestures |
| `SCREEN_CAPTURE` | screenshots, image search, OCR input |
| `VISION` | OCR, OCV, YOLO |
| `TASKER` | Tasker events, variables, actions, profiles |
| `SHIZUKU` | shell/system calls through Shizuku |
| `TERMUX` | Termux command execution |
| `CUSTOM_TAB` | Custom Chrome Tab control and navigation events |
| `SCRCPY` | scrcpy bridge |
| `CHARTS` | chart generation and data views |

Dry-run may simulate unavailable capabilities. Live-run must fail with diagnostics when required capabilities are missing.

## Source Mapping

The canonical source-mapping target is:

- script id
- line and column
- block id
- field id
- slot id
- branch id
- IR node id
- IR edge id

Current implementation already supports block/slot/branch-oriented IR mapping. Parser-level statement positions should be added before full text-editor roundtrip diagnostics are considered complete.

## Blockeditor Mapping

The Blockeditor should map commands through the command catalog:

- statement commands become statement blocks
- value-returning commands become reporter blocks
- object constructors become reporter blocks
- chained builders may become mutator/special blocks
- unsupported known commands become visible degraded blocks with diagnostics
- unknown commands become preserved opaque blocks where possible

Not every language construct needs a separate first-class block at first. Complex expressions may initially be represented by a compact expression reporter and later expanded into structured reporter blocks.

## Flowchart Mapping

Flowchart is derived from semantic IR:

- one executable command or control construct becomes an IR node
- value/reporters may appear as data-flow nodes where useful
- branches are explicit IR branch records
- collapsed regions and dummy/group nodes are editor facets, not alternate runtime semantics
- runtime trace maps to IR node/edge ids first and to Flowchart ids second

## Compatibility Decisions

Accepted but non-canonical:

- uppercase commands: `WAIT`, `CLICK`, `BEEP`
- block-style control flow: `IF ... END IF`
- `ELSEIF`
- `LOOP 10 ... END LOOP`
- missing semicolons
- legacy `LET`

Rejected for canonical export:

- mixed uppercase/lowercase command output
- command statements without parentheses
- implicit ambiguous argument grouping
- runtime-only spelling differences between editors

## Migration Phases

1. Freeze this language spec and command-catalog schema.
2. Move current hard-coded commands into a catalog-backed registry.
3. Make the parser accept both legacy and canonical control-flow syntax.
4. Make the EMScript generator emit only canonical syntax.
5. Add statement/expression source positions to parser IR.
6. Expand dry-run through catalog handlers.
7. Add live-runtime capability gates per command.
8. Add command groups from Macrorify and VisualTasker plugins category by category.

## Initial Command Scope

The first catalog migration should cover only the already working foundation:

- `var`
- `set`
- `wait`
- `click`
- `beep`
- `vibrate`
- `log`
- `if`
- `else if`
- `else`
- `repeat`
- `while`
- arithmetic operators
- comparison operators
- boolean reporters
- variable reporters

All Macrorify automation, image, OCR, Tasker, Shizuku, Termux, CustomTab, scrcpy and chart APIs remain planned until their command entries, block blueprints and runtime capabilities are implemented.
