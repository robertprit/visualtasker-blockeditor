# EMScript Core Types

Status: draft/planned.

This page is a planning note for future EMScript vocabulary. It does not claim
that the listed syntax or type contracts are implemented in the current
Blockeditor repository.

## Draft Type Families

- Event: workflow entry points.
- Action: executable steps after acceptance by the workflow layer.
- Value: typed values passed into commands.
- Condition: boolean-producing expressions for branching.
- Selector: references to UI, accessibility, or data targets.
- Resource: files, screenshots, models, or external handles after validation.

## Draft Type Properties

Planned type metadata may include:

- stable type identifier
- display label
- serializer name
- nullability or optionality
- validation rules
- permission or capability requirements

## Non-Claims

- This page does not define a parser contract.
- This page does not add runtime handlers.
- This page does not grant permission for UI automation commands.
- This page does not make tolerant legacy imports executable.

## Public-Readiness Rule

Any public EMScript type page must clearly separate implemented syntax from
planned syntax before the repository is made public.
