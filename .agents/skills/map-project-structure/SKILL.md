---
name: map-project-structure
description: Map overall repository architecture, module boundaries, build wiring, and runtime entry points for this Android/Kotlin Multiplatform launcher project. Use when asked to understand the codebase quickly, onboard to the project, produce architecture summaries, locate where changes belong, or explain how app, shared, native C++ code, and external assets fit together.
---

# Map Project Structure

## Quick Start

1. Run `scripts/project_map.sh [repo-root]` to generate a current structure snapshot.
2. Read `references/general-project-information.md` for direct project context.
3. Read `references/rotatingart-architecture.md` for curated architecture notes.
4. Verify findings against `settings.gradle`, `app/build.gradle`, `shared/build.gradle.kts`, and `app/src/main/AndroidManifest.xml`.

## Mandatory Pre-Modification Context

Before proposing edits in any subcomponent:

1. Confirm target module and package boundary with `scripts/project_map.sh`.
2. Load `references/general-project-information.md`.
3. If the target is a specific feature/package, also load:
   - `../analyze-subcomponents/references/subcomponent-implementation-details.md`
   - `../analyze-subcomponents/references/component-analysis-checklist.md`
4. State the chosen edit entry point and expected blast radius.

## Workflow

1. Confirm module graph from `settings.gradle` includes and module build files.
2. Identify runtime entry points from manifest components and application bootstrap files.
3. Map package topology:
   - `app/src/main/java/com/app/ralaunch/core` for runtime/platform infrastructure.
   - `app/src/main/java/com/app/ralaunch/feature` for feature-level product code.
   - `shared/src/commonMain` and `shared/src/androidMain` for KMP shared logic and Android bindings.
4. Separate first-party code from vendored code:
   - Treat large trees under `app/src/main/cpp` (SDL, FNA3D, gl4es, FAudio) as external unless the task explicitly targets native internals.
5. Deliver a concise map tailored to the current request, not a full tree dump.

## Output Contract

- Return a concise architecture map with concrete paths and one-line responsibilities.
- Highlight one recommended edit entry point for the user task.
- State uncertainty explicitly when docs and on-disk structure differ.

## Resources

- Script: `scripts/project_map.sh`
- Reference: `references/general-project-information.md`
- Reference: `references/rotatingart-architecture.md`
