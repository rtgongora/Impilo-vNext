# L5 Delegated / Assisted Access — Scoped Design + SoR Question (STOP for PO)

**Status:** DESIGN ONLY — **do not build before a PO ruling on the SoR question (§4).**
**Gap:** G-CZO-03. **Wave:** Citizen Zero-to-One. **Date:** 2026-06-26.

> Per the wave brief, full L5 delegated access is a LARGE net-new build. This document scopes it
> and surfaces the one decision that must be made first (where the relationship lives). It is
> deliberately not implemented.

## 1. What exists today (grounded)

- **Role constants only:** `CAREGIVER`, `CARE_PARTNER` exist as roles, with no relationship model behind them.
- **Stubbed delegated pickup:** `experience-bff/.../service/citizen/CitizenLongtailService.stubDelegatedPickup`
  issues a pickup token with **no persistence**, no authz check, no scope/expiry enforcement, no audit.
- **No "acting for X" anywhere:** no act-on-behalf dimension in `PolicyEngine`, no "acting for X" UI banner,
  no relationship registry. `X-Actor-Type` enumerates `CAREGIVER` but nothing populates a delegation context.
- **Adjacent, reusable primitives:** record-sharing (`patient-shares`, time-bound, revocable) and the
  step-up engine (now citizen-wired, Slice 4) are related but are *subject-grants-access-to-provider*, not
  *person-acts-as-another-person*.

## 2. What L5 must guarantee (acceptance)

A delegate (caregiver / guardian / CHW / facility staff) acts **for another person**, and the access is:
explicit · limited in scope · consented or legally authorised · auditable · revocable · time-bounded.
Concretely:
1. A **relationship** binds delegator (subject) ↔ delegate, with: relationship type, scope (which
   capabilities/resource classes), legal basis (consent | guardianship | facility-assignment), assurance
   floor (the delegate's own LOA must meet a minimum), start/expiry, status (active/suspended/revoked).
2. Every delegated action is **authorised against that relationship** (a new access dimension) and **audited**
   as "delegate D acted for subject S".
3. The UI shows an unmistakable **"You are acting for <S>"** banner in any delegated session, and the
   delegate can only see the authorised scope (never the subject's full sensitive record unless the basis allows).
4. Subjects (and guardians) can **review and revoke** delegations.

## 3. Proposed model (pending §4 ruling)

```
Relationship {
  id, tenantId,
  subjectHealthId,            // the person being acted for
  delegateActorId,            // the person acting
  relationshipType,           // GUARDIAN | CAREGIVER | CHW | FACILITY_STAFF | ...
  legalBasis,                 // CONSENT | LEGAL_GUARDIANSHIP | FACILITY_ASSIGNMENT
  scope[],                    // capability/resource-class allowlist
  minDelegateLoa,             // assurance floor for the delegate
  startsAt, expiresAt,
  status,                     // PENDING | ACTIVE | SUSPENDED | REVOKED
  createdBy, revokedBy, audit fields
}
```

Request flow: a delegated request carries `X-Subject-ID` (already a doctrine header) + the delegate's own
identity. The trust plane resolves the relationship, checks it is ACTIVE, in-scope, unexpired, and that the
delegate's LOA ≥ `minDelegateLoa`, then ALLOWs with an audit record binding both identities. This is a **new
PolicyEngine dimension** ("act-on-behalf"), composing with the existing 10 dimensions — not a bypass.

## 4. THE SoR QUESTION FOR THE PO (decide before building)

**Where does the act-on-behalf relationship live?**

- **Option A — `mvumo-service`** (sovereign Ring-0 consent orchestration) — **LEADING.** Mvumo **already models
  the building blocks**: `actorRef` + `actorRelationship` on communication-preferences (someone acting for the
  patient), remote/assisted **identity-verified** consent sessions (`/remote-sessions/*` with verify → grant),
  adaptive assurance, and proof artefacts. A delegation is naturally a consent-backed, identity-verified,
  revocable, time-bounded directive — exactly Mvumo's domain. Pro: reuses existing machinery (assisted capture,
  assurance floor, proof, withdraw); the BFF already has `MvumoServiceClient`. Con: Mvumo `must-not-own-clinical-
  record-content` — fine, a relationship is not clinical content.
- **Option B — Vito** (person registry) as a person-to-person identifier link. Pro: owns the person anchor. Con:
  identity-of-record, not an authorization/consent engine; scope + legal-basis + revocation lean consent/trust.
- **Option C — `tshepo-consent-service`** as a consent directive. Con: it's the downstream record store Mvumo
  writes through to; capture/orchestration (which delegation needs) is Mvumo's role, not tshepo-consent's.
- **Option D — a new relationship/delegation service.** Con: heavy; must prove Mvumo can't own it (it likely can).

**Registry check (done):** `system-of-record-map.md` shows no row literally named "act-on-behalf relationship",
but `mvumo-service` is the consent-orchestration SoR and already carries `actorRelationship`/assisted-session
primitives. So the ownership is *most likely* Mvumo — but it crosses into a new capability, so it still needs a
PO ruling rather than being silently assumed.

**Recommendation (non-binding, REVISED):** **Option A — `mvumo-service`.** Model delegation as a Mvumo
consent-backed relationship/directive (legal basis ∈ {CONSENT, LEGAL_GUARDIANSHIP, FACILITY_ASSIGNMENT}, scope,
assurance floor, expiry, revoke), reusing its assisted/remote identity-verified capture and proof. Vito holds
only the identity link; tshepo-consent remains the downstream record store. Avoid a new service unless the PO
finds delegation genuinely outside Mvumo's charter.

## 5. Build outline (only after the ruling)

1. SoR: relationship entity + migration in the chosen service; create/list/revoke API; audit.
2. Trust: new `act-on-behalf` dimension in `PolicyEngine` (resolve relationship via `X-Subject-ID` + delegate
   identity; ACTIVE/in-scope/unexpired/LOA-floor; audit both identities).
3. BFF: replace `stubDelegatedPickup` with real delegation-backed flows; delegation management endpoints.
4. UI: "acting for <S>" banner (web + mobile); subject/guardian review-and-revoke surface; scope-limited views.
5. Tests: Persona G end-to-end (delegate sees only authorised scope; revoke takes effect; audit binds both).

**Until the §4 ruling, none of the above is implemented.** This is the explicit STOP point for the wave.
