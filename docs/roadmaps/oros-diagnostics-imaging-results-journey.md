# OROS Diagnostic Orders, Imaging, PACS & Results Journey — Implementation Plan (Wave 1)

**Status:** Wave 1 (archaeology + design alignment) — produced by grounded repo inspection.
**Doctrine:** Every diagnostic assessment starts as an order → requester → responsible fulfiller →
result or documented exception → returns to patient file + requesting workspace under Tshepo-governed,
audited access. PACS is the image/study layer; the *workflow* belongs to **OROS** with VITO, VARAPI,
TUSO, TSHEPO, Butano/SHR, notification/channels, share-slip and the experience layer.

> **Core rule for this epic:** EXTEND canonical services. Do **not** create a parallel order/PACS/imaging
> model. ~70% of the journey already exists; the work is the *delta* + wiring + governance + parity.

---

## A. What already exists (reuse map — do NOT duplicate)

| Capability | Canonical home | State |
|---|---|---|
| Order lifecycle, types (incl. `IMAGING`), items, priority | `oros-service` `OrderEntity`/`OrderItemEntity`/`OrderStatus`/`OrderType` | Real; 13-status state machine `OrderStateMachine` |
| Routing + adapter modes (INTERNAL/ADAPTER/HYBRID; LIMS/PACS/PHARMACY_EXT/OTHER) | `oros-service` `RoutingEngine`/`RoutingEntity` | Real |
| PACS dispatch + study callback | `oros-service` `adapter/pacs/PacsAdapter`, `OrosEventConsumer` (`pacs.imaging_study`) | Real |
| Results, critical-flag, acknowledgements (DEPT/CLINICIAN/CRITICAL) | `oros-service` `ResultController`/`AckController` | Real |
| Worklists, worksteps, SLA timers, reconcile queue | `oros-service` | Real |
| Event outbox → Kafka (`oros.order.*`, `oros.result.critical`, `oros.ack.*`) | `oros-service` `OutboxPublisher` | Real |
| FHIR ServiceRequest + DiagnosticReport writeback (SHR) | `butano-service` via `oros-service` `ButanoIntegration` | Real |
| Imaging study→series→instance→report→annotation→viewer-session + ops/audit | `pacs-adapter-service` | Real |
| DICOMweb proxy (QIDO/WADO/STOW) + governed imaging API (access policy + audit + viewer launch) | `experience-bff` `PacsController` + `ImagingExperienceController` | Real |
| External PACS DICOMweb connector (fail-closed) | `pacs-adapter-service` `ExternalPacsClient` (G045, just built) | Real |
| Real DICOM viewer (DWV + native window/level + WADO rendered + OHIF iframe) | `ui/one-ui-shell` `DwvNativeViewer`, `/ehr/[id]/imaging/viewer` | Real |
| Patient file: orders / results / imaging / documents tabs | `ui/one-ui-shell/src/app/ehr/[patientId]/*` | Real |
| Results inbox (facility worklist + bulk authorise) | `ui/one-ui-shell/src/app/lab/results` | Real |
| Notifications inbox + templates + channels; messaging/paging | `notification-service` + `channels-service`; `NotificationsCommsHub` | Real |
| **Secure link + OTP + QR + PDF + public claim/verify** (generic `subject_type`/`documentIds`) | `share-slip-service` (`ShareLinkEntity`, `PublicShareController`, `ShareSlipPdfService`) | Real — **reuse for QR order intake AND external secure result access** |
| Facilities/capabilities/units; providers + external-provider-collaboration | `tuso-service`; `varapi-service` | Real |
| Gates: product-truth scanner, route-parity, no-stubs, route registry | `scripts/guard/*`, `scripts/completeness/*`, `ui/one-ui-shell/src/lib/routes.ts` | Active |

---

## B. The genuine delta to build (extend these)

1. **OROS draft phase** — expose `DRAFT` create/update + submit (enum exists; API starts at PLACED).
2. **Diagnostic/imaging order fields** — modality, laterality, contrast, safety questionnaire
   (renal/pregnancy/implants/allergy), **requester identity separate from `placedBy`** (referring
   provider via VARAPI), **accession-number lifecycle**, scheduled acquisition, **request source**
   (INTERNAL/PAPER/QR/WALKIN/EXTERNAL). New migration on `oros_orders`/`oros_order_items` (+ optional
   `oros_imaging_detail`), not a new service.
3. **Imaging sub-lifecycle** — map the spec's intermediate states (Received/Scheduled/Arrived/Awaiting
   Images/Images Linked/Preliminary|Final Report/Released/Acknowledged) onto OROS via a sub-status or
   workstep-backed status projection; add transition guards. Keep the canonical `OrderStatus` superset.
4. **Internal department/service-point routing** — extend `RoutingEngine` to resolve a TUSO
   department/service-point/queue destination (not just adapter target); a **routing-destination
   directory** read endpoint combining TUSO capability + VARAPI + onboarded/route flags.
5. **Paper / QR / external-manual intake** — source flag + scanned-request attachment; **QR order
   claim via `share-slip` `subject_type=DIAGNOSTIC_ORDER`**.
6. **Secure external result access** — issue `share-slip` link `subject_type=DIAGNOSTIC_RESULT`
   (OTP, expiry, max-claims, audit) + a scoped public result-view; "notify-only" through insecure
   channels (no clinical content).
7. **Critical-result notification + escalation** — `notification-service` listener on
   `oros.result.critical` → inbox alert to requester/workspace; ack closes loop; escalate if unacked
   past threshold (reuse SLA timers / a scheduled check). New templates.
8. **Diagnostic provider directory + admin UI** (routing rules, critical-escalation rules,
   integration-status configured/not-configured).
9. **Reconciliation/management dashboards** — surface OROS reconcile + pacs ops + new stuck-order
   queries (sent-not-received, performed-not-reported, released-not-acknowledged, unlinked paper,
   external pending, turnaround).
10. **TSHEPO policies + audit** for every new sensitive action (§21/§22 of the brief).
11. **Integration readiness** — FHIR ServiceRequest/DiagnosticReport/ImagingStudy + HL7 ORM/ORU +
    DICOM MWL adapter *interfaces* with honest configured/not-configured status (no fake integrations).
12. **Routes + parity + product-truth** — declare new routes in `routes.ts`, regenerate parity +
    product-truth datasets, keep gates green (no new UI-without-backend blockers, no route drift).

---

## C. Final route / API naming (align to existing conventions)

- OROS backend stays `/v1/orders/**`, `/v1/results/**`, `/v1/routes/**`, `/v1/reconcile/**`; **add**
  `/v1/orders/{id}/route`, `/v1/orders/draft`, `/v1/orders/{id}/submit`, `/v1/orders/{id}/schedule`,
  `/v1/orders/{id}/arrive`, `/v1/orders/{id}/release`, `/v1/intake/paper`, `/v1/intake/qr/claim`,
  `/v1/routing/destinations`.
- BFF governed surface under existing `/internal/v1/imaging/**` (`ImagingExperienceController`) +
  a new `/internal/v1/diagnostics/**` for the order/route/result-inbox/dashboards; reuse
  `/internal/v1/pacs/**` for pixels and `/internal/v1/notifications/**` for inbox.
- External secure result via `share-slip` `/v1/public/share/{verify,claim}` (no new public surface).
- Frontend zones reuse `ehr`, `lab`→generalise to `diagnostics`, `operations`, `admin`.

---

## D. Wave sequence (each = coherent, verified, pushed slice — our standing cadence)

- **W2 Data+backend foundation:** OROS migration (imaging fields, source, accession, requester),
  draft+submit+schedule+arrive+release endpoints, transition guards, accession service, events.
- **W3 Order creation + routing:** create/draft/submit; internal dept/service-point routing +
  destination directory; printable/QR order via share-slip; route validation; notifications.
- **W4 Imaging worklist + fulfilment:** receive/accept/reject/clarify/schedule/arrive/exam-capture/
  link-study (reuse pacs sync) + status transitions (extend imaging workspace UI).
- **W5 Reporting + result return:** preliminary/final/amend reports (pacs report-links + Butano
  DiagnosticReport), release, requester results-inbox, acknowledgement, **critical-result workflow**.
- **W6 Paper/QR/external:** paper intake + scan attach; QR claim; external manual order; secure
  external result link + scoped public view; patient-mediated share hooks.
- **W7 PACS/DICOM + integration readiness:** accession/study-UID/viewer-link surfacing; MWL +
  FHIR/HL7/DICOM adapter interfaces + integration-status admin (honest).
- **W8 Admin + dashboards:** provider directory admin, routing/escalation rules, reconciliation +
  turnaround dashboards.
- **W9 Mobile parity + notifications:** provider order-tracking, results inbox, critical ack, QR scan.
- **W10 Product Truth + gates + report:** regenerate datasets, run all gates, fix drift, final report.

Each wave: TSHEPO policy + audit + tests (backend MockMvc/unit, RTL, gate tests) before push.

---

## E. Gate obligations (must stay green)

- `bash scripts/guard/check-product-truth.sh` (no new blocker/regression; baseline ratchet only with honest gaps)
- `bash scripts/guard/check-backend-frontend-parity.sh` + `check-route-inventory.sh` (declare routes in `routes.ts`)
- `bash scripts/guard/check-frontend-mocks-and-stubs.sh` (no stubs / fake success)
- `tsc --noEmit`, vitest, `mvn test` per touched service.

---

## F. Honest constraints

- **No live external systems** here (real external PACS/VNA, SMS gateway, biometric, external RIS/HIS):
  build data model + API contract + adapter interface + admin config + honest configured/not-configured
  state + tests for both branches. No fake integrations, no clinical content over insecure channels.
- This is a multi-wave program; deliver as coherent verified slices, not one shallow drop.
</content>
