<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Exception-Escape Chain Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Warning on a `catch (Exception e)` that silently swallows a real
  checked exception reachable, directly or transitively, from a call
  in its `try` block (bounded same-class call chain).
- Catch-type matching uses `PsiType.equalsToText` rather than
  `resolve()?.qualifiedName` -- more robust across differently-indexed
  projects.

[Unreleased]: https://github.com/GapHunterLabs/exception-escape-chain-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/exception-escape-chain-companion/commits/0.1.0
