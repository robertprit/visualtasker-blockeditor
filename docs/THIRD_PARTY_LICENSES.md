# Third-Party Licenses

Status: Public-readiness working inventory.

This file records the public-release license posture for the standalone
VisualTasker Blockeditor repository. It is not legal advice.

## Project License

VisualTasker Blockeditor is prepared for publication under the Apache License,
Version 2.0. The license text is in `LICENSE`.

## NOTICE Decision

No standalone `NOTICE` file is required for the current source snapshot.

Reason: the repository does not currently vendor third-party source packages,
media assets, model files, or generated artifacts that carry preserved NOTICE
text. The Gradle wrapper scripts contain their own Apache-2.0 header comments;
those comments remain in place.

Re-check this decision before public release if any vendored assets, copied
source files, generated code, screenshots, models, fonts, icons, or third-party
documentation are added.

## Dependency Inventory

| Component | Use | Expected license posture | Public action |
|---|---|---|---|
| Android Gradle Plugin | Build tooling | Apache-2.0 expected | Verify version and distribution source before release. |
| Gradle Wrapper | Build bootstrap | Apache-2.0 header retained in wrapper scripts | Keep wrapper files unchanged and verify wrapper distribution checksum. |
| Kotlin and Kotlin Compose compiler plugin | Build/compiler | Apache-2.0 expected | Include in final dependency inventory. |
| AndroidX Core, Activity, Lifecycle, Compose UI/Foundation/Material3, Material Icons Extended | Android UI/runtime dependencies | Apache-2.0 expected | Include versions and scopes in final dependency inventory. |
| kotlinx.coroutines and kotlinx.serialization | Kotlin runtime libraries | Apache-2.0 expected | Include versions and scopes in final dependency inventory. |
| JUnit and AndroidX Test | Test-only dependencies | JUnit 4 EPL-1.0 expected; AndroidX Apache-2.0 expected | Document as test-scope dependencies. |

## Remaining License/Public Blockers

- Final owner/contributor approval for publishing this standalone repository
  under Apache-2.0.
- Dirty tracked code and test changes must be reviewed as implementation work
  before they are committed for public release.
- Untracked `docs/wiki/` is intended public documentation, but it still needs a
  deliberate commit decision because it is not part of `HEAD`.
- A fresh standalone validation clone must be run after the chosen dirty and
  untracked changes are committed.
