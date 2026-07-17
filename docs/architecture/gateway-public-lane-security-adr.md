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
| `/internal/v1/public/gateway/find-care/**` | tuso `PublicFacilityController` (service-facet search + profile) + ndila distance-matrix, composed by `FindCareOrchestrationService` | W2 | service-aware access-to-care search; read-only composition; abuse note below |
| `/internal/v1/public/gateway/guidance/**` | guidance `PublicGuidanceController` (explain-steps + public education: topic index, category filter, article read) | W1 (education article/category reads W2) | Nompilo escalation explainers + citizen-language health-information articles (PD-4: guidance owns citizen education); no personalization |
| `/internal/v1/public/gateway/practitioners/**` | varapi `PublicPractitionerVerificationController` (verify-by-registration-number) | W2 | exact-match only (enumeration resistance); miss = 200 with uniform NOT_FOUND shape — no existence oracle |
| `/internal/v1/public/gateway/find-care/facilities/{id}/practitioners` | varapi `PublicFacilityPractitionersController` via `FindCareOrchestrationService.verifiedProvidersAtFacility` | W5 | verified providers at a facility; pure composition passthrough of varapi's allow-listed register fields only (`displayName, profession, cadre, roleTitle, registrationNumber, registerStatus, registeredSince, registrationExpiryDate, licenceStatus, licenceValidFrom, licenceValidTo, registeringAuthority`, all nullable) — no other service's PII, no availability/slots; response `{"providers":[...]}`; unknown/empty facility = 200 `{"providers":[]}` (empty-list-on-miss — no 404, no existence oracle); covered by the `/internal/v1/public/` prefix (no new Envoy route) |
| `/internal/v1/public/gateway/sos` (POST) | daidzai SOS intake via `PublicSosIntakeService` → `EmergencyController.createRequest` | W2 | **anonymous WRITE** (ADR §5, PD-3); abuse note below |
| `/internal/v1/public/gateway/sos/{reference}` (GET) | daidzai `PublicEmergencyStatusController` via `PublicSosIntakeService` | W2 | disclosure-limited status read by the 202 receipt reference (reference/status/coarse-stage/createdAt/callback-pending only — no callback number, description, location, or subject); miss = 404 |
| `/internal/v1/public/gateway/feedback` (POST + claim-code GET) | rito `PublicCaseIntakeController` via `PublicFeedbackIntakeService` | W4 | **anonymous WRITE** (`gateway-feedback-claim`); abuse note below |
| `/internal/v1/public/gateway/advisory/**` | guidance `AdvisoryResolveController` via `PublicGatewayAdvisoryBffController` | W4 | Nompilo service-advisory resolve (GET, PUBLISHED/in-window/audience=public only) + impression/dismiss (POST) — advisories are DATA; impression payload allow-listed to advisoryId + event + opaque anon dismissal key (no PII) |
| `/internal/v1/public/gateway/get-involved/**` | participation `PublicContributionController` via `PublicGatewayGetInvolvedBffController` | W4 | Citizen "Get Involved" co-design: anonymous idea/experience submit (**anonymous WRITE**, `gateway-get-involved-claim`) with claim-code status GET; moderated public idea board GET; anonymous board support (**anonymous WRITE**, opaque supporter key); open testing-cohort GET + anonymous enrol (**anonymous WRITE**). Abuse note below; disclosure-limited (no claim-code hashes, submitter refs, or metadata) |
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

**Anonymous-write abuse note — `POST /internal/v1/public/gateway/get-involved/**`:**
the co-design lane is the "something could be better" surface (distinct from feedback's
"something went wrong"). Its three anonymous writes — contribution submit, board
support, cohort enrol — are rate-limited per-IP (5 / 600s) and globally (60 / 60s) in
Redis, and, like feedback, fail **CLOSED** when the limiter store is down (co-design is
not life-safety). Submittable types are allow-listed (`IDEA`, `EXPERIENCE`); title,
free-text, beneficiary/need-area, and contact are length-capped (512/8000/255). A
submission returns a one-time claim code generated in participation and stored only as a
SHA-256 hash (`contribution.claim_code_hash`, V001); the reference alone never unlocks
status. Submitter identity is never captured (`ANONYMOUS` forced); volunteered contact
goes into contribution metadata only. The public idea board (GET) shows **only**
moderation-`APPROVED` contributions; support uses an opaque supporter key (no PII) and is
idempotent per supporter. Status reads
(`GET /internal/v1/public/gateway/get-involved/contributions/{claimCode}`) are
disclosure-limited and share a per-IP window to throttle probing.

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
| `gateway-get-involved-claim` | W4 | anonymous idea → claim code → status check; board shows only APPROVED; anonymous support increments once |
| `gateway-cover-pay` | W5 | enrol → eligibility → bill → step-up → receipt; public plan-browse has zero member data |
| `gateway-marketplace-guest` | W6 | anonymous browse → guest cart → sign-in claim → order → track |
| `gateway-delegated` | W7 | delegated booking + anti-self-grant negative test |

## Find-care public lane (service-aware access-to-care search)

The find-care lane (`/internal/v1/public/gateway/find-care/**`, GET-only, anonymous) upgrades
the flat facility directory into an access-to-care search without adding any new source of truth.
It is a **read-only composition** governed by the same discipline as the rest of this ADR:

- **Registry truth, never a guess.** A facility appears for a service only if TUSO (the facility
  registry, system of record) holds an **ACTIVE** matching capability for it
  (`FacilityCapabilityRepository.findFacilityIdsByActiveServiceToken` → `FacilityService.searchFacilitiesByService`,
  exposed via the existing `PublicFacilityController` `search` with an optional `service` facet).
  The BFF (`FindCareOrchestrationService`) composes; it does not decide what a facility offers.
- **Deterministic interpretation (with an off-by-default NL upgrade seam).** Free text is mapped to a
  capability token by a seeded, in-code synonym table (`FindCareServiceTaxonomy.interpret`) — no PII,
  no model, fully auditable. The single `interpret(...)` seam is where a semantic path plugs in
  without touching the orchestrator. That seam is now wired to the governed structured-output
  endpoint of **llm-orchestration-service** (`POST /internal/v1/llm/structured`, the system of record
  for model routing — not guidance-service) via `LlmStructuredServiceClient`, behind the feature flag
  `impilo.findcare.llm-interpret.enabled` (**default false**, so the deterministic path always ships).
  When enabled, the model runs only after the synonym table finds nothing, and its output is fenced to
  the existing token vocabulary (`KNOWN_TOKENS`) — it can never invent a capability the registry
  search does not understand. Any failure or all-unknown result falls back to the deterministic map
  (honesty gate). An unrecognized phrase still degrades to a plain name search (surfaced in `notes`).
- **Virtual-care truth (read enrichment, ACTIVE-gated).** The search response carries a
  `virtualCare` option set + `virtualCareAvailable` flag, sourced from the TUSO virtual-service
  registry ("virtual hospitals") filtered to the **ACTIVE** lifecycle only (never
  CONFIGURED/SUSPENDED) and, when a province was shared, to that province. It is surfaced as its own
  option set — **never attached to a specific facility card**, because the register does not hold
  per-facility telemedicine — and is capped (10) and PII-free (name/level/province/service-lines). The
  lookup is best-effort: any failure omits virtual care rather than fabricating it. This stays on the
  same GET `/find-care/search` read (no new public path). Actually *starting* virtual care is a
  resource-moving, signed-in step (see the access-to-care actions section below), not an anonymous one.
- **Service-aware distance.** Only facilities that offer the service are ranked. When the caller
  shares a location, travel distance + ETA come from Ndila's distance-matrix (geography/routing
  SoR); if Ndila is unavailable the lane falls back to a straight-line (haversine) estimate for
  distance and leaves ETA null — it never fabricates a travel time. No location → no distance,
  stated in `notes`.
- **Never presents stale status as live.** Register `operationalStatus` is returned flagged
  `operationalStatusUnverified=true`; `openNow` is `null` (operating hours are not in the search
  summary — only the profile endpoint carries them), and per-facility telemedicine availability is
  `null` (not yet held in the register). These honest gaps are surfaced, not smoothed over.
- **Abuse note.** Reads are lighter than the SOS write lane: a per-IP fixed window
  (`60`/`60s`) and a global window (`600`/`60s`) in `FindCareOrchestrationService`, both
  **fail-open** (a public read must not be lost because Redis is down). Free-text is capped at 200
  chars and page size at 50; the distance fan-out is capped at 50 candidates per call. Responses
  are PII-free (facility name/type/level/district/province/coordinates only — no contacts, no
  internal identifiers, no internal service names).

## Access-to-care actions (find-care journey → signed-in, resource-moving steps)

The find-care **search** is anonymous; the **actions** that follow it — booking an appointment,
starting virtual care, requesting patient transport, and reading referral movement — move real
resources and therefore require a person anchor. They are **not** on the public gateway lane. This is
the graduated-friction doctrine in practice ("care before coverage; trust rises with the action;
help before identity"): a guest can search freely, and the public find-care UI routes a
"Book" / "Start virtual care" / "Request transport" tap to sign-in with a `returnTo` that lands back
on the same facility/service selection (the find-care journey store preserves it), then completes the
action authenticated.

| Route family | Auth | Backing | Notes |
|---|---|---|---|
| `POST /internal/v1/citizen/access-to-care/appointments/request` | CITIZEN | booking-service `AppointmentController` (`createCitizen`) | booking-service is the **system of record for citizen appointments**. No real citizen slot-availability is published anywhere in the estate, so this is a governed **REQUEST**: it persists `REQUESTED` and returns a reference — a slot/confirmation is never fabricated. Reschedule/cancel use the existing citizen appointment endpoints. |
| `POST /internal/v1/citizen/access-to-care/transport/request` | CITIZEN | nhume-service (`delivery_type=PATIENT`) via `NhumeServiceClient` | planned (non-emergency) patient transport tied to a facility and, optionally, a referral (`clinical_context_ref`). Submitted as a pending request; nhume owns dispatch and the receipt never claims a courier moved. **Emergency stays on the Daidzai SOS lane — not duplicated here.** |
| `GET /internal/v1/citizen/access-to-care/transport/{transportRef}` | CITIZEN | nhume-service via `NhumeServiceClient` | citizen-safe coarse status (REQUESTED/ASSIGNED/PICKUP/EN_ROUTE/ARRIVED/CANCELLED) only. |
| `GET /internal/v1/citizen/access-to-care/referrals/{id}/movement` | CITIZEN | referral-service (+ nhume transport leg) via `ReferralServiceClient` | read-only citizen-safe movement (referral status mapped to citizen language + safe receiving-facility name + optional transport leg). Internal clinical/ops payload is never echoed. |

Starting virtual care reuses the existing authed citizen telehealth request
(`POST /internal/v1/mobile/citizen/telehealth/sessions` → PCT teleconsult intake) — no new
appointment/telemedicine truth is created. Abuse controls, ownership binding (transport reads are
IDOR-guarded because the sovereign reads are only tenant-scoped), body caps, and PII-safe shaping live
in `CitizenAccessToCareService`, mirroring `PublicSosIntakeService`. The rate-limiter fails **closed**
here (a resource-moving write is not life-safety — emergencies use the SOS lane, which fails open).

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
