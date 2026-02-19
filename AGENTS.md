# Repository Guidelines

## Rules
- Reuse existing code paths, components, and classes before creating new ones.
- Do not introduce parallel implementations for behavior already present in `app` or `shared`.
- Create a new class only when no suitable extension point exists; keep it small and feature-scoped.
- Store configuration in JSON only. For new configuration data, do not introduce XML, YAML, or `.properties` formats.
- Keep configuration schemas backward-compatible when possible and validate migration impacts in PR notes.
- Respect module boundaries: place reusable/domain logic in `shared`, and Android/platform/runtime integration in `app`.
- Use existing DI patterns (Koin modules) instead of ad-hoc singletons or hidden global state.
- Prefer extending existing feature packages over creating new top-level feature namespaces.
- Treat `app/src/main/cpp` vendored trees (for example `SDL`, `FNA3D`, `FAudio`, `gl4es`) as external code; avoid edits unless dependency work is intentional.
- Keep changes scoped to the task; avoid drive-by refactors in unrelated files.
- Preserve backward compatibility for persisted data and public contracts; document any required migration in the PR.

## Coding Style & Naming Conventions
- Follow Kotlin official style with 4-space indentation.
- Naming: packages `lowercase`, classes/objects `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`.
- Keep boundaries clean: platform/runtime concerns in `app/core`, user-facing features in `app/feature`, reusable logic in `shared`.
- No repo-wide ktlint/detekt is configured; run IDE reformat before opening PRs.

## Commit & Pull Request Guidelines
- Match existing history style: `feat(scope): ...`, `fix: ...`, `refactor: ...`, `chore: ...`.
- Keep commits focused and self-contained.
- PRs should include: what changed, why, how it was tested (commands), linked issue (if applicable), and screenshots/video for UI updates.
- For bug fixes, include repro details: device model, Android version, and relevant logs.
