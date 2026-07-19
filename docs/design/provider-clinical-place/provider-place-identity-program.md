# Provider + Place Identity Program — Decision Record & Wave Map

**Status:** APPROVED (PO, 2026-07-19) · Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
**Doctrine:** [Identity Journey Doctrine](../../architecture/identity-journey-doctrine.md) (PJ1–PJ18) ·
[Place Journey Doctrine](../../architecture/place-journey-doctrine.md) (FJ1–FJ9, SJ1–SJ6)
**Supersedes/updates:** stale reservations in [implementation-lane-plan.md](implementation-lane-plan.md) §3;
extends [tshepo-policy-contract-list.md](tshepo-policy-contract-list.md) (Appendix H).

The third identity program. The patient program landed the identity spine + Wave-F private matching; IATG
landed registry truth + adjudication + Trust Console under a PDP freeze. This program extends the same
load-bearing doctrine (claim-not-search, private matching, no candidate lists, second factor before binding,
multi-dimensional trust, projection-only public read) to **Provider ID** (varapi/vashandi) and **Place ID**
(tuso/indawo/ndila), and lands enforcement through the sanctioned trust-layer channels.

**PO rulings:** doctrine first, then interleave provider/place waves · passkey/WebAuthn deferred to W10
(joint Keycloak window) · IATG facility-claim eligibility enumeration leak: **harden** (approved behaviour
change to the live Wave-2 surface).

## Hard constraints

- `services/tshepo-service` **NO-TOUCH** (frozen legacy PDP; IATG lease invariant). Live PDP =
  `tshepo-authz-service`.
- CZO single-writer lock on `PolicyEngine.java` / `ExtAuthzGrpcService.java` / `AuthorizeController.java` /
  rego: policy lands **only** as `policy_rule` seed migrations (V031–V033 pattern) + `impilo.authz` rego
  (each with `*_test.rego`, SHADOW→ENFORCE at divergence≈0). CZO-LEAD queue (spec only):
  WORK-PRO-LIFE-ISOLATION, PRESCRIBE co-sign, SELF-TREATMENT-BLOCK, BREAK-GLASS,
  EMERGENCY-CARE-NEVER-BLOCKED.
- Person-proofing framework (PR1–PR4) is **consumed** from the patient program, never rebuilt here.
- Shared hot files (CompanionHeaders.java, api-client.ts, Keycloak realm, Envoy config) — coordinate with the
  live patient-ID sessions; realm changes in a joint window.
- Facility-mode lane ownership per [facility-mode-ownership-split.md](facility-mode-ownership-split.md)
  (provider lane ENTERS, place lane BUILDS; `FacilityModeContext` single producer).
- rtc-gateway = media only (decisions persist in the requesting registry). experience-bff stays stateless.
  org-registry stays relationship-only. New JVM tests are named `*Test` (surefire; `*IT` never runs).

## Migration reservations (verified heads, 2026-07-19)

| Service | Head | Reserved | For |
|---|---|---|---|
| varapi | V023 | **V024–V027** | W1 authorization-link + resolve hashes · W7 badge |
| vashandi | V007 | **V008–V009** | W5 engagement types · approval inbox |
| tshepo-identity | V003 | **V004** | W1/W3 scoped-token `token_kind` + `context_claims` |
| tshepo-offline | V001 | **V002** | W10 device-bound provider credential |
| tshepo-authz | V035 | **V036–V037** provider · **V038–V040** place | policy seeds (CZO-mediated) |
| tuso | V029 | **V030–V039** | W4/W6/W8/W9 |
| indawo | V009 | **V010–V019** | W2/W4/W6/W8/W9 |
| ndila | V003 | **V004–V006** | W2 anchor + site sync |

vito untouched (patient-program territory).

## Provider-lane decisions (D-P)

- **D-P1** Standalone `varapi.provider_authorization_link` mirroring vito `ClientAuthorizationLinkEntity`
  (link_type CLAIM|RECOVERY|ADJUDICATED_REBIND|GOVERNANCE_UNBIND · status · evidence_ref ·
  assurance_outcome · proofing_channel; one ACTIVE per provider via partial unique index; seeded from
  `claimed_health_id`). Rationale: preloaded providers hold a **synthetic** `impilo_health_id` (V010
  backfill from provider_ref); the column binding has no history/evidence/assurance and cannot serve
  disputes (PJ18) or recover-not-reissue (PJ17).
- **D-P2** varapi `ProviderRegistryResolveController` — INTERNAL `/v1/registry/resolve`
  (PROVIDER_ID|COUNCIL_REG|EC_NUMBER), uniform hit/miss envelope, 120 ms floor; `ProviderContactHasher` on
  the shared `HmacService`. BFF claim eligibility/preview rewired onto it (shapes preserved).
- **D-P3** Work-context token = extend tshepo-identity `ScopedToken`: `token_kind` (SERVICE|WORK_CONTEXT) +
  `context_claims` jsonb (facility/department/workspace/role_template/provider_public_id/purpose/
  session_assurance). Professional scope, supervision, live registry status are **resolved at the PDP per
  action**, never baked into the token. Context switch = revoke jti + reissue. TTL 15-min silent reissue
  (PO-revisable). Revocation teardown: tshepo-identity consumes the varapi privilege-revocation events →
  `revokeAllForActor`.
- **D-P4** Prominent "Health Provider Login" entrance; `/auth/login/provider-id` repurposed to
  identify-then-strong-auth (PIN as sole credential removed — satisfies `LOGIN-PROVIDERID-DENY`). Daily
  login v1 = password + TOTP step-up; passkey = W10.
- **D-P5** `varapi.provider_badge` (badge_serial, signed Ed25519 QR, status); badge = **account selector
  only**; lost badge revokes the serial only. Shared workstations: fast-user-switch tiles, persistent
  current-user banner, reauth-before-sign seam (BFF → `StepUpController`). No shared clinical accounts,
  ever.
- **D-P6** Suspended/restricted provider: authentication succeeds; the PDP denies Work; BFF serves an
  owner-only post-login work-eligibility summary (from `deriveStatusProjections()`); shell renders a
  `work_denied` remediation state. **My Life and My Professional are never lost.**
- **D-P7** vashandi `engagement_type` (PERMANENT|ROTATION|LOCUM|TELEMED|SUPERVISORY|TRAINING) +
  validity window + expiry sweep feeding the revocation pipeline; access-request approval materialises the
  assignment (with varapi scope check); transfer = end + new + token reissue.
- **D-P8** Public verify: badge-QR deep-links to `/verify/practitioner` (register facts only); the verify
  directory and the claim journey are never linked.
- **D-P9** Break-glass = capture (reason/patient-ctx/duration) + audit + review on the existing BFF
  mapper; rego queued CZO-LEAD. Offline = tshepo-offline device-bound credential issued only during an
  online session; cached status; high-risk denylist; no new identity offline.
- **D-P10** Rego (WS-OPA): LOGIN-PERSON-FIRST, LOGIN-PROVIDERID-DENY, LOGIN-ANTI-ENUM, IDRES-SILENT-CHAIN,
  WORK-REQUIRES-ASSIGNMENT / CONTEXT-SELECT / WORKSPACE-ENTER (verify vs existing modules first), net-new
  **WORK-TOKEN-CONTEXT-MATCH** and **BADGE-NEVER-AUTHORISES**. Seeds V036 (login), V037 (work-context).

## Place-lane decisions (D-L)

- **D-L1** Shared geospatial anchor = **Ndila** (no third place master): `ndila_place_anchor` + nullable
  `anchor_id` on `ndila_locations`; `NdilaSiteMasterSyncService` brings `INDAWO/SITE` rows in (mirror of the
  facility sync, reusing the geocode review queue). Anchor assignment is stewarded. Ndila stays geo-only.
- **D-L2** Typed Tuso↔Indawo links = `ind_place_links` in **Indawo** (single writer): left/right
  (TUSO_FACILITY|INDAWO_SITE)+id, **enum** link_type (LOCATED_WITHIN, SAME_CAMPUS_AS, CONTAINS,
  HOSTS_SERVICE_POINT, TEMPORARY_SERVICE_AT, REGULATED_COMPONENT_OF, REPLACED_BY, SERVES), validity,
  provenance, steward; internal both-direction read API + `impilo.indawo.place_link` events; tuso consumes
  read-only. `ind_site_relationships.rel_type` gets enum-tightened.
- **D-L3** Premises: keep BOTH models (tuso `facility_premises` = HPA occupancy axis; `ind_sites` =
  category-regulated site SoR); cross-express via place links; never auto-create across registries.
- **D-L4** Claim hardening on the V017 appointment rail: eligibility goes **generic** until verified person
  identity + one proof-of-authority factor (OTP-to-record-contact, ACTIVE-admin invitation token, document
  cross-check vs HPA data, place-verification event). `facility_admin_appointment` gains role enum
  (FACILITY_VIEWER|DATA_STEWARD|SERVICE_CONFIG_MANAGER|FACILITY_ADMINISTRATOR|REGULATORY_LIAISON) +
  `expires_at` + evidence refs; unique-ACTIVE per (facility, person, role). Indawo `SiteAssignmentEntity`
  mirrored (SITE_VIEWER|SITE_OPERATOR|SITE_ADMINISTRATOR|REGULATORY_LIAISON). Recovery (FJ8) = claim-type
  flag, never a new record.
- **D-L5** Silent duplicate detection: extract `FacilityMatchService` from `FacilityMasterImportService`
  (trgm + alias exact + Ndila proximity); registration blocks silently on credible match → steward case
  (V015 review pattern). `SiteMatchService` mirrors with category-aware geo-dominant thresholds.
- **D-L6** Place verification: evidence in document-service (refs only); decisions in
  `facility_verification_case` / `site_verification_case` (ClientVerificationReview pattern); GPS
  plausibility vs Ndila anchor; video via rtc-gateway with decision-only retention.
- **D-L7** QR credential: `facility_credential` / `site_credential`, locally-held Ed25519 signing (vito
  `QrSigningService` **pattern**, replicated — no vito dependency); public verify endpoints beside
  `PublicCertificateVerificationController`; revoke on trust collapse.
- **D-L8** Trust dimensions = new `facility_trust_dimension` / `site_trust_dimension` (9 graded dimensions,
  evidence_ref, materialised); `facility_source_legitimacy` + `platformAccessAllowed` unchanged as the
  gatekeeping input.
- **D-L9** Inspection engines stay separate (HPA in tuso; site engine in indawo — different competent
  authorities). V019 checksum content-catalogue format for Indawo checklists = later consolidation note.
- **D-L10** Public projections: redaction-at-read formalised; Indawo per-category declarative allowlists
  (`ind_site_disclosure_policy` + `SitePublicProjectionService`); complaints via rito claim-codes with
  rito→indawo private matching; complainant identity only in the rito case.

## Wave map (interleaved; one commit-set per wave)

| Wave | Lane | Scope |
|---|---|---|
| W0 | both | doctrine + reservations + this record + coordination broadcast |
| W1 | provider | authorization-link (V024) · private resolve · revocation teardown · login rego + V036 |
| W2 | place | ndila anchor (V004–V005) + site sync · indawo `ind_place_links` (V010) + events |
| W3 | provider | WORK_CONTEXT token issuance · Health Provider Login entrance · work_denied UX · V037 |
| W4 | place | trust dimensions (tuso V030–V031, indawo V011–V012) · claim/anti-enum hardening (IATG leak fix) · V038 |
| W5 | provider | vashandi engagement types (V008–V009) · access-request→assignment · transfer |
| W6 | place | FacilityMatchService · registration + verification cases (tuso V032–V033, indawo V013–V014) · V039 |
| W7 | provider | badge (V026) + shared workstation + reauth-before-sign · badge-QR public verify |
| W8 | place | FJ5–FJ7, FJ9, SJ3–SJ6 (tuso V034+, indawo V015+) · V040 |
| W9 | place | QR credentials + public projections (tuso V036, indawo V017–V018) |
| W10 | provider | passkey (joint Keycloak window — **DEFERRED**, see note) · break-glass (**CZO-queued**) · offline credential (tshepo-offline V002 ✅) |

### W10 status (2026-07-19)

- **Offline provider device credential — DONE** (tshepo-offline V002
  `provider_device_credential` + `ProviderDeviceCredentialService`): issued only from an
  authenticated online session (no cold issuance), caches a provider/facility/role status
  snapshot, restricts a high-risk denylist (prescribe/sign/close-sensitive/dispense-controlled/
  break-glass/consent-change) offline, supersedes prior device credential; **never mints identity
  offline**. Default TTL 72h (PO open question). 70 tests green.
- **Passkey/WebAuthn — DEFERRED to a joint Keycloak realm window.** The realm import is a hot
  shared file with the patient-ID sessions; WebAuthn needs realm-config + FE ceremony + a recovery
  path, and daily login v1 (password + TOTP step-up + badge-tap-as-selector) already ships. To be
  scheduled with the patient sessions as a coordinated realm change; the login preference order in
  D-P4 becomes passkey-first at that point.
- **Break-glass — LANDED (2026-07-19).** The `BREAK-GLASS` rule (CZO-LEAD) was authored on the
  single-writer PDP surface via the identity/policy handoff (CZO cluster dormant since 2026-06-26).
  A new PolicyEngine **Step-4.5 doctrine guard** (`evaluateBreakGlassAccess`, composing with
  `evaluateDelegation`/`evaluateSelfTreatment`) now enforces, on the `purpose=BREAK_GLASS` branch:
  verified-provider capacity (never mints a health worker) + facility context + named patient,
  ahead of the existing reason-capture (active request) + step-up + ELEVATED-audit + PENDING_REVIEW
  retrospective-review path. A disputed (revoked) provider is already denied at the top of
  `evaluate()`. Governance perimeter for the request/review endpoints seeded in tshepo-authz
  **V041** (verified-provider raise, supervisor review; citizen surface = fail-closed default-deny,
  since the engine ignores a DENY rule's `path_contains`). BFF capture/audit reuses the existing
  `TrustBreakGlassResourceMapper`. 54 PolicyEngine tests green (161 module-wide).
| W11 | both | acceptance packs: `tests/identity-contract/provider-journeys.sh` + `tests/place-contract/` |

Verdict gate: pack green = SOFTWARE_CONTRACT_GREEN; real council/HPA/CRVS links = EXTERNAL_INTEGRATION_GREEN
(declared honestly, out of program scope).

## Open PO questions (carried, not blocking)

Provider: final work-token TTL · long-term fate of `/auth/login/provider-id` URL · locum approval authority
(PIC vs dual) · offline credential TTL + definitive high-risk action list.
Place: per-category competent authority for SJ2 routing · verification-evidence retention / OCR persistence ·
REGULATORY_LIAISON cross-registry span · role expiry defaults / auto-renew · QR credential as public-listing
precondition vs advisory · per-category public field allowlists · anonymous vs claim-code-only complaints.
