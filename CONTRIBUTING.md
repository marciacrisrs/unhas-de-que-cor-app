# Contributing

## TDD is a project principle

This repository follows Test-Driven Development as the default development workflow.

For new behavior, use **Red → Green → Refactor**:

1. **Red:** write a failing test describing the expected behavior.
2. **Green:** implement the smallest change that makes the test pass.
3. **Refactor:** improve the implementation without changing the behavior.

### Pull requests

Every PR that changes behavior should include the corresponding automated tests. Bug fixes should include a regression test whenever practical.

Do not:

- add superficial tests after implementation just to raise coverage;
- weaken or remove tests to make CI pass;
- test implementation details when behavior can be tested directly;
- use coverage as a substitute for meaningful test cases.

### For AI agents

AI coding agents must read `AGENTS.md` before modifying the repository. The TDD rules in that file are mandatory, not suggestions.

### Verification

Run the narrowest relevant tests during development and the documented CI verification before considering the change complete.
