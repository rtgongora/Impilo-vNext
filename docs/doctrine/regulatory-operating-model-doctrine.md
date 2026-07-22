# Regulatory Operating Model — Foundational Doctrine

> **Canonical summary**: Zimbabwe's health regulatory estate — the Health Professions Authority
> and the eight professional councils beneath the Health Professions Act — operates on Impilo as
> nine distinct regulatory organisations sharing one governed substrate. Organisation identity
> lives in the organization registry; professional regulation truth lives in Varapi; premises
> regulation truth lives in Tuso; complaint voice lives in Rito; learning evidence lives in
> Fundo; money lives in COSTA/MusheX. Every regulatory process exists simultaneously as an
> applicant journey, a regulator workflow, a communication thread, an auditable case record and
> a measurable reporting process. Access flows only from a verified regulatory appointment —
> never from facility attachment — and no council ever sees another council's desk.

> **Short doctrine line**: Nine regulators, one substrate: identity in org-registry,
> professional truth in Varapi, premises truth in Tuso, voice in Rito — and no council sees
> another's desk.

This doctrine inherits from [`health-os-doctrine.md`](health-os-doctrine.md) (one person anchor,
many roles, governed runtime), [`service-relationship-doctrine.md`](service-relationship-doctrine.md)
(the Tuso/Varapi/Vashandi/TSHEPO backbone and integration patterns) and
[`provider-reputation-doctrine.md`](provider-reputation-doctrine.md) (the regulation firewall).
Where this document is silent, those doctrines govern.

The nine organisations: **Health Professions Authority (HPA)** and the professional councils —
**Medical & Dental Practitioners Council of Zimbabwe (MDPCZ)**, **Nurses Council of Zimbabwe
(NCZ)**, **Pharmacists Council of Zimbabwe (PCZ)**, **Allied Health Practitioners Council of
Zimbabwe (AHPCZ)**, **Environmental Health Practitioners Council of Zimbabwe (EHPCZ)**,
**Medical Rehabilitation Practitioners Council of Zimbabwe (MRPCZ)**, **Medical Laboratory &
Clinical Scientists Council of Zimbabwe (MLCSCZ)**, **Natural Therapists Council of Zimbabwe
(NTCZ)**.

---

## 1. One organisational truth

1.1. The organization registry (`organization-registry-service`) SHALL be the sole system of
record for the *existence and identity* of every regulatory organisation: legal name,
organisation type (`PUBLIC_HEALTH_AUTHORITY`, `STATUTORY_REGULATOR`, `PROFESSIONAL_COUNCIL`),
status, contact and lifecycle.

1.2. `varapi.councils` SHALL be the *professional-regulation configuration profile* of an
org-registry organisation — registration-number patterns, workflow parameters, CPD rules, fee
schedule references — bound to it by a mandatory unique foreign key. A council SHALL NOT exist
in Varapi without its org-registry anchor.

1.3. An organisation attribute (name, type, status) SHALL never be written in Varapi or Tuso; a
regulation attribute (register pattern, workflow configuration) SHALL never be written in the
organization registry. Tuso SHALL NOT grow an organisation table.

---

## 2. Appointment before access

2.1. Regulatory personnel are NOT facility-attached. A council officer's or HPA officer's
working context SHALL derive exclusively from a **regulatory appointment**: a governed,
time-bounded, verified record binding a person to a regulatory organisation in a named role
with a jurisdiction.

2.2. Every regulatory session SHALL resolve the full chain:
**person → verified identity → regulatory organisation → appointment → role → jurisdiction →
permitted workspace → permitted records and actions.** No link in the chain may be skipped,
assumed or inherited.

2.3. Appointments live in the organization registry with a **closed role vocabulary**
(Registrar, Deputy Registrar, Registration Officer, Inspector, CPD Officer, Investigations
Officer, Committee Member, Finance Officer, Council CEO, HPA Oversight Officer, …). Free-text
role titles SHALL NOT grant access. The operational session mirror (workforce assignment,
work-context token) is derived from the appointment and torn down when it ends or is revoked.

2.4. A person MAY hold several capacities simultaneously (professional, practice owner, council
officer, committee member). After authentication, TSHEPO SHALL present only the person's
verified contexts; entering one capacity SHALL NOT leak the permissions of another. An
applicant with an active case SHALL never see internal regulator workspaces by virtue of the
case.

---

## 3. Equal-depth generic model

3.1. All nine organisations run on ONE generic model — organisation, appointment, register,
application, request-for-information, correspondence, committee, hearing, decision, restriction,
fee, certificate, report. Differences between councils are **parameters** (registers,
professions, committees, application types, renewal cycles, CPD rules, fee references) recorded
in each council's regulatory configuration — never code forks, never bespoke per-council
services or duplicate schemas.

3.2. All nine organisations SHALL be seeded together and served at equal depth. No council is a
second-class tenant of another's model.

---

## 4. Strict cross-council isolation

4.1. A council's records, queues, dashboards and correspondence SHALL be visible only to
holders of appointments at that council. A Nurses Council officer SHALL NOT see Pharmacists
Council queues; an inspector SHALL NOT see finance administration unless separately appointed.

4.2. Committee members SHALL see only cases formally docketed to their committee — membership
of a committee grants docket-scoped visibility, not council-wide access.

4.3. Isolation SHALL be enforced at the policy decision point (organisation and jurisdiction
dimensions on policy rules), proven in SHADOW before ENFORCE, and continuously asserted by the
conformance pack.

4.4. **Dual capacity and self-regulation.** A regulator is frequently also a registrant — a
council member who practises. The same person holds coexisting capacities on one Health-ID
anchor (clinical provider + regulatory appointment); a regulatory session SHALL NOT require,
inherit or expose clinical facility/provider context, nor a clinical session inherit regulatory
authority (per §2.4). Because a regulator may be a registrant of the same council, a person
SHALL NOT act in a regulatory capacity on their own record — no self-review, self-decision,
self-moderation, self-adjudication, or committee sitting on a matter whose subject is that same
person. Such an action SHALL be refused (RECUSAL_REQUIRED) and audited; the tie is detected on
the person Health-ID, never the provider identifier alone.

---

## 5. Two-sided delivery

5.1. Every regulatory process SHALL ship as BOTH an applicant journey and a regulator workflow
in the same delivery slice. A back-office queue with no applicant surface, or an applicant form
with no processing workflow, is an incomplete process and SHALL NOT be declared done.

5.2. The three participating sides are: regulatory personnel performing statutory duties;
professionals, prospective registrants, practice owners and managers applying, responding and
maintaining compliance; and the public and institutions verifying and reporting where
disclosure is lawful.

---

## 6. Progressive applications

6.1. Applications SHALL be progressive, never one giant form: explore requirements →
eligibility → start → save draft → complete sections → invite contributors → submit →
completeness review → correction/clarification loops → technical review → inspection or
committee review → decision → payment where applicable → certificate or licence → ongoing
compliance → renewal.

6.2. Every application SHALL always show the applicant: current stage; completed and
outstanding requirements; the responsible regulatory office; requests awaiting the applicant;
submission and response dates; statutory or service-standard timelines; inspection status; fees
and payment status; decisions and conditions; the next required action; and the complete
correspondence history. An applicant SHALL never face an unexplained status — Nompilo explains
what a stage means, who holds the case and what may happen next.

6.3. A practice manager MAY prepare and administer an application, but legally reserved
declarations and submissions SHALL be completed only by the appropriate owner, director or
practitioner-in-charge.

6.4. A prospective practice owner SHALL be able to create a pre-licensing practice profile
before any facility exists; the canonical facility record is created only on approval.

---

## 7. Correspondence duality

7.1. Regulatory case correspondence is two-way and lives in the case: the regulator requests
specific information, returns sections, annotates evidence, schedules inspections and hearings,
issues findings and decisions; the applicant responds from the same case and sees whether each
issue is accepted, rejected or outstanding.

7.2. Internal assessments SHALL be recorded separately from applicant-visible feedback — one
case, two visibility lanes — and internal notes SHALL never serialize into applicant-facing
payloads. Case history is immutable.

7.3. Khuluma delivers notices through the person's permitted channels; Nompilo guides the
required action on entry.

---

## 8. The regulation firewall (inherited)

8.1. Per [`provider-reputation-doctrine.md`](provider-reputation-doctrine.md): no rating,
complaint, feedback item or quality signal SHALL automatically modify a licence, scope,
registration, employment or access. A Rito case becomes a disciplinary proceeding only through
an explicit, recorded human decision by an appointed officer of the competent council; the
proceeding then runs in the owning registry (Varapi for professionals, Tuso for premises) with
its own state machine, and only its formal determination alters register truth.

---

## 9. The CPD seam

9.1. Fundo (learning-service) is the system of record for learning evidence: courses,
completions, learning records, approved providers. Varapi is the system of record for the
**regulatory interpretation** of CPD: cycles, required units, adjudicated compliance status,
exemptions and deferrals, per council.

9.2. Completions flow from Fundo as governed candidates; self-declared external CPD enters with
evidence; the council verifies, samples and adjudicates. Renewal decisions SHALL consume only
Varapi's adjudicated CPD status — never raw Fundo records.

---

## 10. Five report classes

10.1. The reporting model SHALL distinguish, with distinct owners and audiences:
**operational dashboards** (queues, pending applications, inspections, renewals, complaints,
appeals, overdue actions — the working officer's view); **management dashboards** (workload and
turnaround by officer, team, region, profession, facility type, process stage — the registrar's
and CEO's view); **statutory reports** (submission-ready returns required by law); **public-
interest reports** (lawful public disclosure); and **cross-regulator oversight reports** (HPA
and Ministry consolidated intelligence).

10.2. Operational and management dashboards are stateless compositions over the owning
services' queues; statutory, public-interest and oversight reports are governed definitions in
the reporting service, each resolving against a named system-of-record read model. Drill-down
from national summaries to individual cases SHALL respect the viewer's mandate at every level.

---

## 11. Oversight without intrusion

11.1. HPA supervises the councils; it SHALL NOT casually inherit their operational permissions.
HPA oversight staff see consolidated, aggregate regulatory intelligence across all nine
organisations by default, and row-level council records only through a defined oversight,
audit, appellate or delegated role — or an explicit, per-case escalation grant.

11.2. Oversight is implemented as read grants and escalation referrals — never data copies,
never standing operational workspace access.

---

## 12. Audit visibility

12.1. Every review, change, approval, rejection and **access** of a regulatory record SHALL be
attributable: who, in which appointment and capacity, when, on which record, with what outcome.
Audit reports over this trail are a first-class report class available to registrars, councils
and HPA oversight within their mandates.

---

## 13. Design Consequences

- The organization registry gains a regulatory appointment model (closed role vocabulary +
  jurisdiction) reusing its claim/invitation/verification rails; `varapi.council_users` is
  deprecated in its favour.
- Vashandi mirrors appointments as organisation-scoped assignments (no facility); the
  WORK_CONTEXT token carries the organisation; the session lane accepts org-only contexts.
- TSHEPO-authz gains organisation and jurisdiction policy dimensions (and later a
  committee-docket dimension), landed as seed migrations + rego, SHADOW → ENFORCE.
- The experience shell gains a `regulatory_work` operational mode and ONE parameterised council
  workspace serving all nine organisations.
- Varapi gains first-class registers, restrictions and good-standing rows; a disciplinary case
  state machine; application sections, RFI and dual-visibility correspondence; a fee/payment
  rail mirroring Tuso's V021 pattern. Tuso gains a pre-licensing establishment case. Rito gains
  the report-unregistered-practice public intake branch.
- Fee amounts follow the SI 78/2017 discipline: seeded structurally, amounts NULL and
  `PENDING_REGULATOR_APPROVAL` until governed configuration — never invented.
- Full design: [`../design/regulatory-operating-model/`](../design/regulatory-operating-model/README.md).
