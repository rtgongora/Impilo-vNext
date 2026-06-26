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

- **Option A — Vito** (person registry) as a person-to-person identifier link. Pro: Vito already owns the
  person anchor and identifier classes; relationships are an identity concern. Con: Vito is identity-of-record,
  not an authorization/consent engine; scope + legal-basis + revocation semantics lean consent/trust.
- **Option B — tshepo-consent-service** as a consent-backed delegation directive. Pro: legal basis + scope +
  revocation are consent-shaped, and this is the consent authority. Con: guardianship/facility-assignment
  bases aren't strictly "consent."
- **Option C — a new relationship/delegation service** (trust plane). Pro: clean ownership of a genuinely new
  capability. Con: a new sovereign service is a heavy commitment; must prove no existing service should own it.

**Registry check (done):** `docs/registry/system-of-record-map.md` shows **no current owner for act-on-behalf
relationships**. `tshepo-consent-service` owns consent records; Vito owns identity; neither today models a
person-acts-as-person delegation. So this is a real ownership gap requiring a ruling — it cannot be inferred.

**Recommendation (non-binding):** Option B (tshepo-consent-service) if the PO accepts modelling
guardianship/facility-assignment as legal-basis variants of a delegation directive; otherwise Option C. Avoid
Option A — Vito should hold the identity link but not the authorization/scope/revocation semantics.

## 5. Build outline (only after the ruling)

1. SoR: relationship entity + migration in the chosen service; create/list/revoke API; audit.
2. Trust: new `act-on-behalf` dimension in `PolicyEngine` (resolve relationship via `X-Subject-ID` + delegate
   identity; ACTIVE/in-scope/unexpired/LOA-floor; audit both identities).
3. BFF: replace `stubDelegatedPickup` with real delegation-backed flows; delegation management endpoints.
4. UI: "acting for <S>" banner (web + mobile); subject/guardian review-and-revoke surface; scope-limited views.
5. Tests: Persona G end-to-end (delegate sees only authorised scope; revoke takes effect; audit binds both).

**Until the §4 ruling, none of the above is implemented.** This is the explicit STOP point for the wave.
