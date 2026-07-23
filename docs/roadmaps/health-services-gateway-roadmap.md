# National Health Services Gateway — Phased Build Roadmap

Execution-oriented roadmap for building the citizen gateway defined in
[`docs/doctrine/health-services-gateway-doctrine.md`](../doctrine/health-services-gateway-doctrine.md)
(the normative reference for every wave). Grounded capability truth:
[`docs/architecture/gateway-experience-capability-map.md`](../architecture/gateway-experience-capability-map.md).
Clause register: `docs/doctrine/doctrine-gap-matrix.md` §8 (GW-01…GW-08).

Conventions follow
[`agent-led-fullstack-completeness-roadmap.md`](agent-led-fullstack-completeness-roadmap.md):
agent-cycle sizing (not calendar weeks), explicit parallelization rules, single-owner
files, atomic conventional commits, wave end = commit → `git pull --rebase` → push.
SoR-first discipline throughout: every capability extends its owning service; no new
service without proof that no existing service owns the capability.

---

## 0. Shape

- **8 execution waves (W0–W7)** + **4 cross-cutting tracks (T1–T4)** + **4 coordination
  gates** + **7 open product decisions (PD-1…PD-7)**.
- Trust rungs per doctrine §4: **R0 Public → R1 Reachable → R2 Person-verified → R3
  Strongly-authenticated → R4 Authorised-relationship → R5 High-assurance**. R2–R5
  already exist in the estate (effective-LOA enforcement, step-up engine, mvumo
  delegation, break-glass). **R0 API lanes and the R1 rung are the new build.**
- Tracks are capabilities every pillar journey consumes; each has an establishing wave
  and is extended by later waves. Pillars are wave-scoped.

## 1. Cross-cutting tracks

| Track | What | Established | Extended by | Key anchors |
|---|---|---|---|---|
| **T1 Trust-rung infra** | R1 Reachable rung (`CONTACT_VERIFIED` attestation in identity-assurance), phone-OTP registration/login door (G-CZO-11), real SMS provider, reusable step-up UI | W1 | every wave adds rung transitions | `services/identity-assurance-service/.../core/AssurancePolicy.java` + `AttestationController.java`, `experience-bff/.../CitizenStepUpController.java`, `services/notification-service/.../provider/SmsStubProvider.java` |
| **T2 Nompilo mediation** | Public "explain this step" guidance lane; plain-language escalation explainers per doctrine §9 | W1 | per-pillar explainer packs each wave | `services/guidance-service/`, `ui/one-ui-shell/src/components/intelligent/Nompilo*.tsx` |
| **T3 Journey/intent persistence** | Semantic **Intent** primitive `{pillar, goal, params, createdAt}` extending (not replacing) returnTo; anonymous→authenticated continuity; resumable drafts (G-CZO-09) | W1 (client-side) | W3+ server-side drafts; W6 guest-cart handoff | `ui/one-ui-shell/src/lib/resolve-post-login-destination.ts`, `middleware.ts`, `src/lib/routes.ts` |
| **T4 Persistent Emergency Help** | Emergency control on every surface at every rung, never blocked by identity/payment (doctrine §7) | W1 (control + static guidance + tel:) | W2 anonymous intake; W3+ authenticated SOS parity; mobile every wave | `ui/one-ui-shell/src/app/welcome/emergency/`, `services/daidzai-service/.../EmergencyController.java`, `experience-bff/.../DaidzaiController.java` |

Anonymous emergency **intake** (T4's riskiest piece) is deliberately a W2 workstream, not
a W1 one: it needs its own abuse-control design and daidzai changes.

## 2. Public-lane security architecture (adopted as ADR in W0)

**Pattern: the BFF is the only public front; downstream stays fail-closed.** Consistent
with the two existing public routes (BFF permitAll + Envoy `ExtAuthzPerRoute` disable)
and the anonymous-on-behalf pattern of `/internal/v1/auth/register`.

1. **No PDP changes.** No PUBLIC actor tier in tshepo-authz; no synthesized guest trust
   headers toward downstream services. The gRPC PDP's hard-deny on missing trust headers
   remains the estate backstop.
2. **BFF public controllers** under one consolidated family
   `/internal/v1/public/gateway/**` (legacy `patient-shares`/`facility-certificates`
   prefixes untouched), added to the `SecurityConfig.java` permitAll block. Each calls
   downstream as the BFF service identity, against endpoints designed public-safe.
3. **Service-side rule:** a downstream endpoint may enter the public lane **only if it
   lives in a `Public*Controller` class in the owning service** (tuso
   `PublicFacilityController` is the template) — public-safety is a reviewable
   service-side property, not a BFF-side hope. Allow-listed DTOs, no PII, no internal
   service names.
4. **Envoy:** extend the existing per-route ext_authz-disable pattern in **both**
   `infra/envoy/envoy.yaml` and `envoy-runtime.yaml` (after W0 reconciliation), with
   header sanitisation stripping externally-supplied trust headers
   (`X-Assurance-Level` etc.) on public routes.
5. **Abuse controls:** Envoy local rate-limit on the public prefix + BFF per-identifier
   limits; anonymous **writes** exist only for feedback and SOS, each with its own
   abuse note (claim-code / OTP-callback friction, unverified flags).
6. **Guard:** `check-public-lane.sh` (W0) asserts every permitAll route maps to a
   documented public contract entry and every public DTO passes the naming-dictionary
   scan (doctrine §3).

## 3. Waves

> **Public-First breadth track (adopted 2026-07-23, doctrine §13).** Alongside the
> pillar waves below, the Public-First Access program opens every remaining domain's
> discovery surface to R0 per the four-question test: PF-W0 doctrine+register (landed),
> PF-W1 default-tenant unification (prerequisite), PF-W2 regulatory reference +
> disaster alerts + blood appeals + triage urgency tier, PF-W3 marketplace public
> browse (satisfies MSIKA-GATE's R0 slice ahead of W6), PF-W4 coverage public compare,
> PF-W5 wellness + learning public lanes, PF-W6 guest-session affordances ("Continue
> without signing in", reasoned sign-in prompts). Enumeration of record:
> [`../registry/public-capability-register.md`](../registry/public-capability-register.md).
> Gateway W3 keeps ownership of journey-context-across-auth (PD-5 sovereign drafts);
> the PF track uses client-side continuity only until W3 lands.

### W0 — Public-lane foundations & program gates (S; serial; 1–2 agent cycles)

- Public-lane ADR (§2) in `docs/architecture/`.
- **Reconcile Envoy prod/compose divergence**: `envoy.yaml` has two `ExtAuthzPerRoute`
  bypasses (`/v1/public/verify`, `/v1/public/share`); `envoy-runtime.yaml` has none. One
  authoritative pattern + diff-equivalence check before any new public route.
- **Public naming dictionary** (doctrine §3): citizen-facing names for internal services,
  enforced by a guard script (informational in CI first).
- Codify the four coordination gates (§5); MSIKA-GATE handshake note acknowledged by the
  msika lane owner.
- Golden-journey harness plan: `gateway-*` scenario family writing to `reports/journeys/`.

**Acceptance:** ADR merged; Envoy configs proven equivalent; naming guard runs;
gates documented.

### W1 — Gateway shell + progressive trust (XL; 3 parallel workstreams) — the priority wave

> **Status (2026-07-12): LANDED** (unit/offline proof; live-estate `gateway-*` journey
> runs pending next deploy). Delivered: `/welcome` intent home + public facility lane;
> R1 `CONTACT_VERIFIED` attestation + OTP contact-verification/registration + real SMS
> provider config (V013 templates, incl. the previously unseeded STEP_UP_OTP); semantic
> intent primitive across auth; guidance V012 public explainers + sign-in escalation
> panel; persistent Emergency Help control on public and authenticated chrome.
> **Deviation:** OTP *login* deliberately not built — unachievable app-layer without
> Keycloak realm changes (KEYCLOAK-GATE); OTP proves the channel at registration,
> password remains the login credential. Tests: identity-assurance 45, notification 50,
> guidance 32, BFF new classes 42, one-ui-shell 626 — all green; `check-public-lane.sh`
> fully PASS.
>
> **Live rig proof (2026-07-12): 21/21 checks green** against jars from HEAD with real
> JWT enforcement (evidence `reports/journeys/gateway-w1-runtime-proof-20260712/`). The
> rig caught and fixed 4 real end-to-end defects unit tests missed: (1) the companion
> V11 header filter 400'd truly anonymous callers on the public lane (new
> `PublicGatewayAnonymousDefaultsFilter`); (2) tuso had no `permitAll` for
> `/v1/public/facilities/**` so the lane 502'd E2E; (3) the OTP delivery hop reached
> notification unauthenticated → R1 503'd; (4) `IDENTITY_ASSURANCE_BASE_URL` never bound
> to its client → REGISTER rolled back off-localhost.
>
> **Known follow-ups (honest, not yet closed):**
> - **KEYCLOAK-GATE — realm-file gaps** reproduce on any virgin import of
>   `realm-impilo-preview.json` and affect pre-existing governed onboarding too: (a) no
>   `roles` client scope/mappers → service-account tokens carry no `resource_access` →
>   Admin API 403 → server-side user creation dead; (b) user profile requires email so
>   phone-only ROPC auto-login degrades to 201; (c) `${env.KC_CLIENT_SECRET_BACKEND}`
>   didn't substitute on `--import-realm`. These need a dedicated KEYCLOAK-GATE slice with
>   rollback + full auth-regression proof — NOT patched here.
> - **`next build` fails at HEAD** on a pre-existing msika-lane page-export type error
>   (`findIntentId`, commit `f725fc6a3`) — blocks the production UI build estate-wide;
>   owned by the msika lane (follow-up chip spawned).
> - **PDP-denies-R2 negative** unproven (tshepo-authz not in the W1 rig).
> - **Preview helm wiring** for the three W1 BFF hops added to
>   `values-full-preview.yaml` (`GUIDANCE_BASE_URL`/`IDENTITY_ASSURANCE_BASE_URL`/`NOTIFICATION_BASE_URL`).

**Workstream A — Public gateway shell.** Evolve `/welcome` into the intent home ("How
can we help you today?", 9 pillar cards; honest not-yet states for pillars without
public journeys). Anchors: `src/app/welcome/page.tsx`, `PUBLIC_PREFIXES` in
`middleware.ts`, `guard:"none"` rows in `routes.ts`. First BFF public lane:
`/internal/v1/public/gateway/facilities/**` → tuso `PublicFacilityController`
(service-complete today, just not BFF-exposed). Brochure keeps its Traefik paths and adds
a prominent "Get Health Services" CTA → `/welcome` (PD-1); the gateway never claims
brochure-owned prefixes in W1.

**Workstream B — Reachable rung (T1).** R1 = LOA1 account whose sole attestation is
`CONTACT_VERIFIED`, owned by **identity-assurance-service** (new attestation pathway
alongside IN_PERSON/SUPERVISED_REMOTE/BIOMETRIC). Registration stays at the BFF door
(`/internal/v1/auth/register`; Keycloak `registrationAllowed:false` unchanged); add
phone-OTP registration + login app-layer, reusing tshepo's SMS_OTP mechanics. Replace
`SmsStubProvider` with an env-selected real provider (stub retained for compose).
**Deliberately zero Keycloak realm changes** (KEYCLOAK-GATE); effective LOA continues to
ride `X-Assurance-Level` per the landed G-CZO-01 mechanism. R1 entitlements per PD-2.

**Workstream C — Intent + Nompilo + Emergency control (T2/T3/T4).** Semantic Intent
primitive carried through auth (client-side in W1: session/local storage + URL-safe
token), resolving to destination + restored state in
`resolve-post-login-destination.ts`. Public Nompilo lane: guidance-service gains a
public allow-listed "explain this step" read endpoint; every W1 rung transition ships
with its explainer copy. Persistent Emergency Help control in `PublicShell` + the
authenticated shell + mobile shell → `/welcome/emergency` (static guidance + tel: in W1).

**File ownership:** A owns `SecurityConfig.java`, `middleware.ts`, `routes.ts`; B/C
submit route requests to A.

**Acceptance (runtime proof):**
1. `gateway-shell` journey: anonymous → intent page → pillar card → facility search →
   profile; zero auth redirects; zero internal names (guard assert).
2. `gateway-reachable` journey: phone → OTP → R1 session → identity-assurance shows
   `CONTACT_VERIFIED` → PDP **denies** an R2 action for that session (negative proof).
3. Intent preservation: start booking intent anonymously → sign-in → land on booking
   with intent restored (state assert; covers the AuthGuardProvider contract-race
   regression).
4. Emergency control renders on 100% of route classes (route-inventory script extension).
5. Existing suites green: `scripts/guard/*`, `mvn -q -o test` per touched service,
   `npx vitest run` in one-ui-shell.

### W2 — Public knowledge & verification + anonymous emergency intake (L; pillars 3, 4, 9; rungs R0–R1)

> **Status (2026-07-12): find-&-verify + health-info LANDED; anonymous SOS in progress.**
> W2-A public practitioner verification (varapi `PublicPractitionerVerificationController`,
> enumeration-resistant uniform-shape NOT_FOUND, BFF proxy, `/verify/practitioner` page —
> integration-caught the same missing-permitAll bug the rig found for tuso, now fixed);
> W2-B public health-information lane (guidance V013 = 13 citizen-language articles,
> topic/category/read endpoints, `/welcome/health-info` page, pillar card flipped to open).
> Tests: varapi 207/full + 5 new, guidance 37, BFF guidance 9 + practitioner 5, one-ui-shell
> green (route count 704). Guard strict PASS. **W2-C anonymous SOS: intake lane + UI landing
> now; the dispatcher-side callback-before-dispatch hard gate (PD-3) requires a daidzai
> `emergency_request` callback/verification schema seam + a dispatcher verify action, built
> and rig-proven as its own slice — not merged blind onto the daidzai spine.**
>
> **Update: W2-C LANDED (rig-proven 7/7).** daidzai V002 adds callback columns + a
> real dispatch gate (triage 409s on `AWAITING_CALLBACK` until `POST
> .../requests/{id}/verify-callback` by a dispatcher); BFF `POST
> /internal/v1/public/gateway/sos` (required callback, per-IP 5/600s + global 60/60s
> rate limits that fail **open** for life-safety, `PUBLIC_ANONYMOUS`); `/welcome/emergency`
> assistance form. Rig `gateway-emergency-anon` proved 202→AWAITING_CALLBACK, no-callback
> 400, triage-blocked 409, verify→triage 201, rate-limit 429. Tests: daidzai 13, BFF SOS 5
> (+facility regression 5), UI form 4. Integration-caught (coordinator): the SOS test
> predated the W1 KeycloakAdminClient ctor change — fixed. **Operator-surface follow-up:**
> dispatcher verify-callback console UI (API-proven only) + automated callback-OTP
> (dispatcher calls back manually today). **All three W2 pillars now landed.**

- **Find & verify:** full public facility finder on the W1 lane; **new** varapi
  `PublicPractitionerVerificationController` (verify-by-registration-number, allow-listed
  register-status DTO — mirrors tuso's public pattern; varapi is all `/v1/internal/**`
  today); ndila tiles for map view; search-service public federated read restricted to
  facility/knowledge indices.
- **Health information:** public content lane over guidance-service education +
  clinical-knowledge-platform pathways via a new BFF public controller. **SoR check
  in-wave** (PD-4): default ruling — clinical-knowledge-platform owns clinical content,
  guidance-service owns citizen-language education; no new service without
  proof-of-no-owner.
- **Anonymous emergency intake (T4):** public SOS into daidzai `EmergencyController` via
  the public lane with abuse controls: strict rate-limit, coarse location until callback,
  dispatcher "unverified" flag. Per **PD-3 (decided)**: the request is captured
  immediately and never gated on sign-in, but **callback verification is required before
  dispatch** — a dispatcher/responder must reach the callback number (W1 SMS provider)
  before resources move.
- Parallel workstreams: (A) find/verify, (B) health-info, (C) SOS intake — disjoint
  services.

**Acceptance:** `gateway-public-reads` journey (anonymous facility search → profile →
practitioner verify → health-info article; all 200, zero PII, zero internal names);
`gateway-emergency-anon` (anonymous SOS → incident with unverified flag → 429 at
threshold N+1); no ext_authz denials on the lane.

### W3 — Get care + My health (L; pillars 1, 2; rungs R0→R3)

The flagship "journey preserved across auth" wave: public facility profile → "Book" →
Nompilo explains the R2 requirement → sign-in/step-up → booking resumes with slot
pre-selected (T3 server-side draft debut — SoR decision PD-5); telehealth entry (pct +
`CitizenTelehealthController`); My-health summary/records/results behind R2/R3 with
step-up bound to record download/share/results; patient-share claim folded into the
gateway narrative; referral status view. Mobile booking/records parity (Maestro proof).

**Acceptance:** `gateway-book-with-escalation` journey (anonymous → intent → R1 OTP
sign-in → R2 upgrade prompt → booking confirmed; same intent id pre/post auth);
step-up DENY→challenge→ALLOW proof on record download; mobile guest→booked flow.

### W4 — Feedback & complaints + Applications & licensing (M; pillars 6, 7; rungs R0–R3)

- Anonymous + Reachable feedback intake into rito (new public/reachable lane with W2's
  abuse pattern; anonymous cases get **claim-code tracking** like patient-shares);
  "track my case" at R1.
- Applications & licensing: public "requirements / categories / fees" reads from tuso
  application-types + requirement-sourcing; authenticated varapi `PortalController` and
  `ProviderApplicationController`/`LicenseController` journeys behind R2/R3;
  `HpaRegulatoryBffController` reuse; establishment-guide entry (UI path currently under
  `/marketplace/` — naming coordinated with MSIKA-GATE, UI-only).

**Acceptance:** anonymous complaint → claim code → status check without sign-in (curl
proof); registrant journey from public requirements through authenticated submission
with RFI round-trip (extends `hpa-runtime-proof-*` conventions in `reports/journeys/`).

### W5 — Health cover & payments (L; pillar 5; rungs R0, R2, R3)

Public coverage-plan browse + cost explainers (new public allow-listed read on
coverage-service; NHI-ready framing per doctrine §6.2, scope PD-6); authenticated
enrolment (MemberCoverage model exists), eligibility self-check, contributions, subsidy,
appeals; COSTA bills/receipts/waivers via `Citizen{Coverage,Costa,Wallet}Controller`;
MusheX/wallet rails; step-up bound to payment mutations; Nompilo cover-decision
explainers; **verification of doctrine §6.3 safeguards** (esp. no visible
vulnerability flags, no silent rejection).

**Acceptance:** enrol→eligibility→bill→receipt journey with step-up proof on payment;
public plan-browse returns zero member data; waiver path + T4 jointly prove
"payment never blocks emergency" (explicit cross-check test).

### W6 — Products & suppliers (L; pillar 8; rungs R0–R3) — **GATED on MSIKA-GATE**

Written now, executed only after the gate: anonymous catalog/storefront browse (msika
Catalog/Listing/Storefront/Search — `AssurancePolicy` already floors marketplace-browse
at LOA1; the missing piece is the R0 lane); guest cart with T3 intent handoff at sign-in
(msika-flow Carts, PD-7); order lifecycle + tracking; `RxController` prescription attach
+ substitutions at R3; pickup/delivery via nhume; honest availability labels + supplier
trust badges per doctrine §8.

**Acceptance:** anonymous browse → guest cart → sign-in resume → order → track journey;
prescription-attach step-up proof; gate-exit checklist signed.

### W7 — Delegation, high assurance & program hardening (M; all pillars; rungs R4–R5)

Surface the built delegation substrate in gateway journeys: "acting for X" banner,
book-for-child, caregiver medicine pickup, CHW-assisted registration (assisted R1→R2
via IN_PERSON/SUPERVISED_REMOTE pathways); LOA4 flows + banner state (G-CZO-12);
break-glass post-hoc citizen notice; policy-consent persistence completion if still open
(G-CZO-06/07); accessibility/low-data sweep (G-CZO-08/10); full `gateway-*` suite
becomes CI-required; brochure coexistence/transfer decision executed (PD-1 final).

**Acceptance:** delegated booking journey with anti-self-grant negative test; LOA4
dual-control upgrade runtime proof; `reports/journeys/gateway-suite-<stamp>/summary.txt`
green.

## 4. Sizing & parallelism

| Wave | Size | Pillars | Rungs | Parallel workstreams |
|---|---|---|---|---|
| W0 | S | — | — | serial |
| W1 | XL | entry to all 9 | R0, R1 | 3 (shell / reachable / intent+Nompilo+emergency) |
| W2 | L | 3, 4, 9 | R0, R1 | 3 (find-verify / health-info / SOS intake) |
| W3 | L | 1, 2 | R0→R3 | 2 (care / my-health) + mobile lane |
| W4 | M | 6, 7 | R0–R3 | 2 (feedback / licensing) |
| W5 | L | 5 | R0, R2, R3 | 2 (coverage / payments) |
| W6 | L (gated) | 8 | R0–R3 | 2, post-gate only |
| W7 | M | all | R4, R5 | 2 (delegation / hardening) |

Mobile (`apps/mobile/citizen-app`) is a standing lane inside each wave (guest-first
shell in W1, pillar parity per wave, Maestro proofs), not a separate wave — doctrine §10.

### UI catch-up pass (2026-07-12/15) — bring UI up to the shipped backend

A dedicated pass closed the nonexistent/thin UIs the W0–W2 backend had outrun, on
**both surfaces**:
- **R1 onboarding front door (was backend-only):** web `/auth/register/contact` (shared
  `OtpCodeInput`, intent-preserving, now the primary Create-account CTA) + mobile
  `ContactSignUpScreen` over a new anonymous `publicApiClient` seam (the mobile api-client
  had no pre-auth path — that gap also broke on-device password registration, now fixed).
- **Dispatcher callback console (was API-only):** `/work/daidzai/verify-callbacks`
  worklist + verify action releasing the PD-3 gate (BFF verify proxy added).
- **Emergency thickening:** public SOS status-by-reference tracking
  (`/welcome/emergency/track` → PII-free `GET /public/gateway/sos/{reference}`,
  rig-proven 10/10) + receipt "Track this request" link + GPS "share my location"; mobile
  SOS GPS + track-by-reference.
- **Health-info search:** `q` text search on the public education lane (web + mobile),
  reusing the existing `guidanceService.search`.
- **Nompilo explainer coverage:** broadened beyond login to the R1 register page and the
  welcome intent home.
- **Mobile parity:** guest health-info browse/search + practitioner/facility verify
  screens. Verified: web tsc + vitest + guard-strict; mobile tsc + 209 tests +
  parity-guard (no-mocks); BE additions rig-proven 10/10.

## 5. Coordination gates

| Gate | Protects | Rule |
|---|---|---|
| **MSIKA-GATE** | `services/msika-*/**`, marketplace UI trees, `Citizen{Marketplace,Prescription,Delivery}Controller`, nhume write-backs | Marketplace lane may be under active concurrent development. W0–W5 read/reference only, never edit; W6 opens only after written handoff (lane branch merged or paused; joint review of cart/order surface for intent-handoff compatibility). |
| **KEYCLOAK-GATE** | `infra/keycloak/**`, realm exports | Realm changes (otpPolicy, authenticationFlows, ACR, registrationAllowed) affect ALL auth. Any wave needing them files a dedicated single-purpose slice with rollback plan + full auth regression proof. W1 is designed to need none. |
| **ENVOY-GATE** | `infra/envoy/envoy.yaml` + `envoy-runtime.yaml` | Divergence reconciled in W0; thereafter both files change together in every public-route slice; single owner per wave; diff-equivalence check. |
| **BRANCH-GATE** | concurrent branches | CLAUDE.md wave-completion cadence per slice; waves start from fresh rebase; files also touched by active branches flagged in the wave ownership table before work starts. |

## 6. Product decisions — DECIDED by PO 2026-07-12

| # | Decision | Ruling |
|---|---|---|
| PD-1 | Brochure vs gateway ownership of `/` and `/services` | **Coexist**: brochure keeps its paths and adds a prominent "Get Health Services" CTA → `/welcome`. Final ownership revisited by W7. PO emphasis: the website and vNext are all Impilo — one cohesive ecosystem/OS; the seam must never read as two products. |
| PD-2 | R1 Reachable entitlements + retention | R1 may **save journeys/drafts, receive notifications/reminders, and track claim-coded items** (complaints, SOS callbacks, applications). **No health-record access of any kind.** Drafts expire after 90 days of inactivity. |
| PD-3 | Anonymous SOS dispatch policy | **Callback verification required before dispatch.** The anonymous request is captured immediately and never blocked; a dispatcher/responder must reach the callback number before resources move. Rate-limits + coarse location until callback stand. (W2 SOS workstream scoped accordingly.) |
| PD-4 | Citizen health-content SoR | **Guidance + clinical-knowledge own it.** clinical-knowledge-platform owns clinical content, guidance-service owns citizen-language education; W2 adds public read lanes; no new CMS service. |
| PD-5 | Journey-draft SoR beyond a session | **Sovereign draft owner.** Durable drafts are records: W3 runs proof-of-no-owner and, if none owns them, establishes an owning home (not the stateless BFF — see `experience-bff has no datasource` constraint). Client-side intent (W1) is unaffected. |
| PD-6 | W5 financing scope | **Full rails**: public plan-browse + live authenticated enrolment, eligibility, bills, receipts, waivers on coverage/COSTA/MusheX. NHI remains a configured-payer placeholder until legislation lands. |
| PD-7 | Guest-cart semantics | **Client-side guest cart, claimed at sign-in** (converted to a server cart at R1/R2 via the intent handoff). No anonymous server-side cart state. |
