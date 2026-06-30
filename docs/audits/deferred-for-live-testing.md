# Deferred for Live Testing & Dedicated Waves

> **Purpose.** A single authoritative list of everything from the gap-remediation program that is
> **built and unit-/migration-proven in the sandbox but cannot be *verified done* without a live
> deployment**, plus the items that are deliberately scheduled as **dedicated later waves**. This
> consolidates the "remaining / at rollout / needs-env / cross-service wiring" notes that were
> previously scattered across [`product-truth-full-gap-register.md`](product-truth-full-gap-register.md)
> and [`gap-remediation-session-log.md`](gap-remediation-session-log.md).
>
> **Why this exists.** Most of the program's logic, migrations, policy rules, and decision cores were
> proven here (mvn `-o test`, CLI-Postgres Flyway, `node --test`, web `tsc`/`vitest`). But three classes
> of verification are **impossible inside the sandbox** and must happen against a real environment:
> (1) soaking adapters against **live external counterparties**, (2) **end-to-end persona journeys**
> across running services with real policy enforcement, and (3) **SHADOW→ENFORCE** policy cutover.
> Nothing below is "dropped" — each has a clear definition of done that a live environment unlocks.
>
> Last updated: 2026-06-30.

---

## Category 1 — Needs live **external counterparties** (cannot soak without real external systems)

| ID | What is built (sandbox-proven) | What needs a live counterparty to verify | Done when |
|----|-------------------------------|------------------------------------------|-----------|
| **G-OR-02 / G-OR-03** | OROS interop mappers + listeners (FHIR ServiceRequest/DiagnosticReport/ImagingStudy/Observation, HL7 ORM/ORU, DICOM MWL, LIMS) + unit tests; adapters flag-OFF | A **soak against live FHIR/HL7/DICOM/LIMS endpoints** — round-trip real orders/results/studies. No external systems exist in-sandbox to exchange with. Also reconcile `IntegrationStatusService` wording ("seams NOT implemented") vs the present mapper/listener classes | A rollout integration window exercises each adapter against the counterparty and the status doc reflects built-but-soaked |
| **G-OR-03 (MADI)** | MADI blood-bank event consumer + tables | **MADI added to a compose/live profile** for a blood-bank order→issue e2e | A compose profile runs MADI and the blood-bank journey passes end to end |
| **Khuluma W6 external comms** (`G-KH-03`) | `DeliveryService` + `khuluma_channel_adapter`/`delivery_attempt` (V005); IN_APP delivers; SMS/WhatsApp/EMAIL/USSD are **honest `NOT_CONFIGURED` seams** (never a fake send) | Wiring a **real provider** (e.g. Twilio/WhatsApp Business) behind each external adapter and flipping its status to `CONFIGURED`, then confirming real delivery + receipts | Each external channel has a live provider, dispatch records `SENT`→`DELIVERED` against real sends |
| **Patient-Safety PoC** (`G-PS-03`) | Pharmacovigilance service + surveillance signal consumer | integration-hub **live adapters OFF**: VigiFlow (MANUAL), E2B (`E2B_R3_ALIGNED` disabled), VigiMobile (external link-out); Envoy `/v1/public/patient-safety/*` upstream route | Post-PoC: the regulatory egress adapters are enabled + soaked against the real pharmacovigilance endpoints |
| **Fundo external AI** (`G-FU-04`) | Learning service; AI provider **disabled-by-default** (no external egress) | A live LLM/AI provider + governance review before enabling external egress | The AI provider is enabled against a real model endpoint under egress governance |

---

## Category 2 — Needs a running **multi-service deployment** (live e2e + cross-service wiring + ENFORCE)

| Item | What is built (sandbox-proven) | What needs a live deployment | Done when |
|------|-------------------------------|------------------------------|-----------|
| **SYS-3 persona journeys** (`G-OR-05`, `G-CZO-16`, `G-CT-04`, `G-PX-07`) | Per-service unit/MockMvc + the patient-lane persona **progression** test; capability matrix; probeEvidence | **Live click-through** of each DoD journey (identity→sort→triage→queue→encounter→outcome→settle; CZO LOA DENY→ALLOW; OROS order→result→ack) with **real cross-service state + ext_authz enforcement**. The BFF→PCT WireMock e2e needs the experience-bff IT harness (**Redis + Postgres**), which self-skips in-sandbox | A staging env runs the persona journey ITs green against real services; they become the probeEvidence feed |
| **Nompilo handoff cross-service wiring** (`G-KH-06`) | guidance-service handoff **lifecycle** (V003) + `NompiloHandoffService` + outbox events; all unit-proven | Replace the BFF `requestNompiloHandoff` **stub** with a call to guidance; add the **mobile** Nompilo handoff client; verify the end-to-end create→queue→accept→close across running BFF+guidance+mobile | The BFF persists real handoffs via guidance and the mobile client drives the lifecycle in a running stack |
| **Safe-disclosure dispatch wiring** (`G-KH-05`) | `SafeDisclosureService` decision core (fail-safe-redact) — 8/8 incl. no-leak proof | Wire **mvumo `DelegationService.resolve` + a consent check** into the khuluma/notification **send path**, and confirm redaction end-to-end against running mvumo+consent | A real send resolves the recipient relationship + consent live and redacts/reveals correctly |
| **Feedback→Rito routing wiring** (`G-KH-06` / `G-RT-03`) | `FeedbackRoutingService` decision + exact Rito quality-signal body — 5/5 | The **HTTP POST to Rito** `/quality-signals` at the routing call site, verified against a running Rito | A quality/safety feedback item creates a real Rito signal across services |
| **Fundo training-gate advisory consumer** (`G-FU-02`) | Graduated training-gate (ADVISORY/SOFT/HARD) + `decision` — 8/8; PO decision resolved | Build + verify the **vashandi→fundo** consumer that calls the gate at check-in/workspace-entry and acts on `decision`, against running vashandi+fundo (+ the workspace/role→course mapping) | vashandi consults the gate live and warns/conditions/blocks per the decision |
| **OPA-as-PDP ENFORCE** (`SYS-1`, Phase 7) | `shadowCompareOpa` logs divergence; Java authoritative; minimal `infra/opa/authz/authz.rego` (`opa test` 7/7) | The full Phase-7 migration: **mount** the 7 doctrine rego modules (currently don't compile under 0.68), expand the OPA input schema, the bundle pipeline, a **Java↔OPA equivalence harness**, then **SHADOW→ENFORCE** canary per tenant in a live cluster | OPA is authoritative (fail-closed) with divergence→0, validated live before cutover |

---

## Category 3 — Dedicated **UI / mobile waves** (large frontend builds, not single closures)

| ID | Status | What the wave delivers |
|----|--------|------------------------|
| **G-PX-05 / G-PX-06** | deferred (Wave 8) | Mobile provider parity (login/onboarding/context-picker/check-in/Work/My-Professional/Facility-Mode) + clinical UX depth (cadre form content, sorting-session UI, encounter tools, ZIBO order sets, ICD-11/SNOMED, seed scenarios) |
| **Patient-lane mobile screens** (`G-CT-01`) | web screens **built**; mobile pending | Citizen mobile visit-status + inpatient-stay screens on the patient-lane endpoints (the web `/citizen/visit/*` + `/citizen/inpatient/*` are done) |
| **G-TI-04** | deferred (Wave 10 tail) | Facility-admin mobile screens (public-health mobile controllers already exist) |
| **G-RT-02** | partial | Rito survey dynamic-question renderer + mobile triage parity + document-upload UX (mobile is currently a focused citizen-feedback + provider-safety slice) |
| **Khuluma W8 tail** | comms-ops admin screen **built** | Broader Khuluma UI breadth + governance surfaces beyond the escalation/adapter/on-call ops console |

---

## Category 4 — Feature builds that are **honest-safe now** (no live env needed — just dedicated build time)

| ID | Current honest state | The deferred build |
|----|---------------------|--------------------|
| **G-PX-03** | **honest uniform-deny, no enumeration leak** (proven by `phoneAndEmail_uniformDenyUntilWired`) | The VITO **contact-resolve seam** so phone/email/invite can start a journey — must preserve the no-leak property |
| **G-OR-01 (refinement)** | RBAC **enforced** (V022) | Finer **per-action** gating (release vs view) + purpose tightening on the enforced OROS routes |
| **G-CZO-10/11/12/15** | honest, marked | Low-data/text-first mode; SMS/phone-OTP as a primary login *door* (exists as step-up only); LOA4 banner state; Vito↔identity-assurance level-sync hook |
| **G-FU-04 (tail)** | honest partials | Document binary upload; campaign/surveillance BFF consumers; federated academies |

---

## What is **NOT** here (already closed — do not re-defer)

- **Facility-scope + restricted-PHI masking** — these were **not** left deferred. They were closed earlier
  in the program by **consuming the PDP's existing `VisibilityProfile`/`maxScope`/`suppressFields`
  obligations** (`VisibilityContextHolder` + `JsonRepresentationShaper`), not by adding roles to
  `TrustContext`. See the session log "Phase 1 follow-on — deferred refinements CLOSED via obligation
  consumption" and the doctrine note on the visibility-obligation seam.
- **The cross-tenant facility read IDOR** (`G-TI-01`) — found + fixed + tested this program (`f98a6847c`).
- **No-closure-without-audit** (`G-CT-02`), **native Fundo CPD consumer** (`G-FU-03`), **the fake biometric
  door** (`G-CZO-14`), **temp-tier discarded-data collection** (`G-CZO-13`) — all closed in-sandbox.

---

## Verification posture summary

| Class | Count (approx) | Gating environment |
|-------|----------------|--------------------|
| Live external counterparties | 5 areas | Real FHIR/HL7/DICOM/LIMS, comms providers, pharmacovigilance endpoints, AI provider |
| Live multi-service e2e + ENFORCE | 6 items | Staging cluster (Redis+Postgres+Kafka), running BFF/guidance/mvumo/rito/vashandi, OPA |
| Dedicated UI/mobile waves | 5 areas | Frontend build capacity (no special env) |
| Honest-safe feature builds | 4 areas | Build time only (no special env) |

**Bottom line:** the **decision logic, persistence, migrations, policy rules, and honesty seams** are
proven here; what remains is **integration verification against live systems** and **scheduled wave
builds**. None of it is silently incomplete — each item above has a definition of done and the specific
environment that unlocks it.
