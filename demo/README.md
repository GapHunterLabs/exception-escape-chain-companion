# Demo data for screenshots

`PaymentService.java` — `chargeUnsafe` flagged; `chargeSafe` not
flagged (logs before swallowing).

## How to get the screenshot

1. `./gradlew runIde` from `exception-escape-chain-companion`, open
   this `demo/` folder as the project.
2. Full Screen, open `PaymentService.java` — a warning should appear
   on `chargeUnsafe`'s `catch (Exception e)` but not on `chargeSafe`'s.
3. Screenshot with both methods visible, save into
   `exception-escape-chain-companion/docs/screenshots/`. Close the
   sandbox.
