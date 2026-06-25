# OROS Diagnostic Orders, Imaging, PACS & Results Journey — Final Report (§28)

> Session deliverable for the diagnostics-journey epic. Honest status: a thorough, fully-tested
> **backend + cross-service + first UI vertical slice** was delivered; the full UI/mobile build-out
> and several integration adapters remain (scoped below). Nothing is overclaimed.

## 1. Branch & HEAD

- **Branch:** `intake/oros-diagnostics-journey` (cut from `intake/wave-b-tshepo-gdhcn-trust-primitives`)
- **HEAD:** `b2937ec8b208021be06be53f14a4ee074b2a9cc3`
- Base of work: `3d40ec78` (W1 plan/brief + W2a/W2b foundation, pre-existing)

## 2. Commits (this session)

| Wave | Commit | Summary |
|------|--------|---------|
| 2c | `be0d69f1` | Order lifecycle wiring: draft→submit→schedule→arrive→release, accession reserve at submit, `ImagingWorkflowService`, VARAPI provider resolution |
| 3a | `3020dd80` | Tenant-scoped order listing/search `GET /v1/orders` |
| 3b | `7ff06bfc` | Routing-destination model (V005) + referral `POST /v1/orders/{id}/route` |
| 4a | `f599c446` | Imaging worklist + `accept/reject/clarify/start/complete` fulfilment endpoints |
| 5a | `b24b7321` | Versioned reporting lifecycle (V006) + critical workflow + results inbox |
| 8a | `6c36508d` | Diagnostic reconciliation + turnaround dashboards |
| 5b-1 | `97b61a1f` | Enriched `RESULT_CRITICAL` payload + unacknowledged-critical escalation (V007) |
| 5b-2 | `2efe665d` | notification-service critical-result listener → urgent in-platform alert |
| 7a | `48dda25f` | Honest integration-status surface |
| 6 | `3038d1b4` | External secure result link (share-slip) + paper/external intake |
| BFF | `88332298` | experience-bff diagnostics proxy controller (reads) |
| UI | `51d10c41` | `/diagnostics/orders` tracking page (real BFF→OROS→DB) |
| 4b | `d3c3b247` | PACS study linkage `POST /v1/orders/{id}/link-study` (criterion F) — V008 |
| BFF | `4977fc8d` | BFF write proxies (create draft / submit / route) |
| UI | `a0d9b1b6` | `/diagnostics/orders/new` create-order flow (criterion A) |
| BFF+UI | `4977fc8d`, `a0d9b1b6`, `6a4f92e8` | Create-order + routing/referral UI (criterion A) + study surfacing |
| UI | `ea4a08e6` | Results inbox + critical-results dashboard (criterion B, with acknowledge) |
| UI | `ef6f5b72` | Reconciliation/turnaround ops + integration-status admin (W8/W7) |
| UI | `7a2ae1e3` | Imaging worklist with fulfilment actions (W4) |
| chore | `b2937ec8`, `b3eb5562`, `b25f0256` | Regenerated datasets; cleared scanner false-positives |

## 3. Inventory — what changed

### Services
`oros-service` (primary), `notification-service`, `experience-bff`, `ui/one-ui-shell`.

### Migrations (Flyway, oros-service)
- `V005__routing_destination_fields.sql` — `oros_routing` destination columns.
- `V006__result_report_lifecycle.sql` — `oros_results` reporting lifecycle + version chain.
- `V007__result_escalation.sql` — `oros_results.escalated_at`.

### New backend APIs (oros-service, `/v1`)
- Orders: `POST /orders/draft`, `PUT /orders/{id}`, `POST /orders/{id}/submit`,
  `POST /orders/{id}/route`, `/schedule`, `/arrive`, `/release`, `/imaging/transition`,
  `/accept`, `/reject`, `/clarify`, `/start`, `/complete`; `GET /orders` (search);
  `GET /orders/imaging-worklist`.
- Results/reporting: `POST /results/{orderId}/preliminary|final|amend|addendum`,
  `POST /results/{resultId}/release|ack|critical/flag|critical/ack|external-link`,
  `GET /results/inbox`.
- Intake: `POST /intake/paper`, `POST /orders/external-manual`.
- Ops: `GET /reconcile/diagnostics/{summary,stuck,critical-unacknowledged}`,
  `GET /metrics/turnaround`, `GET /integrations/status`.

### New BFF APIs (experience-bff, `/internal/v1/diagnostics`)
`GET /orders`, `/imaging-worklist`, `/results-inbox`, `/reconcile-summary`, `/integration-status`.

### New frontend route
`/diagnostics/orders` (declared in `src/lib/routes.ts`) + `useDiagnosticsOrders` hooks.

### Domain/core additions (oros-service)
`ImagingWorkflowService`, `OrderSubmissionService`, `OrderQueryService`, `ReportService`,
`ExternalResultLinkService`, `ReconciliationDashboardService`, `CriticalEscalationService`,
`IntegrationStatusService`, `VarapiClient`, `ShareSlipClient`; enums `RouteDestinationType`,
`ResultStatus`, `DiagnosticReconcileBucket`.

### Events / topics
`ORDER_DRAFT_CREATED/UPDATED` → `oros.order.draft`; `IMAGING_STATE_*` → `oros.imaging.state_changed`;
`RESULT_PRELIMINARY/FINAL/AMENDED/ADDENDUM` → `oros.result.available`; `RESULT_RELEASED` →
`oros.result.released`; `RESULT_CRITICAL` (enriched) → `oros.result.critical`;
`RESULT_ACKNOWLEDGED/RESULT_CRITICAL_ACKNOWLEDGED` → `oros.ack.received`; `ACK_ESCALATION` →
`oros.ack.escalation`; `RESULT_EXTERNAL_LINK_ISSUED`.

### Tests
- oros-service: **91** unit tests (`mvn -o test` green) — new suites:
  `ImagingWorkflowServiceTest`, `OrderSubmissionServiceTest`, `VarapiClientTest`,
  `OrderQueryServiceTest`, `RoutingEngineTest` (+routing), `ReportServiceTest`,
  `ReconciliationDashboardServiceTest`, `CriticalEscalationServiceTest`,
  `IntegrationStatusServiceTest`, `ExternalResultLinkServiceTest`, `OrderStateMachineTest` (+draft).
- notification-service: **31** tests (3 new — `OrosResultCriticalKafkaListenerTest`).
- experience-bff: `DiagnosticsExperienceControllerTest` (3, green).
- one-ui-shell: `useDiagnosticsOrders.test.tsx` (3, green); `tsc --noEmit` clean.

## 4. Policy & audit posture

Authorization is enforced at the **Envoy → TSHEPO ext_authz edge** (existing platform pattern;
`/v1/**` authenticated via `TrustContextFilter`). Every sensitive action emits an **outbox event**
carrying actor / from→to / reason — the event journal is the audit trail. No in-service OPA client
or AuditService was invented (none exists in oros-service); this matches the codebase convention.

## 5. Gates (all run, all PASS)

- `check-route-inventory.sh` — **PASS** (frontend parity docs in sync).
- `check-backend-frontend-parity.sh` — **PASS** (blocking=0, advisory=0).
- `check-frontend-mocks-and-stubs.sh` — **PASS** (616 pages; 1 legacy non-blocking warning on an
  unrelated page `landela/page.tsx`).
- `check-product-truth.sh` — **PASS** (92 services; violations=5 ≤ baseline=6; blockers=0).

## 6. Preview status

Not deployed in this session. Verification is via unit/integration tests (the project convention —
CLI-Postgres/Mockito, RTL/vitest, tsc). No runtime/preview boot was performed.

## 7. Honest external-integration status

| Adapter | Direction | Status |
|---------|-----------|--------|
| FHIR DiagnosticReport (Butano SHR) | OUT | **Configured** (ButanoIntegration base-url) |
| Provider directory (VARAPI) | OUT | **Configured** (VarapiClient base-url) |
| Secure external result link (share-slip) | OUT | **Configured** (ShareSlipClient base-url) |
| PACS DICOMweb | OUT | Configured **iff** `oros.integration.pacs.dicomweb.base-url` set; else NOT_LIVE |
| FHIR ServiceRequest inbound | IN | **NOT_LIVE** — contract seam only |
| FHIR ImagingStudy outbound | OUT | **NOT_LIVE** — contract seam only |
| HL7 v2 ORM inbound | IN | **NOT_LIVE** — contract seam only |
| HL7 v2 ORU outbound | OUT | **NOT_LIVE** — contract seam only |
| DICOM MWL outbound | OUT | **NOT_LIVE** — contract seam only |

`GET /v1/integrations/status` reports these honestly; both branches (configured / not) are tested.

## 8. Remaining gaps (NOT swept under the baseline)

1. **Printable order QR (W3d / criterion D)** — deferred. share-slip's `CreateShareLinkRequest`
   requires non-empty `documentIds`; a document-less order QR needs a share-slip enhancement.
   The QR *claim* flow (`/v1/intake/qr/claim` with auth + identity confirmation) is not yet built.
2. **DICOM viewer surfacing (W7)** — `POST /v1/orders/{id}/link-study` now records study UID +
   viewer URL and drives `IMAGES_LINKED` (criterion F backend done); the remaining piece is
   surfacing the viewer launch through the BFF/`ImagingExperienceController` (which exists) and
   the OROS↔pacs-adapter `order-links`/`report-links` reverse correlation.
3. **Butano DiagnosticReport amendment lifecycle** — report versions are modelled in OROS but the
   FHIR `DiagnosticReport.relatesTo` writeback for amend/addendum is not wired.
4. **Provider/destinations directory (W3c/W8)** — `/v1/routing/destinations` and the admin provider
   directory (TUSO internal + VARAPI external aggregation) are not built; the routing primitive
   (destination assignment) is.
5. **Admin config (W8)** — routing-rule and critical-escalation-rule admin endpoints not built
   (escalation runs off `oros.escalation.ack-timeout-minutes`).
6. **UI surface (W3–W9)** — LIVE (all real, UI→BFF→OROS→DB, tested): `/diagnostics/orders`
   (tracking + study surfacing), `/diagnostics/orders/new` (create→submit, criterion A),
   `/diagnostics/orders/route` (referral, criterion A), `/diagnostics/results-inbox`,
   `/diagnostics/critical-queue` (with acknowledge, criterion B), `/diagnostics/worklist`
   (fulfilment actions, W4), `/operations/diagnostics-reconciliation` (buckets + turnaround, H),
   `/admin/integrations` (honest status, §27 #11). REMAINING: reporting-authoring page,
   patient-file investigations tab, QR-claim screen, DICOM viewer deep-launch, and **mobile
   parity (W9)**.
7. **channels-service** external notify-only adapters not touched (in-platform alert path is live).
8. **Offline/low-connectivity** queueing (§15) — not designed.

## 9. Recommended next hardening steps

- **Security:** add explicit TSHEPO/OPA policy points (or document the edge-only model) for the new
  sensitive actions (amend, critical-flag/ack, external-link, intake); break-glass audit; cross-
  facility routing restrictions.
- **Correctness:** integration tests against CLI-Postgres for the new Flyway migrations + JPA queries
  (`stuckImaging`, `criticalUnacknowledged`, `resultsInbox`); concurrency tests for simultaneous
  amendments and the escalation sweep (lock/`@Version`).
- **Performance/scale:** paginate the order/worklist/inbox queries; index review for the new
  predicates; Kafka lag monitoring on `oros.imaging.state_changed` at volume.
- **Completeness:** wire PACS study/report linkage + DICOM viewer launch; Butano amendment writeback;
  build the remaining UI + mobile slices; deliver the printable-order-QR via a share-slip
  document-less-link enhancement.
- **Ops:** runbook for the escalation scheduler + notification consumer enablement
  (`KAFKA_OROS_CONSUMER_ENABLED`); integration deployment guide per adapter.
