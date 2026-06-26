# MCAZ Pharmacovigilance — Demo Script

A guided walkthrough for demonstrating the Patient Safety PoC to MCAZ for requirements refinement.
Each step lists the **UI route** and the **API call** behind it so the demo can be run from the
browser or `curl`. Every mutating call needs the v1.1 trust headers + an `Idempotency-Key`.

> **Honesty framing to state up front:** this PoC captures and triages ADR/AEFI reports end-to-end
> and prepares E2B(R3)-**aligned** packages. It does **not** auto-submit to VigiFlow — VigiFlow entry
> is **manual**, with the reference id recorded on the case. Nothing is ever shown as "Submitted"
> until a real adapter is connected.

Base URLs: one-ui-shell `:3000`, self-service `:3003`, BFF `:8160`, service `:8202`.
Common headers (examples): `-H 'X-Tenant-ID: 00000000-0000-4000-8000-000000000001' -H 'X-Pod-ID: national-spine' -H 'X-Request-ID: $(uuid)' -H 'X-Correlation-ID: $(uuid)' -H 'Idempotency-Key: $(uuid)' -H 'X-Actor-ID: dr-moyo' -H 'X-Actor-Type: PROVIDER' -H 'X-Purpose-Of-Use: PHARMACOVIGILANCE'`.

## 1. Provider ADR from clinical context
**UI:** `/work/patient-safety/new` (type **ADR**). Fill patient, suspect medicine, reaction, submit.
**API:** `POST /internal/v1/patient-safety/reports` (with nested `products` + `events`) → then
`POST /internal/v1/patient-safety/reports/{id}/submit`.
**Show:** the receipt — report `PSR-2026-…` and the opened case `PSC-2026-…`.

## 2. Provider AEFI from vaccination context
**UI:** `/work/patient-safety/new` (type **AEFI**) — note vaccine-specific fields (dose number, site,
batch). Mark **serious** with criteria (e.g. HOSPITALISATION).
**Show:** the opened case is **URGENT** priority because it is a serious AEFI.

## 3. Citizen / caregiver simplified report
**UI:** self-service `/report-side-effect`. Plain-language flow, "Myself / Someone I care for",
medicine name, what happened, severity.
**API:** `POST /v1/public/patient-safety/reports` (BFF public controller injects system trust headers).
**Show:** the citizen receipt + that a case opens for MCAZ — and the honest note that this is *not*
the external WHO-UMC VigiFlow form.

## 4. Proactive safety check-in campaign *(reference wiring)*
Solicitation cohorts are owned by **campaigns-service** and dispatched via
**campaigns → channels → notification**. In this PoC the linkage is described, not seeded; a check-in
reply would land on the citizen/provider report path above. *Flag for MCAZ: confirm the solicitation
cadence and channel mix (SMS/USSD/WhatsApp).*

## 5. Reply creates / updates a report
A Comms-Hub reply (channels-service) attaches to a case via `ps_conversation_link`; in the demo this is
shown through the follow-up response step (8). *Flag: confirm inbound-reply → report mapping rules.*

## 6. MCAZ workbench review
**UI:** `/work/patient-safety/mcaz` — incoming + priority queues and headline counts.
**API:** `GET /internal/v1/patient-safety/mcaz/workbench`.
Open the ADR case: **UI** `/work/patient-safety/cases/{caseId}`.
**Triage:** `POST /cases/{id}/triage` `{ "priority":"HIGH","assignedReviewerId":"mcaz-reviewer-1" }`.

## 7. MCAZ requests follow-up via Comms Hub
**UI:** case detail → "Request follow-up". **API:** `POST /cases/{id}/follow-up`
`{ "requestText":"Confirm concomitant medicines","channelHint":"COMMS_HUB","conversationRef":"conv-123" }`.
**Show:** case moves to **AWAITING_FOLLOW_UP**; a `ps_conversation_link` is recorded.

## 8. Follow-up response attaches to the case
**API:** `POST /cases/{id}/follow-up/{followUpId}/response`
`{ "responseText":"No concomitant medicines","responderKind":"PROVIDER" }`.
**Show:** case returns to **UNDER_REVIEW**; the response is on the timeline.

## 9. MCAZ records a manual VigiFlow entry + reference id
**UI:** case detail → "Record VigiFlow reference". **API:** `POST /cases/{id}/vigiflow-manual-entry`
`{ "vigiflowReferenceId":"ZW-2026-0001" }`.
**Show:** case → **MANUAL_ENTRY_COMPLETED**; `entry_mode = MANUAL` is explicit — **not** an automated
submission.

## 10. Serious-AEFI opens an investigation workflow
**UI:** open the serious AEFI case → "Open AEFI investigation".
**API:** `POST /cases/{caseId}/investigation` → then `PATCH /investigations/{id}`
`{ "status":"FINAL","finalClassification":"Vaccine product-related reaction" }`.
**Show:** the investigation lifecycle and final WHO AEFI classification on the case.

## 11. Export-ready / adapter-disabled (NOT a faked submission)
**UI:** case detail → "Mark export-ready". **API:** `POST /cases/{id}/mark-export-ready`.
**Show:** case → **EXPORT_READY**, package `E2B_R3_ALIGNED`, **adapter not configured**, with the
honest message. Compare against `GET /config/adapters` (vigiflow-e2b **enabled=false**). This is the
core honesty talking point.

## 12. Surveillance consumes selected serious / cluster events
Serious reports emit `impilo.patient_safety.report.serious.v1`; **surveillance-service** consumes this
for signal/cluster detection (it does **not** own the regulatory case). *Flag: confirm which events
feed signal detection and thresholds.*

## Audit trail
Open any case timeline (`GET /cases/{id}` → `timeline[]`): every triage, review, follow-up, VigiFlow
entry, export and investigation action is recorded with actor, from/to status and note.

## VigiMobile external links
**UI:** provider home → "WHO-UMC VigiMobile (external)" panel. **API:** `GET /config/vigimobile-links`.
**Show:** the disclaimer that these are external destinations Impilo links out to.

## What needs MCAZ refinement next
Walk through [`docs/services/patient-safety-known-limitations.md`](../services/patient-safety-known-limitations.md)
§ "Suggested MCAZ refinement agenda".
