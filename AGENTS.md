# AGENTS.md

## Engineering principle: TDD

Test-Driven Development (TDD) is a mandatory development principle for this repository.

### Rule
For every new behavior or business rule, follow **Red → Green → Refactor**:

1. **Red** — write the smallest test that expresses the expected behavior and verify that it fails for the right reason.
2. **Green** — implement the minimum production code required to make the test pass.
3. **Refactor** — improve the design while keeping the tests green.

### Instructions for AI coding agents

- **Tests come before implementation** for new behavior whenever the change is testable.
- Do not implement a feature first and add tests afterward merely to increase coverage.
- When fixing a bug, add a regression test that reproduces the bug before changing production code when practical.
- Treat recommendation rules, try-on behavior, image/mask processing, persistence behavior, and idempotency as observable behavior that must be tested.
- Prefer fast JVM unit tests for domain and business rules.
- Keep tests deterministic, isolated, readable, and focused on behavior rather than implementation details.
- Do not weaken, delete, skip, or bypass an existing test just to make the build pass.
- Do not change production behavior solely to satisfy a test unless that behavior is part of the intended requirement.
- If a requirement is ambiguous, clarify the expected behavior through tests before implementation.
- Existing tests are part of the contract. Preserve them unless the intended behavior has deliberately changed.
- Before considering a change complete, run the narrowest relevant tests and then the repository's normal verification command.

### AI-specific anti-shortcuts

AI agents must not:

- generate production code first and retrofit superficial tests;
- add tests that merely mirror the implementation;
- mock everything when a small real collaborator would make the test clearer;
- increase coverage by testing trivial getters/setters instead of behavior;
- remove a failing test without first identifying the intended contract; or
- declare a task complete without running relevant verification.

### Android guidance

- Keep business rules in testable domain code whenever possible.
- Prefer unit tests over instrumented tests when Android framework behavior is not required.
- UI tests are appropriate when behavior genuinely depends on Compose/UI integration.
- Do not use coverage percentage as a substitute for meaningful tests.

## Existing environment and verification guidance

Single Gradle module Android app (`:app`) — Kotlin + Jetpack Compose, Hilt, Room, DataStore. There is no backend/service to run; it is a client app. Standard build/lint/test commands live in `README.md` and `build.gradle.kts` (`verifyCi` task); prefer those over duplicating them.

- Full CI parity: `./gradlew verifyCi`.
- Unit tests: `./gradlew :app:testDebugUnitTest`.
- Prefer JVM tests for core recommendation/domain logic.
- The Android emulator may not be available in every development environment; do not treat emulator execution as the only verification path.
- Use `./gradlew verifyCi` for final verification when practical.
