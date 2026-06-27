# 02 — Health ID Trust Ladder (L0–L5 cross-walk)

Maps the **intended** ladder ↔ Vito `IdentityStatus`/`ClientVerificationState` ↔ identity-assurance
`AssuranceLevel` (LOA1–4) ↔ `IdentityAssuranceBanner` states. Line-cited.

## The four state vocabularies (verified)

### A. Vito `IdentityStatus` — Health ID lifecycle
`services/vito-service/.../core/IdentityStatus.java:6-19`
`DRAFT, PROVISIONAL, REGISTERED, PENDING_VERIFICATION, PENDING_MATCH_REVIEW, VERIFIED, ACTIVE, FLAGGED_FOR_REVIEW, RESTRICTED, INACTIVE, DECEASED, MERGED`
- Temporary/provisional: `DRAFT, PROVISIONAL, REGISTERED, PENDING_VERIFICATION, PENDING_MATCH_REVIEW`
- Verified/active: `VERIFIED, ACTIVE`

### B. Vito `ClientVerificationState` — *how* identity was proven (orthogonal to A)
`core/ClientVerificationState.java:3-10`
`UNVERIFIED → SELF_ASSERTED → PROVIDER_CAPTURED → PARTIALLY_VERIFIED → VERIFIED` (+ `REVIEW_REQUIRED`)

### C. identity-assurance `AssuranceLevel` — permission tier (canonical owner)
`services/identity-assurance-service/.../core/AssuranceLevel.java:8-18`; permissions `core/AssurancePolicy.java:18-48`
| Level | Meaning | Newly granted (cumulative) |
|-------|---------|----------------------------|
| LOA1 | Self-asserted, minimal friction | view-own, wellness, search, education, marketplace-browse, community |
| LOA2 | Supervised remote proofing | + appointment-booking, messaging |
| LOA3 | In-person verification | + prescribing, claims-submission |
| LOA4 | Biometric + token binding | + verify-others, provider-activation, admin-ops |

Upgrade is a reviewed, dual-control workflow (`AssuranceService.decideUpgrade:85-118`,
`UpgradeStatus{PENDING,APPROVED,REJECTED,EXPIRED}`, 30-day expiry). **Monotonic** (`raiseLevel:129-131`).

### D. `IdentityAssuranceBanner` — citizen UI state
`ui/one-ui-shell/src/components/citizen/IdentityAssuranceBanner.tsx:17-24,32`; type `contracts/health-os-identifiers.ts:164-170`
`SELF_REGISTERED, ASSISTED_REGISTRATION, FACILITY_CONFIRMED, REGISTRY_MATCHED, COUNCIL_VALIDATED, FULLY_VERIFIED (renders nothing)`

## The cross-walk

| Ladder | Intended capability ceiling | Vito IdentityStatus | Vito VerificationState | AssuranceLevel | Banner state | Status |
|--------|-----------------------------|---------------------|------------------------|----------------|--------------|--------|
| **L0 Public/Guest** | Public info, service finder, emergency, education, help. No account. | — | — | — (pre-auth) | — | ❌ no surface (G-CZO-02) |
| **L1 Account-only** | Start/continue Health-ID request, basic account, help. **No private records.** | (none yet) / `DRAFT` | `UNVERIFIED`/`SELF_ASSERTED` | LOA1 | `SELF_REGISTERED` | ✅ states exist |
| **L2 Temporary Health ID** | Receive care, show temp ID/QR, book selected services, notifications. **No sensitive records.** | `PROVISIONAL`/`REGISTERED` | `PROVIDER_CAPTURED`/`PARTIALLY_VERIFIED` | LOA2 | `ASSISTED_REGISTRATION`/`FACILITY_CONFIRMED` | ⚠️ states exist but **not policy-enforced** (G-CZO-01) |
| **L3 Verified Health ID** | Health summary, appointments, selected results, Rx, referrals, care plans per policy | `VERIFIED`/`ACTIVE` | `VERIFIED` | LOA3 | `REGISTRY_MATCHED`/`COUNCIL_VALIDATED` | ⚠️ same break (G-CZO-01) |
| **L4 High-assurance session** | Download/share, manage delegates/trusted contacts, sensitive results, high-risk settings, revoke devices | `ACTIVE` | `VERIFIED` | LOA4 | `FULLY_VERIFIED` (no banner) | ❌ no UI step-up to *enter* L4 (G-CZO-04); no LOA4 banner state |
| **L5 Delegated/Assisted** | Caregiver/guardian/CHW/staff acting for another — explicit, scoped, consented, auditable, revocable, time-bound | n/a (relationship object) | n/a | n/a | n/a | ❌ **not built** (G-CZO-03) |

## The golden rule (must hold after fixes)

> A **temporary** Health ID (L2) must be useful enough for care but **never** powerful enough to expose
> sensitive personal health records. That boundary is expressed as policy `min_loa` / `account_assurance_required`
> on clinical resources — which is exactly why G-CZO-01 (LOA propagation) is **Blocking**: without it,
> L2 and L3 are indistinguishable at the gate.

## Mapping gaps (net findings)

1. **No L0 vocabulary.** Pre-auth users are outside every enum. Acceptable *if* a public surface exists
   (it doesn't — G-CZO-02).
2. **L2/L3 collapse at the gate.** The banner/assurance UI distinguishes them; PolicyEngine does not,
   because `loaLevel` is frozen from the Keycloak ACR claim (G-CZO-01).
3. **No LOA4 banner state.** LOA4 maps to `FULLY_VERIFIED` (which renders nothing). A high-assurance
   *session* (strong login) is conflated with high *identity* assurance — they are different axes.
4. **L5 has no state object at all.** Needs an SoR ruling (relationship in Vito vs. a new service) — see
   [10-patch-plan.md](10-patch-plan.md) §Slice 3.
5. **Vito↔identity-assurance not synced.** Vito stores an integer `identityAssuranceLevel` on `ClientEntity`;
   identity-assurance owns the canonical `AssuranceLevel`. No hook raises Vito status when an upgrade is
   approved. (Document-only for now; the propagation fix routes through the header, not Vito.)
