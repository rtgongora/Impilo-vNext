# Gateway Wave 1 — Live Runtime Rig Journal (2026-07-12)

Rig: scratch Postgres 16 (:15733) + Redis 7 (:16499) + Keycloak 25.0 (:18080,
`realm-impilo-preview.json` imported virgin); five service jars packaged clean from
HEAD and booted with real JWT enforcement (NO disable-oauth flags, NO allow-anonymous):
tuso (:28084), guidance (:28260), identity-assurance (:28201), notification (:28200),
experience-bff (:28460 — 28160 was occupied by an unrelated BFF process from the main
checkout). No Kafka broker (all four Kafka-configured services boot and serve without
one; outbox publishers retry loudly, rows persist). Journey script + evidence JSONs
alongside; boot recipe in `rig-boot.sh`.

**Result: 21/21 journey checks green** (J1 gateway-shell 8/8, J2 gateway-reachable
11/11, J3 SSR smoke 2/2) after four rig-caught fixes. Supplementary:
`scripts/guard/check-public-lane.sh` passes (envoy parity + registry coverage; one
pre-existing WARN on a tuso mention in a BFF javadoc comment, non-payload).

## Proven LIVE (real services, real HTTP, real Keycloak, psql read-backs)

### J1 gateway-shell — anonymous public reads (R0)

1. **Bare `curl`, ZERO headers → 200 facility page** — the full chain
   permitAll → synthesized platform headers → BFF `PublicGatewayFacilityBffController`
   → tuso `PublicFacilityController` → Postgres, with real seeded facilities returned.
   (Only true after fixes #1 and #2 below — at HEAD-of-wave this returned 400, then 502.)
2. **Disclosure-limited profile** — `/facilities/{id}/profile` → 200 with
   contacts/identifiers/readiness/audit/ownership/description all null (the redacted
   public DTO mapper holds at runtime).
3. **Nompilo escalation explainers (V012)** — `/guidance/explain-steps` → 200 with all
   four W1 explainers (`verify-contact`, `signin-to-book`, `signin-to-personal`,
   `step-up-results`); `/explain-steps/signin-to-book` carries the four doctrinal parts
   (why / levelLabel / whatNext / howToGetHelp). guidance-service's own
   `/v1/public/guidance/**` permitAll worked unmodified — the one downstream lane the
   wave got right first time.
4. **Naming guard** — whole-word scan of every public payload against
   `config/gateway-public-naming.yml`: zero internal service names.
5. **Negatives hold** — anonymous GET to an authenticated route (citizen coverage) →
   401; bare curl to it → 400 (v1.1 header contract precedes authn — see honest notes);
   POST into the gateway namespace → 401 (GET-only lane).

### J2 gateway-reachable — R1 phone-OTP registration

6. **OTP issue → 202 SENT, fail-closed honoured** — the challenge lands in Redis
   (SHA-256 hash only) and the delivery is a REAL authenticated enqueue into
   notification-service; `ns_notifications` row persisted with template
   `CONTACT_VERIFY_OTP` + the OTP in `vars_json` (that DB row is how the rig reads the
   code — the active `sms_log` stub provider deliberately logs only a loud
   "NOT DELIVERED" warning without the body, then the worker marks the row SENT).
7. **Wrong code → 401 OTP_INVALID.**
8. **Correct code, purpose=REGISTER → 200 with auto-login token** — CITIZEN user
   created server-side in Keycloak (username = E.164 phone; id equals the auto-login
   subject), ROPC password-grant token returned, refresh cookie set.
9. **CONTACT_VERIFIED attestation in the assurance SoR** — psql read-back of
   `ia.attestations`: `CONTACT_VERIFIED|VERIFIED` with the MASKED value only
   (`+263*******17`); the raw phone appears nowhere in IA. **LOA not raised**: zero
   `ia.assurance_record` rows — R1 = LOA1 + attestation, exactly per doctrine §4.2.
10. **Read-back API** — `GET /internal/v1/attestations/contact-verified/{accountId}`
    with the citizen's own fresh JWT → `contactVerified=true, channel=PHONE`. (Direct
    to IA; the BFF exposes no proxy route for it in W1.)
11. **Rate-limit negative** — immediate re-request for the same number → 429 with
    `Retry-After` (cooldown + fixed windows live in Redis).
12. **Replay negative** — the consumed OTP → 400 OTP_EXPIRED (single-use held).
13. **Registration failure-path is atomic (observed live)** — while fix #4 was still
    missing, attestation recording failed and the BFF rolled the just-created Keycloak
    user back and returned 503 ATTESTATION_UNAVAILABLE; no half-registered account
    survived. The rollback path is real, not just coded.

### J3 gateway-intent-preserved — SSR smoke (see result at bottom)

## Bugs the rig CAUGHT and FIXED (committed on this branch)

1. **`fix(bff)` 08505e376 — the "public" lane 400'd truly anonymous callers.** The
   BFF's companion `V11HeaderFilter` hard-requires the four v1.1 headers on all of
   `/internal/v1/**`, so bare deep-links/curl got `400 MISSING_REQUIRED_HEADER` from
   the gateway namespace — public only for the web shell, which synthesizes headers
   client-side. New `PublicGatewayAnonymousDefaultsFilter` (GET-only, scoped to
   `/internal/v1/public/gateway/**`, ordered just before the V11 filter) synthesizes
   the public tenant / national pod / fresh ids for missing headers only.
2. **`fix(tuso)` f0aaab1a3 — tuso never opened its public lane.** No permitAll for
   `/v1/public/facilities/**`, so the ADR's own template controller
   (`PublicFacilityController`) answered 401 to the BFF's anonymous-safe calls and the
   W1 facility directory 502'd end-to-end (this also affected the pre-existing
   `PublicCertificateVerificationController` under the same prefix). guidance-service
   got the equivalent rule in this wave; tuso was missed.
3. **`fix(bff)` 7f2bb804f — OTP delivery hop arrived unauthenticated at
   notification-service.** The R1 OTP request is pre-auth by definition (no inbound
   Authorization to forward) while `/internal/v1/notify` is fail-closed
   JWT-authenticated → every issue 401'd downstream and surfaced as 503
   OTP_DELIVERY_UNAVAILABLE: the whole R1 rung was dead in production security mode.
   The sibling identity-assurance hop already synthesized a service identity; the
   notification hop was missed. `ContactOtpService#deliver` now carries the
   impilo-backend service-account bearer + synthesized SYSTEM actor headers.
4. **`fix(bff)` 5b1291c47 — `IDENTITY_ASSURANCE_BASE_URL` never reached the client
   that uses it.** `ServiceEndpoints` binds `impilo.services.identity-assurance-base-url`,
   but the key only existed under the dead `orchestration-backlog` block, so the real
   `IdentityAssuranceServiceClient` always called `http://localhost:8201` — REGISTER
   rolled back with 503 on any off-localhost deployment. Key added at the correct level.

## Keycloak realm truths the rig exposed (rig-local mitigations, NOT committed — KEYCLOAK-GATE)

These reproduce on ANY virgin import of `deploy/helm/impilo-vnext/files/realm-impilo-preview.json`;
they are realm-content gaps, not gateway-wave code defects, and per KEYCLOAK-GATE they
need a governed realm decision rather than a rig commit:

- **Service-account tokens carry no roles → Keycloak Admin API 403.** The realm file
  defines its own `clientScopes` list with NO built-in `roles` scope (and no
  client-role protocol mappers), so `impilo-backend` client-credentials tokens have no
  `resource_access` claim and `POST /admin/realms/impilo/users` is 403 — user creation
  (contact registration AND the pre-existing governed-onboarding `createUser` lane) is
  dead against a fresh import. Rig mitigation: added an
  `oidc-usermodel-client-role-mapper` (realm-management roles) to impilo-backend via
  kcadm. Follow-up: add the roles scope/mapper to the realm file under KEYCLOAK-GATE.
- **Phone-only accounts cannot direct-grant: "Account is not fully set up".** Keycloak
  25's default declarative user profile marks `email` required for every user, so the
  phone-anchored CITIZEN user (no email by design) fails ROPC and auto-login falls back
  to 201 "Please sign in" — but interactive sign-in would force the person to add an
  email, undermining phone-first R1. Rig mitigation: relaxed the email/firstName/lastName
  requirement in the realm user profile via kcadm; auto-login then returns the full
  token envelope. Follow-up: a governed user-profile decision for phone-anchored
  accounts (KEYCLOAK-GATE).
- Realm-file secret placeholders (`${env.KC_CLIENT_SECRET_BACKEND}`) did not substitute
  on this rig's `--import-realm`; the client secret was set explicitly via kcadm.
  Worth verifying on the preview import path.

## Honest blockers / pending items (NOT proven here)

- **PDP-denies-R2 negative**: tshepo-authz is not in this rig; "R1 session is denied an
  R2 action by the PDP" remains pending until a rig/e2e run includes the authz plane.
  (The BFF-local negative — anonymous caller denied on an authenticated route — IS
  proven.)
- **Bare-curl 401 on authenticated routes**: a bare curl (no headers at all) to an
  authenticated route yields 400 (v1.1 header contract) rather than 401; with platform
  headers it is 401. Contract observation, not a security hole — both reject.
- **Real SMS delivery**: the rig runs the `sms_log` stub provider (loud no-delivery
  warning, row marked SENT). `http_sms` against a real gateway is deployment config,
  untestable offline.
- **Preview helm wiring**: `values-full-preview.yaml` gives the experience-bff NO
  `GUIDANCE_BASE_URL`, `IDENTITY_ASSURANCE_BASE_URL`, or `NOTIFICATION_BASE_URL`, so in
  the preview cluster all three W1 hops would hit localhost defaults inside the BFF pod.
  Deploy-lane follow-up (three env lines under `experienceBff.env`), deliberately not
  committed from this rig since it cannot be runtime-proven here.
- **Kafka legs**: IA/guidance/notification outbox events persist but do not publish
  (no broker in rig); consumers not exercised. Attestation/notification truth verified
  at the DB instead.
- **Full click-through intent restore** (click pillar card → login → land with intent
  restored) is browser behaviour already covered by the wave's 32 unit tests
  (gateway-intent + resolver); the rig proves the SSR surfaces + token decode route
  only, and does not fake a browser.

## J3 SSR smoke result

- `GET /welcome` → **200 SSR HTML containing "How can we help you today?" and all nine
  pillar titles** (the four "&"-titled cards ride in the RSC flight payload as
  `&`; evidence `j3-welcome-ssr.html`). The flight props visibly carry the
  click-time intents (`{"pillar":"cover-and-payments","goal":"view-coverage",
  "dest":"/coverage","from":"/welcome"}` etc.) — the intent wiring is in the SSR
  output, not just client code.
- `GET /auth/login?gwi=<token>` → **200**, token minted with the exact
  `gateway-intent.ts` encoding (base64url of `{pillar,goal,params,createdAt}`).
- Served via `next dev -p 3010` (BFF pointed at the rig), because **`next build`
  fails at HEAD** on a PRE-EXISTING type error outside this wave:
  `src/app/marketplace/orders/[id]/pay/page.tsx` exports `findIntentId`, an illegal
  Next.js page export (introduced by the msika buyer-lane commit `f725fc6a3`).
  Journaled as a follow-up (task chip spawned), not fixed here — msika lane, not
  gateway code.
- Full click-through intent restore stays with the wave's 32 unit tests, as scoped.
