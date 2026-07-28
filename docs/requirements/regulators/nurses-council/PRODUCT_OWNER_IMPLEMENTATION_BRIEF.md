# Nurses Council of Zimbabwe — Product Owner Implementation Brief

| Field | Value |
|---|---|
| **Source status** | `BRD_DERIVED_PO_APPROVED` |
| **Original BRD repository copy** | **Pending** — see [`SOURCE_RECONCILIATION_PENDING.md`](./SOURCE_RECONCILIATION_PENDING.md) |
| **Product Owner approval** | **Confirmed** |
| **Implementation status** | **Authorised to proceed** |
| **Traceability reconciliation** | **Required** when the original BRD becomes available in the repository |
| Council | Nurses Council of Zimbabwe (`NCZ`, org `a5000000-0000-4000-8000-000000000003`) |
| Created | 2026-07-27 |

## What this document is

The Product Owner read and analysed the Nurses Council BRD in full and issued the functional and
architectural specification recorded here. **This brief is the approved interpretation of that BRD and
the current implementation authority.**

The distinction matters and is deliberate:

- the **original BRD** is the eventual evidentiary source;
- **this brief** is the approved functional and architectural specification derived from it;
- **hash verification and line-by-line BRD citation are deferred** until the original file is copied
  onto the repository host;
- **implementation is not deferred.**

The absence of the original BRD on the repository host is a documentation-provenance gap, not a
product-delivery gate.

## Requirement classification

Every requirement traced to implementation carries one of three classifications. They are not
interchangeable, and a `PLATFORM_DERIVED` item must never be presented as a Nurses Council BRD
requirement.

### `BRD_DERIVED_PO_APPROVED`

Nurses Council requirements supplied by the Product Owner after reviewing the BRD. These are not
speculative — they are BRD-derived requirements communicated through the approved brief:

Student Register · Provisional Register · Main Register · Registered Nurse-Led Health Institutions
Register · student registration · provisional registration · practising certificates and renewals ·
qualifications · examination applications, scheduling, entry numbers, slips and result publication ·
supervised-practice tracking · migration from provisional to the Main Register · invoice and payment
handling · multiple currencies · penalties · refunds and reversals · carry-forward balances · the
stated Nurses Council reports · provider, student, professional and institution self-service
counterparts.

### `PLATFORM_DERIVED`

Architecture required to implement those requirements safely and extensibly across all eight councils:

Regulatory Configuration Registry · configuration-pack schemas · versioning · approval and activation ·
immutable case bindings · tenancy · role-aware workspace generation · reusable form, workflow, register
and certificate engines · provider/regulator mirrored workflow contracts · audit and configuration
governance.

### `POLICY_CONFIRMATION_REQUIRED`

Where neither the BRD nor this brief provides enough precision to *activate* a rule. **Build the
configuration seam; do not invent or activate the missing policy.** Recorded in
[`COUNCIL_DECISIONS_REQUIRED.md`](./COUNCIL_DECISIONS_REQUIRED.md):

exact fee amounts · penalty formulae · final student index-number format · interpretation of
examination failure limits · supervised-practice interruption and part-time calculation · detailed
institution requirements · appeal rules · approval thresholds · certificate wording.

---

## 1. Architectural model — four layers

**Layer 1 — Shared Regulatory Platform.** Implemented once, reusable by HPA and all eight councils:
identity and Health ID; regulator tenancy and workspace isolation; staff appointments and role-based
access; applications and cases; configurable forms; requirements and evidence; review queues;
decisions and approvals; registers; registrations and licences; qualifications and scopes; payments
and receipting; examinations where enabled; supervised-practice tracking where enabled; institution
and practice regulation where enabled; correspondence and applicant tasks; certificates and
verification; reports and dashboards; bulk data intake; audit and records management.

**Layer 2 — Council Configuration Pack.** Each council configured through versioned definitions, not
source forks: professions and cadres; register types; registration and licence categories;
application types; workflow stages; role templates; documentary requirements; eligibility rules; fees
and penalties; qualification types; examination requirements; supervised-practice requirements;
certificates; reports; public-verification fields; terminology; notification templates.

**Layer 3 — Council-Specific Extensions.** Optional capabilities a council enables. For NCZ: student
indexing; student, provisional and main registers; licensure examinations; entry numbers and slips;
supervised-practice tracking; migration provisional→main; multiple nursing qualifications; registered
nurse-led institutions; practising certificates; renewal penalties; multi-currency receipting; refunds,
reversals and carry-forward balances. **Extensions use shared engines — never a separate NCZ
application.**

**Layer 4 — Applicant and Provider Experience.** Every regulatory process has a corresponding external
journey. The governing pattern:

> **One regulatory case, two policy-controlled views.** The regulator sees the operational case; the
> applicant sees the self-service case. Both anchor to the same case, tasks, submissions,
> correspondence and decisions. Internal notes and restricted regulatory information stay hidden.

---

## 2. The mirrored-workflow contract *(the most important implementation discipline)*

> Every regulator-side action must have an applicant/provider-facing projection, an applicant task, an
> institution-facing projection, a public projection, or an **explicit policy reason** why it is
> internal only.

For every workflow stage define: regulator action · applicant-visible status · applicant task if action
is required · notification · allowed response · deadline · escalation · next stage · audit event.

| Regulator action | Applicant/provider counterpart |
|---|---|
| Create application type | Discover service, check eligibility, start application |
| Configure requirements | Personalised checklist and acceptable evidence |
| Review documents | Upload, replace, respond to document queries |
| Raise clarification | Receive task, understand question, respond |
| Generate invoice | View invoice, pay, download receipt |
| Schedule examination | Session, venue, entry number, downloadable slip |
| Approve results | Released result and attempt history |
| Generate student index | Index number and Student Register status |
| Issue provisional licence | Download licence, see conditions |
| Track supervised practice | Record placements, supervisors, periods, evidence |
| Review migration eligibility | Progress towards Main Register eligibility |
| Renew practising certificate | Expiry, fee, penalties, renewal status |
| Register institution | Create profile, complete application, receive findings |
| Request corrections | Correct **only returned sections** and resubmit |
| Make decision | Outcome, reasons, next steps |
| Reconcile balances | Charges, payments, credits, outstanding amounts |

Internal notes, deliberations, fraud indicators and protected material remain regulator-only. **This
requirement is not satisfied by generic status text or a single upload screen.** Never show an
applicant an unexplained "In Review": show current stage, completed steps, outstanding actions,
responsible office, last update, next expected event, response deadline, payment and decision status.

---

## 3. Identity and access

Every person resolves through a Health ID. **No standalone council usernames.**

`Health ID → verified person → regulator appointment or applicant relationship → organisation → role →
jurisdiction → workspace → case and action permissions.`

Supports founding regulator onboarding, regulator-issued invitations, personnel access requests,
appointment verification, substantive vs acting appointments, start/end dates, delegated authority,
automatic expiry, suspension, revocation, access review, and separation of business ownership,
security administration and operational roles.

**A Nurses Council employee must not require a facility assignment to work in the council workspace.**
Users see only workspaces relevant to their role.

---

## 4. Nurses Council configuration pack

**Registers (4):** Student · Provisional · Main · Registered Nurse-Led Health Institutions.

**Process families:** student registration · provisional registration · main registration · practising-
certificate issuance and renewal · restoration after lapse · qualification management · additional
qualification registration · examination application, payment, scheduling, entry-number generation,
slip generation, result processing/approval/publication · supervised-practice tracking · migration
provisional→main · nurse-led institution registration, renewal and amendment · invoicing · payment
processing · debt settlement · penalties · refunds · reversals · carry-forward balances · certificate
issuance · online verification.

**Configurable initial rules (BRD-derived; configured, never buried in code):** supporting documents
mandatory for student registration; student index fee must be paid; student index numbers unique;
repeated examination failure may cause discontinuation; provisional-registration fees mandatory;
licensure examination compulsory where configured; three years of supervised work before migration to
the Main Register *(subject to confirmed council calculation rules)*; renewal fees must be paid;
penalties may accrue after expiry; practising certificates issued after successful renewal; one nurse
may hold multiple qualifications; examination fees paid before examination access; entry numbers
system-generated; approved results published online; payments in configured local or foreign
currencies; debt settled per configured allocation rules; excess payments may create carry-forward
balances; approved payments may be reversed or refunded.

**Role separation the navigation must prove:** a finance officer does not see examination-mark
processing; an examination officer does not see refunds unless separately authorised; a council member
sees assigned papers, decisions or dashboards — not the whole operational system.

**Internal workspaces:** Nurses Council Home · Registrar and Executive · Student Registration ·
Provisional Registration · Main Register and Licensing · Examination Management · Qualifications ·
Supervised Practice · Institutions · Finance and Receipting · Regulatory Data Intake · Reports and
Dashboards · Configuration · Administration and Audit.

**Self-service personas:** student nurse · provisional registrant · registered nurse · examination
candidate · nurse-led institution owner · authorised practice manager · responsible nurse ·
institutional contributor · member of the public.

---

## 5. Regulatory Configuration Registry (`PLATFORM_DERIVED`)

Three layers, and the governing rule:

> **Code owns the configuration language and execution engines. The regulator owns approved runtime
> definitions. Each regulatory case owns an immutable reference to the exact versions that governed
> it.**

**Code owns:** configuration schemas, supported definition types, validators, workflow engine,
form-rendering engine, rules-expression engine, fee and penalty calculation interfaces,
certificate-rendering engine, report-definition contract, approval and activation lifecycle,
compatibility and migration logic. **Code must not contain NCZ fee amounts, form questions, workflow
stages or certificate wording as hard-coded business logic.**

**The Registry owns** activated council definitions, scoped by regulatory organisation, pack, definition
type, key, version, effective period, lifecycle status and approval record. Definition types: council
profile and branding · capability enablement · professions and cadres · registers · application types ·
forms · evidence requirements · workflows · eligibility and validation rules · fee schedules · penalty
policies · numbering policies · examinations · supervised-practice rules · qualifications · institution
categories · roles and workspaces · certificate templates · correspondence templates · notification
templates · public-disclosure policies · dashboards · reports · service standards.

**Source-controlled packs remain** for bootstrap, development, validation, reviewable diffs, testing,
disaster recovery and environment promotion (`config/regulatory/councils/nurses-council/…`). Deployment
imports or reconciles a pack into the Registry; after bootstrap, **regulator-approved runtime versions
are authoritative**, and an approved release is exportable back to a signed portable pack. Git must not
be the only operating interface.

**Lifecycle:** `Draft → In Review → Approved → Scheduled → Active → Retired`, plus Rejected, Superseded,
Withdrawn. **No one edits an Active version in place** — a change creates a new version. Activation
records author, business owner, reviewer, approver, approval reason, effective date, superseded
version, configuration diff, impact analysis and audit events. **Four-eyes** for fees, penalties,
register-entry rules and regulatory decision workflows.

**Releases** group an internally consistent set of definition versions. Before activation validate that
all referenced definitions exist; workflow stages reference valid forms and permissions; fees reference
valid services and currencies; certificates reference valid fields; report fields exist; **applicant
projections are defined**; no unresolved references remain; the release is compatible with the engine.

**Cases pin their configuration.** Each case records the release id and the versions of application
type, each form schema, workflow, evidence requirements, eligibility rules, numbering policy, fee
schedule, penalty policy, certificate template and public-disclosure policy — with a content hash where
needed. Pinning rules by event: application definition at creation; form schema at first instance;
workflow at submission into formal processing (drafts may be offered an explicit audited migration;
**submitted cases never silently jump**); evidence requirements at start or authorised addition (**never
imposed retroactively without recorded basis and applicant-visible explanation**); fee schedule at
chargeable event or invoice creation; penalty policy at liability assessment; numbering policy at
reservation or issuance; certificate template and data snapshot at issuance. Reports may use the
current definition but every generated report records definition version, generation time, parameters,
data cut-off, requesting user and output hash; statutory submitted reports become immutable artefacts.

**Runtime consumption:** varapi and tuso consume activated definitions through a shared client; caches
are scoped by regulator and release, invalidated on activation events; **every command records the
definition version used**; processing **fails closed** when a required definition cannot be resolved.
Council configuration is not copied into service-local mutable tables except as explicitly provenanced
read-model caches.

**Configuration changes declare provider impact.** The activation preview shows both regulator-side and
applicant-side changes. **No release activates where an applicant-facing action has no corresponding
usable self-service projection.**

---

## 6. Shared service integration

Vito (identity/Health ID) · Tshepo (authentication context, appointments, access) · Varapi
(professional regulatory records) · Tuso (institutions and practice relationships) · Costa (invoices,
balances) · Mushex (payment orchestration, reconciliation) · Fundo (CPD and learning) · Khuluma
(notifications and correspondence) · Nompilo (contextual guidance) · Zibo (terminology) · Rito
(complaints/quality signals where later enabled) · Ndila (location) · document-service (evidence and
certificates).

**Do not rebuild these where an authoritative shared service exists.** E-learning is out of BRD scope:
integrate with Fundo, do not embed a learning-management system in the council workspace. CPD keeps an
extensible seam — Fundo evidences, **the council adjudicates and remains authoritative** on whether CPD
requirements are satisfied. NCZ CPD rules are not activated until confirmed.

---

## 7. Security and ownership

The Nurses Council owns and operates its workspace: personnel appointments, roles, workflows, forms,
fee schedules, certificates, reports, queues, applicant content. **National platform administrators
must not receive routine access to Nurses Council operational records.** HPA and Ministry personnel see
only explicitly authorised oversight projections, statutory reports or aggregate dashboards.

Council tenant isolation · least privilege · separation of duties · four-eyes for high-risk actions ·
strong authentication · audit logs · document-access controls · export controls · break-glass ·
time-limited delegations · access reviews · appointment expiry · anomaly monitoring.

Enforce tenant, role, jurisdiction, case and field-level access **server-side**. Front-end hiding is not
security.

---

## 8. Definition of incomplete

The implementation is incomplete if it only creates NCZ screens; rules are hard-coded in shared
services; applicants cannot complete processes online; provider and institution users cannot respond to
regulator requests; the regulator cannot configure or operate its processes; reports are static;
payments are mocked; examination results are not persisted; register entries are not effective-dated;
audit trails are missing; other councils would require copied code; navigation exposes irrelevant
workspaces; or internal staff access depends on facility assignment.

The final outcome must prove **both**: that NCZ can conduct its end-to-end registration, examination,
licensing, institution and finance processes within Impilo; **and** that the same platform can support
the remaining councils through configuration packs and bounded extensions rather than duplicated
products.

---

## 9. Interim traceability identifiers

Until the original BRD lands, requirements are traced against this brief using stable interim ids.
Reconciliation to the original processes, `FR-001`…`FR-021`, reports and registers is required when the
BRD becomes available; **that reconciliation may refine traceability but should not require rebuilding
sound functionality.**

| Interim id | Scope | Classification |
|---|---|---|
| `PO-NCZ-STUDENT-REGISTRATION` | Student registration → Student Register admission | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-PROVISIONAL-REGISTRATION` | Provisional registration and licence | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-MAIN-REGISTER` | Main Register, practising certificates, renewal, penalties | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-EXAMINATIONS` | Examination lifecycle through result publication | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-SUPERVISED-PRACTICE` | Supervised practice and migration readiness | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-INSTITUTIONS` | Nurse-led institution registration lifecycle | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-FINANCE` | Invoicing, payment, currencies, penalties, refunds, reversals, carry-forward | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-REPORTING` | The stated Nurses Council reports and dashboards | `BRD_DERIVED_PO_APPROVED` |
| `PO-NCZ-SELF-SERVICE` | Student, professional, institution and public counterparts | `BRD_DERIVED_PO_APPROVED` |
| `PLATFORM-CONFIG-REGISTRY` | Regulatory Configuration Registry, packs, lifecycle, activation | `PLATFORM_DERIVED` |
| `PLATFORM-CASE-VERSION-PINNING` | Immutable case-to-configuration bindings | `PLATFORM_DERIVED` |

Implementation statuses used in [`traceability-matrix.csv`](./traceability-matrix.csv):
`IMPLEMENTED_VERIFIED` · `IMPLEMENTED_PARTIAL` · `EXISTING_REUSABLE` · `NOT_IMPLEMENTED` ·
`BLOCKED_POLICY_DECISION` · `OUT_OF_SCOPE_CONFIRMED` · `SUPERSEDED_WITH_JUSTIFICATION` ·
`NOT_APPLICABLE_WITH_JUSTIFICATION`.

**`IMPLEMENTED_VERIFIED` requires a persisted, authorised, end-to-end journey with automated
evidence.** A route, DTO, schema, feature flag or static UI is not implementation.
