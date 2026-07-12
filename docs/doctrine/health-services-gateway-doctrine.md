# National Health Services Gateway — Foundational Doctrine

> **Canonical summary**: The National Health Services Gateway is the citizen-facing
> operating mode of the Impilo Health Operating System's unified experience shell. The
> public website and vNext operate as **one** citizen experience: an intent-led front
> door through which any person can discover, understand, and begin any health service —
> care, personal health, health information, finding and verifying services, health cover
> and payments, applications and licensing, feedback and complaints, health products and
> suppliers, and emergency help — with trust, identity, and authority requested
> progressively, only when the action being performed genuinely requires them. The
> website helps people understand, discover, and begin; vNext decides, protects,
> orchestrates, and completes.

> **Short doctrine line**: One front door, many intents; trust rises with the action,
> never restarts the journey; care before coverage; help before identity.

This doctrine refines the Impilo foundational doctrine
([`health-os-doctrine.md`](health-os-doctrine.md)) for the citizen-facing gateway. It
supersedes nothing; §11 states precisely what it inherits and refines.

---

## 1. One Citizen Experience, Three Doors

The public entry experience SHALL present exactly three doors:

```text
[Get Health Services]
Care, records, health information, feedback and applications

[Log in for Work]
For health workers, facilities, regulators and programme teams

[Learn About Impilo]
Programme information, resources, training and updates
```

The public website and vNext are **two coordinated layers of one national digital health
experience**, not two products. The website explains, provides trusted knowledge, helps
people discover and verify, and begins journeys. vNext assesses intent and required
trust, permits public access where risk is low, requests authentication only when
necessary, and delivers the complete governed workflow.

The current physical split — a static public-website deployable owning brochure paths and
`ui/one-ui-shell` owning application paths behind one host — is an engineering fact, not
a product boundary. Per health-os-doctrine §2.0, these MUST be documented and operated as
**one experience behind one browser origin** per environment. No citizen journey may dead-end
at the seam between them: every website service description SHALL link into the
corresponding gateway intent, and vNext SHALL be able to return users to public guidance
after completing a service.

---

## 2. Intent-Led Gateway Doctrine

Choosing **[Get Health Services]** SHALL open a single plain-language question:

> **How can we help you today?**

answered through **nine intent pillars**. Intent — what the person is trying to do — is a
first-class primitive of the gateway, not a URL. The first page stays simple (a small
number of large starting choices); deeper options reveal after the person chooses.

| # | Intent pillar | Website layer (discover / understand / begin) | vNext layer (authenticate / transact / complete) | Owning services (internal) |
|---|---|---|---|---|
| 1 | **Get care** | Explain services, find care, start a request | Telemedicine, appointments, referrals, follow-up, home-based care | PCT, booking, referral, rtc-gateway, Khuluma |
| 2 | **My health** | Explain Health ID, records, consent | Records, results, prescriptions, consent, dependants, care tracking | VITO, BUTANO, mvumo, experience-bff citizen surfaces |
| 3 | **Health information** | Trusted public health knowledge, prevention, outbreak notices, FAQs | Personalised education, reminders, saved content | guidance-service, clinical-knowledge-platform, Fundo |
| 4 | **Find or verify a service** | Facility/service search, professional and facility licence verification, opening hours, accessibility | Personalised discovery, saved providers | TUSO, VARAPI, Indawo, Ndila, search-service |
| 5 | **Health cover & payments** | Explain and compare cover, estimate costs, explain assistance | Enrolment, benefits, preauthorisation, claims, bills, receipts, waivers | coverage-service, COSTA, MusheX, mushe-wallet |
| 6 | **Applications & licensing** | Requirements, categories, guidance, fee estimates | Applications, documents, payment, tracking, renewal, CPD, adjudication | VARAPI, TUSO (HPA regulatory), credential-verification |
| 7 | **Feedback & complaints** | Explain routes, anonymous options | Case intake, reference tracking, escalation, resolution | Rito, patient-safety |
| 8 | **Find health products & suppliers** | Pharmacy/product/supplier discovery and verification | Prescription fulfilment, ordering, delivery, institutional procurement | Msika, msika-flow, Dura, OROS, Nhume |
| 9 | **Emergency help** | Immediate numbers and guidance, always visible | Request assistance, triage, dispatch, tracking, handover | Daidzai, Nhume, Ndila, PCT, Khuluma |

Doctrinal consequences:

1. Every citizen-facing capability SHALL attach to exactly one primary intent pillar.
2. A pillar entry that is not yet operational SHALL present an honest state (what exists,
   what is coming, where to go instead) — never a fabricated or dead surface.
3. The gateway MAY offer a gentle guided route ("What do you need help with?" / "Who is
   this for?") that narrows options by intent and beneficiary, never by internal
   organisational structure.

---

## 3. Public Naming Doctrine

Internal service and module names — TSHEPO, VITO, TUSO, VARAPI, Rito, Msika, Daidzai,
Nhume, COSTA, MusheX, BUTANO, and all others — SHALL NOT appear on citizen-facing
surfaces, in citizen-facing payloads, or in public URLs. Citizens see intent labels
("Find a pharmacy", "Verify a health professional", "Pay a health bill"), never the
platform map.

The internal↔public name translation is a governed vocabulary owned by the experience
layer, maintained as a single dictionary, and enforceable by automated checks on public
routes and public response payloads.

---

## 4. Progressive Trust Ladder Doctrine

> Ask for no more identity or authentication than the service genuinely requires, but
> step up trust smoothly when privacy, clinical risk, legal accountability, or financial
> consequence increases.

vNext SHALL NOT behave as one locked door labelled "Health ID required." Trust is
**progressive and contextual**, organised as six rungs:

| Rung | Name | What the platform knows | Suitable services (illustrative) |
|---|---|---|---|
| **R0** | Public | No identity | Health information, facility/service search, public verification, regulatory requirements, public alerts, emergency guidance |
| **R1** | Reachable | Verified phone or email | Enquiries, saved drafts, feedback tracking, reminders, callback for assistance |
| **R2** | Person-verified | Confirmed identity / linked Health ID | Personal appointments, referrals, application submission, personalised services |
| **R3** | Strongly authenticated | Health ID plus step-up (MFA or equivalent) | Health records, results, prescriptions, consent management, payments |
| **R4** | Authorised relationship | Identity plus verified role, delegation, or organisational authority | Caregiver/guardian access, professional applications, facility representation |
| **R5** | High-assurance transaction | Strong identity, authority, purpose, and auditable confirmation | Licence decisions, clinical signing, sensitive disclosure, controlled prescribing, regulatory adjudication |

These rungs are internal decisions made by TSHEPO, Keycloak, identity-assurance, and the
relevant registries. They SHALL never surface to citizens as jargon.

### 4.1 The three laws

1. **Evaluate the action, not merely the user.** The trust decision considers what is
   being attempted; whether personal health information is involved and how sensitive;
   whether legal, clinical, financial, or regulatory consequences arise; whether the
   person acts for themselves or another; the verification state of any claimed role or
   relationship; device, session, and authentication strength; and whether the activity
   can lawfully be anonymous or pseudonymous. The same person legitimately uses different
   rungs within one journey.
2. **Step up at the point of need; never restart the journey.** Stronger authentication
   happens exactly when it becomes necessary, in place.
3. **Journey context survives every trust transition.** The intended service, completed
   fields, uploaded documents, return route, language, accessibility preferences, and
   referral or campaign context SHALL be preserved across sign-in, account creation,
   verification, and step-up. Authenticating and landing on a generic dashboard with the
   original intent forgotten is a doctrine violation.

### 4.2 Canonical cross-walk

This section canonicalizes the ladder previously mapped in
[`docs/audits/citizen-zero-to-one/02-trust-ladder.md`](../audits/citizen-zero-to-one/02-trust-ladder.md)
(which remains the implementation cross-walk of record, alongside
[`03-login-assurance-matrix.md`](../audits/citizen-zero-to-one/03-login-assurance-matrix.md)
for login methods):

| Gateway rung | CZO ladder | AssuranceLevel (identity-assurance SoR) | Vito states | Enforcement point |
|---|---|---|---|---|
| R0 Public | L0 Public/Guest | — (pre-auth) | — | Public web shell + governed public API lanes |
| R1 Reachable | L1 Account-only, **refined**: account whose contact channel is verified | LOA1 + contact-verified attestation | (none) / `DRAFT`, `UNVERIFIED`/`SELF_ASSERTED` | BFF auth + assurance policy |
| R2 Person-verified | L2–L3 Temporary→Verified Health ID | LOA2–LOA3 | `PROVISIONAL`→`ACTIVE`, `PROVIDER_CAPTURED`→`VERIFIED` | PolicyEngine `min_loa` on effective LOA (max of ACR level and `X-Assurance-Level`) |
| R3 Strongly authenticated | L4 High-assurance session | LOA2+ **plus completed step-up** | `VERIFIED`/`ACTIVE` | TSHEPO step-up engine bound to sensitive actions |
| R4 Authorised relationship | L5 Delegated/Assisted (and professional/organisational authority) | delegate's LOA ≥ relationship assurance floor | relationship object (mvumo), not an identity state | PolicyEngine delegation evaluation on `X-Subject-ID`; VARAPI/TUSO authority verification |
| R5 High-assurance transaction | — (transaction property, not an identity state) | LOA3–LOA4 + step-up + purpose + obligations | `ACTIVE`/`VERIFIED` | PolicyEngine obligations, dual-control, break-glass review |

**Reachable (R1) is the one net-new rung**: the estate has no verified-contact identity
tier today. Identity-assurance remains the assurance system of record; R1 SHALL be
expressed as an attestation there, not as a parallel identity model.

### 4.3 Ladder invariants

- A temporary or low-assurance identity MUST be useful enough for care but never powerful
  enough to expose sensitive personal health records (the CZO golden rule, enforced as
  `min_loa` / `account_assurance_required` policy conditions).
- Identity **existence** is separate from identity **assurance** (health-os §10), and both
  are separate from **authority to act** (for another person or an organisation) and from
  **payment responsibility** (§6).
- Rung upgrades are monotonic, reviewed where doctrine requires (dual control for LOA
  raises), and always auditable.
- Emergency access is never blocked by rung (§7).

---

## 5. Persistent Optional Authentication Doctrine

Authentication is **optional everywhere, mandatory only where risk requires it**.

1. The gateway SHALL always provide clear, persistent **Sign in** and **Create account**
   options (desktop header, mobile navigation, relevant service pages) — regardless of
   whether the current activity requires authentication. Use "Sign in" and "Create
   account", not "Sign up".
2. A person MAY browse health information, search and verify services, review regulatory
   requirements, and use other eligible public services without signing in. A person who
   prefers to sign in immediately SHALL be able to, from any page, and then receives a
   personalised, continuous experience (saved preferences, drafts, tracking,
   notifications, cross-device continuity).
3. Account creation SHALL NOT demand the highest assurance level. It begins at R1
   (verified contact); a Health ID is linked, created, or recovered only when a service
   requires verified personal identity; stronger authentication only when the action
   requires it; professional/organisational authority verified separately from personal
   identity.
4. Public content remains visible to signed-in users. Authentication enhances the
   experience; it never replaces or restricts the public layer.
5. Service entry points SHOULD quietly reinforce the choice:
   "Continue without signing in" / "Sign in for a personalised experience" — with the
   anonymous option remaining available wherever the activity genuinely permits it.

This refines the graduated trust and friction table of health-os-doctrine §8 with the
citizen-gateway rows R0–R5 (§4).

---

## 6. Health Cover & Payments Doctrine (NHI-Ready Financing Rails)

Financial access to care is a **first-class pillar** of the gateway, not an
administrative island. Citizens SHALL be able to move from "I need care" to "how will I
pay for it?" inside one journey: compare and obtain cover, check benefits and
preauthorisation, view intelligible bills, pay through multiple routes, track claims and
reimbursements, and request financial assistance.

### 6.1 The four-way separation

```text
Identity answers:   Who is this person?            → VITO (Health ID)
Entitlement answers: What benefits are they due?    → coverage-service
Authority answers:  Who may act or administer?      → TSHEPO (+ mvumo, VARAPI, TUSO)
Payment answers:    Who settles this service?       → COSTA / MusheX
```

A Health ID SHALL NOT imply cover membership. Absence of a linked Health ID SHALL NOT
block emergency care, public information, eligibility checking, or beginning enrolment.

### 6.2 NHI readiness

The rails SHALL be built now with the policy model configurable:

- Benefit packages, tariffs, contribution categories, and purchasing methods
  (fee-for-service, case-based, capitation, bundled, performance-linked, mixed) are
  **configuration with versioning and effective dates**, never hard-coded policy.
- Claims SHALL derive from actual clinical events wherever possible — no recapture of
  the same information into a separate financing system.
- When National Health Insurance is introduced it becomes **another configured payer and
  coverage programme** alongside medical aid, private insurance, employer cover,
  programme funding, subsidies, exemptions, and vouchers — not a separate digital island.
- Coverage history survives changes of employer, province, or scheme.

### 6.3 Safeguards (SHALL NOT clauses)

1. No denial or delay of emergency care because digital eligibility cannot be confirmed.
2. No public display of financial vulnerability, exemption, or waiver status at the
   point of service or anywhere else.
3. No assumption that inability to pay means ineligibility for care.
4. No disclosure of detailed diagnoses to a payer where a service or claim code
   suffices — minimum necessary disclosure.
5. No silent claim rejection; every rejection is explained and appealable.
6. No opaque bill: every charge is understandable, showing what each payer covered and
   what balance remains.
7. No provider ability to alter coverage or entitlement.
8. No loss of policy rules into code: policy changes must not require rewriting the
   platform.

---

## 7. Persistent Emergency Help Doctrine

This extends the care-first doctrine (health-os §12) to every gateway surface.

1. The public website, all authenticated vNext experiences, and the mobile apps SHALL
   carry a persistent, clearly labelled **Emergency Help** control — visible before and
   after login, on desktop and mobile, not reliant on an icon alone, and accessible to
   users of every literacy level, language, and ability.
2. Emergency access SHALL NEVER be blocked by: absence of a Health ID; failure to sign
   in; incomplete registration; lack of medical aid or insurance; inability to pay;
   unresolved identity matching; missing consent paperwork; or incomplete demographics.
3. The emergency entry point SHALL support: immediate calling of configured emergency
   services; digital requests for urgent assistance where dispatch capability exists;
   urgent health guidance or remote triage; and reporting of public-health, disaster, or
   multiple-casualty incidents — across the configured emergency categories (medical,
   trauma, maternal/newborn, mental-health, poisoning, violence, facility transfer, and
   others), with location capture by device location, map, address, landmark, or free
   text, and communication by phone, SMS, in-app, voice, and low-bandwidth fallback.
4. Identity is **secondary to response**: a guest request proceeds immediately; verified
   contact enables callback and status updates; a signed-in citizen may voluntarily
   permit relevant emergency-profile use; access to sensitive records remains
   purpose-limited, role-based, audited, and subject to emergency-access / break-glass
   controls.
5. The workflow SHALL preserve a complete operational timeline — request, triage,
   assignment, dispatch, routing, citizen-visible status updates, receiving-facility
   notification, handover, encounter documentation, and disposition.
6. **Respond first, stabilise, document care — then determine coverage, payer, and
   billing.** Payment and eligibility resolution occur after stabilisation, jointly
   honouring §6.3(1).

---

## 8. Find Health Products & Suppliers Doctrine

Finding trusted suppliers is a first-class service for two audiences through one gateway:

1. **Citizens and patients** — pharmacies, laboratories, devices and assistive products,
   chronic-care supplies, and authorised outlets: discover and verify publicly; order,
   pay, and track with progressive authentication.
2. **Providers and facilities** — verified manufacturers, wholesalers, and distributors:
   catalogues, quotations, purchase orders, fulfilment, returns, and recalls, gated on
   verified professional **and** organisational authority (a Health ID alone never grants
   institutional procurement capability).

Doctrine requirements:

- **Medicine intelligence**: the gateway distinguishes over-the-counter,
  prescription-only, controlled, programme-supplied, facility-only, and special-handling
  products. Prescription requirements are enforced in the journey (upload or record
  linkage); restricted stock is never exposed or purchasable merely because it is
  searchable.
- **Honest availability**: stock and price information is labelled truthfully — in
  stock, low stock, available on order, availability not confirmed, last updated at — and
  never presents fake certainty.
- **Visible supplier trust**: every listing shows verification status derived from legal
  identity, registration, licensing, authorised product categories, and
  suspension/expiry state, in plain public wording ("Verified supplier", "Licence
  valid").
- **Safety linkage**: counterfeit, quality, and non-delivery reports, product recalls,
  and affected-order identification are wired into the feedback and patient-safety
  pillars, not treated as commerce exhaust.

---

## 9. Nompilo as Trust-Escalation Mediator

Nompilo is the human face of progressive trust: the trust infrastructure decides; Nompilo
makes the decision understandable, reassuring, and completable. This refines
[`NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md`](NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md).

Every meaningful trust escalation SHALL be mediated, never announced as a bare
"authentication required". Nompilo explains, in plain language calibrated to the actual
risk:

1. **Why** the escalation is needed (what is being protected);
2. **What level** of verification is required (no more than the action needs);
3. **What happens next** (progress saved; the person returns to the exact step);
4. **How to get help** (create account, recover Health ID, reset credentials, resend a
   code, alternative verification, assisted access, staffed helpdesk with a reference
   number).

Requirements:

- Options SHALL include **"Continue without signing in"** wherever the activity genuinely
  permits anonymous or public access.
- Assistance is real, not a FAQ pointer: account/Health-ID recovery, OTP resend and
  alternatives, contact-change resolution, duplicate-record identification, assisted
  digital access, voice and local-language guidance, and helpdesk escalation that does
  not force starting over.
- Nompilo explains the reason and required action but SHALL NOT expose security
  internals (risk scores, device fingerprints, detection rules).
- Nompilo never overrides provider judgement and remains auditable (standing doctrine).

---

## 10. Mobile as Third Coordinated Surface

The mobile applications are the third coordinated experience layer — not a smaller copy
of vNext, not a wrapper around the website.

1. **Guest-first**: the landing experience offers *Continue as guest / Sign in / Create
   account*. Guests can use every R0 capability; Sign in and Create account remain
   always visible (§5).
2. **Same engine**: mobile journeys use the same vNext workflow state and data as web —
   a journey started on mobile continues on desktop and vice versa; there are no separate
   "mobile applications" creating duplicate records.
3. **Device-native trust**: PIN, biometrics, passkeys, and trusted-device recognition
   unlock an **already verified** identity; they are never treated as proof of civil
   identity. Step-up applies to sensitive actions exactly as on web.
4. **Device capabilities** (camera/document capture, QR, location, push, Bluetooth,
   voice) are consented, purpose-specific, and revocable.
5. **Offline honesty**: low-connectivity operation is essential — cached public content,
   locally preserved forms, queued submissions — with statuses that never let a person
   believe something was submitted when it is only stored locally:
   `Saved on this phone / Waiting for connection / Submitted successfully / Action required`.
6. **Handoff**: website↔app continuity via deep links and short-lived journey tokens,
   with the journey continuing on mobile web when the app is not installed — never a
   forced installation.
7. **Role-aware surfaces**: citizen, provider, and facility modes derive from
   TSHEPO-verified identity and authority, never from a self-selected dropdown.
8. **Navigation stays simple**: the mobile home answers "What do you need now?", not
   "Which of our modules would you like to open?". Emergency Help is persistently
   accessible on every citizen-facing screen, including an offline emergency screen.

---

## 11. Relationship to Existing Doctrine

This doctrine **supersedes nothing**. It inherits and refines:

| Existing doctrine | Relationship |
|---|---|
| `health-os-doctrine.md` §2.0 one orchestration surface | Inherits; §1 applies it across the website/app seam |
| `health-os-doctrine.md` §8 graduated trust & friction | Refines into the six-rung ladder (§4) and persistent optional authentication (§5) |
| `health-os-doctrine.md` §10–11 progressive assurance, temporary identity | Inherits; §4.2 supplies the canonical rung cross-walk |
| `health-os-doctrine.md` §12–13 emergency/care-first, abuse prevention | Refines into the persistent Emergency Help doctrine (§7) |
| `health-os-doctrine.md` §7 marketplace risk graduation | Refines into the products & suppliers pillar (§8) |
| `health-os-doctrine.md` §20 access control dimensions | Inherits; the ladder is the citizen-facing expression of dimensions 6–9 |
| `identity-access-trust-governance.md` | Inherits trust-plane governance; the gateway adds no new trust authority |
| `NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md`, `NOMPILO_INTELLIGENT_COMMAND_LAYER.md` | Refines: Nompilo's mediation of trust escalation (§9) |
| `CORE_TRANSACTION_DOCTRINE.md` + journey pack | Inherits: every pillar journey maps to core transactions and journey stages; the gateway adds the intent-led public entry |
| `docs/audits/citizen-zero-to-one/*` | Canonicalizes the trust ladder naming (§4.2); the audit set remains the point-in-time implementation evidence |

---

## 12. Design Consequences

1. Every citizen-facing route, payload, and label passes the public naming rule (§3).
2. Every citizen action declares its minimum trust rung, enforced at the policy engine —
   not hard-coded in UI visibility alone.
3. Every trust escalation is mediated by Nompilo (§9) and preserves journey context
   (§4.1 law 3).
4. "Sign in / Create account" appear on every gateway surface; no public or low-risk
   journey requires them (§5).
5. Emergency Help renders on every citizen-facing surface and is never gated (§7).
6. Coverage, billing, and payment capabilities compose into care journeys (§6) — never
   as a separate finance portal — and honour all eight safeguards.
7. Supplier and product surfaces show honest availability and visible verification (§8).
8. Mobile ships the same journeys against the same workflow state, guest-first, with
   honest offline status vocabulary (§10).
9. New gateway capability attaches to an existing owning service per the
   system-of-record map; the gateway composes, it does not own truth.
10. Implementation truth for every clause of this doctrine is tracked in
    [`doctrine-gap-matrix.md`](doctrine-gap-matrix.md) (clause register GW-01…GW-08) and
    grounded in
    [`../architecture/gateway-experience-capability-map.md`](../architecture/gateway-experience-capability-map.md);
    the phased build sequence lives in
    [`../roadmaps/health-services-gateway-roadmap.md`](../roadmaps/health-services-gateway-roadmap.md).
