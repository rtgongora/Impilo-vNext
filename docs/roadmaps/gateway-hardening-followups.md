# Gateway Hardening — Follow-up Plan (the four remainders)

Closes the four honest remainders left after the gateway UI catch-up pass. Companion to
[`health-services-gateway-roadmap.md`](health-services-gateway-roadmap.md). Same
conventions: phased, dependency-ordered, runtime-proof culture, explicit STOP-for-PO
decisions.

## The four remainders — reclassified

| # | Item | True nature | Blocker |
|---|------|-------------|---------|
| R-1 | OTP login for existing accounts | Doable; security-sensitive | KEYCLOAK-GATE (realm/client change) + hardening + real SMS |
| R-2 | Automated SOS callback-OTP | Doable; small | **Product decision** (conflicts with PD-3 human-callback intent) + real SMS |
| R-3 | Maestro flows headless | Not a code gap | **Environment/CI** (emulator + full stack; not runnable in this sandbox) |
| R-4 | Live-estate golden journeys | Mostly proven by local rigs | Gated deploy authorization + R-1's realm fix |

Grounding notes (verified against HEAD):
- The BFF already documents why OTP-login is absent (`AuthContactOtpController` javadoc:
  ROPC needs the password; token-exchange/impersonation need realm+client config) and
  already holds the admin service account (`KeycloakAdminClient.serviceAccountBearer()`).
- **Real delivery providers already exist**: `notification-service` has `HttpSmsProvider`
  + `SmtpEmailProvider` (not just stubs). "Real SMS" is a **config + credential** task,
  not new code — env-select `notification.sms.provider=http` + a gateway URL/secret.
- The preview realm has `clientScopes` but (per the W1 rig) lacks the `roles` client-scope
  + `resource_access` mappers, and its user-profile requires email — both are the same
  KEYCLOAK-GATE slice R-1 depends on.
- Three gateway rigs exist to extend: `reports/journeys/gateway-{w1,w2c,be}-runtime-proof-*`.

## Decisions — DECIDED (2026-07-15)

- **PD-H1 — OTP-login mechanism: BOTH.** Build **token-exchange** (BFF-brokered, standard,
  revocable) **and** a **custom direct-grant SPI authenticator** (Keycloak-native OTP as a
  first factor). Two independent paths → resilience + flexibility (native flow for clients
  that authenticate directly against Keycloak; BFF-brokered exchange for the experience
  shell). Scope note: this ~doubles the Keycloak work in Phase C — the SPI authenticator is
  a Java Keycloak extension (packaged into the Keycloak image) plus an authentication-flow
  binding, on top of the token-exchange client config. OTP is accepted as a login factor
  under mandatory hardened lockout + production SMS.
- **PD-H2 — SOS callback-OTP: COMPLEMENT.** Auto-OTP confirms the number is *reachable* and
  fast-tracks; a dispatcher still confirms the *emergency* before resources move. Preserves
  PD-3's human-confirmation intent.
- **PD-H3 — deploy authorization** for the live-estate journeys (R-4): still pending; G runs
  when authorized.

## Phases

### Phase A — Real delivery config (S; unblocks R-1, R-2, R-4-auth; NO new code)
Env-select the existing `HttpSmsProvider`/`SmtpEmailProvider` in the preview values
(`deploy/helm/impilo-vnext/values-full-preview.yaml` + secrets) with a real (or
sandbox-gateway) SMS credential; keep the stub for local compose. Acceptance: OTP
delivered end-to-end in a booted stack (the W1 rig already reads the code from
`ns_notifications.vars_json`; a real provider adds the actual send).

### Phase B — Keycloak realm hardening slice (M; KEYCLOAK-GATE; unblocks R-1 + R-4-auth)
The realm gaps the W1 rig found, as ONE reviewed slice with rollback:
1. Add the `roles` client-scope + `resource_access` protocol mappers so service-account and
   user tokens carry roles (fixes Admin-API 403 on user creation on a virgin import — this
   already affects governed onboarding, not just the gateway).
2. Make the user-profile email **optional** so phone-only accounts complete ROPC/session.
3. Fix `${env.KC_CLIENT_SECRET_BACKEND}` substitution on `--import-realm`.
4. (For R-1) enable **token-exchange** for the BFF client (per PD-H1).
Acceptance: virgin `--import-realm` boots; full auth-regression green (existing
login/register/refresh unchanged — prove on the rig); rollback = revert the realm file.
Risk: HIGH (touches all auth) → dedicated slice, no other changes ride with it.

### Phase C — OTP login for existing accounts (M; needs A + B + PD-H1)
BFF `POST /internal/v1/auth/login/otp`: request OTP to a **known, already-attested** contact
for an existing user → verify → **token-exchange** to a real signed session (same
`auth_token` envelope as password login). Reuse `ContactOtpService` (hashed codes, 5-attempt
lockout, 300s TTL) + add per-contact **and** per-IP login-rate caps and full audit. No
account-enumeration (uniform response whether or not the contact maps to a user). Remove the
"deliberately absent" javadoc. Web: add "Sign in with a code" on `/auth/login`; mobile: an
OTP-login option beside PKCE. Acceptance: rig proves an existing user gets a JWT **accepted
by a resource server** (not the old unsigned fallback); lockout + enumeration negatives hold.

### Phase D — Automated SOS callback-OTP (S–M; needs A + PD-H2)
On anonymous SOS submit, issue an OTP to the callback number; public
`POST /internal/v1/public/gateway/sos/{reference}/verify-callback` verifies it and moves the
request `AWAITING_CALLBACK → CALLBACK_CONFIRMED` (reachability). **If PD-H2 = complement**:
dispatch still requires the dispatcher's `verify-callback` (the console stays; the gate now
shows "reachable, awaiting triage"). **If replace**: auto-confirm releases dispatch. Keep the
SOS lane's fail-open posture (never drop a life-safety request if SMS/Redis is down — a
failed OTP falls back to the manual dispatcher path). Web + mobile UI: an optional "enter the
code we texted you" step on the receipt. Rig-proven; dispatcher console reflects the new state.

### Phase E — Rig extension for the catch-up flows (S; **DONE 2026-07-15, 18/18 PASS**)
Extended the gateway rig against booted daidzai+guidance+bff jars + scratch pg/redis
(`reports/journeys/gateway-catchup-runtime-proof-20260715/`): dispatcher worklist →
409 gate → `verify-callback` → gate release → triage succeeds; SOS status-by-reference
(5-field PII-free); health-info `?q=` search; R1 contact-OTP register surface. Proven
through the BFF (booted fully open) — stronger than the BE rig. **Rig-caught + fixed
(`5493cfaea`):** the BFF daidzai proxy collapsed downstream 4xx into 500, masking the
PD-3 409 gate as an opaque 500 — `get()`/`post()` now preserve daidzai's real status.

### Phase F — Maestro headless in CI (M; environment, not this sandbox)
A CI job (GitHub Actions) with an Android emulator + an ephemeral backend (the compose stack
/ the rig) running the mobile flows (`apps/mobile/maestro/flows/citizen-gateway-*.yaml`).
Deliverable here = the workflow + making the flows fully runnable (OTP sink, seeded
reference); **execution is CI/dev-machine only** — no emulator or live stack in this sandbox.

### Phase G — Live-estate golden journeys (M; needs B + PD-H3 deploy)
After the realm slice (B) and a deploy authorization: run the `gateway-*` journeys against
the preview estate per the no-stale fullboot checklist. The local rigs already give
real-service/real-HTTP/real-DB proof; this is the deployed-stack confirmation.

## Dependency graph & sequencing

```
E (now) ─── independent, immediate
A ──► B ──► C          (real SMS → realm slice → OTP login)
A ──► D                (real SMS → callback-OTP)   [PD-H2]
B ──► G                (realm slice → live journeys) [PD-H3 deploy]
F  ─── independent (CI infra)
```

Recommended order: **E now** → **A** → **B** (the keystone; also fixes pre-existing
onboarding) → **C** + **D** in parallel → **G** on deploy → **F** as CI capacity allows.

## Effort / risk

| Phase | Size | Risk | Gate |
|---|---|---|---|
| E rig extension | S | Low | none — start now |
| A real-delivery config | S | Low | credential |
| B realm slice | M | **High** (all auth) | KEYCLOAK-GATE |
| C OTP login | M | Med (security) | A+B, PD-H1 |
| D callback-OTP | S–M | Med | A, PD-H2 |
| F Maestro CI | M | Low | CI infra |
| G live journeys | M | Med (deploy) | B, PD-H3 |

## What I can start immediately without any decision
**Phase E** (extend the rig to prove the catch-up flows) — zero external dependencies.
Everything else waits on PD-H1 / PD-H2 / PD-H3 or a credential.
