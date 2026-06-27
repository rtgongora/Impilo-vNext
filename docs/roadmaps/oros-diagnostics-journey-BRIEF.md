# OROS Diagnostic Orders, Imaging, PACS & Results Journey — Full Mandate (source of truth)

> This is the authoritative brief for the epic. The grounded implementation plan (extend-don't-
> duplicate delta + wave sequence) is in [`oros-diagnostics-imaging-results-journey.md`](oros-diagnostics-imaging-results-journey.md).
> Read both before continuing the build.

## 1. Strategic intent
A fully working diagnostic assessment journey that can introduce vNext into an institution through
imaging, PACS and diagnostic services. Full vertical slice across web, mobile/provider experience,
backend, BFF, policy, routing, audit, notifications, patient-file integration, external-provider
workflows, QR/paper intake, PACS/DICOM linkage, result return, reconciliation and Product Truth parity.

Journey: client acquisition → registration/verification (VITO) → diagnostic/imaging order (OROS) →
routing/referral (internal or external) → receipt by imaging/assessment team → fulfilment →
image/study/report linkage (PACS) → result release → patient file update (Butano/SHR) → notification
to requester/workspace → secure access for external providers/patients → reconciliation and audit.

Entry points to support: (1) full internal clinical request; (2) department-first imaging-only;
(3) standalone imaging provider; (4) paper request; (5) QR/secure-code; (6) external-system readiness;
(7) internal facility routing; (8) cross-facility/external referral; (9) patient-carried + patient-
mediated result sharing; (10) result return to non-vNext providers via secure controlled access.

Doctrine: every diagnostic assessment starts as an order; every order has a requester and a responsible
fulfiller; every fulfilment produces a result or a documented exception; every result returns to the
patient file and the responsible requesting workspace under TSHEPO-governed, audited access.
**PACS is the image/study layer; the workflow belongs to OROS** + VITO, VARAPI, TUSO, TSHEPO, comms,
Butano/SHR, COSTA/MUSheX (billing hooks).

## 2. Services to extend (NOT duplicate)
VITO (client registry), **OROS (backbone)**, TUSO (facility/department/unit registry), VARAPI
(provider registry + external provider directory), TSHEPO (trust plane — govern + audit every
sensitive action), Butano/SHR (order/result/report storage, DICOM links, version history), PACS/DICOM
layer (`pacs-adapter-service` + Orthanc + experience-bff DICOMweb/governed imaging API), notification-
service + channels-service (in-platform first; external adapters notify-only, never clinical content),
COSTA/MUSheX (billing/authorisation hooks — do not block core on payments).

## 3. Order status lifecycle (explicit, guarded)
Draft, Submitted, Routed, Received, Accepted, Scheduled, Arrived, In Progress, Performed,
Awaiting Images, Images Linked, Awaiting Report, Preliminary Report, Final Report, Released,
Acknowledged, Closed. Exceptions: Returned for Clarification, Rejected, Cancelled, Deferred, No Show,
Failed/Could Not Perform, Reassigned, Amended, Superseded. Every transition records actor, timestamp,
workspace, facility/department, reason (where applicable), audit event, policy check, notification
side-effect. Deny impossible transitions (transition guard). *(Implemented as `ImagingWorkflowState` +
`ImagingWorkflow` guard, a projection over the canonical `OrderStatus`.)*

## 4. Order data model (domains)
Client details; requester details (referring provider distinct from placing actor); clinical details
(indication, provisional dx, history, urgency, infectious risk, pregnancy, allergies, renal/creatinine
for contrast, implants for MRI, prior imaging/reports); examination details (modality, body part,
laterality, procedure ± code, contrast, sedation, priority, requested date, special instructions);
routing details (type internal/external/cross-facility/patient-carried/QR/paper/external-system,
destination facility/department/service-point/provider, expected result-return method, route expiry);
attachments (scanned paper request, referral letter, prior report, consent, uploads); payment/
authorisation readiness (payer, exemption, payment status, pre-auth, auth number); result & report
(id, status preliminary/final/amended/addendum/corrected, text, impression, recommendations, critical
flag+reason, reporting/validating provider, release+ack timestamps, amendment history); PACS/DICOM
metadata (accession number, study UID, series, MWL ref, viewer URL, archive status, image count,
study datetime, modality device, external PACS ref).

## 5. Internal provider journey
A. Client search/register (VITO): Health ID / name+DOB+phone / temp ID / recent / ward-OPD context /
duplicate warning / register-if-new / demographic confirmation before submit.
B. Create order: modality, procedure/body part, indication, urgency, safety questionnaire, attachments,
payment/auth readiness, consent, requester auto-fill, draft save, review-before-submit.
C. Routing/referral page: internal dept/unit, internal facility service point, another facility,
external provider, patient-carried QR/printable, save draft, return-for-completion. Internal selects
facility/department/service-point/queue/room/appointment/transport/responsible-team. External selects
trusted-directory provider, onboarding status, accepted result-return method, patient-carries flag,
reason. **Routing must be policy-secured — no unrestricted provider lists.**
D. Submit: OROS order id, accession reserved, status Submitted/Routed, destination receives, requester
tracks, audit, notification.

## 6. Imaging/diagnostic team workspace
Worklists: New, Received, Accepted, Scheduled, Arrived, In Progress, Performed, Awaiting Images/Report,
Reported, Released, Rejected/Returned, Critical Results, Unacknowledged, Paper/QR Intake, External.
Intake: view summary/indication/safety/attachments; accept/reject(reason)/return-for-clarification/
request-info/reassign/schedule/arrive/no-show/cancel. Exam capture: start/end, modality, procedure,
body part/laterality, contrast, complications, technical notes, technologist, device/room, study
metadata, uploads, PACS study link. Report authoring: preliminary/final, structured sections,
impression, recommendations, critical flag, sign/validate, send-for-validation, amend/addendum.
Release: internal/external/patient (policy), hold, mark sensitive, critical workflow, notify requester.

## 7. Standalone imaging provider journey
Start from paper / QR / secure code / external request / walk-in / referred patient with incomplete
details. Client intake (search/register/min-demographics/contact/referrer/payment). Paper capture
(scan/upload, indication, requester, create OROS order, link attachment, source=PAPER). QR/code intake
(scan/enter, authenticate, claim per TSHEPO policy, confirm identity, accept/reject — **no sensitive
data without auth+policy**). Manual order creation (source=EXTERNAL/WALK_IN/PAPER, preserve source +
requester + paper + identity + fulfilment trail + result-return pathway).

## 8. External result return
A. vNext requester: results inbox, status update, patient-file update, notification, acknowledge,
critical requires ack, audit view+ack. B. External provider with vNext account: secure notification,
scoped workspace access, access logged, images only if authorised. C. External provider not onboarded:
secure result link / QR / one-time claim code / portal / time-limited / OTP / view-download per policy /
access log — **no clinical content via insecure SMS/email/WhatsApp (notify-only)**. D. Patient-mediated:
view where allowed, share time-limited link/QR, recipient authenticates, sensitive results restricted,
logged. *(Reuse `share-slip-service`: generic secure-link + OTP + QR + PDF + public claim/verify.)*

## 9. Critical results workflow (first-class)
On critical: require reason, identify responsible requester/workspace, urgent in-platform alert,
require acknowledgement, record ack actor+timestamp, escalate if unacked, Critical Results dashboard,
escalate to facility/team roles, full audit. Policy-controlled access + ack.

## 10. Amendments / addenda / versioning
Preliminary, final, amended, addendum, corrected, superseded; version history; reason; author;
timestamp; notify prior recipients; patient-file preserves history. **Never overwrite a final report.**

## 11. Diagnostic provider directory (TUSO + VARAPI + TSHEPO)
Facility/provider name, ownership, location, level, dept/unit, services, modalities, hours, emergency
availability, internal/external, onboarded, can-receive-electronic/QR/paper, can-return-results,
can-expose-images, verification status, restrictions, accepted payment/auth, referral rules, contacts,
route availability, inactive/suspended. Routing UI uses the directory (not free text), but allows
free-text capture for legacy paper under source flags.

## 12. Patient file / investigations
Show active+historical orders, modality, date, requester, destination, status, report availability,
critical flag, attachments, scanned request, image/study links, final report, prelim/amended history,
ack status, external-source indicator. TSHEPO-governed.

## 13. Notifications + workspace inboxes
Events: order created/routed/received/accepted/rejected/clarification/info-requested/scheduled/arrived/
no-show/in-progress/completed/images-linked/report-prelim/report-final/released/amended/critical/
acknowledged/unacknowledged-after-threshold/external-access-used/link-expired. Destinations: requesting
provider, requesting workspace, ward/dept, imaging team, reporting provider, facility admin, external
portal, patient app (policy). In-platform first; external adapters never leak clinical detail.

## 14. Reconciliation + management dashboards
Sent-not-received, received-not-accepted, accepted-not-scheduled, scheduled-no-show, performed-not-
reported, images-linked-report-pending, reported-not-released, released-not-acknowledged, critical
unacknowledged, paper-not-linked, external-awaiting-result, QR claims pending, failed external delivery,
cancelled/rejected trends, turnaround by modality/facility/provider, workload by unit, duplicate/
mismatch warnings. Role + facility scoped.

## 15. Downtime / low-connectivity
Printable order + result, QR on order, secure short code, offline capture queue (only if architecture
supports — truthfully), later sync/reconciliation, local temporary accession, paper upload after
reconnect, visible source + sync status. Do not pretend offline sync exists if unsupported.

## 16. External integration readiness
Adapter contracts for FHIR ServiceRequest/DiagnosticReport/ImagingStudy, HL7 v2 ORM/ORU, DICOM MWL,
DICOM study metadata, external PACS viewer launch, secure document bundle intake. Clean interfaces +
data models + extension points; **no fake integrations** — mark endpoints configured/not-configured
with honest UI.

## 17–22. Backend / API / Frontend / Mobile / Policy / Audit
- Backend: extend canonical OROS (do not fork); BFF under existing naming; Flyway migrations; DTOs;
  controllers; services; repositories; outbox/events; audit; notification events; tests.
- API surface (≈): client search; draft create/update; submit; route; cancel; get; list by
  client/requester/destination/status; routing (list internal destinations, list external providers,
  validate/assign/reroute, printable/QR, claim QR); imaging worklist (list/accept/reject/clarify/
  schedule/arrive/start/complete/link-study/upload); reporting (prelim/final/sign/release/amend/
  addendum/mark-critical/ack-critical/ack-normal); external access (create/validate/view/expire/log);
  reconciliation (stuck/unack-critical/paper-unlinked/report-pending/external-pending/route-failures/
  turnaround).
- Frontend (real pages, follow vNext style, **no dead buttons / fake success / stubs**; honest
  configured/not-configured + admin path): provider (client search/register, create order, clinical+
  safety, attachments, route/refer, review+submit, tracking, results inbox, critical ack, patient-file
  investigations); imaging (worklist, intake, arrival, exam capture, study linkage, report authoring,
  validation, release, exceptions, critical queue); external (scan QR/enter code, claim, register/
  confirm client, upload paper, perform, upload report/link images, release, secure result view);
  admin (provider directory, service catalogue, routing rules, access policies, critical escalation,
  audit/reconciliation dashboard, integration status).
- Mobile/provider parity where route-parity gates require: order tracking, results inbox, critical
  ack, QR scan/claim, patient-file investigation summary, imaging team task list.
- TSHEPO/OPA policy for every sensitive action (create/submit/route int/ext, view directory, receive,
  accept/reject, schedule, perform, link images, author/validate/release/view report, view images,
  create external link, claim QR, access external result, ack, ack-critical, amend, admin directory,
  configure routing, view dashboards, break-glass). Audit every sensitive action with actor/role/
  workspace/facility/patient/order/action/timestamp/IP-device/reason.

## 23. Product Truth & route parity
No backend capability without a real frontend surface (unless documented internal-only); no route
drift; no fake/stubbed frontend; no placeholder-only pages; no mock data in production paths; route-
parity + no-stubs guards pass; product-truth dataset regenerated; baseline ratcheted only with honest
gaps; all new routes documented. Where an external integration can't complete: build data model + API
contract + adapter interface + admin config + honest status + tests for configured/unconfigured.

## 24. Testing
Backend (order create/route, transition guard, internal/external routing, QR gen/claim, paper metadata,
report create/release, critical workflow, ack, amendment/versioning, audit events, policy denials,
reconciliation queries); BFF/API (provider order flow, imaging worklist, patient-file investigations,
result inbox, external access, dashboards, integration status); Frontend (provider creates+submits,
routing page, imaging receives+acts, report entered+released, requester sees result, patient-file
updates, critical ack, QR/paper intake, admin directory+rules); gates (route parity, no stubs, tsc,
build, backend tests, frontend tests, product truth, preview quality gates).

## 25. Delivery sequencing (waves — coherent commits, not MVP-stop)
W1 archaeology+plan (DONE); W2 data+backend foundation (DONE: data model + accession + imaging
lifecycle guard; REMAINING: draft/submit/schedule/arrive/release endpoints + transition wiring);
W3 order creation + routing + directory + printable/QR + notifications; W4 imaging worklist +
fulfilment + study linkage; W5 reporting + result return + patient-file + ack + critical workflow;
W6 paper/QR/external + secure result link + portal + patient-mediated; W7 PACS/DICOM + MWL + FHIR/HL7/
DICOM adapter interfaces + integration-status admin; W8 admin + dashboards + reconciliation +
turnaround; W9 mobile parity + notifications; W10 Product Truth + gates + final report.
Each wave: TSHEPO policy + audit + tests before push.

## 26. Acceptance criteria (end-to-end, real state + UI + policy)
A Internal facility order; B Internal critical result; C Paper request intake; D QR/order-code intake;
E External requester secure result access; F Patient-file continuity; G Report amendment with history;
H Reconciliation (stuck/pending/unack-critical/external-failures/paper-unlinked/turnaround).

## 27. Non-negotiables
No mock-only screens. No orphan PACS features outside OROS. No clinical content via insecure
notifications. No provider directories without policy. No silent report overwrite. No QR opening
sensitive data without auth + scoped authorisation. Don't ignore patient-file integration, workspace
notifications, audit, Product Truth, route parity. Don't claim external PACS/DICOM integration unless
implemented and testable — use honest integration-readiness states.

## 28. Final report required
Branch; HEAD commit; files/services/routes/APIs/migrations/policies/tests changed/added; gates run +
pass/fail; preview deployment status; known limitations; honest external integration status; remaining
gaps; recommended next hardening steps.
</content>
