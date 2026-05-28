# Frontend Parity Backlog

## Priority model

- **P0:** doctrine-breaking or trust/safety-breaking gaps
- **P1:** parity blockers for high-value journeys
- **P2:** depth and consistency expansion
- **P3:** optimization and polish

## Active backlog

| Priority | Backlog item | Journey | Plane(s) | Current status | Target |
|---|---|---|---|---|---|
| P0 | Complete doctrine journey wiring from fixture to live where backend exists | Person/Provider/Platform | Experience, Trust, Clinical, Enterprise | Partial | Real orchestration paths with explicit fail-close states |
| P0 | Finish trust header parity across web and mobile context fields | Cross-cutting | Trust, Experience | Partial | Uniform tenant/actor/context/governance header contract |
| P0 | Enforce maturity badge and no-fake policy on all non-live surfaces | Cross-cutting | Experience, Governance | Partial | 100% explicit `live/partial/fixture/not_wired` labeling |
| P1 | Close mobile reachability gaps for implemented screens | Person/Provider | Experience, Registry, Clinical | In Progress (provider discovery + provider tools wired; mobile encounter triage now renders the live triage component and mobile vitals/triage payloads align to typed BFF contracts) | Reachable navigation or explicit deprecation |
| P1 | Expand core transaction state machine UI on mobile and web parity routes | Person/Provider/Platform | Enterprise, Experience, Trust | Partial | Visible state transitions, obligations, reconciliation |
| P1 | Surface workflow/activity/audit/event timelines on major ops screens | Provider/Platform | Data & Intelligence, Experience | In Progress (shared telemetry panel powers platform + operations + provider; workflow definitions/instances and dispatch backend datasets now surfaced) | Actionable timeline and alert integration |
| P1 | Add provider/platform operator quick actions from telemetry rows | Provider/Platform | Experience, Data & Intelligence | In Progress (web ops + provider mobile Flow/Ops now wire core-transaction Nompilo actions, workflow start/transition, dispatch task create/assign/complete, delivery-create, and delivery-action commands; citizen parity not applicable except journey-specific tracking) | Trust-safe action commands with explicit failure handling |
| P1 | Surface coverage/claims/payer commands in operational UI | Person/Provider/Platform | Enterprise, Experience, Trust | In Progress (Coverage page now exposes guided eligibility, member enrollment, claim, preauth, appeal submit/review/decision; payer-ops composes claims/remittance with intent-linked attempts/receipts/settlement/refund state; citizen/provider mobile expose payer workspaces with reconciliation and durable provisional queues) | End-to-end coverage actionability with guided and advanced operator paths |
| P1 | Resolve remaining contract drift from local frontend DTO variants | Cross-cutting | Integration, Experience, Governance | Partial | Canonical contracts only |
| P2 | Nompilo command parity between web and mobile | Cross-cutting | Experience, Data & Intelligence | Partial | Role-aware command/action continuity |
| P2 | Mobile/web parity for registry/admin capabilities | Provider/Platform | Registry, Experience | Closed for current registry wave (legacy product/terminology/identity aliases removed, unavailable federation/key routes labelled, Mvumo typed admin routes added, registry intake list/cancel added; web Registry Hub identity commands are guided; provider mobile Admin & Registry surfaces identity, facility lifecycle, locality review, intake/import, product registry, ZIBO terminology, and trust/consent reads; citizen mobile ID Recovery has live identity search/resolve/recovery) | Future work is deeper per-family UX, not missing first-class access |
| P2 | Deepen offline/provisional UX for conflict and reconciliation | Person/Provider | Integration & Edge, Trust, Experience | Partial (coverage command queues now show retry history and reconcile through shared mobile offline sync; remaining domains still need parity) | Clear queue/conflict/retry flows across routes |
| P3 | Improve environment/config consistency and runbook clarity | Cross-cutting | Governance, Runtime | Partial | Consistent app defaults and docs |
| P3 | Expand test coverage on newly wired parity surfaces | Cross-cutting | Runtime validation | Partial (coverage/payer web tests, mobile command queue service tests, registry identity/operations service tests, provider AdminRegistry interaction tests, citizen ID Recovery interaction tests, mobile typechecks, web typecheck/build now pass) | Stable parity regression coverage |

## Backlog governance rules

- No item is closed without journey + plane + trust + contract evidence.
- No item is closed without a test/runtime validation update.
- Any intentionally deferred item must include explicit reason and risk owner.

## Scaling through 150+ services

- Use `docs/registry/services-registry.yaml` as the service inventory baseline; current registry snapshot contains 98 explicit service records, many marked `frontend_wiring_status: unknown-or-partial`.
- Execute in domain waves (Trust/Registry/Clinical/Data/Integration/Experience/Enterprise) rather than isolated screens.
- For each wave, enforce this sequence: **surface read models** -> **wire contract-backed commands** -> **close mobile/offline parity**.
- Track throughput by converting `IMPLEMENTED_NOT_SURFACED` and `unknown-or-partial` entries to `PARTIAL` then `SURFACED_AND_WIRED_*` with evidence links.
