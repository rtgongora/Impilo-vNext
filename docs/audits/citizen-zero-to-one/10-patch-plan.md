# 10 — Patch Plan (sequenced, SoR-first)

Atomic Conventional Commits, gates green per slice, `git pull --rebase` → push each slice. Priority order
follows the keystone-first rule from the gap register.

## Slice 1 — LOA propagation end-to-end (G-CZO-01) 🔴 keystone

**SoR:** identity-assurance-service owns `AssuranceLevel`; Tshepo consumes it. No new SoR.

**Change:**
1. **BFF** (`experience-bff/.../config/ServiceClientConfig.java` trust-header interceptor): before forwarding
   to downstream, look up the caller's current level via `IdentityAssuranceServiceClient.getAssuranceStatus()`
   and **set `X-Assurance-Level`** to the canonical `AssuranceLevel.name()` (overwrite any inbound value so a
   client cannot spoof it). Cache per request.
2. **tshepo-authz `PolicyEngine`**: compute `effectiveLoa = max(parseLoa(assuranceLevelHeader), loaLevel(ACR))`
   and use `effectiveLoa` for the `min_loa` comparison (keep ACR as fallback so existing flows don't regress).
   Map `LOA1..4 → 1..4`.
3. **Trust boundary:** `X-Assurance-Level` must be stripped at Envoy ingress from external callers and only
   set by the BFF, so it cannot be forged. Verify Envoy header sanitisation; if absent, add it (note in commit).

**Proof:** the Proof-1 integration test (see [09](09-persona-e2e-test-plan.md)). DENY at LOA1 → upgrade → ALLOW at LOA3.

## Slice 2 — Public L0 entry (G-CZO-02) 🔴

**Change:** new public route group (e.g. `app/(public)/welcome` + `/find-care` + `/emergency`) with: what
Impilo is, get-started, **service/facility finder** (read-only public directory — no personal data), emergency
+ public-health info, language/accessibility entry, and sign-in / create-account / request-Health-ID CTAs.
Add the new paths to `middleware.ts` public set. Root `/` → public landing when unauthenticated, `/home`
when authenticated. **Hard rule:** zero personal/health data on any public route; facility finder reads only
a public facility registry endpoint.

**Proof:** RTL — unauthenticated render reaches landing; middleware test asserts new paths public and `/home`
still gated; assert no PII fetch on the public routes.

## Slice 4 — Step-up UI (G-CZO-04) 🟠

**Reuse, don't invent.** `usePolicyDecision()` already captures `STEP_UP_REQUIRED` + `stepUpMethods`, and
`/share/claim` already renders a challenge. Extract a `StepUpChallenge` component (method picker →
`POST /v1/step-up/challenge` → poll `GET /v1/step-up/status/{id}` → retry original action) and bind it to:
record download/share, sensitive results, manage delegates/trusted contacts, high-risk account changes.
Replace the plain step-up-token text inputs in `id-recovery` and `delegated-pickup` with it.

**Proof:** RTL — action returns 401+methods → challenge renders → on success original mutation retries.

## Slice 5 — Mobile trust banner (G-CZO-05) 🟠→🟡

**Narrowed by audit:** clinical sections are already wired — **no reconciliation needed**. Just: read the
current assurance level post-login (call `/internal/v1/identity/assurance/status` or read claim into
`SessionContext.assuranceLevel`) and render an assurance banner on `HomeScreen` mirroring the web
`IdentityAssuranceBanner` states.

**Proof:** RTL/jest — HomeScreen shows banner for non-verified level; hidden when `FULLY_VERIFIED`.

## Slice 6 — Accessibility basics (G-CZO-08/09) 🟡

Expose an accessibility settings panel (high-contrast toggle + text size + language stub) wired to the
existing `display-settings` persistence (`PrivacyRightsController`). Add resumable draft to
`health-id/request`. Document SMS-fallback/offline as explicitly deferred.

## Slice 7 — Consent persistence + citizen consent UI (G-CZO-06/07) 🟡

**SoR-first (RESOLVED):** experience-bff has **no datasource** — do NOT persist in the BFF. The SoR is
**`mvumo-service`** — the sovereign Ring-0 consent *orchestration* service (generic `consentType` covering
PRIVACY_POLICY/TERMS_OF_USE, templates, multi-channel + assurance + actor capture, offline-sync, proof), which
writes through to tshepo-consent. (NOT tshepo-consent directly — that's the downstream record store; NOT
data-governance — that owns privacy-prefs/DSR.) The BFF already has `MvumoServiceClient`. **Build:** route
`PolicyConsentController` accept → Mvumo consent-request + grant transition (carry channel/assurance/actor);
revoke → withdraw transition; `/status` & `/history` → Mvumo `listForPatient` filtered to policy consent types;
then give `/settings/privacy` true feedback. Fail-safe so the login consent interstitial never hard-breaks if
Mvumo is unavailable.

## Slice 3 — L5 delegated access (G-CZO-03) 🟠 — DESIGN ONLY, STOP FOR PO

**Do NOT build.** Produce a scoped design doc covering: the relationship object (delegator, delegate, scope,
purpose, legal basis/consent, assurance floor, expiry, revocation), the authz check ("act-on-behalf"
dimension in PolicyEngine), audit, and the "acting for X" UI banner. **SoR question for the PO:** does the
delegation relationship live in **Vito** (as a person-to-person identifier link) or a **new relationship
service**? `docs/registry/system-of-record-map.md` shows no current owner for act-on-behalf relationships.
Flag and halt for ruling. (See companion `docs/audits/citizen-zero-to-one/DELEGATION-DESIGN.md` once drafted.)

## Cadence & gates per slice

`mvn -q -o test` (owning service) · `npx tsc --noEmit && npx vitest run` (one-ui-shell / mobile) ·
`scripts/guard/{check-backend-frontend-parity,check-route-inventory,check-frontend-mocks-and-stubs,check-product-truth}.sh`
· regenerate product-truth. Atomic commit → `git pull --rebase` → push.
