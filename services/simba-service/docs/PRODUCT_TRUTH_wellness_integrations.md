# Simba Wellness — Integration Seams (Product Truth)

Simba is the **wellness / prevention** system-of-record. It owns wellness journeys, assessments,
risk scoring, reminders, timeline, follow-ups, consent, and the social-wellness layer. It **owns none**
of the following, and hands off honestly via the `simba.simba_integration_event` ledger + outbox
events. No handoff fakes success — an unwired owner endpoint is recorded `PENDING`/`UNSUPPORTED`.

| Capability | Owner (never Simba) | Simba seam | Status today |
|---|---|---|---|
| Notification / reminder delivery | **Khuluma** (8200) | outbox `impilo.simba.wellness.reminder.due.v1` + `simba_integration_event(KHULUMA, PENDING)` | Event-based seam live; delivery ack updates ledger (deferred) |
| Learning content | **Fundo** (8260 learning) | plans/tasks carry `fundo_content_ref`; Simba links, never duplicates | Reference-only, live |
| Orders / referrals | **OROS** (8089) | `simba_integration_event(OROS)` | Deferred (no wellness order intent yet) |
| Clinical encounter / diagnosis / referral lifecycle | **PCT** (8088) | `CareLinkageService.route(...)` records the linkage; BFF `WellnessHomeController.routeCare` creates the PCT referral | Live (linkage + BFF orchestration) |
| On-platform guidance (Nompilo) | **Nompilo** (8260 guidance) | UI surfaces `NompiloContextualGuidance`; Simba emits nothing clinical | Live (UI composition) |
| Identity / demographics | **Vito / Varapi / Tuso** | Simba stores CPID / provider id / facility id only | Live (reference-only) |
| Trust / policy / authz | **Tshepo / OPA / Keycloak** | Envoy ext_authz → Tshepo; Simba runs a LOCAL `WellnessAccessGuard` for wellness-shape enforcement + audit, no rego | Live |
| Stock / commodities | **Dura** | none — Simba never touches inventory | N/A (no seam) |
| FHIR / SHR clinical records | **Butano** | none — Simba stores wellness truth, not clinical records | N/A (no seam) |

## Ledger semantics
`simba_integration_event`: `target_owner`, `intent`, `status` (PENDING → DISPATCHED → ACKED / FAILED,
or UNSUPPORTED when the owner endpoint is unwired), `request_payload`, `response_ref`, `error`.
Read-only per-person surface: `GET /internal/v1/wellness/integration-events?person_cpid=`.

## Escalation safety
HIGH / URGENT assessment bands and social SELF_HARM / ABUSE / safeguarding reports **always** build a
`CareLinkageEntity` and route via `CareLinkageService.route(...)` — they are never handled purely
socially or silently. EMERGENCY severity is forced to the EMERGENCY owner, not a routine queue.
