# Service Relationship & Operational Backbone — Foundational Doctrine

> **Canonical summary**: Impilo is not a collection of branded modules that occasionally
> exchange data — it is a governed ecosystem with a single operational backbone. **Tuso**
> is authoritative for *where* health services exist (facilities, organisations,
> departments, wards, service points, capabilities, hours, referral relationships,
> Facility Mode). **Varapi** is authoritative for *who is professionally authorised*
> (provider identity, cadre, qualifications, registration, licence, scope, restrictions,
> sanctions, affiliations). **Vashandi** is authoritative for *who is working where and in
> what capacity* (employment, post, deployment, facility/service-point assignment,
> supervision, roster, leave, acting appointments). **VITO** anchors the person and Health
> ID; **TSHEPO** computes the effective permission. No one of these is sufficient alone: the
> effective operational context is *person identity + Varapi authority + Vashandi deployment
> + Tuso context + TSHEPO decision = what this person may do here, now*. Every other service —
> Khuluma, Rito, Ndila, Nhume, Ruvimbo, PCT, OROS, Dura, Madi, Fundo, Msika, Indawo, Nompilo,
> Impilo Performance — obtains the truth it needs from that backbone and never maintains its
> own unofficial registry of facilities, providers or workers.

> **Short doctrine line**: Tuso is *where*, Varapi is *who-authorised*, Vashandi is
> *who-working-here-now*, TSHEPO is *what-may-happen*; everyone else communicates, evaluates,
> navigates, dispatches, finances, learns and improves the relationship — never re-owns it.

This doctrine refines the Impilo foundational doctrine
([`health-os-doctrine.md`](health-os-doctrine.md)) and complements the gateway doctrine
([`health-services-gateway-doctrine.md`](health-services-gateway-doctrine.md)). It supersedes
nothing; it makes explicit the ownership backbone the whole ecosystem already depends on, and
governs how services integrate. Provider ratings/reputation are carved out separately in the
[provider-reputation doctrine](provider-reputation-doctrine.md).

---

## 1. The Three-Registry Backbone

Three registries form the operational directory and authority backbone. Each answers exactly
one question and SHALL NOT answer another's.

| Registry | Owns (system of record) | Answers |
|---|---|---|
| **Tuso** | Facilities, organisations, ownership/affiliation, departments/wards/service points, geographic location + catchment, facility type/level/capabilities, services offered, hours, equipment/infrastructure, licensing/regulatory status, virtual facilities/service points, hierarchy, referral relationships, Facility Mode configuration | *Where can this work happen, and what is this facility configured and authorised to provide?* |
| **Varapi** | Provider identity, Provider ID, professional category/cadre, qualifications, professional registration, licence status, scope of practice, specialities/competencies, restrictions/suspensions/sanctions, CPD status, prescribing/procedural privileges, Provider↔Health-ID linkage, professional affiliations | *Who is this professional, and what are they legally and professionally permitted to do?* |
| **Vashandi** | Workforce records, employment relationship, post/position, employer, deployment/posting, facility + department + service-point assignment, supervisor/reporting line, contract status, roster/shift, leave/attendance, acting appointments, availability, delegated duties, on/offboarding, organisational role, establishment/vacancies | *Where is this person currently employed, deployed, scheduled and operationally responsible?* |

> A provider may be professionally licensed in Varapi, employed by MoHCC in Vashandi, assigned
> to a Tuso facility, rostered at a service point, and authorised by TSHEPO to only the
> functions appropriate to that context. None of these facts substitutes for another.

### 1.1 The effective-context equation (SHALL)

Any decision about what a person may do operationally SHALL be computed as:

```text
Person identity (VITO)
  + Professional authority (Varapi)
  + Employment & deployment (Vashandi)
  + Facility & service-point context (Tuso)
  + Consent, role & policy decision (TSHEPO)
  = What the person may do here, now
```

A service SHALL NOT infer authority from any single registry. Being licensed is not being
deployed; being deployed is not being authorised; being present is not being permitted.

---

## 2. The Authority Relationship Model

```text
                         VITO
                 Person and Health ID
                           │
            ┌──────────────┴──────────────┐
            │                             │
         VARAPI                       VASHANDI
 Professional authority         Workforce relationship
 licence, scope, cadre          employment, posting, shift
            │                             │
            └──────────────┬──────────────┘
                           │
                          TUSO
         Facility, organisation and service-point context
                           │
                         TSHEPO
         Effective role, access, policy and trust decision
                           │
       ┌───────────────────┼────────────────────┐
       │                   │                     │
 Clinical work      Operational services   Experience services
 PCT / OROS         Ndila / Nhume          Khuluma / Rito
 BUTANO / MADI      Dura / Daidzai         Nompilo / Impilo Live
                           │
                       RUVIMBO
       Coverage, networks, authorisations and claims
```

This is an **authority model**, not a technical dependency tree. It shows where each service
obtains the truth it needs to operate. Arrows are *reads-authority-from*, not *calls*.

---

## 3. Consumer Service Contracts

Each consumer service SHALL read authoritative context from the backbone and SHALL NOT keep an
unofficial parallel list of facilities, providers or workers. Each contract below is
*reads-from → owns → writes-back*.

### 3.1 Khuluma — omnichannel communication

- **Reads:** facility/service-point identity + hours + escalation contacts (Tuso); provider
  identity/title + professional events e.g. licence expiry (Varapi); assignment/roster/shift/
  leave/deployment (Vashandi); case/feedback state (Rito).
- **Owns:** message delivery, conversation journey, acknowledgements, channel selection
  (SMS/WhatsApp/app/USSD/email/voice/in-app).
- **SHALL NOT:** own the facility, provider or workforce truth it communicates about. Khuluma
  invites, reminds and follows up; **Impilo Live** hosts the synchronous interaction; **Nompilo**
  guides the person once they arrive.

### 3.2 Rito — quality, safety, experience & reputation

- **Reads:** facility/department/ward/service-point context (Tuso); provider identity (Varapi);
  employment/assignment/supervisor-at-the-time (Vashandi); verified interaction evidence (PCT).
- **Owns:** feedback, complaints, compliments, incidents, quality cases, moderation, and —
  per the [provider-reputation doctrine](provider-reputation-doctrine.md) — **ratings and
  reputation**.
- **Writes back:** governed referrals, quality alerts, corrective-action triggers — **never** a
  direct change to licence, scope, employment, TSHEPO access or registration.

### 3.3 Ndila — access, geospatial discovery, routing

- **Reads:** facility coordinates/catchment/type/services/hours/accessibility/referral hierarchy
  (Tuso); specialist category/speciality/languages/telemedicine capability (Varapi); operational
  availability — is the team on duty, is a specialist rostered, is the service temporarily
  unavailable (Vashandi).
- **Owns:** discovery, geospatial search, routes, travel time, access navigation.
- **Combined truth rule (SHALL):** Ndila SHALL NOT declare a service *accessible* on Tuso
  capability alone. *Tuso: configured to offer surgery; Vashandi: a surgical team is available;
  Dura: commodities are available; PCT: the theatre is operational.* Only the combined picture is
  real accessibility.

### 3.4 Nhume — dispatch, transport, field coordination

- **Reads:** dispatch bases/stations/destinations/landing-zones + capability (Tuso); crews/
  drivers/paramedics/on-call teams/availability (Vashandi); emergency scope + ALS/transport
  competence + current professional authority (Varapi); geospatial intelligence (Ndila);
  incident command (Daidzai).
- **Owns:** dispatch missions, movement, handover, mission status.
- **SHALL NOT:** dispatch to an unverified free-text facility when a canonical Tuso facility
  exists. Vashandi says a person is rostered; Varapi decides whether they remain authorised.

### 3.5 Ruvimbo — coverage, payer, claims, networks

- **Reads:** canonical facility identity for network enrolment/contracting/claim routing/payment
  destination (Tuso); provider credential/scope/sanctions for network enrolment + claim
  attribution (Varapi); facility assignment / acting capacity / on-duty status where reimbursement
  depends on it (Vashandi).
- **Owns:** membership, coverage, network status, authorisations, adjudication, claim routing.
- **SHALL NOT:** create a competing provider or facility registry. A provider may be valid in
  Varapi yet not authorised to bill under a particular facility (Vashandi). Emergency care SHALL
  NOT be delayed pending financial authorisation.

### 3.6 Clinical & platform services (summary)

- **PCT** turns the three registries into a care-delivery context (Tuso + Varapi + Vashandi +
  TSHEPO + VITO + BUTANO); it records the provider's role **as it existed during the encounter**.
- **OROS** needs ordering provider (Varapi), current assignment (Vashandi), originating service
  point (Tuso), scope/order authority (Varapi + TSHEPO), receiving facility (Tuso).
- **Dura** relates mainly to Tuso (stores/pharmacies/service points), with Vashandi (storekeepers/
  pharmacists/requisitioners) and Varapi (authority for controlled clinical activity).
- **Madi** uses Tuso (blood banks/transfusion points), Varapi (authorised clinicians), Vashandi
  (duty teams), Nhume (transport), Ndila (routing), Khuluma (donor/recipient comms), Rito
  (haemovigilance).
- **Fundo** uses Varapi (learning requirements), Vashandi (role + mandatory training), Tuso
  (facility capability gaps), Rito (quality-improvement learning), Khuluma (reminders), TSHEPO
  (apply achieved competencies where appropriate). **Training completion SHALL NOT silently
  overwrite professional scope in Varapi** — formal authority requires the appropriate approval.
- **Msika** uses Tuso (facility/supplier destinations), Varapi (regulated professional suppliers),
  Vashandi (procurement actors), Ruvimbo (payer pathways), COSTA/MUSHEX (finance), Rito (supplier
  quality).
- **Indawo** uses Tuso (facility/site relationships), Vashandi (public-health workforce), Varapi
  (qualified personnel), Ndila (boundaries), Khuluma (engagement), Rito (community feedback),
  Daidzai (emergency coordination).
- **Nompilo** SHALL NOT become the source of facility, provider or workforce truth. It interprets
  and orchestrates the operating context (Tuso/Varapi/Vashandi/TSHEPO/Ndila/Nhume/Rito/Ruvimbo/
  Khuluma); it explains, it does not duplicate.

---

## 4. Canonical Identifiers

Every service interaction SHALL use shared canonical identifiers issued by the authoritative
service. Records SHALL NOT rely on **names** as integration keys. This table extends the
Multi-Class Identifier Model ([CLAUDE.md](../../CLAUDE.md) · [health-os-doctrine.md §16](health-os-doctrine.md)).

| Identifier | Authority | Class |
|---|---|---|
| Health ID | VITO | Actor |
| Provider ID | Varapi | Actor |
| Workforce member ID / Employment ID / Assignment ID / Position ID / Shift ID | Vashandi | Actor / Context / Transaction |
| Facility ID / Organisation ID / Service-point ID | Tuso | Context |
| Encounter ID (`encounterRef`) | PCT | Transaction |
| Episode / case ID | Relevant clinical service | Transaction |
| Feedback / incident / rating ID | Rito | Record |
| Dispatch mission ID | Nhume | Transaction |
| Coverage / membership ID / Payer ID | Ruvimbo | Context / Transaction |
| Communication thread ID | Khuluma | Event |

Patients are keyed by **CPID** in clinical/SHR contexts (no PII in SHR); providers by **Provider
ID / registration / EC-number**.

---

## 5. Preserve Historical Context (the snapshot rule)

A provider may later change facility, change employer, gain a speciality, lose a licence, change
surname, move department, or leave employment. Historical encounters, claims, ratings and
incidents SHALL continue to show the **context that existed at the time**, not the current truth.

Every meaningful transaction SHALL retain a contextual snapshot:

- Provider ID, workforce assignment ID, facility ID, service-point ID
- Role during the event
- Professional status at the time, employment status at the time
- Encounter/transaction date, effective policy decision

> A rating for a difficult emergency-department shift SHALL NOT become an unexplained permanent
> judgement on a provider's entire career after they transfer elsewhere.

---

## 6. Integration Patterns (no point-to-point spaghetti)

Services SHALL NOT create bespoke direct integrations with every other service. Exactly three
patterns are permitted.

1. **Authoritative APIs** — for immediate validation: *is this provider licensed? is this
   facility active? is this worker assigned here? is this service point valid? is this payer
   relationship active?*
2. **Domain events** — for changes others must know about: `ProviderLicenceChanged`,
   `ProviderScopeRestricted`, `WorkforceAssignmentStarted/Ended`, `ShiftChanged`,
   `FacilityServiceActivated`, `FacilityTemporarilyClosed`, `FeedbackCaseEscalated`,
   `DispatchMissionCreated`, `PayerNetworkStatusChanged`, `EncounterCompleted`.
3. **Composite read models** — derived directories for fast UX (provider directory, service-
   availability index, facility operational directory, workforce-on-duty index, referral
   directory, emergency-response directory, provider reputation summary). These are **derived
   views, not new systems of record.**

---

## 7. Responsibility Matrix

| Service | Owns | Reads from backbone | Writes back / triggers |
|---|---|---|---|
| Khuluma | Messages, conversations, delivery, acknowledgements | Tuso, Varapi, Vashandi | Communication status + responses |
| Rito | Feedback, complaints, incidents, reputation, quality cases | Tuso, Varapi, Vashandi, PCT | Referrals, actions, quality alerts |
| Ndila | Discovery, geospatial search, routes, access | Tuso, Varapi, Vashandi | Search + routing context |
| Nhume | Dispatch missions, movement, handover | Tuso, Varapi, Vashandi, Ndila | Mission status + operational events |
| Ruvimbo | Membership, coverage, networks, authorisations, claims | Tuso, Varapi, Vashandi | Network / authorisation / payment events |
| PCT | Encounters, appointments, telemedicine, inpatient | Tuso, Varapi, Vashandi | Encounter + workflow events |
| Dura | Stock and commodity operations | Tuso, Vashandi, Varapi | Stock alerts + availability |
| Fundo | Learning, competency, supervision | Varapi, Vashandi, Tuso | Completion + competency evidence |
| Nompilo | Guidance and workflow assistance | All relevant services | User choices + guided actions |
| Impilo Performance | Analytics and monitoring | All services | Alerts + management insights |

---

## 8. Composite Experiences

Citizens and workers SHALL NOT have to understand these boundaries. One experience is composed
from many authorities:

- **Find health services** — Tuso (capability) + Vashandi (staffing/availability) + Varapi
  (professional capability) + Ndila (location/route) + Ruvimbo (coverage) + Dura (commodities) +
  PCT (queue/appointment) + Nompilo (guidance) + Khuluma (confirmation).
- **Provider profile** — Varapi (identity/profession/speciality/licence) + Vashandi (current
  assignment) + Tuso (facility context) + Rito (governed experience summary) + Ruvimbo (network) +
  Fundo (CPD).
- **Emergency access** — Daidzai (command/triage) + Ndila (nearest appropriate) + Tuso
  (capabilities) + Vashandi (crews/teams) + Varapi (authority) + Nhume (dispatch) + PCT (handover)
  + Khuluma (family comms) + Ruvimbo (coverage, after stabilisation).
- **Post-visit feedback** — PCT (confirms encounter) + Tuso (facility/service point) + Varapi
  (professional) + Vashandi (assignment/manager) + Khuluma (invites) + Nompilo (guides) + Rito
  (records/investigates/resolves) + Performance (aggregates).

---

## 9. Design Consequences

1. **One backbone, not three silos.** Tuso, Varapi and Vashandi are designed and evolved as a
   coherent authority backbone; new operational capability attaches to them, never re-implements
   them.
2. **No shadow registries.** Any service found maintaining its own list of facilities, providers
   or workers is a defect to be corrected, not a feature.
3. **Authority is composite.** Access/eligibility logic that reads a single registry is
   incomplete; the effective-context equation (§1.1) is the contract.
4. **Availability ≠ capability.** Discovery, dispatch and coverage decisions compose Tuso
   capability with Vashandi availability, Dura commodities and PCT operational state.
5. **History is immutable context.** Transactions snapshot who/where/what-role/what-status at the
   time; downstream views never re-derive historical records from current truth.
6. **Three integration patterns only.** Authoritative APIs, domain events, composite read models —
   derived views are never systems of record.
7. **Ratings are Rito's, not the backbone's.** See the
   [provider-reputation doctrine](provider-reputation-doctrine.md): Varapi says who the provider
   is; Rito says how they were experienced; the two never collapse.
