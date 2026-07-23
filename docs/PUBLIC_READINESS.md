# Public Readiness

Status: not ready to publish yet.

This repository is prepared for standalone Apache-2.0 publication, but the
current checkout still contains uncommitted implementation and documentation
work. Do not make the repository public from this working tree.

## Current Checkout

- HEAD: `4cb7cf7d2c841e56da7ea2b376ecf378494db97c`
- Branch state: detached HEAD in the VisualTasker Studio submodule checkout
- Intended license: Apache-2.0, now represented by `LICENSE`
- NOTICE: no separate `NOTICE` file is currently required; see
  `docs/THIRD_PARTY_LICENSES.md`

## Dirty Tracked Changes Reviewed

The dirty tracked diff is implementation work, not only public-readiness work.
It includes:

- README link to temporary v2.1 wiki documentation.
- Controller changes for undo/redo history, fit-to-canvas, zoom, delete
  selected block, replace document, viewport constraints, and derived output
  handling.
- Compose host/scaffold changes for toolbar actions, grid toggle, trash drop
  target, haptics/sound toggles, theme-driven colors, and Material tooltips.
- Domain changes for explicit root positions and workspace history state.
- Layout changes introducing measured and placed layout tree projections while
  keeping the existing flat index.
- Serialization changes for explicit `rootPositions` with legacy metadata
  migration.
- Registry/theme/rendering changes for an EMScript category and visible
  unsupported legacy blocks.
- Tests for controller persistence behavior, workspace history, layout
  projections, serialization, visual contracts, and unsupported block display.

Public assessment: these changes appear coherent as editor/library work, but
they alter public behavior and serialization shape. They require normal code
review and test evidence before public release. They were not committed here.

## Untracked Files Reviewed

The untracked files are:

- `blockeditor-compose/src/test/kotlin/de/visualtasker/blockeditor/compose/ui/BlockEditorVisualContractTest.kt`
- `blockeditor-domain/src/main/kotlin/de/visualtasker/blockeditor/domain/WorkspaceState.kt`
- `blockeditor-domain/src/test/kotlin/de/visualtasker/blockeditor/domain/WorkspaceStateTest.kt`
- `blockeditor-layout/src/main/kotlin/de/visualtasker/blockeditor/layout/LayoutPasses.kt`
- `docs/wiki/Blockeditor-Mapping.md`
- `docs/wiki/Canonical-Command-Mapping.md`
- `docs/wiki/Command-Categories.md`
- `docs/wiki/EMScript-Commands.md`
- `docs/wiki/EMScript-Overview.md`
- `docs/wiki/Examples.md`
- `docs/wiki/Home.md`
- `docs/wiki/Known-Limitations.md`
- `docs/wiki/Legacy-Blockly-XML-Import.md`
- `docs/wiki/Roadmap.md`

## Wiki Decision

`docs/wiki/` should be included in the standalone public repository if this
Blockeditor repo is published before the main VisualTasker Studio repository and
wiki. The README already points to it as transitional v2.1 documentation, and
the pages clearly document descriptor-only limits for Canonical Command Mapping,
Legacy Blockly XML import, EMScript overview, examples, roadmap, and known
limitations.

The wiki pages should not be excluded silently. If the main repository wiki is
published first, move or mirror these pages there and then update the
Blockeditor README before release.

## Remaining Public Blockers

- Commit approval is still required.
- The dirty implementation changes need code review and test evidence.
- The untracked wiki and support files need an explicit include decision in the
  eventual commit.
- Final dependency/license inventory should be verified against Gradle versions.
- A fresh standalone validation clone should be run after commit, before the
  repository is made public.
