# Nompilo Reality Check

## Capability Classification

| Capability | Web | Mobile | Class | Notes |
|---|---|---|---|---|
| Command entry UI | Partial | Limited/missing | PARTIAL | Web has command-like surfaces; mobile parity incomplete. |
| Core transaction contextual guidance | Fixture-backed in doctrine pages | Missing dedicated equivalent | FIXTURE / MISSING | Needs live BFF composition wiring. |
| Handoff/feedback actions | Endpoint exists | Endpoint parity unclear | PARTIAL | BFF currently accepts with synthetic response semantics in some paths. |
| Search-assisted navigation | Present in shell palette and ask surfaces | Partial | PARTIAL | Capability breadth differs by platform. |
| Voice/dictation assist | Present in specific web/mobile controls | Partial | PARTIAL | Not a full command/assistant parity layer. |

## Risks

- Capability overstatement when fixture-backed guidance appears operational.
- Web/mobile feature asymmetry without explicit user-facing scope statement.

## Immediate Remediations

1. Keep fixture/prototype labeling where live orchestration is not wired.
2. Define and publish a Nompilo capability matrix (web + mobile + endpoint + test).
3. Add parity tests for supported commands and graceful “not available” responses.
