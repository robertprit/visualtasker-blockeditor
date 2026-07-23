# Roadmap

Status: private/pre-public working documentation.

This roadmap is a planning document for commit and publication sequencing. It
does not promise that draft EMScript APIs or future syntax are implemented.

## Phase 1: Public-Readiness Foundation

- Add standalone Apache-2.0 license.
- Document NOTICE and dependency posture.
- Keep repository private until commit split and validation clone are complete.
- Keep README links limited to files that are committed in the same release
  slice.

## Phase 2: Blockeditor Document And Layout

- Commit root position state and serialization migration.
- Commit measure/place layout artifacts while preserving the flat render index.
- Keep viewport, drag, and selection out of serialized workspace data.

## Phase 3: Blockeditor Interaction

- Commit undo/redo history support.
- Commit fit-to-canvas, zoom, delete-selected, drag runtime state, and callback
  behavior.
- Validate with controller and interaction tests.

## Phase 4: Blockeditor UI

- Commit toolbar, grid, trash drop target, opt-in sound/haptics, visual tokens,
  and unsupported-block rendering.
- Validate with Compose unit tests and visual contract tests.

## Phase 5: Flowchart Boundary

- Document Flowchart as a derived visualization pipeline.
- Keep strict semantic input separate from tolerant Blockeditor import state.
- Add adapter documentation only when the owning package and tests exist.

## Phase 6: EMScript Drafts

- Keep EMScript core types and Find API pages marked draft/planned.
- Do not publish unfinished syntax as implemented.
- Add parser/runtime documentation only after implementation and tests land.

## Phase 7: Public Validation

- Commit wiki pages deliberately or keep them untracked until ready.
- Run a fresh standalone validation clone.
- Only then decide whether to make the repository public.
