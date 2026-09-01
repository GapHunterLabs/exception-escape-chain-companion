# Exception-Escape Chain Companion

Warning on a `catch (Exception e)` (the broad type, not a narrower
specific exception) that silently swallows -- no rethrow, no logging
-- a specific CHECKED exception that a call inside its `try` block can
actually throw, directly or transitively through a bounded same-class
call chain.

## Why it exists

A real business exception (e.g. `PaymentDeclinedException`) caught
this way leaves the system in an inconsistent state with no trace to
diagnose the real incident. "Exception Catcher" (Marketplace) solves
the opposite problem (where is X caught); no plugin found that
computes the real escape set through a call chain.

## Why built this way

- Merges the throws-clauses of a bounded same-class call chain (via
  `EscapeChainResolver`, the same memoized/cycle-safe shape as
  `deadlock-lock-order-companion`'s `TransitiveLockResolver`), not
  just the immediately-called method's own `throws`.
- Matches the catch type by **text** (`PsiType.equalsToText`), not
  `resolve()?.qualifiedName` -- `resolve()` can return `null` for
  `java.lang.Exception` in an incompletely-indexed project (confirmed
  the hard way while building this plugin's own test suite), and its
  `canonicalText` can stay in the short unqualified form. Accepting
  both `"Exception"` and `"java.lang.Exception"` handles a fully-indexed
  real project and a lighter one the same way.

## v0.1 scope — stated honestly, not exhaustively

Chain depth is bounded to a fixed constant (never unlimited); only
follows calls to methods of the SAME class -- a call to another class
(a further narrowing from the original design, which also considered
same-module calls) or an external library is treated as unknown and
cuts the chain there, never inferred.

## Usage

Open any Java file. A `catch (Exception e)` that silently swallows a
real checked exception reachable from a call in its `try` block shows
a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
