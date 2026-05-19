# Backend Not Surfaced Register

| ID | Severity | Backend capability | Evidence | Web | Mobile | Recommended remediation |
|---|---|---|---|---|---|---|
| BNS-001 | HIGH | Core transaction composition endpoints | Experience BFF core transaction controller/composition | Fixture only | Missing dedicated journey shell | Implement shared core transaction hooks and mobile parity baseline |
| BNS-002 | HIGH | Workflow operations | BFF workflow controller paths | Missing | Missing | Add workflow list/detail operator surfaces |
| BNS-003 | HIGH | Dispatch operations | BFF dispatch controller paths | Missing | Missing | Add dispatch operational route and queue integration |
| BNS-004 | MEDIUM | Nompilo command/handoff analytics pipeline | Command/handoff endpoint stubs accepted responses | Partial | Partial/missing | Connect command/handoff to support/reporting pipelines with audit trail |
