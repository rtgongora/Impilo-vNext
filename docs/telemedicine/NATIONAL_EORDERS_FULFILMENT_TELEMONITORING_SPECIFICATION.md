# Impilo Platform vNext — National e-Orders, e-Prescription, Fulfilment Marketplace, Coverage, Logistics, Community Telemonitoring and IoT

## Volume II of the National Telemedicine & Virtual Care Specification Pack

---

## 1. Document Control

| Field | Value |
|---|---|
| Title | Impilo Platform vNext — National e-Orders, e-Prescription, Fulfilment Marketplace, Coverage, Logistics, Community Telemonitoring and IoT: Full Functional, Clinical, UX and Technical Implementation Specification |
| Volume relationship | **Volume II** of the National Telemedicine & Virtual Care specification pack. Co-normative with [Volume I](NATIONAL_TELEMEDICINE_VIRTUAL_CARE_SPECIFICATION.md). The pack shares ONE traceability matrix, ONE backlog, ONE journey catalogue and ONE open-decision register across both volumes. |
| Owner | Impilo vNext Platform Programme (order spine: OROS product ownership; marketplace: Msika; coverage: Ruvimbo; logistics: Nhume; telemonitoring: clinical plane) |
| Status | **CANONICAL — NORMATIVE** for e-order, e-prescription, fulfilment-marketplace, coverage-resolution, payment, logistics, telemonitoring and health-IoT product, design and engineering work. Implementation-status claims are evidence-graded (see §25 pointer and the [traceability matrix](telemedicine-traceability-gap-matrix.md) §4). |
| Version | 1.0.0-draft (Wave 0 scaffold — sections marked `[AUTHORING]` are reserved and not yet normative) |
| Effective date | 2026-07-23 |
| Scope note | Clinical orders arise from **all** encounter types — in-person, ward, theatre, discharge, chronic review, community visit, remote-monitoring alert, standing protocol, and teleconsultation. Volume I's teleconsultation is ONE originating context of this pipeline, not its owner. |
| Composes with (cites, never duplicates) | [`docs/marketplace/msika-health-marketplace.md`](../marketplace/msika-health-marketplace.md) · [`docs/architecture/nhume-dispatch-and-delivery.md`](../architecture/nhume-dispatch-and-delivery.md) · [`docs/product/ruvimbo-coverage-specification.md`](../product/ruvimbo-coverage-specification.md) · [`docs/supply-iot-platform/README.md`](../supply-iot-platform/README.md) · [`docs/assets/asset-device-iot-discovery.md`](../assets/asset-device-iot-discovery.md) · [`docs/doctrine/health-os-doctrine.md`](../doctrine/health-os-doctrine.md) (§16 identifier doctrine, §17 device/IoT doctrine) · [`docs/registry/system-of-record-map.md`](../registry/system-of-record-map.md). Where any cited document conflicts with this volume, registry constraints win on ownership and `identity-trust-contract.md` wins on identifier semantics (Volume I §1 precedence rule). |
| Normative status | RFC-2119 keywords **MUST/MUST NOT/SHOULD/MAY**. Implementation-status tags: `[LIVE]` runtime-proven · `[BUILT]` code-verified · `[PARTIAL]` · `[CONFIG-ONLY]` · `[ABSENT]` · `[PENDING-POLICY]`. |
| Change control | Pull request against this file on the canonical branch; identifier/consent/ownership language changes additionally sign off against `identity-trust-contract.md` and `services-registry.yaml` (Volume I rule applies pack-wide). |

### 1.1 Commissioning-instruction → section map (completeness contract)

The commissioning instruction ("ADDITIONAL MAJOR DOMAIN", 2026-07-23) prescribes nineteen content areas. Each maps to exactly one normative home:

| Instruction § | Content | Volume II home |
|---|---|---|
| 1 | Product doctrine | §4 (with instruction §19 critical constraints as doctrine invariants) |
| 2 | Platform service ownership | §6 |
| 3 | Canonical identifiers and core objects | §5 |
| 4 | Order and prescription types | §7 |
| 5 | End-to-end pipeline stages A–N | §8 (detail volumes: §10 coverage/payment, §11 marketplace, §12 logistics) |
| 6 | E-prescription safety, legality, anti-fraud | §13 |
| 7 | State machines | §9 |
| 8 | Community telemonitoring | §14 |
| 9 | IoT architecture | §15 |
| 10 | Clinical and IoT data mapping (FHIR) | §16 |
| 11 | Frontend and mobile experiences | §20 |
| 12 | Notifications and Nompilo guidance | §19 |
| 13 | Analytics, quality and market fairness | §21 |
| 14 | Required domain events | §18 |
| 15 | Failure modes and recovery | §22 |
| 16 | End-to-end test journeys | §23 → [journey catalogue](telemedicine-journey-catalogue.md) #41–#70 |
| 17 | Implementation truth recovery | §25 → [traceability matrix](telemedicine-traceability-gap-matrix.md) §4 (R41+, OF-G*) |
| 18 | Implementation backlog | §26 → [backlog](telemedicine-implementation-backlog.md) OF-B1..OF-B30 |
| 19 | Critical constraints | §4.9 (verbatim-or-stronger doctrine invariants) |

### 1.2 Reserved namespaces (locked in this Wave-0 commit)

- **Epics:** `OF-B1`..`OF-B30` in the shared [backlog](telemedicine-implementation-backlog.md) (titles reserved in §26 below; blocks authored Wave 3).
- **Gap refs:** `OF-G1`.. in the shared matrix; **requirement rows:** `R41`.. continuing the single monotonic namespace.
- **Journeys:** `#41`–`#70` in the shared [journey catalogue](telemedicine-journey-catalogue.md).
- **Open decisions:** `OD-11`+ appended to the pack-wide register in Volume I §32.
- **Handoffs:** `HO-7`+ (HO-1..HO-6 taken by Volume I workstreams).

---

## 2. Executive Summary

`[AUTHORING — Wave 2]` A teleconsultation, ward round or community visit is not complete when advice is written: the clinical decision must execute — as an authorised order or prescription, through provider discovery and regulated offers, coverage and shortfall resolution, payment, preparation/dispensing, pickup or tracked delivery with chain of custody, result and dispense documentation, follow-up, reconciliation and permanent SHR linkage. This volume specifies that national **order-to-outcome pipeline** as a reusable orchestration framework with clinically appropriate profiles (medicines, diagnostics, procedures, devices, supplies, home services, transport), plus the community-telemonitoring and health-IoT programme that keeps the loop closed at home. It is grounded in a code-verified truth recovery: the estate already holds a mature order spine (OROS), real dispensing (pharmacy), a real marketplace transaction plane (Msika), the most complete coverage engine in the platform (Ruvimbo), real payments (MusheX), and a comprehensive logistics machine (Nhume) — and this volume's backlog closes the precisely-located gaps (request-for-offer layer, prescription aggregate, stock-reservation wiring, RPM engine) rather than rebuilding what exists.

## 3. Purpose, Scope and Non-Scope

`[AUTHORING — Wave 2]` **Purpose.** Single canonical, implementation-ready description of order-to-fulfilment execution and community telemonitoring/IoT. **Scope.** Orders/prescriptions from all encounter types; the regulated fulfilment marketplace; coverage/prior-auth/payment resolution; pickup/delivery logistics with chain of custody; community telemonitoring programmes; health-IoT device lifecycle and telemetry. **Non-scope.** Teleconsultation session mechanics (Volume I), EMS clinical dispatch internals (Daidzai), payer internals beyond the platform contract, general retail commerce.

## 4. Product Doctrine and Critical Constraints

`[AUTHORING — Wave 2]` The fourteen doctrine clauses of instruction §1 (order ≠ its downstream identifiers; provider eligibility gates; patient choice; emergency never auctioned; regulated request-for-offer not cheapest-bidder; ranking dimensions; PII-minimised competition; governed substitution; claim-of-stock ≠ fulfilment; video-end ≠ pipeline-end) plus §4.9 critical-constraint invariants (instruction §19, verbatim-or-stronger).

## 5. Canonical Identifiers and Core Objects

`[AUTHORING — Wave 2]` The ~40-identifier registry: existing owners mapped (ClinicalOrderId=OROS ULID · DispenseId=pharmacy · DeliveryId/custody=Nhume · DeviceId=iot-ingestion · Equipment/AssetId=asset-registry · Claim/Authorisation/LiabilityId=Ruvimbo · PaymentIntent/Refund/SettlementId=MusheX · Cart/MarketOrderId=msika-flow · ReservationId=DURA · Tariff/EstimateId=COSTA) and net-new mints (Prescription/PrescriptionVersion/PrescriptionTokenId→OROS · MarketplaceRequest/Invitation/Offer/OfferLine/SelectionId→msika-flow · MonitoringPlan/ThresholdProfile/AlertRule/AlertEpisode/DeviceAssignmentId→telemonitoring), issuance/uniqueness/exposure/lifecycle rules per health-os-doctrine §16, and the settled collision rules (no bare "OrderId"; clinical AlertEpisode ≠ surveillance alert; nhume delivery ≠ daidzai EMS mission).

## 6. Architectural Positioning and Service Ownership

`[AUTHORING — Wave 2]` Instruction §2's ownership contract, reconciled against the registry: OROS (order spine + prescription aggregate) · MSIKA core (catalogue/listing/eligibility) vs MSIKA Flow (transactional RFO/carts/orders) · DURA=inventory-service (sole stock/reservation ledger) · ZIBO (terminology + national medicine registry) · TSHEPO/VARAPI/TUSO/VASHANDI/VITO (trust axes) · BUTANO (SHR) · PCT (tasks/loop closure) · RUVIMBO (coverage) · COSTA (pricing) · MUSHEX (money) · NHUME (logistics) · NDILA (geospatial) · KHULUMA/NOMPILO/RITO/DAIDZAI/FUNDO (comms/guidance/quality/emergency/training) · telemonitoring-service (new, clinical plane — ownership-exhaustion proof) · iot-ingestion + asset-registry (device identity vs physical truth).

## 7. Order and Prescription Type Catalogue

`[AUTHORING — Wave 2]` The formal catalogue (instruction §4 categories A–E: medication/pharmacy, diagnostics, procedures/services, products/supplies, movement/logistics) with per-type: authorised initiators, minimum clinical data, validity, urgency, cancellation/amendment/substitution rules, fulfilment actors, coverage pathway, delivery constraints, completion artefact, loop-closure requirement.

## 8. The Fourteen-Stage Order-to-Outcome Pipeline (Stages A–N)

`[AUTHORING — Wave 2]` Stage A authoring · B signing/activation/publication decision · C marketplace-request creation · D provider eligibility/matching · E offer/quotation · F comparison/patient choice · G coverage/benefits/prior-auth · H shortfall resolution/payment · I acceptance/reservation/commitment · J preparation/dispensing/service delivery · K pickup/collection/delivery options · L logistics/chain of custody · M transport modes (incl. drones as governed capability) · N fulfilment confirmation and clinical loop closure. Each stage: actors, inputs, MUSTs, race conditions, events, failure paths.

## 9. State Machines

`[AUTHORING — Wave 2]` Six linked machines with Volume-I-§11.3-style transition tables: §9.1 clinical order (reconciled with the live 13-status OROS machine) · §9.2 prescription version lifecycle · §9.3 marketplace request · §9.4 offer · §9.5 financial resolution · §9.6 fulfilment (reconciled with pharmacy dispense statuses); shipment states delegated to Nhume's live 24-status machine (§12).

## 10. Coverage, Prior-Authorisation, Liability and Payment

`[AUTHORING — Wave 2]` The RUVIMBO/COSTA/MUSHEX contract for stages G–H: eligibility vs benefit vs adjudication distinction; funding-source matrix; per-offer liability calculation; prior-auth lifecycle (mapping to the live 14-status cv_authorisations machine); payment-intent doctrine (no two-phase capture; escrow-on-handover); emergency financial-bypass policy.

## 11. Fulfilment Marketplace — the Request-for-Offer Model

`[AUTHORING — Wave 2]` The regulated RFO layer (net-new, msika-flow): request creation and PII-minimised publication shape; invitation modes; eligibility validation (VARAPI/TUSO/network/capability); offer content and stock-attestation grades; comparison/ranking transparency rules; selection/revalidation/commitment with idempotency and race handling; anti-dark-pattern requirements.

## 12. Logistics, Chain of Custody and Transport Modes

`[AUTHORING — Wave 2]` Nhume contract for stages K–M: pickup/locker/curbside/caregiver-collection flows; delivery-task minimisation (courier never sees clinical content); custody events; proof-of-handover grades (never GPS-proximity alone); cold-chain; failed-delivery and return; per-mode enablement matrix (motorcycle/car/van/ambulance/drone) with drones modelled as governed capability, not claimed functionality.

## 13. Anti-Fraud and Integrity Controls

`[AUTHORING — Wave 2]` Instruction §6: threat catalogue; the signed-reference token model (no clinical payload in QR; server retrieval; single-active claim; server-side dispense counters); controlled-medicine separate workflow (DURA controlled register as audit spine); revocation; anomaly detection; claim-to-dispense and shipment-to-delivery matching.

## 14. Community Telemonitoring

`[AUTHORING — Wave 2]` Instruction §8 in full: programme profiles; enrolment (clinician-approved plans); monitoring-plan model; CHW workflow (offline-first, scope-safe); the 21-step data pipeline; alert model (multi-signal, levels, dedup/storm control, accountable closure); escalation ladder (repeat→CHW task→virtual review→urgent teleconsult (Volume I seam)→Daidzai/Nhume/Ndila); the six workspaces.

## 15. IoT Architecture

`[AUTHORING — Wave 2]` Instruction §9: the 18-layer reference architecture mapped onto the live estate (iot-ingestion + asset-registry + telemetry bus + bronze lake); connectivity patterns (MQTT as `[ABSENT]` transport addition, HTTP/Kafka live); device categories and trust grading; registry/digital identity; lifecycle states; assignment; data-quality evaluation (stamp, never silently drop); edge/offline; constrained remote commands; IoT security controls.

## 16. Data Model and FHIR Mapping

`[AUTHORING — Wave 2]` Instruction §10: canonical entity model per service; FHIR R4 mapping for ordering (MedicationRequest/ServiceRequest/DeviceRequest/SupplyRequest/Task/CarePlan), fulfilment (MedicationDispense/SupplyDelivery/Procedure/DiagnosticReport/Specimen), financial (Coverage/Claim/ClaimResponse/PaymentNotice), monitoring (Device/DeviceMetric/Observation/Provenance); gateway allow-list deltas `[ABSENT]`-tagged; logistics kept out of the SHR unless clinically meaningful.

## 17. API Catalogue

`[AUTHORING — Wave 2]` Per-service API surface for the pipeline (existing endpoints evidence-tagged; net-new endpoints normative): OROS orders/prescriptions/tokens · msika-flow RFO · Ruvimbo eligibility/auth/liability · COSTA estimates · MusheX intents · pharmacy dispense · Nhume deliveries · telemonitoring plans/alerts · iot-ingestion telemetry/devices · BFF composition routes.

## 18. Event Catalogue

`[AUTHORING — Wave 2]` The ~150 logical events of instruction §14 as a registry table `LogicalName → wire topic`: existing topics grandfathered (oros.order.*, pharmacy.dispense.*, mushex.payment.status.changed, msika.flow.*, nhume legacy set, impilo.iot.*.v1); net-new families `oros.prescription.*.v1` · `msika.flow.request.*.v1` · `inventory.reservation.*.v1` · `telemonitoring.plan.*.v1` · `telemonitoring.alert.*.v1`; naming rule `<domain>.<aggregate>.<action>.v1` for all new topics.

## 19. Notification Model and Nompilo Guidance

`[AUTHORING — Wave 2]` Instruction §12: the notification catalogue across the pipeline (Khuluma/notification-service, PHI-minimised) and Nompilo's guidance duties (explain, compare, never commercially biased, never diagnose) — extending the Volume-I-proven guidance-registry pattern.

## 20. Frontend and Mobile Experience

`[AUTHORING — Wave 2]` Instruction §11: the nine role workspaces (prescriber, patient/caregiver, pharmacy/provider, payer, dispatcher, courier, CHW, remote-monitoring clinician, operations) with One-UI/mobile-parity/offline/accessibility requirements and no-decorative-controls rule.

## 21. Analytics, Quality and Market Fairness

`[AUTHORING — Wave 2]` Instruction §13: metric set; Rito capture; fairness monitoring (concentration, ranking transparency, collusion/anomaly detection); rating-integrity safeguards; clinical-safety indicators separated from convenience ratings.

## 22. Failure Modes and Recovery

`[AUTHORING — Wave 2]` Instruction §15: the failure catalogue with per-failure visible status, owner, retry, escalation, patient communication, financial handling, clinical handling, audit, terminal resolution. No silent disappearance.

## 23. Testing Strategy and Journey Catalogue

The runtime-proof doctrine of Volume I §28 applies unchanged (bash rigs + psql asserts + Playwright/Maestro; "a green frontend mock is never proof"). The thirty Volume II journeys are **#41–#70** in the shared [journey catalogue](telemedicine-journey-catalogue.md), authored Wave 3 from instruction §16's list.

## 24. Detailed Acceptance Criteria

`[AUTHORING — Wave 2]` Per-stage "W" acceptance criteria (Volume I §29 pattern), including the instruction's stage-level MUSTs and the §19 constraints as testable assertions.

## 25. Implementation Truth Recovery and Gap Analysis

Normative home: the shared [traceability matrix](telemedicine-traceability-gap-matrix.md) **§4 (rows R41+, gap refs OF-G*)**, authored Wave 1 from the three-service-family evidence sweeps. This section will summarise the recovered truth (what is REAL vs SCAFFOLD vs ABSENT) with row citations.

## 26. Prioritised Implementation Backlog

Normative home: the shared [backlog](telemedicine-implementation-backlog.md). Reserved epic identifiers (1:1 with instruction §18):

| ID | Epic (reserved title) |
|---|---|
| OF-B1 | Canonical clinical-order aggregate |
| OF-B2 | E-prescription authoring and signing |
| OF-B3 | Medication safety validation |
| OF-B4 | MSIKA fulfilment marketplace (request-for-offer) |
| OF-B5 | Provider eligibility and matching |
| OF-B6 | Offer and quotation lifecycle |
| OF-B7 | Patient offer-comparison and selection |
| OF-B8 | Ruvimbo coverage and prior authorisation |
| OF-B9 | COSTA patient-liability calculation |
| OF-B10 | MUSHEX payment and reconciliation |
| OF-B11 | DURA stock reservation |
| OF-B12 | Pharmacy dispense workflow |
| OF-B13 | Diagnostics fulfilment |
| OF-B14 | Multi-provider and partial fulfilment |
| OF-B15 | Substitution and prescriber clarification |
| OF-B16 | Pickup and collection |
| OF-B17 | NHUME delivery orchestration |
| OF-B18 | Chain of custody and proof of delivery |
| OF-B19 | Cold-chain IoT |
| OF-B20 | Drone and alternative delivery-mode enablement |
| OF-B21 | Community telemonitoring programme |
| OF-B22 | Monitoring-plan engine |
| OF-B23 | CHW monitoring workflow |
| OF-B24 | Device registry and lifecycle |
| OF-B25 | IoT ingestion and normalisation |
| OF-B26 | Monitoring alert and escalation engine |
| OF-B27 | Patient and caregiver monitoring experience |
| OF-B28 | Remote-monitoring command workspace |
| OF-B29 | Fraud, anomaly and marketplace fairness controls |
| OF-B30 | End-to-end order-to-outcome runtime proof |

## 27. Open Decisions

The pack maintains a **single** open-decision register: Volume I §32. Volume II decisions are appended there as **OD-11+** (candidates reserved: OD-11 e-prescription legal signature model and non-medication signing scope · OD-12 marketplace offer-ranking fairness policy and broadcast mode · OD-13 legacy `rx_prescriptions` migration cutover · OD-14 IoT device attestation authority and BYOD enrolment · OD-15 controlled-substance chain-of-custody policy · OD-16 MONITORING OrderType vs OTHER · OD-17 vendor-without-DURA attested-stock tolerance — final set fixed during Wave 2 authoring).

## 28. Appendices

`[AUTHORING — Wave 2]` Diagram set: order-to-outcome sequence (A–N) · RFO flow · custody chain · telemonitoring alert ladder · IoT topology. Cross-volume reading guide.
