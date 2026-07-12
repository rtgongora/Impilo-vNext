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
| `/internal/v1/public/gateway/guidance/**` | guidance `PublicGuidanceController` (explain-steps + public education) | W1 | Nompilo escalation explainers, no personalization |

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
