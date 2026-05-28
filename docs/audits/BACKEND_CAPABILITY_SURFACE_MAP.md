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
| Coverage/claims | PARTIAL | Coverage page now includes guided tabs plus live eligibility/member/claim/preauth command console | Partial | Mobile depth lower than web finance suite; finance refund/settlement routes remain dedicated web surfaces. |
| Payments (wallet/costing) | PARTIAL | Real+partial | Partial | Some mobile paths intentionally limited. |
| Reporting | PARTIAL | Real+partial | Partial | Mobile access exists but less complete controls. |
| Workflow/dispatch ops | PARTIAL | Workflow/dispatch ops routes now surface telemetry, backend datasets, workflow instance commands, dispatch task commands, and delivery commands | Provider mobile Flow/Ops now surfaces feeds and command controls | Backend and BFF present; web and provider mobile moved from unsurfaced/read-only to actionable partial. |
| Registry administration | PARTIAL | Registry Hub now includes VITO/VARAPI identity search and command operations plus existing admin routes | Limited provider/citizen registry surfaces | Web moved from linked admin pages to actionable identity operations; mobile/admin depth remains uneven. |
