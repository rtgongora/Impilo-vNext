# Mvumo — Current State Audit (Consent Across Impilo vNext)

**Date:** 2026-04-27  
**Scope:** Repository scan for consent-related logic, duplicates, gaps, mocks, and integration with Tshepo / Butano / audit.  
**Search terms used (sample):** consent, Consent, permission, authorization, assent, guardian, proxy, witness, FHIR Consent, signature, revoke, withdrawal, break glass, referral, telemedicine, OTP, USSD, biometric, etc.

---

## 1. Executive summary

| Finding | Detail |
|---------|--------|
| **Authoritative trust-layer consent** | **`tshepo-consent-service`** implements FHIR R4 `Consent` storage, **evaluation** (`GET /v1/consent/evaluate`), share links, audit table, outbox, Redis. |
| **Enforcement at FHIR edge** | **`fhir-gateway-service`** `ConsentEnforcementService` calls Tshepo Consent; break-glass bypass with audit expectations. |
| **Product orchestration** | **Previously missing** as a first-class national consent *orchestration* service — addressed by **`mvumo-service`** (templates, adaptive methods, remote sessions, lifecycle, proof coordination). |
| **Experience policy consent** | BFF/experience: `/internal/v1/consent/accept`, `status`, `history` — **policy/ToU-style** consent, not clinical MVUMO-style orchestration. |
| **UI patterns** | `one-ui-shell` **consent page** = privacy/terms **interstitial** + `useConsentStore` (local versioned acceptance) — **not** adaptive clinical consent. |
| **Mobile** | Citizen profile **consent preferences** (mocked tests) call `/internal/v1/mobile/citizen/profile/consents/...` — **preference toggles**, not full Mvumo. |

**Duplication risk:** Policy consent UI vs clinical consent vs Tshepo FHIR — **must** keep boundaries clear (see `docs/architecture/mvumo-consent-architecture.md`).

---

## 2. Backend — notable locations

| Area | Path / notes |
|------|----------------|
| **Tshepo Consent** | `services/tshepo-consent-service` — directives, `ConsentEvaluationService`, `FhirConsentMapper`, share links, portal controller, evaluation API. |
| **FHIR gateway** | `services/fhir-gateway-service/.../ConsentEnforcementService.java` — evaluates via Tshepo Consent. |
| **Policy consent API** | Routed via `experience-bff` (search `/internal/v1/consent` in BFF) — user-facing accept/revoke. |
| **AuthZ** | `tshepo-authz-service` — expects consent decisions in authz path (per architecture docs in repo). |
| **Other** | Grep hits across `oros`, `document-service`, `referral`, `pacs-adapter`, `mushex`, `coverage` — mostly **wording** or **future hooks**; **not** a unified consent orchestration layer pre-Mvumo. |

---

## 3. Frontend — notable locations

| Area | Notes |
|------|--------|
| `ui/one-ui-shell` | `/consent` interstitial; localStorage/version — **low assurance** notice acceptance. |
| `ui/experience` | `usePolicyConsent.ts` — policy consent vs `useConsent.ts` (comment distinguishes TSHEPO clinical). |
| `ui/ehr` | Legacy client paths may reference consents; align with Mvumo over time. |
| `apps/mobile/citizen-app` | Profile consent preferences; tests mock API. |

---

## 4. FHIR Consent support

- **Yes (trust layer):** Tshepo Consent stores and evaluates FHIR R4 `Consent` JSON.  
- **Mvumo:** Orchestrates **human workflows** and **produces** linkage to FHIR resources via Tshepo Consent integration (to be fully wired in future iterations).

---

## 5. Remote / offline consent

| Capability | Pre-Mvumo state |
|------------|------------------|
| **Remote** | Share links in **Tshepo Consent**; citizen mobile patterns; **no** unified Mvumo remote session model in repo before `mvumo-service`. |
| **Offline** | `tshepo-offline-service`, offline SDK — **capability/ sync**, not consent UX orchestration. **Mvumo** defines offline pending/sync/conflict **semantics** (implementation staged in Mvumo). |

---

## 6. Gaps and risks (before Mvumo)

1. **Assumption without recording:** Some flows may show “consent” in UI without a durable, queryable **Mvumo request** + **proof** + **Tshepo directive** where required.  
2. **Checkbox-only / PDF-only** anti-patterns: risk in legacy UX; **Mvumo doctrine** rejects treating these as sole mechanisms.  
3. **Reusability:** Consent captured in one module may not be **addressable** for another workflow without shared **template IDs** and **scope** metadata.  
4. **Not connected to audit / Butano:** Any consent not flowing through Tshepo + audit leaves **evidence gaps**.  
5. **Equity:** No single channel (smartphone, email, biometrics) can be the only path — **adaptive** matrix required (Mvumo).  

---

## 7. Recommendations

1. **Adopt Option B** architecture: **Mvumo** = orchestration; **Tshepo Consent** = evaluation + FHIR authority.  
2. **Route new consent UX** through Mvumo APIs; **deprecate** ad-hoc duplicate endpoints over time.  
3. **Map** each clinical/financial/data-sharing workflow to a **consent type** + **minimum assurance level**.  
4. **Integrate** `rules-service` for requirement matrix; **Zibo** for template binding.  
5. **BFF:** Add proxied routes to `mvumo-service` for shell and portal.  
6. **Continuous audit:** Re-run this document when new consent surfaces appear.

---

## 8. References

- `docs/architecture/mvumo-consent-architecture.md`
- `services/mvumo-service`
- `services/tshepo-consent-service`
- `docs/plan/SERVICE_CATALOG.md` (Mvumo entry)
