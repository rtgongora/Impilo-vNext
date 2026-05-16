# Backend Capability Surface Map

## Capability Classes

- `SURFACED_AND_WIRED_WEB_AND_MOBILE`
- `SURFACED_AND_WIRED_WEB_ONLY`
- `SURFACED_AND_WIRED_MOBILE_ONLY`
- `SURFACED_BUT_MOCKED_WEB`
- `SURFACED_BUT_MOCKED_MOBILE`
- `IMPLEMENTED_NOT_SURFACED`
- `DOCUMENTED_NOT_IMPLEMENTED`
- `PARTIAL`
- `UNKNOWN`

## Mapped Capabilities

| Capability | Class | Web status | Mobile status | Notes |
|---|---|---|---|---|
| Queue / triage / waiting | SURFACED_AND_WIRED_WEB_AND_MOBILE | Real | Real | Verified through route/hook usage and tests. |
| Core transaction composition | IMPLEMENTED_NOT_SURFACED | Fixture pages | Missing dedicated journey shell | BFF/controller exists but doctrine UI not wired. |
| Nompilo command/handoff | PARTIAL | Partial surfaces | Limited parity | Command endpoints exist; frontends incomplete. |
| Telemedicine | PARTIAL | Real pages | Real screens | Endpoint namespace split and partial depth. |
| Coverage/claims | PARTIAL | Real+partial | Partial | Mobile depth lower than web finance suite. |
| Payments (wallet/costing) | PARTIAL | Real+partial | Partial | Some mobile paths intentionally limited. |
| Reporting | PARTIAL | Real+partial | Partial | Mobile access exists but less complete controls. |
| Workflow/dispatch ops | IMPLEMENTED_NOT_SURFACED | Missing | Missing | Backend and BFF present. |
| Registry administration | SURFACED_AND_WIRED_WEB_ONLY | Real | Missing | Intentional admin-heavy web scope; document explicitly. |
