# RUVIMBO — Impilo Coverage Functionality

> **Repo header (not part of the canonical spec text below).**
>
> - **Status:** Canonical target specification for the Coverage / Health-Financing capability.
> - **Brand:** **Ruvimbo** (front-and-centre product name). **Owner service:** `coverage-service` (port 8140, DB `impilo_coverage`) — technical id and packaging stay `coverage-service` per Health-OS doctrine (`docs/doctrine/health-os-doctrine.md` §engineering-names); "Ruvimbo" is the sovereign brand.
> - **Boundary (load-bearing):** **Coverage decides who pays & what is covered; COSTA prices; MUSHEX moves money.** Coverage references — never duplicates — COSTA billing artifacts and MUSHEX settlement rails. The switch (transaction routing) is distinct from adjudication (payability). A technical acknowledgement is never a financial approval; no claim is "paid" until settlement evidence is received.
> - **Claim-model canon:** `cv_claims` (in coverage-service) is the **canonical claim system of record**. COSTA `costa_claim_packs` is a billing artifact that *files into* coverage (`POST /internal/v1/coverage/claims`); MUSHEX `mushex_claims`/`mushex_adjudications` are settlement artifacts. The BFF `/finance/claims` surface currently reads COSTA claim packs and is scheduled to be reconciled onto the coverage canon in the Claims wave (W3).
> - **Delivery:** built in the spec's four waves (§42). Each wave remains production-capable at the completion-lens bar (no mocks/stubs/dead controls in production paths; every journey live-proven).
> - **Execution register:** `docs/roadmaps/ruvimbo-epics.md`.

---

*The remainder of this document is the canonical specification as issued by the product owner.*

## Impilo Coverage Functionality — Full Product, Functional, Data, Integration and Technical Specification

Status: Canonical target specification
Product: Impilo vNext / Phoenix
Capability: Coverage, Benefits, Eligibility, Authorisations and Claims
Primary channels: Impilo, Impilo Provider, Facility Mode, Payer Portal and Administration Console

### 1. Product Definition

Coverage is the national health-financing entitlement capability that determines:

- Whether a person has an active source of health-care coverage.
- Who is financially responsible for a service.
- Which services, medicines, investigations and procedures are covered.
- Which limits, exclusions, co-payments, deductibles and conditions apply.
- Whether prior authorisation or referral is required.
- How much the payer, patient, employer, programme or facility must pay.
- Whether a submitted claim is valid, payable, partially payable or rejected.
- How approved amounts are settled, reconciled and explained.

Coverage must support more than conventional medical aid. It must represent all legitimate health-financing arrangements, including medical aid and health insurance; employer-sponsored schemes; government-funded health programmes; social protection and assisted-treatment arrangements; donor-funded or programme-funded services; occupational injury and accident-related cover; facility-approved waivers and exemptions; capitation and contracted provider arrangements; self-pay and mixed-payment arrangements; emergency provisional coverage; and multiple simultaneous coverages with coordination of benefits.

Coverage is not itself the pricing engine or payment processor: Coverage determines who should pay, what is covered and under which rules; COSTA determines the price, charge, tariff, invoice and patient liability; MUSHEX collects, transfers, settles, refunds and reconciles money.

### 2. Product Objectives

Coverage must give citizens a single, understandable view of their health coverage; allow providers to verify eligibility during the care journey; prevent patients from being turned away merely because electronic verification is unavailable; support real-time and offline eligibility checks; calculate benefits and remaining limits accurately; support prior, concurrent and retrospective authorisations; produce complete, valid and traceable claims from clinical activity; support deterministic and explainable claim adjudication; allow payers to manage schemes, memberships, benefits, networks and claims; enable Government to administer targeted coverage programmes; support multiple payers without hard-coding payer-specific logic; reduce duplicate billing, fraudulent claims and administrative leakage; provide citizens with understandable explanations of what was paid and why; and maintain complete auditability from enrolment through settlement.

### 3. Guiding Principles

**3.1 Care must not be blocked by financing verification.** Coverage verification may inform registration, billing and claims, but failure to verify coverage must never prevent emergency assessment or stabilisation. Clinical urgency takes precedence over financial administration.

**3.2 Effective dating is mandatory.** Every membership, scheme, plan, benefit rule, tariff contract and provider-network relationship must have an effective-from date, an effective-to date where applicable, a version, a status, a source authority and an approval record. Historic claims must always be adjudicated against the rules that were effective on the date of service.

**3.3 Decisions must be explainable.** Every eligibility, authorisation and claims decision must return a decision status, human-readable explanation, standardised reason code, rule or contract version used, date and time of decision, deciding organisation or system, and evidence or data relied upon. No claim may be denied solely through an unexplained artificial-intelligence output.

**3.4 No payer-specific forks.** Differences between medical aid societies, insurers, Government programmes and employers must be represented through configurable products and plans, versioned rules, standard APIs, payer adapters, terminology mappings, and contract and tariff configuration. The platform must not require separate hard-coded workflows for every payer.

**3.5 One longitudinal coverage record.** Coverage must be linked to the person's VITO Health ID, while retaining payer-issued membership and policy identifiers.

**3.6 Progressive certainty.** The platform must distinguish between verified coverage, payer-reported coverage, employer-reported coverage, citizen-declared coverage, provisionally matched coverage, and unverified or disputed coverage. Unverified information may support workflow progression but must not be presented as definitively confirmed.

### 4. Scope

Coverage consists of the following functional domains: Payer Registry; Scheme and Product Management; Plan and Benefit Configuration; Member and Dependant Enrolment; Coverage Wallet; Coverage Verification; Eligibility and Benefit Inquiry; Provider Network Management; Coordination of Benefits; Referrals and Gatekeeping; Prior Authorisation; Concurrent and Retrospective Review; Patient Liability Estimation; Claims Creation and Submission; Claims Validation and Adjudication; Remittance and Explanation of Benefits; Settlement and Reconciliation; Appeals, Complaints and Disputes; Coverage Waivers and Exemptions; Fraud, Waste and Abuse Controls; Employer and Group Administration; Programme and Government Coverage Administration; Notifications and Communication; Reporting, Analytics and Regulatory Oversight; Offline and Downtime Operations.

### 5. Supported Coverage Types

The canonical coverage type enumeration must include: `SELF_PAY`, `MEDICAL_AID`, `COMMERCIAL_INSURANCE`, `EMPLOYER_SPONSORED`, `GOVERNMENT_PROGRAMME`, `SOCIAL_PROTECTION`, `DONOR_PROGRAMME`, `FACILITY_WAIVER`, `OCCUPATIONAL_INJURY`, `MOTOR_VEHICLE_ACCIDENT`, `TRAVEL_INSURANCE`, `CAPITATED_CARE`, `CONTRACTED_PACKAGE`, `EMERGENCY_PROVISIONAL`, `OTHER`. A person may have more than one active coverage record.

### 6. Primary Users and Roles

Citizen and household roles (principal member, adult/child dependant, parent/guardian, authorised caregiver, financial guarantor, employer-sponsored employee, programme beneficiary, citizen service agent with recorded authority); provider and facility roles (reception/registration, clinician, nurse, pharmacist, lab/imaging/theatre officers, billing/claims officers, cashier, social welfare/exemption officer, facility finance manager/administrator, clinical authorisation coordinator); payer roles (membership/scheme/benefits/provider-network administrators, authorisation officer, clinical reviewer, claims assessor/supervisor, fraud investigator, finance/settlement officer, complaints/appeals officer, payer administrator/auditor); national and programme roles (coverage platform administrator, government programme administrator, health-financing policy officer, regulatory oversight, national auditor, read-only M&E, terminology/integration/security administrators). All access must be authorised through TSHEPO using identity, role, organisation, facility, purpose, relationship and context.

### 7. Core End-to-End Journeys

**7.1 Citizen adds existing medical aid cover** — sign in → Coverage → Add coverage → choose payer/search → enter/scan membership, policy, card barcode/QR, national identifier → consent for verification → verification request to payer → response matched against VITO → conclusive match = Verified; uncertain = Pending verification, routed for review → citizen sees scheme, plan, principal, dependant relationship, effective dates, status, network, benefits, exclusions → full audit retained.

**7.2 Facility discovers coverage at registration** — patient identified via VITO → registration retrieves active coverage as of the encounter date → verified and declared coverage shown separately → select expected payer → eligibility check (patient, facility, provider, service category, date/time) → response gives eligibility, plan, network, referral/authorisation requirements, co-payment, deductible, remaining limits, exclusions → decision attached to encounter/account → COSTA estimates payer/patient responsibility → registration continues even when payer unavailable.

**7.3 Provider requests prior authorisation** — order/plan identifies authorisation requirement → request pre-populated from clinical record → provider confirms service, indication, urgency, facility, provider, date, documents → consent checked → submit → payer may approve/partially approve/request info/pend/deny → provider and patient notified → approved quantities/dates/facilities/conditions enforceable at adjudication → urgent-care pathway allows retrospective completion where permitted.

**7.4 Claim created from delivered care** — services completed and signed → OROS/pharmacy/lab/imaging/procedure/encounter records provide billable items → COSTA prices using applicable contract/tariff → Coverage identifies responsible payer(s) → claim draft generated → billing officer reviews exceptions → validation passes → submit → payer acknowledges → each line adjudicated → approved/denied amounts returned → patient receives EOB → approved payer liability proceeds to MUSHEX → COSTA updates account/balance → reconciliation confirms remittance and settlement match.

**7.5 Mixed coverage** — primary medical aid, secondary employer scheme, government programme for a specific service, remaining patient co-payment. Coverage applies configurable coordination-of-benefits rules and creates a transparent payment waterfall. Example: Government programme covers the eligible diagnostic; medical aid covers the remaining professional fee; employer benefit covers the medical aid co-payment; the patient pays any final non-covered amount. No amount may be claimed twice.

**7.6 Coverage unavailable during emergency care** — patient arrives in emergency → identity/coverage cannot be verified → encounter marked "Emergency – coverage pending" → treatment proceeds → provisional financial account created → verification retried automatically → once confirmed, responsible account and claim updated → changes fully auditable → no clinical action reversed because verification was delayed.

### 8. Payer Registry

A national registry of organisations that may financially cover care. Each payer record: Payer ID, legal name, trading name, organisation type, registration information, operational status, contact information, claims/authorisation/complaints/technical contacts, supported currencies, settlement arrangements, integration method, API endpoint metadata, certificates and public keys, supported transaction types, provider-network model, regulatory status, effective dates, suspension/termination details, source authority and approval information. A suspended payer must not accept new enrolments, but historic transactions must remain accessible.

### 9. Scheme, Product and Plan Management

Hierarchy: **Payer → Scheme → Product → Plan Version.** A scheme represents the broad financing arrangement (employer, medical aid, government programme, donor programme, accident compensation). A product represents a marketed or contracted package within a scheme. A plan version defines the precise rules applicable for a specific period, containing: plan code/name, coverage type, effective dates, eligibility criteria, member categories, dependant rules, waiting periods, geographic restrictions, provider-network requirements, referral rules, benefit categories, limits, exclusions, co-payments, deductibles, co-insurance, authorisation rules, tariff contract reference, claims submission deadline, appeal deadline, renewal rules, currency, ruleset version, approval authority. **Published plan versions are immutable.** Corrections must create a new version or an explicitly governed retrospective correction.

### 10. Membership and Enrolment

**10.1 Membership record:** Coverage membership ID, VITO Health ID, payer, scheme, product, plan version, payer membership number, policy number, group/employer number, principal/dependant status, relationship to principal, effective period, membership status, verification status, verification date/source, coverage priority, member category, benefit tier, card identifiers, assigned primary provider, restrictions, termination reason, supporting documents, consent record, source system, last synchronisation status.

**10.2 Membership statuses:** `DRAFT`, `DECLARED`, `PENDING_VERIFICATION`, `VERIFIED`, `ACTIVE`, `SUSPENDED`, `GRACE_PERIOD`, `EXPIRED`, `TERMINATED`, `CANCELLED`, `DISPUTED`, `MERGED`, `ENTERED_IN_ERROR`. **Verification status and membership status must remain separate.**

**10.3 Dependant management:** spouse/partner, child, adopted child, stepchild, parent, disabled adult dependant, other contractually recognised dependant. Rules must support age limits, student-status requirements, disability-based continuation, waiting periods, newborn provisional enrolment, marriage/divorce changes, dependant migration to principal, retroactive addition/termination.

### 11. Coverage Wallet in Impilo

The citizen-facing Coverage Wallet displays active coverage cards, expiring coverage, pending verification, suspended/disputed coverage, principal/dependant relationship, payer/plan, member number (masked by default), effective dates, verification badge, digital member card/QR, primary/secondary order, benefit summary, remaining benefits, co-payment/deductible, provider-network search, prior authorisations, submitted claims, EOB, outstanding responsibility, contributions/premiums, documents/correspondence, complaints/appeals. Citizens can add coverage, remove an incorrectly declared record, request verification, report an incorrect membership, change preferred primary coverage, download/present a digital card, share limited proof via a time-limited QR code, view dependants for whom they have authority, request addition of a dependant, track statuses, and ask Nompilo to explain terms. Sensitive membership identifiers are masked unless deliberately revealed.

### 12. Eligibility and Benefit Inquiry

**12.1 Eligibility request** supports person, coverage/membership, date of service, facility, provider, service category, specific procedure/medicine/investigation, diagnosis/indication (where disclosure authorised), emergency indicator, referral, authorisation reference, estimated quantity, estimated price, channel and requesting organisation.

**12.2 Eligibility response** returns one of `ELIGIBLE`, `PARTIALLY_ELIGIBLE`, `INELIGIBLE`, `PENDING`, `UNKNOWN`, `PAYER_UNAVAILABLE`, `MANUAL_REVIEW_REQUIRED`; plus confirmed member, scheme/product/plan, effective coverage period, provider-network status, benefit category, covered quantity/amount, used benefit, remaining benefit, deductible status, co-payment, co-insurance, exclusions, waiting-period status, referral requirement, authorisation requirement, service frequency restriction, age/gender rule, decision reason codes, response validity period, source/timestamp, ruleset version. Eligibility is a point-in-time decision and must not be treated as an unconditional guarantee of payment.

**12.3 Signed eligibility token.** Successful eligibility checks may produce a signed token containing eligibility decision ID, coverage ID, patient reference, facility, covered service scope, decision date, expiry time, payer, signature. The token supports offline continuation and later claim validation.

### 13. Benefits and Limits

Benefits support service category, procedure, diagnosis, medicine, laboratory test, imaging study, device, consumable, admission, bed day, professional fee, transport, rehabilitation, dental/optical, preventive services, chronic disease packages, maternal/newborn packages, mental health, telemedicine/virtual care, home/community care. Limit types: monetary, quantity, visit, day, frequency, episode, household, member, diagnosis-specific, lifetime, annual, rolling-period, no explicit limit. Limit periods: calendar year, scheme year, membership year, month, quarter, episode, pregnancy, admission, lifetime, custom range. **Benefit consumption must be reservation-aware** so that pending authorisations and submitted claims do not allow accidental overuse.

### 14. Provider Networks

Coverage supports open, preferred, restricted, tiered, referral, primary-care gatekeeper, capitated, and emergency out-of-network networks. Network records link to TUSO facility, VARAPI provider, service point, specialty, geographic area, contract, effective dates, service restrictions. Citizen and provider can search participation by facility, provider, service, specialty, location (Ndila), availability, virtual/physical. Network status must be checked as of the service date.

### 15. Coordination of Benefits

When multiple coverages apply, the system determines primary/secondary/tertiary payer, programme-before/after-insurance ordering, and patient responsibility. Coordination rules may use coverage type, contract priority, employment relationship, dependant relationship, service-specific funding, accident liability, government programme eligibility, date of service, explicit payer agreement. The platform must prevent duplicate claims, total reimbursement exceeding the allowed charge, a secondary payer charged before primary adjudication where prohibited, and claiming patient-paid amounts as unpaid payer liability. Every coordination result shows a payment waterfall.

### 16. Referrals and Gatekeeping

Plans may require a referral from a primary provider, from a defined level of care, to an in-network specialist, before imaging/procedures, or renewal after a defined period. Coverage validates referring provider, provider authority (VARAPI), facility relationship, referral date/scope/validity, permitted visits, referral status. Invalid financial referral rules must not invalidate clinically necessary emergency care.

### 17. Authorisation Management

**17.1 Types:** prior, concurrent review, extension, retrospective, emergency notification, admission, procedure, medicine, diagnostic, rehabilitation, transport, chronic-care.

**17.2 Statuses:** `DRAFT`, `SUBMITTED`, `ACKNOWLEDGED`, `IN_REVIEW`, `INFORMATION_REQUESTED`, `PARTIALLY_APPROVED`, `APPROVED`, `DENIED`, `WITHDRAWN`, `EXPIRED`, `USED`, `CANCELLED`, `APPEALED`, `OVERTURNED`.

**17.3 Data:** patient/coverage, requesting provider/facility, requested service lines, diagnosis/indication, clinical justification, urgency, proposed service date, quantity/duration, estimated price, referral reference, supporting documents, reviewer, decision, approved quantity/amount, conditions, validity dates, denial reasons, information requests, related encounters/orders/claims. Partially approved requests must clearly identify approved and rejected lines.

### 18. Patient Liability Estimation

Before non-emergency services, the platform calculates an estimate: total expected charge, contractual allowed amount, primary/secondary payer estimate, deductible, co-payment, co-insurance, non-covered items, patient responsibility, amount already paid, assumptions, expiry. Users must be told an estimate is not a final claim determination. COSTA owns price calculation; Coverage provides benefit and payer-responsibility rules.

### 19. Claims Management

**19.1 Types:** professional, facility, outpatient, inpatient, pharmacy, laboratory, imaging, dental, optical, rehabilitation, transport, capitation encounter report, citizen reimbursement, corrected, replacement, void/cancellation.

**19.2 Creation:** claims generated from signed, completed source transactions (PCT encounter, OROS order, pharmacy dispense, lab result/completion, imaging completion, procedure/theatre, ADT, telemedicine, rehabilitation, COSTA invoice/charge lines) — not manually retyped. Each line retains references to originating clinical and financial records.

**19.3 Validation:** patient identity, active coverage, provider identity/authority, facility identity/network, date/place of service, required referral, required authorisation, diagnosis/procedure compatibility, duplicate detection, quantity/frequency, benefit availability, tariff/contract, required documents, coding completeness, submission deadline, mathematical consistency, currency, replacement/void relationships. Warnings may permit submission; blocking errors must explain resolution.

**19.4 Statuses:** `DRAFT`, `READY_FOR_REVIEW`, `VALIDATION_FAILED`, `VALIDATED`, `SUBMITTED`, `ACKNOWLEDGED`, `REJECTED_AT_GATEWAY`, `PENDED`, `IN_ADJUDICATION`, `INFORMATION_REQUESTED`, `PARTIALLY_APPROVED`, `APPROVED`, `DENIED`, `PAYMENT_SCHEDULED`, `PARTIALLY_PAID`, `PAID`, `REVERSED`, `VOIDED`, `RESUBMITTED`, `APPEALED`, `CLOSED`. Claim lines have independent statuses.

**19.5 Adjudication** considers membership eligibility, benefit coverage, network status, authorisation, referral, contracted tariff, benefit limits, deductible, co-payment, co-insurance, coordination of benefits, duplicate service, clinical/coding edits, exclusions, submission timelines, previous payments, recoveries/reversals. Per line: submitted, allowed, approved, denied amounts; deductible; co-payment; co-insurance; other-payer amount; patient responsibility; reason codes; remark codes.

### 20. Explanation of Benefits and Remittance

**20.1 Citizen EOB:** facility/provider, date of service, services claimed, amount charged, amount allowed, amount paid by each payer, amount not covered, patient responsibility, denial/adjustment reasons, appeal rights, contact details. Technical codes translated to plain language.

**20.2 Provider remittance advice:** claim/line references, submitted/allowed/paid amounts, adjustments, denial reasons, withholding/recovery, payment batch, settlement reference, expected settlement date, reconciliation status.

### 21. Settlement and Reconciliation

Once approved: Coverage produces an approved liability; MUSHEX creates/receives the settlement instruction; payment associated with payer/provider/facility/claim; COSTA posts remittance against account; patient responsibility recalculated; variances routed to reconciliation. Underpayment, overpayment, duplicate-payment workflows supported. Reconciliation statuses: unmatched, partially matched, matched, overpaid, underpaid, reversed, disputed, written off, closed. Payment batches may contain multiple claims.

### 22. Appeals, Complaints and Disputes

Appeals may relate to membership, eligibility, benefit limit, authorisation, network classification, claim denial, partial payment, patient responsibility, termination/suspension, incorrect identity match. An appeal contains appealing party, decision appealed, grounds, supporting evidence, submission date, deadline, assigned reviewer, review status, outcome, reason, effective correction, notification history. Appeals must be reviewed by a suitably authorised person who was not the sole originator of the disputed decision where segregation of duties is required.

### 23. Government Programmes, Waivers and Exemptions

Targeted programmes based on configurable criteria (age, pregnancy/maternity, disability, disease programme, socioeconomic assessment, geographic location, occupational category, emergency/disaster declaration, authorised-institution referral, specific service/medicine, defined eligibility period). The platform distinguishes automatic, assessed, provisional eligibility; approved exemption; facility-level waiver; programme budget exhaustion; eligibility under review. Waivers/exemptions require authorising role, reason, scope, amount/percentage, validity period, supporting evidence, audit trail. No facility employee may approve their own waiver or an unauthorised waiver beyond delegated limits.

### 24. Employer and Group Administration

Supports employer/sponsor registration, group contract, membership roster, employee/dependant enrolment, bulk addition/termination, effective-dated employment changes, contribution status, waiting periods, member categories, branch/location, roster reconciliation, exceptions/unmatched members, audit of roster changes. Bulk imports use stage → validate → review → apply; they must not write directly into active membership tables without validation.

### 25. Fraud, Waste and Abuse Controls

Controls include duplicate claims; same patient at incompatible locations/times; claims after death; claims before birth; provider outside scope; suspended provider; unlicensed/inactive facility; impossible quantities; unbundling; excessive frequency; altered/reused documents; card-sharing indicators; unusual provider-member relationships; repeated retrospective authorisation; excessive manual overrides; claims exceeding delivered services; settlement account changes; conflict-of-interest flags. Automated detection may create risk flags, but adverse action must follow governed review. Every override records original decision, override decision, authorising user, reason, evidence, date/time.

### 26. User Experience Specifications

**26.1 Impilo** Coverage area: Overview, My coverage, Dependants, Benefits, Find covered care, Authorisations, Claims and payments, Documents, Complaints and appeals. Clear status cards, minimal jargon, next required action shown, eligibility-vs-guaranteed-payment explained, Nompilo explanations, English + local languages, screen-reader/large-text support, minimal scrolling, horizontal step flows for add-coverage and reimbursement.

**26.2 Impilo Provider** coverage banner in patient context, eligibility check, benefit inquiry, authorisation request/tracking, referral validation, patient-liability estimate, claim evidence completion, role-appropriate claim status. Clinical users not forced into finance screens for routine care.

**26.3 Facility Mode** eligibility exceptions queue, pending/expiring authorisations, claim preparation queue, validation failures, submitted/pended claims, denials requiring action, expected remittances, unmatched settlements, patient balances, waiver/exemption queue, coverage performance dashboard. Left navigation collapses on detailed work pages.

**26.4 Payer Portal** membership administration; scheme/product/plan configuration; benefit configuration; provider networks; contracts and tariffs; eligibility operations; authorisation workbench; claims workbench; clinical review; payment batches; reconciliation; appeals; fraud review; reporting; integration monitoring; user/delegation management.

**26.5 Administration Console** payer onboarding; standard transaction profiles; terminology mappings; integration certificates; ruleset approvals; programme configuration; data-quality queues; global reason codes; audit access; platform health.

### 27. Core Data Model

Entities: Payer, Scheme, CoverageProduct, PlanVersion, BenefitDefinition, BenefitLimit, CoverageMembership, CoverageRelationship, CoverageVerification, ProviderNetwork, CoverageContract, EligibilityRequest, EligibilityResponse, BenefitAccumulator, ReferralRequirement, Authorisation, AuthorisationLine, Claim, ClaimLine, AdjudicationDecision, RemittanceAdvice, ExplanationOfBenefits, Settlement, ReconciliationRecord, Appeal, Waiver, CoverageDocument, CoverageAuditEvent. All transactional records include a stable UUID, tenant/organisation context, created/updated timestamps, created/updated by, version number, source system, correlation ID, idempotency key where applicable, status, effective period where applicable, audit provenance.

### 28. Interoperability and Standards

Coverage aligns with HL7 FHIR resources: Coverage, InsurancePlan, CoverageEligibilityRequest, CoverageEligibilityResponse, Claim, ClaimResponse, ExplanationOfBenefit, Account, Invoice, PaymentNotice, PaymentReconciliation, Contract, Organization, Patient, RelatedPerson, Practitioner, PractitionerRole, HealthcareService. FHIR alignment must not prevent efficient internal domain models; mappings are versioned and tested.

### 29. Internal Integrations

VITO (person identity, principal/dependant matching, merged identities, deceased status); VARAPI (provider identity/licence/cadre/scope/restrictions); TUSO (facility identity/service points/ownership/status/network participation); TSHEPO (authentication context, authorisation, consent, delegation, audit policy); ZIBO (procedures, diagnoses, medicines, benefit categories, reason codes, mappings); BUTANO (longitudinal clinical references, patient-authorised evidence); PCT (encounters, admissions, referrals, procedures, telemedicine); OROS (orders, fulfilment evidence); COSTA (charges, tariffs, estimates, invoices, patient accounts); MUSHEX (collections, settlements, refunds, reconciliation); NDILA (location, covered-provider discovery); NHUME (authorised transport/dispatch coverage); NOMPILO (explanations, guided journeys, next-action); KHULUMA (secure notifications, payer-provider communication); RITO (complaints, feedback, quality escalation). Clinical evidence must be referenced or selectively shared — Coverage must not copy entire medical records into claims unnecessarily.

### 30. API Specification (internal REST, minimum)

```
GET    /v1/people/{healthId}/coverages
POST   /v1/coverages
GET    /v1/coverages/{coverageId}
PATCH  /v1/coverages/{coverageId}
POST   /v1/coverages/{coverageId}/verify
POST   /v1/coverages/{coverageId}/terminate
POST   /v1/eligibility-checks
GET    /v1/eligibility-checks/{eligibilityId}
POST   /v1/eligibility-checks/{eligibilityId}/refresh
GET    /v1/plans/{planId}/benefits
POST   /v1/benefit-inquiries
GET    /v1/people/{healthId}/benefit-accumulators
POST   /v1/authorisations
GET    /v1/authorisations/{authorisationId}
PATCH  /v1/authorisations/{authorisationId}
POST   /v1/authorisations/{authorisationId}/submit
POST   /v1/authorisations/{authorisationId}/decisions
POST   /v1/authorisations/{authorisationId}/appeals
POST   /v1/claims
GET    /v1/claims/{claimId}
PATCH  /v1/claims/{claimId}
POST   /v1/claims/{claimId}/validate
POST   /v1/claims/{claimId}/submit
POST   /v1/claims/{claimId}/void
POST   /v1/claims/{claimId}/replace
GET    /v1/claims/{claimId}/adjudication
GET    /v1/claims/{claimId}/explanation-of-benefits
GET    /v1/remittances/{remittanceId}
POST   /v1/appeals
GET    /v1/appeals/{appealId}
POST   /v1/appeals/{appealId}/decision
POST   /v1/payers/{payerId}/membership-imports
POST   /v1/payers/{payerId}/provider-network-imports
GET    /v1/payers/{payerId}/integration-status
```

All create and submission endpoints must support idempotency keys.

### 31. Domain Events (durable, via outbox)

`coverage.declared`, `coverage.verification-requested`, `coverage.verified`, `coverage.activated`, `coverage.suspended`, `coverage.terminated`, `eligibility.checked`, `eligibility.failed`, `benefit.reserved`, `benefit.consumed`, `benefit.released`, `authorisation.submitted`, `authorisation.information-requested`, `authorisation.approved`, `authorisation.denied`, `claim.created`, `claim.validated`, `claim.submitted`, `claim.acknowledged`, `claim.pended`, `claim.adjudicated`, `claim.denied`, `claim.approved`, `remittance.received`, `settlement.initiated`, `settlement.completed`, `settlement.reconciled`, `appeal.submitted`, `appeal.decided`, `waiver.approved`. Consumers must be idempotent.

### 32. Payer Integration Gateway

External payer connectivity via a Coverage Payer Gateway supporting real-time API, FHIR transactions, standards-based claims formats, secure file exchange, managed batch import/export, webhook callbacks, and a manual payer workbench for non-integrated payers. Each adapter maps between the canonical Coverage model and the payer's format. The gateway provides authentication/certificate management, request signing, encryption, retry/back-off, circuit breaking, duplicate detection, message correlation, dead-letter handling, replay, technical/business acknowledgements, and monitoring dashboards.

### 33. Rules Engine

Coverage business rules must be declarative, versioned, effective-dated, testable, approveable, explainable, traceable to source authority. The rules engine supports eligibility, benefit, network, referral, authorisation, coordination-of-benefits, adjudication, waiver-approval-limit, and fraud-flag rules. OPA remains responsible for access-control policy; Coverage business decisions must not be embedded invisibly inside access-control rules. Every decision records the exact rule version used.

### 34. Security, Consent and Privacy

TSHEPO-issued identity/context; role- and purpose-based access; payer data isolation; facility-level access restrictions; consent for payer verification and clinical disclosure; guardian/dependant authority; proxy access; masking of membership/financial identifiers; encryption in transit and at rest; field-level protection; immutable audit logs; segregation of duties; session/device risk controls; revocation of compromised payer integrations; break-glass logging. Break-glass access must not allow unauthorised alteration of claims or financial decisions. A payer may only receive clinical information necessary to determine the specific authorisation or claim.

### 35. Offline and Downtime Operations

Offline functionality: viewing recently synchronised coverage summaries; reading signed eligibility tokens; recording citizen-declared coverage; creating provisional eligibility checks; capturing authorisation requests; creating claim drafts; capturing documents; continuing emergency and routine care; queueing transactions for later submission. Offline data shows last synchronisation time, source, verification status, expiry, and whether a response is provisional. On reconnection: transactions replayed with idempotency keys; server decisions replace provisional decisions; conflicts surfaced rather than silently overwritten; accounts recalculated; users notified of material changes.

### 36. Notifications

Coverage verified/failed; approaching expiry; membership suspended/terminated; dependant added/removed; authorisation submitted/info-requested/approved/denied; claim submitted/pended/approved/partially-approved/denied; payment made; patient balance changed; appeal deadline approaching; appeal outcome; required document missing. Channels: in-app, push, SMS, email, secure KHULUMA. Notifications must not reveal sensitive clinical information on insecure lock screens or shared devices.

### 37. Reporting and Analytics

Membership (active members, principals/dependants, coverage by type/geography, expiring, verification success/failure, unmatched records); Eligibility (checks, response times, payer availability, ineligible/unknown, offline, network exceptions); Authorisations (by service, approval/partial/denial, turnaround, info-request frequency, retrospective); Claims (submitted, value, approval/denial, first-pass, pended, resubmissions, denial reasons, days service→submission→adjudication→payment); Financial (payer/patient liability, settled amounts, outstanding remittances, under/overpayments, reconciliation rate, waivers, recoveries); Integrity (duplicates, suspicious activity, overrides, claims against suspended providers, out-of-network claims, claims after termination). Dashboards enforce role-appropriate aggregation and PII suppression.

### 38. Non-Functional Requirements

**38.1 Availability** — core local Coverage ≥ 99.9%; payer unavailability degrades gracefully; clinical services usable when Coverage unavailable. **38.2 Performance** — local coverage lookup p95 < 2s; cached eligibility p95 < 2s; real-time external eligibility p95 < 5s (excl. payer-declared delays); claim validation p95 < 5s ordinary; wallet open p95 < 3s. **38.3 Scale (design targets)** — 25M person-linked coverage records; 100M historic eligibility transactions; 100M claim lines; 500 eligibility req/s burst; large roster imports without blocking. **38.4 Reliability** — at-least-once event delivery, idempotent consumers, transactional outbox, automatic retries, dead-letter queues, reconciliation jobs, no silent data loss. **38.5 Auditability** — every material change records actor, organisation, role, action, previous/new value, reason, date/time, device/client, correlation ID, source system. **38.6 Accessibility/localisation** — responsive web/mobile, keyboard navigation, screen-reader compatibility, accessible status indicators, configurable language packs, currency/date localisation, plain-language explanations.

### 39. Required Edge Cases

Newborn without final Health ID; incorrectly linked dependant; duplicate coverage records; retroactive activation/termination; plan change during admission; coverage expiry during admission; emergency care without identification; payer API unavailable; member number changed by payer; citizen and payer names differ; identity merged in VITO; multiple active primary coverages; dependant reaches age limit; provider suspended after service before claim; facility out of network during admission; authorisation approved for only part of a request; claim after authorisation expiry; claim corrected after partial payment; payment without matching remittance; remittance without settlement; overpayment/recovery; refund to patient after payer payment; payer reverses an earlier approval; currency mismatch; claim after recorded death; service delivered offline; benefit exhausted by another pending claim; citizen disputes disclosure to payer; scheme rules changed retrospectively; employer roster conflicts with payer roster; manual waiver exceeds delegated authority.

### 40. Acceptance Criteria

Coverage is complete only when each of the following journeys works with real persistence and real state transitions: citizen adds and verifies medical aid; principal views authorised dependants; facility discovers coverage from a VITO-linked patient; receptionist performs a real eligibility check; eligibility identifies network/benefit/co-payment/authorisation requirements; a failed external payer connection produces a recoverable provisional workflow; an emergency encounter continues without verified coverage; a provider submits a prior-authorisation request with evidence; a payer requests additional information; the provider responds without creating a duplicate authorisation; a payer partially approves an authorisation; COSTA produces a patient-liability estimate using Coverage rules; a claim is generated from real delivered services; validation detects missing referral, duplicate lines, inactive coverage; a valid claim is submitted and acknowledged; claim lines receive independent adjudication outcomes; a citizen receives an understandable EOB; MUSHEX settles an approved claim; COSTA posts the remittance and recalculates balance; a settlement is reconciled against claim liabilities; a denied claim is corrected and resubmitted; a citizen or provider submits an appeal; an appeal overturns a decision and triggers financial recalculation; multiple coverages are coordinated without duplicate reimbursement; a Government programme covers only the eligible service category; a facility waiver is approved within delegated authority; an unauthorised waiver is blocked; a coverage record is updated offline and synchronised safely; every material action is visible in audit history; web and mobile reach equivalent outcomes.

**No acceptance journey may depend on** mock payer responses in production paths, in-memory-only persistence, hard-coded member records, hard-coded plan rules, fake success notifications, UI-only status changes, manually edited database records, unimplemented buttons, or placeholder pages.

### 41. Recommended Service Architecture

Coverage is delivered initially as a cohesive `coverage-service` with internal modules for payers, plans, membership, eligibility, benefits, authorisations, claims, adjudication, remittance, appeals. Supporting components: `coverage-payer-gateway`, `coverage-rules-engine`, `coverage-integration-worker`, `coverage-reconciliation-worker`. Claims adjudication may later be separated into an independent service when transaction scale or organisational separation justifies it — not prematurely fragmented. Coverage owns coverage and claims decisions; it references, rather than duplicates, authoritative identities, clinical records, invoices and payments owned by other platform services.

### 42. Delivery Waves

**Wave 1 — Coverage Foundation:** payer registry; schemes/products/plan versions; membership and dependants; Coverage Wallet; coverage verification; eligibility and benefit inquiry; basic provider-network checks; offline signed eligibility; audit and notifications.

**Wave 2 — Authorisations and Estimates:** referrals and gatekeeping; prior authorisation; concurrent and retrospective review; benefit reservations; patient-liability estimation; provider and payer workbenches.

**Wave 3 — Claims and Remittance:** claim generation; validation; electronic submission; adjudication; EOB; remittance; settlement integration; reconciliation; corrected and void claims.

**Wave 4 — Advanced Financing Operations:** coordination of benefits; employer and group administration; government programme administration; appeals; advanced fraud controls; capitation; cross-payer analytics; more extensive external payer integration.

Each wave must remain production-capable and must not introduce temporary fake workflows.

### 43. Definition of Done

Coverage is operationally complete when: citizens can see and manage real coverage; providers can verify benefits in the care workflow; coverage failures do not block necessary care; payers can configure real plans without code changes; benefits and limits are effective-dated and traceable; authorisations operate end to end; claims originate from real clinical and financial activity; claims are adjudicated using explainable rules; approved liabilities settle through MUSHEX; accounts reconcile in COSTA; citizens receive clear explanations; appeals and corrections are supported; offline transactions synchronise safely; security, consent and organisation boundaries are enforced; every decision is auditable; web and mobile have functional parity; automated tests cover happy paths, exceptions, downtime and security boundaries; and there are no mocks, stubs, dead controls or decorative-only screens in production functionality.

### 44. Canonical Product Statement

Coverage gives every authorised participant a shared, trustworthy answer to four questions: **Is this person covered? What care is covered? Who should pay what? What happened to the authorisation, claim and payment?** It must connect entitlement, care delivery, billing and payment without allowing financing administration to become a barrier to clinically necessary care.

---

## Coverage Functional Expansion — Provider Operations, Administration, Claims Switching, Adjudication, Reporting and Performance Monitoring

*(The functional-expansion specification — provider revenue-cycle operations, provider/payer/national administration, the Claims Switch, adjudication modes and pipeline, claims lifecycle, reporting, performance monitoring, dashboards, expanded APIs and acceptance criteria — is retained in full as the companion canonical document. Its key contracts are summarised here; the exhaustive text is the source of the W2–W4 epics in `docs/roadmaps/ruvimbo-epics.md`.)*

**Five operating environments:** Citizen Coverage; Provider Coverage Operations; Payer Administration; Claims Switch; National Administration and Oversight.

**Claims Switch (distinct from adjudication):** switching routes/validates/tracks transactions between providers, payers, Government programmes and financing entities; adjudication decides payability. A payer may use the Impilo adjudication engine or its own external platform. The switch processes each transaction through defined stages (received → authenticated → integrity-verified → duplicate-checked → structurally validated → envelope-validated → sender/receiver resolved → code-translated → format-transformed → routed → technical ack → business ack → response received → response transformed → response delivered → closed/exception-routed), with store-and-forward, circuit-breaking, dead-letter handling, replay, and full control numbers. **Technical acceptance must never be displayed as claim approval.**

**Adjudication modes:** fully automated; automated with exception review; manual; external payer; hybrid; pre-payment; post-payment; batch; real-time point-of-service. The pipeline runs ingestion → identity/coverage/provider/facility validation → duplicate detection → coding validation → eligibility/network/referral/authorisation determination → benefit matching → clinical/coding edits → tariff determination → allowed-amount → benefit-limit → deductible → co-payment → co-insurance → coordination of benefits → patient-liability → fraud/integrity screening → exception routing → final decision → claim response → remittance preparation → settlement instruction. **Each claim line is adjudicated independently**; overall claim status is derived from its lines and must not conceal mixed outcomes. A contractual adjustment must not automatically become patient liability. Benefit accumulators distinguish used/reserved/submitted/approved/paid/reversed/remaining with concurrency control. Every decision records rule/contract/benefit versions and calculation inputs so a historic adjudication can be reconstructed exactly.

**Expanded lifecycle:** `DRAFT → VALIDATED → SUBMITTED → SWITCH_ACCEPTED → PAYER_ACCEPTED → IN_ADJUDICATION → PENDED/INFORMATION_REQUESTED → APPROVED/PARTIALLY_APPROVED/DENIED → PAYMENT_SCHEDULED → PAID → RECONCILED → CLOSED`, with alternatives `REJECTED_AT_SWITCH`, `REJECTED_BY_PAYER`, `CORRECTED`, `REPLACED`, `VOID_REQUESTED`, `VOIDED`, `REVERSED`, `APPEALED`, `OVERTURNED`, `RECOVERY_INITIATED`, `RECOVERED`. Each transition records actor/system, organisation, date/time, reason, source transaction, correlation reference, previous and new status.

**Revised canonical definition:** Coverage is the national platform capability that manages membership and entitlement, provider eligibility verification, benefits, provider networks, referrals, authorisations, patient-liability estimation, claim creation, claims switching, claims adjudication, remittance, settlement, reconciliation, appeals, administration, reporting and performance monitoring — supporting citizens, providers, facilities, payers, employers, Government programmes and national oversight through one interoperable, auditable and standards-aligned financing workflow.

**RUVIMBO — Health Financing, Coverage and Claims.**
