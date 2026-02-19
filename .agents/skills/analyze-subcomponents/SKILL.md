---
name: analyze-subcomponents
description: Deep-dive into a selected subcomponent (feature folder, package, module, or file) to identify responsibilities, dependencies, integration points, and change impact. Use when asked to understand a subsystem, plan refactors, estimate blast radius, investigate regressions, or document ownership boundaries inside this launcher codebase.
---

# Analyze Subcomponents

## Quick Start

1. Run `scripts/component_profile.sh <component-path-or-token> [repo-root]`.
2. Load `references/subcomponent-implementation-details.md` for direct per-subcomponent implementation context.
3. Load `references/component-analysis-checklist.md` for this repo's coupling/change checkpoints.
4. Validate script output by reading the top affected files directly.

## Mandatory Pre-Modification Context

Before editing any subcomponent:

1. Read the matching section in `references/subcomponent-implementation-details.md`.
2. Identify DI, navigation, manifest, storage, process, and runtime coupling using `references/component-analysis-checklist.md`.
3. Run `scripts/component_profile.sh` for the target and at least one adjacent dependency folder.
4. Describe expected blast radius before applying code changes.

## Workflow

1. Resolve the target component to a concrete path.
2. Classify the target:
   - Android feature (`app/.../feature/...`)
   - Android core infrastructure (`app/.../core/...`)
   - Shared KMP common layer (`shared/src/commonMain/...`)
   - Shared Android implementation (`shared/src/androidMain/...`)
   - Native runtime/vendor code (`app/src/main/cpp/...`)
3. Profile internals:
   - File/type inventory.
   - Primary declarations.
   - Internal vs external imports.
4. Profile coupling:
   - Upstream/downstream references.
   - DI registration points.
   - Manifest/service/provider touchpoints when relevant.
5. Report impact:
   - What breaks if changed.
   - What likely needs coordinated updates.

## Output Contract

- Return a concise component brief with concrete paths and ownership hints.
- Include a change-impact checklist tailored to the current request.
- List high-risk interfaces first (runtime bootstrap, storage, navigation, process boundaries, JNI/native bridges).

## Resources

- Script: `scripts/component_profile.sh`
- Reference: `references/subcomponent-implementation-details.md`
- Reference: `references/component-analysis-checklist.md`
