# ADR — Gateway Public-Lane Security Architecture

**Status:** Accepted (W0 of
[`docs/roadmaps/health-services-gateway-roadmap.md`](../roadmaps/health-services-gateway-roadmap.md))
**Date:** 2026-07-12
**Doctrine:** [`health-services-gateway-doctrine.md`](../doctrine/health-services-gateway-doctrine.md)
§4 (trust ladder), §3 (public naming)

## Context — the verified edge topology

Three facts constrain how anonymous (R0) traffic can safely enter the estate:

1. **The live host's public edge is Traefik → experience-bff, not Envoy.** On
   `impilo.mohcc.gov.zw`, Traefik routes `/internal`, `/actuator`, `/health` directly to
   `experience-bff:8160` and everything else to `one-ui-shell:3000`
   (`deploy/tls/mohcc-gov/ingressroutes.yaml`). The BFF's Spring Security permitAll list
   is therefore the **real** public gate on the live path.
2. **Envoy ext_authz is fail-closed for everything it fronts.** Both configs call the
   tshepo-authz gRPC PDP with `failure_mode_allow: false`, and the PDP hard-denies
   requests missing trust headers. There is no anonymous tier in the PDP, by design.
3. **The legacy `/v1/public/*` routes are not actually public through Envoy.** Correction
   to earlier survey claims: `infra/envoy/envoy.yaml` routes `/v1/public/verify` and
   `/v1/public/share` carry **no** `ExtAuthzPerRoute` override (the only per-route
   override in the file is the `/mushex/v1/` block, which is empty — an anomaly noted
   below, owned by the mushex lane). `envoy-runtime.yaml` (compose) has neither the
   routes nor any override. Anonymous requests to these prefixes **through Envoy** are
   denied by the PDP; the flows work on the live host only because Traefik bypasses
   Envoy for the shell/BFF path.

## Decision

**The BFF is the only public API front; downstream stays fail-closed.**

1. **One public namespace.** All anonymous gateway APIs live under
   **`/internal/v1/public/gateway/**`** (joining the two legacy BFF public prefixes
   `/internal/v1/public/patient-shares/**` and
   `/internal/v1/public/facility-certificates/**`, which stay as-is). New public
   endpoints under any other prefix are prohibited.
2. **No PDP changes.** No PUBLIC actor tier in tshepo-authz, no synthesized guest trust
   headers toward downstream services. The PDP's hard-deny remains the estate backstop.
3. **Service-side public-safety rule.** A BFF public controller may only call a
   downstream endpoint that lives in a **`Public*Controller`** class in the owning
   service (template: tuso `PublicFacilityController`). Public-safety — allow-listed
   DTOs, no PII, no internal service names — is a reviewable property of the owning
   service, not a BFF-side hope.
4. **Envoy parity.** Wherever Envoy fronts traffic (compose :10000, any mesh path), the
   route `prefix: /internal/v1/public/` → `experience_bff` carries an explicit
   `ExtAuthzPerRoute disabled: true` **and strips externally supplied trust headers**
   (`x-actor-id`, `x-actor-type`, `x-assurance-level`, `x-purpose-of-use`,
   `x-subject-id`, `x-escalation-grant-id`, and the visibility outputs) so an anonymous
   caller can never smuggle trust context. This route MUST exist identically in
   **both** `infra/envoy/envoy.yaml` and `infra/envoy/envoy-runtime.yaml`
   (ENVOY-GATE: the two files change together in every public-route slice).
5. **Anonymous writes are exceptional.** Only feedback intake and emergency SOS may
   accept anonymous writes; each requires its own abuse note (rate limits per route and
   per identifier, claim-code issuance, OTP-callback friction, dispatcher "unverified"
   flag). Per PD-3, SOS requests are captured anonymously but dispatch requires callback
   verification.
6. **Naming enforcement.** Public payloads, routes, and pages must pass the
   internal→citizen naming dictionary (`config/gateway-public-naming.yml`);
   `scripts/guard/check-public-lane.sh` enforces this plus route-parity and
   registry-coverage checks (informational in W0, blocking once W1 lands).

## Public contract registry

Every permitAll route family must have a row here (the guard checks this file).

| Route family | Backing | Since | Notes |
|---|---|---|---|
| `/internal/v1/auth/**` | BFF → Keycloak brokering | pre-gateway | login/register/refresh |
| `/internal/v1/bootstrap/**` | BFF | pre-gateway | first-admin bootstrap |
| `/internal/v1/ndila/tiles/**` | ndila | pre-gateway | map tiles, no PII |
| `/internal/v1/public/patient-shares/**` | VITO patient shares | pre-gateway | claim-code pattern |
| `/internal/v1/public/facility-certificates/verify/**` | tuso `PublicCertificateVerificationController` | pre-gateway | licence verify |
| `/v1/public/patient-safety/**` | patient-safety | pre-gateway | public reports lane |
| `/internal/v1/public/gateway/**` | per-pillar `Public*Controller`s | W1+ | THE gateway lane (GET-only until an anonymous-write wave); sub-paths below |
| `/internal/v1/public/gateway/facilities/**` | tuso `PublicFacilityController` (search + profile) | W1 | disclosure-limited facility directory |
| `/internal/v1/public/gateway/guidance/**` | guidance `PublicGuidanceController` (explain-steps + public education: topic index, category filter, article read) | W1 (education article/category reads W2) | Nompilo escalation explainers + citizen-language health-information articles (PD-4: guidance owns citizen education); no personalization |
| `/internal/v1/public/gateway/practitioners/**` | varapi `PublicPractitionerVerificationController` (verify-by-registration-number) | W2 | exact-match only (enumeration resistance); miss = 200 with uniform NOT_FOUND shape — no existence oracle |
| `/internal/v1/public/gateway/sos` (POST) | daidzai SOS intake via `PublicSosIntakeService` → `EmergencyController.createRequest` | W2 | **anonymous WRITE** (ADR §5, PD-3); abuse note below |
| `/internal/v1/public/gateway/sos/{reference}` (GET) | daidzai `PublicEmergencyStatusController` via `PublicSosIntakeService` | W2 | disclosure-limited status read by the 202 receipt reference (reference/status/coarse-stage/createdAt/callback-pending only — no callback number, description, location, or subject); miss = 404 |
| `/internal/v1/public/gateway/feedback` (POST + claim-code GET) | rito `PublicCaseIntakeController` via `PublicFeedbackIntakeService` | W4 | **anonymous WRITE** (`gateway-feedback-claim`); abuse note below |
| `/internal/v1/public/gateway/advisory/**` | guidance `AdvisoryResolveController` via `PublicGatewayAdvisoryBffController` | W4 | Nompilo service-advisory resolve (GET, PUBLISHED/in-window/audience=public only) + impression/dismiss (POST) — advisories are DATA; impression payload allow-listed to advisoryId + event + opaque anon dismissal key (no PII) |
| `/internal/v1/wallet/bill-contributions/{shareToken}` (GET only) | mushe bill-contribution view via `CitizenCardController` | W4 | fundraiser share-link read (claim-token pattern, unguessable token); read-only, donations require sign-in |

**Anonymous-write abuse note — `POST /internal/v1/public/gateway/sos`:** rate-limited
per-IP (5 / 600s fixed window) and globally (60 / 60s), both in Redis mirroring the
OTP lane's `enforceWindow`; the callback number is REQUIRED and normalized to E.164
(reusing `ContactOtpService.normalize`), a blank/invalid number is a 400 that never
reaches daidzai; free-text fields are length-capped (description 2000, location 512).
The rate-limiter fails **open** (unlike OTP) — a life-safety request is never dropped
because Redis is down. Daidzai captures the request as `PUBLIC_ANONYMOUS` and holds it
`AWAITING_CALLBACK`: dispatch is gated until a dispatcher verifies the callback (PD-3).

**Anonymous-write abuse note — `POST /internal/v1/public/gateway/feedback`:**
rate-limited per-IP (3 / 600s) and globally (30 / 60s) in Redis; case types are
allow-listed (`COMPLAINT`, `SAFETY_CONCERN`); title/description/contact are
length-capped (200/4000/255). Unlike SOS, this lane fails **CLOSED** when the limiter
store is down — feedback is not life-safety, and an anonymous write lane without
working abuse controls stays shut. The one-time claim code is generated in rito and
stored only as a SHA-256 hash (`rit_case.claim_code_hash`, V002); the case reference
alone never unlocks status (its 6-char suffix is too guessable to be a secret).
Status reads (`GET /internal/v1/public/gateway/feedback/{claimCode}`) are
disclosure-limited (reference/type/status/timestamps) and share a per-IP window to
throttle brute-force probing. Reporter identity is never captured on this lane
(`anonymous=true` forced); volunteered contact goes into case metadata only.

Legacy Envoy-only prefixes `/v1/public/verify` and `/v1/public/share` are **deprecated
as public entries** (not anonymous-capable through Envoy today; live traffic reaches
these capabilities via the shell/BFF). They remain routed for compatibility; do not add
new consumers.

## Golden-journey `gateway-*` scenario family (plan)

Extends the existing golden-journey harness conventions (`reports/journeys/`):

| Scenario | Wave | Proof |
|---|---|---|
| `gateway-shell` | W1 | anonymous → `/welcome` intent home → pillar card → facility search → profile; zero auth redirects; naming guard clean |
| `gateway-reachable` | W1 | phone → OTP → R1 session → `CONTACT_VERIFIED` attestation → PDP denies an R2 action (negative proof) |
| `gateway-intent-preserved` | W1 | anonymous intent → sign-in → same intent id restored post-auth |
| `gateway-public-reads` | W2 | facility search/profile + practitioner verify + health-info article, all anonymous, zero PII/internal names |
| `gateway-emergency-anon` | W2 | anonymous SOS → incident `unverified` → callback-before-dispatch honoured → 429 at rate threshold |
| `gateway-book-with-escalation` | W3 | anonymous → R1 → R2 upgrade → booking confirmed with intent continuity |
| `gateway-feedback-claim` | W4 | anonymous complaint → claim code → status check without sign-in |
| `gateway-cover-pay` | W5 | enrol → eligibility → bill → step-up → receipt; public plan-browse has zero member data |
| `gateway-marketplace-guest` | W6 | anonymous browse → guest cart → sign-in claim → order → track |
| `gateway-delegated` | W7 | delegated booking + anti-self-grant negative test |

## Gate handshake notes (W0)

- **MSIKA-GATE:** the marketplace lane's completion wave has landed on the default
  branch (`5f2142b98`, `ead284785`); the gate is expected to be satisfiable at W6 with a
  short cart/order compatibility review rather than a long pause.
- **KEYCLOAK-GATE:** W1 confirmed to require zero realm changes.
- **Anomaly (not this lane):** the `/mushex/v1/` route in `envoy.yaml` carries an empty
  `ExtAuthzPerRoute` block (no `disabled`/`check_settings` field). Owned by the mushex
  gateway-neutrality lane; flagged, not touched here.

## Consequences

- Anonymous capability = BFF permitAll + service-side `Public*Controller` + Envoy
  bypass-with-strip, all three in one reviewable slice per endpoint family.
- The PDP and every sovereign service stay fail-closed; compromise of the public lane
  yields only what `Public*Controller`s expose by design.
- The guard script turns the registry above into an enforced contract.
