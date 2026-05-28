# Backend Not Surfaced Register

| ID | Severity | Backend capability | Evidence | Web | Mobile | Recommended remediation |
|---|---|---|---|---|---|---|
| BNS-001 | HIGH | Core transaction composition endpoints | Experience BFF core transaction controller/composition | Fixture only | Missing dedicated journey shell | Implement shared core transaction hooks and mobile parity baseline |
| BNS-002 | HIGH | Workflow operations | BFF workflow controller paths | Partial (telemetry + definitions + instances + start/transition commands surfaced in `/operations/workflows`) | Partial (provider `Flow/Ops` reads + start/transition commands) | Add workflow detail pages and broaden mobile operator ergonomics |
| BNS-003 | HIGH | Dispatch operations | BFF dispatch controller paths | Partial (telemetry + dispatch datasets + delivery-create/action and task create/assign/complete surfaced in `/operations/dispatch`) | Partial (provider `Flow/Ops` task and delivery commands) | Add guided dispatch detail pages and offline command queue UX |
| BNS-004 | MEDIUM | Nompilo command/handoff analytics pipeline | Command/handoff endpoint stubs accepted responses | Partial | Partial/missing | Connect command/handoff to support/reporting pipelines with audit trail |
| BNS-005 | HIGH | Registry identity operations | Identity BFF controller paths for VITO/VARAPI | Partial (Registry Hub exposes identity search, patient resolve/register/recovery, provider lookup/create) | Partial | Add mobile registry/admin ergonomics and facility identity only after real BFF contract exists |
| BNS-006 | HIGH | Coverage/claims command operations | Coverage BFF controller paths for eligibility, members, claims, preauth | Partial (Coverage page guided tabs + live command console) | Partial | Add mobile payer/provider parity and reconcile refunds/settlements into role-specific finance journeys |
