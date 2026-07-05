# Impilo vNext Identity, Access and Trust Governance Doctrine

> **Short label**: Access Governance Doctrine.

> **Canonical summary**: Impilo vNext governs *who exists*, *who may act*, and *what they
> may do right now* as three separate, independently evidenced questions. Identity is
> anchored on the personal Health ID; professional capacity is a linked, governed Provider
> ID; facilities and organizations carry per-source, per-status legitimacy rather than a
> single boolean of "valid"; trust is expressed in granular, honest verification blocks;
> and access is activated — never assumed — through policy evaluation at Tshepo/OPA using
> registry truth plus live operational context. The platform bootstraps from multiple
> imperfect national sources through three governed channels (regulatory, public workforce,
> delegated organization onboarding) and reconciles them through adjudication, not through
> silent exclusion or silent trust.

> **Short doctrine line**: One person anchor, one governed professional identity, many
> evidence sources, honest statuses, granular trust, activated — never implied — access.

This doctrine extends the foundational
[Impilo Health OS doctrine](health-os-doctrine.md) (Identity Doctrine §9, Progressive
Identity Assurance §10, Regulated Professional Participation §14, Access Control §20,
Audit §22). Implementing services and lease boundaries for delivery Wave 1 are recorded in
[`docs/registry/iatg-wave1-leases.md`](../registry/iatg-wave1-leases.md).

---

## 1. Platform Origin Authority

Every governed environment needs a lawful first mover. Impilo vNext defines the
**Platform Origin Administrator** as that first mover — and deliberately constrains it.

The Platform Origin Administrator is an **origin key, not a daily operator**. It exists to
bring a country operation into being and then step back. It is not a super-user, not a
help-desk escalation path, not a data administrator, and never a participant in clinical,
financial, or workforce transactions.

Its powers are **narrow and enumerated**:

1. **Create a country operation** — instantiate the governance container for a national
   deployment (see §2).
2. **Appoint national administrators** — designate the initial National Administrators for
   a country operation, recording the appointment source and authority.
3. **Define the legal framework binding** — record which national legal instruments,
   regulators, and governing authorities the country operation answers to.
4. **Configure trusted channels** — enable and parameterize the governed onboarding
   channels (Channels A, B, C — §§6–8) for that country operation.
5. **Hand over** — transfer operational governance to the appointed national
   administration, after which routine authority rests with the country operation.

Constraints on the origin authority:

- **Heavily audited**: every origin-level action produces an immutable audit event with
  actor, justification, and effect; origin activity is expected to be rare, and volume
  itself is an audit signal.
- **Two-person approval for sensitive actions**: creating a country operation, appointing
  or removing a national administrator, changing the legal framework binding, and
  reconfiguring trusted channels each require a second, independent origin-level approver.
  No single key can perform them alone.
- **No transactional reach**: the origin authority holds no clinical, financial,
  registry-write, or person-data privileges inside a country operation.
- **Handover is expected**: a country operation still governed day-to-day by the origin
  authority is in an exceptional state and must be flagged as such.

## 2. Country / National Administration

A **Country Operation** is the country-specific governance container inside which all
national identity, registry, trust, and access governance occurs. Nothing in Impilo vNext
is governed "globally by default"; everything actor-facing is governed within a country
operation.

Each country operation records, at minimum:

| Attribute | Meaning |
|---|---|
| Country | The nation the operation serves |
| Jurisdiction | The legal jurisdiction(s) the operation answers to |
| Governing authority | The national body with governance authority (e.g. Ministry of Health) |
| Appointment source | The instrument under which each national administrator is appointed |
| Appointment date | When the appointment took effect |
| Appointed by | The authority (origin or national) that made the appointment |
| Appointment expiry | When the appointment lapses unless renewed |
| Appointment scope | What the appointee may govern (whole operation or a bounded domain) |
| Audit | Full, immutable trail of appointments, changes, and revocations |

**National Administrators** govern within their recorded scope: they operate the trusted
channels, oversee registry adjudication, appoint subordinate governance roles, and answer
to the governing authority. Their appointments are time-bound, scoped, sourced, and
auditable — a national administrator whose appointment has expired holds no authority,
regardless of technical credentials.

## 3. Personal Health ID as Primary Identity

The **person comes first**. Every natural person participating in Impilo — patient,
provider, administrator, community health worker, wellness participant — is anchored by
exactly one **Health ID**, as established in the foundational Identity Doctrine.

- The Health ID establishes *personhood and continuity*. It is the longitudinal anchor
  across time, roles, facilities, and life stages.
- All other actor identifiers — Provider ID, Staff ID, caregiver linkages — are **linked,
  governed identities attached to the person**, never parallel persons.
- A provider is therefore *a person with a linked governed professional identity*, not a
  separate identity class. Signing in always happens as the person; professional capacity
  is activated on top (§10).
- Identity existence is separated from identity assurance: a Health ID may exist at low
  assurance and gain assurance progressively (Health OS doctrine §10) without ever being
  reissued.

## 4. Provider ID as Secondary Professional Identity

The **Provider ID** is the platform's governed professional identity, attached to a Health
ID. It is:

- **Secondary**: it never exists without a person anchor, and it never substitutes for one.
- **Confidential and platform-issued**: the Provider ID is an internal platform identifier.
  It is not a council registration number, not an EC (employment) number, and not a
  national ID number.

**Doctrine: external numbers are matching evidence, never the identity.** Council
registration numbers, EC numbers, HSC numbers, and national identity numbers are used to
*match* a person to registry records and to *evidence* professional and employment claims.
They are never used as the platform identity itself, never embedded as the primary key of
professional participation, and never treated as interchangeable with the Provider ID.

### Provider registry status vocabulary

The provider registry states its truth honestly, using this controlled vocabulary:

| Status | Meaning |
|---|---|
| `registered-active` | Council register confirms current, active registration |
| `expired` | Registration existed but has lapsed |
| `suspended` | Council has suspended the registration |
| `deregistered` | Council has removed the registration |
| `deceased` | Registry source records the person as deceased |
| `pending` | Claim or preload exists; verification not yet concluded |
| `conflict` | Sources disagree and the record awaits adjudication (§11) |
| `matched-employment-only` | Matched to public workforce/employment records (Channel B) but not to a council register |
| `matched-both` | Matched to both the council register and employment records |

A status is a **statement of what the sources say**, not a permission. What a provider may
*do* under any status is a separate policy decision (§10).

## 5. Facility ID and Organization ID

### 5.1 The honest facility registry

Impilo rejects the fiction of a single "valid facility" flag. Real facilities exist in
overlapping, imperfect registers: the legal licensing register may be stale, the
ministry's operational list may include unlicensed-but-operating public facilities, and
the platform itself accrues operational knowledge. The facility registry therefore records
**per-source legitimacy**: each facility carries one legitimacy assertion *per source*,
each with its own status.

**Legitimacy sources:**

| Source | Nature of truth |
|---|---|
| `HPA-legal` | Health Professions Authority licensing — *legal* legitimacy |
| `Ministry-operational` | Ministry of Health operational recognition — *operational* legitimacy |
| `Platform-operational` | Platform-observed operational standing — *runtime* legitimacy |

**Per-source statuses:**

| Status | Meaning |
|---|---|
| `registered-current` | Source confirms current registration/recognition |
| `expired` | Source registration has lapsed |
| `suspended` | Source has suspended the facility |
| `known-not-compliant` | Source affirmatively records non-compliance |
| `pending` | Verification against this source is in progress |
| `not-found` | The facility is not present in this source |
| `government-operational-exception` | Operating under the government sovereign operational exception (§7) — honest about the absence of ordinary registration |

> **Doctrine sentence**: the facility registry must always be able to say — *"this
> facility exists, this is who says it exists, this is its status, this is whether the
> platform allows it to operate, and why."*

Whether the platform allows a facility to operate is a **platform decision recorded with
its reason**, derived from — but not identical to — the per-source statuses. A rural
clinic with `HPA-legal: expired` and `Ministry-operational: registered-current` may be
allowed to operate under a recorded exception; a private facility with the same pattern
may not (§7).

### 5.2 The organization model

An **Organization ID** identifies a legal or institutional entity that operates
facilities, employs or engages providers, or otherwise participates institutionally.
Organizations are distinct from facilities: one organization may operate many facilities;
a facility is always operated by exactly one accountable organization.

**Organization types** include (extensible by governed vocabulary, not by convention):

- Government health authority (national, provincial, district)
- Public facility operator (hospital, clinic network)
- Mission / faith-based health organization
- Private hospital group
- Private practice (solo or group)
- Pharmacy operator / chain
- Laboratory / diagnostics network
- Non-governmental organization / implementing partner
- Funder / medical aid / insurer
- Training institution
- Supplier / marketplace vendor
- Community-based organization
- Regulator / professional council (as a participating entity)

**Organization onboarding journey** (the canonical shape; Channel C in §8 governs the
delegated variant):

1. **Registration** — the organization's legal identity, type, and jurisdiction are
   recorded.
2. **Authorized representative verification** — the natural persons (Health IDs) who may
   act for the organization are verified and recorded, with scope and expiry.
3. **Evidence and legitimacy** — legal registration, licensure, and authority evidence is
   attached and verified against Channels A/B or national authority records.
4. **Affiliation establishment** — facilities operated, providers engaged, and programme
   participation are linked as governed affiliations, each independently evidenced.
5. **Activation with honest status** — the organization becomes operational with its
   per-source legitimacy visible; unresolved claims remain `pending` or `conflict`, never
   silently approved.
6. **Ongoing governance** — representative changes, licensure lapses, and affiliation
   changes flow through the same evidence-and-adjudication discipline.

## 6. Regulatory Authority Channel (Channel A)

**Channel A** ingests truth from the statutory regulators: the professional councils
(medical, nursing, pharmacy, allied) and the Health Professions Authority (HPA).

- **Nature of truth**: *legal register truth* — who is lawfully registered to practice,
  and which facilities are lawfully licensed.
- **Preload with pre-assigned IDs**: council and HPA registers are preloaded into the
  platform ahead of individual sign-up. Preloaded provider records receive
  **pre-assigned Provider IDs** in `pending` linkage state; when the person later claims
  their professional identity, the claim is matched to the preloaded record rather than
  creating a duplicate.
- **Honest statuses, not exclusion**: a person or facility found `expired`, `suspended`,
  or `not-found` in Channel A is **recorded as such, not erased**. Absence or
  irregularity in the legal register is a status to display and adjudicate — it is not
  grounds to pretend the actor does not exist, because pretending breaks the operational
  truth of Channels B and C.
- Channel A data can only be corrected at its source or through adjudication (§11); it is
  never edited to "make onboarding work."

## 7. Public Workforce and Operational Channel (Channel B)

**Channel B** ingests truth from the public employer: MoHCC human-resources records,
Health Services Commission (HSC) establishment records, EC (employment) numbers, and
posting/assignment data.

- **Nature of truth**: *employment truth* — who is actually employed, in what post, at
  which facility, since when.
- Channel B evidence upgrades trust (§9) and grounds operational context (§10); it does
  not by itself establish council registration.

### The government sovereign operational exception

Government health services must run even where the legal register is imperfect. A public
facility operating under ministry authority, or a public employee posted by the HSC, may
be granted a **government operational exception**: the platform records the ordinary
source status honestly (`expired`, `not-found`, …) *and* records a governed exception —
with its authority, reason, scope, and expiry — that permits operation.

**Doctrine: the sovereign operational exception belongs to government alone. Private
actors never receive it automatically.** A private facility or private practitioner with
lapsed registration gets an honest status and an adjudication path — not an exception.
Any extension of exception-like treatment beyond government requires explicit national
administration decision, recorded through adjudication (§11).

## 8. Delegated Organization Onboarding Channel (Channel C)

**Channel C** lets verified organizations onboard their own facilities, providers, and
staff affiliations — **same tooling, different trust**.

- Channel C uses the same registration, claim, and evidence tooling as Channels A/B, but
  everything arriving through it is a **claim**, not truth.
- **Claims must be verified** against Channel A (legal register), Channel B (employment
  records), or a national authority source before they confer trust. An unverified
  Channel C claim remains `pending` and confers only the minimal participation appropriate
  to a pending state.
- The onboarding organization's own legitimacy (§5.2) and its authorized representatives'
  verification bound what it may claim at all.
- Channel C never bypasses the honest-status discipline: a claim that contradicts A/B
  becomes a `conflict` for adjudication, not an override.

## 9. Trust Levels and Verification

Impilo expresses verification as **granular trust blocks**, not one vague "verified" flag.
Each block is independently evidenced, independently displayed, and independently usable
in policy. This is deliberately better than a single flag: it tells every consumer *what*
is verified, *by which source*, and *what remains open*.

| Trust block | Facts within the block |
|---|---|
| **Identity Trust** | Health ID verified · biographic details verified · contact details verified |
| **Professional Trust** | Council registration confirmed · qualification verified · cadre confirmed · scope of practice confirmed · licence current |
| **Employment Trust** | EC number matched · HSC/MoHCC record matched · posting verified · supervisor confirmed |
| **Operational Trust** | Facility assignment active · workspace active · shift context active |

Rules:

- Each fact carries its evidence source (Channel A/B/C or national authority), timestamp,
  and verifying actor or process.
- Blocks degrade independently: a lapsed licence lowers Professional Trust without
  touching Employment Trust; an ended posting lowers Employment and Operational Trust
  without touching Identity Trust.
- Trust blocks are **descriptive inputs to policy**, never permissions in themselves.
- The composed "trust profile" shown to users and administrators must present all four
  blocks honestly, including what is *not* verified.

## 10. Access Activation and Context Resolution

**The critical separation** — these are four different questions, answered by different
authorities, and no earlier answer implies a later one:

> A Provider ID **exists**
> ≠ the provider **can practice**
> ≠ the provider **can practice at this facility**
> ≠ the provider **can perform this action in this workspace today**.

- **Identity comes from registries**: who the person is (Vito/Health ID), what their
  professional identity and registry statuses are (Varapi), where they are employed and
  posted (workforce records via Vashandi and workforce governance), and which facilities
  and organizations exist with what legitimacy (Tuso, organization registry).
- **Permissions come from policy**: Tshepo/OPA evaluates every access decision using
  registry truth from Varapi + Vashandi + Tuso **plus live operational context**
  (facility assignment, workspace, shift, purpose of use, subject relationship, consent,
  assurance level, workflow state — Health OS doctrine §20).
- **A Provider ID request is never a shortcut into privileges.** Requesting, claiming, or
  even holding a Provider ID grants nothing by itself; every capability is activated by
  policy at the moment of use, in context.
- **Recovery, not reissue**: a lost, locked, or compromised Provider ID is *recovered*
  onto the same governed professional identity through verified recovery. New Provider
  IDs are never issued to the same person to bypass status, history, or restrictions —
  the professional record is continuous for life.

## 11. Adjudication, Audit and Revocation

Where sources conflict, evidence is contested, or exceptional access is sought, the answer
is **adjudication** — a governed, recorded human decision — not silent auto-resolution.

**Actors**: the requesting/affected person or organization representative; the reviewing
officer (national administration or delegated governance role); the deciding authority
(scoped per case class); observers with audit rights (regulators, governing authority).

**Case states**:

`Draft → Submitted → Triaged → Under Review → Evidence Requested ⇄ Under Review →
Decision Proposed → Decided → Effective → Closed`

with `Appealed` (re-entering review from `Decided`), `Withdrawn` (from any pre-decision
state), and `Revoked` (a decided outcome later rescinded through a new decision) as
governed exits. Every transition records who, when, and why.

**The decision record** — every adjudication outcome captures, at minimum:

| Field | Content |
|---|---|
| Decision | The outcome (approve, approve with restrictions, reject, revoke, grant exception) |
| Reason | The recorded justification and the evidence relied upon |
| Authority | Who decided, under what appointment and scope |
| Effective | When the decision takes effect |
| Expiry | When it lapses unless renewed (exceptions and restrictions are time-bound by default) |
| Permissions | What the decision enables, expressed as policy inputs |
| Restrictions | What the decision constrains or conditions |
| Audit | The immutable trail of the case and the decision |

**Revocation** is a first-class decision, not a data deletion: revoking an appointment,
exception, provider linkage, or organization affiliation produces a new decision record
and honest status change; history is never rewritten.

---

## Closing doctrine paragraph

> Impilo vNext shall not depend on a single perfect register to bootstrap the health
> workforce and service-delivery estate. The platform shall support multi-channel
> onboarding using regulatory truth, government operational truth, and delegated
> organizational claims. Every provider, facility, and organization shall receive a
> confidential platform digital identity, while external identifiers such as council
> numbers, EC numbers, and facility registration numbers shall be treated as matching
> evidence rather than the platform identity itself. Personal Health ID remains the
> primary human identity; Provider ID is a secondary professional identity linked to
> the person and activated according to verified professional, employment, facility,
> and workspace context.

The elaborated operating consequences of this paragraph: distinct evidence streams are
recorded with their own source, status, and date, none silently overriding another;
where sources agree, trust is composed; where sources are silent, status is honest;
where sources conflict, the conflict is adjudicated by accountable national authority
and the decision is recorded with its reason, scope, and expiry. No actor is invented
to make onboarding convenient, no actor is erased because a register is stale, and no
external number — council, employment, or national — is ever mistaken for the
platform's own governed identity.

---

## Implementing services

| Capability in this doctrine | Owning service | Notes |
|---|---|---|
| Platform origin authority, country operations, national administrator appointments (§§1–2); HSC/public-workforce employment records (§7); adjudication decision records (§11) | `workforce-governance-service` | Governance container and decision system of record |
| Provider trust blocks, channel typing, provider registry status vocabulary (§4, §9), verification attempts | `varapi-service` | Provider registry system of record |
| Facility per-source legitimacy (§5.1) | `tuso-service` | Facility registry system of record |
| Organizations, authorized representatives, affiliations, Channel C claims (§5.2, §8) | `organization-registry-service` (port 8153) | New Wave-1 service |
| Identity assurance levels (LOA) (§3, §9 Identity Trust) | `identity-assurance-service` | Assurance policy holder |
| Health ID / person anchor (§3) | `vito-service` | Person identity system of record |
| Adjudication state machines (§11 case states) | `workflow-service` | State machine engine; decisions themselves recorded in workforce governance |
| Trust profile composition (§9 display) | `experience-bff` | Composition only — explicitly **not** a system of record |

Ownership context: [`docs/registry/service-ownership-matrix.md`](../registry/service-ownership-matrix.md) ·
[`docs/registry/forbidden-responsibilities-map.md`](../registry/forbidden-responsibilities-map.md) ·
Wave-1 delivery boundaries: [`docs/registry/iatg-wave1-leases.md`](../registry/iatg-wave1-leases.md) ·
End-to-end walkthrough: [`docs/demo/iatg-wave1-demo-script.md`](../demo/iatg-wave1-demo-script.md).
