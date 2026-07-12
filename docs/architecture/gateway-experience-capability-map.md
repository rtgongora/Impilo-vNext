# Gateway Experience Capability Map

> Grounded truth for the National Health Services Gateway doctrine
> ([`docs/doctrine/health-services-gateway-doctrine.md`](../doctrine/health-services-gateway-doctrine.md)).
> Pillar → owning services → existing endpoints → status per surface.
> Method: three-agent estate sweep (public experience layer, trust/assurance
> infrastructure, backend pillar coverage) verified against tree at `d1be05395`
> (2026-07-12), including the just-landed msika completion wave (`5f2142b98`,
> `ead284785`). Statuses: **BUILT** / **PARTIAL** / **ABSENT**. Negative claims cite
> the disproving search. Update this map whenever a gateway wave lands.

## 1. The public surface today (the R0 baseline)

The entire genuinely unauthenticated API surface of the estate is:

| Lane | Anchor |
|---|---|
| Auth brokering `/internal/v1/auth/**` | `services/experience-bff/.../config/SecurityConfig.java` permitAll block |
| Bootstrap first-admin, actuator/health, `/health/version` | same |
| Ndila map tiles `/internal/v1/ndila/tiles/*/*/*.{png,mvt}` | same |
| Patient-share claim `/internal/v1/public/patient-shares/**` | `VitoPublicPatientSharesBffController` |
| Facility-certificate verify `/internal/v1/public/facility-certificates/verify/**` | `PublicCertificateVerificationBffController` → tuso `PublicCertificateVerificationController` |
| Patient-safety public reports `/v1/public/patient-safety/**` | `PatientSafetyPublicController` (non-`/internal` prefix) |

Public **web** routes (page render without session): `/welcome` (+`find-care`,
`emergency`, `accessibility` via `src/components/public/PublicShell.tsx`), `/auth/**`,
`/verify/credential`, `/verify/facility-certificate`, `/share/claim`, `/kiosk`,
`/privacy`, `/terms`, `/consent`, `/account-deletion` — `PUBLIC_PREFIXES` in
`ui/one-ui-shell/src/middleware.ts` + `guard: "none"` rows in
`ui/one-ui-shell/src/lib/routes.ts`. Root `/` redirects sessionless users to `/welcome`
(`src/app/page.tsx`, G-CZO-02 closure). A public page only *works* where a public API
backs it — everything else 401s at the BFF.

Envoy: prod `infra/envoy/envoy.yaml` carries exactly two `ExtAuthzPerRoute` disables
(`/v1/public/verify`, `/v1/public/share`); compose `envoy-runtime.yaml` carries **none**
— a config divergence any public-lane wave must reconcile first. The tshepo-authz gRPC
PDP hard-denies requests missing trust headers (`ExtAuthzGrpcService.check`); there is
no anonymous tier in the PDP (searched tshepo-authz for `permitAll`/`public`/`anonymous`
branches: none).

The static brochure `public-website` (prebuilt image; **source not in this repo**) owns
`/`, `/about`, `/services`, `/features`, `/resources`, `/docs`, `/training`,
`/get-involved`, `/community`, `/technical`, `/contact` at Traefik
(`deploy/tls/mohcc-gov/public-website.yaml`, priority between BFF and shell routes).

## 2. Cross-cutting trust substrate

| Capability | Status | Anchor / evidence |
|---|---|---|
| Effective-LOA enforcement (`min_loa`, max of ACR level and `X-Assurance-Level`) | **BUILT** | `services/tshepo-authz-service/.../core/PolicyEngine.java` `effectiveLoa()` + `evaluateConditions()`; G-CZO-01 closure markers in-code |
| `X-Assurance-Level` authoritative population | **BUILT** | `services/experience-bff/.../config/AssuranceLevelResolutionInterceptor.java` (citizen actors → identity-assurance `getAssuranceStatus()`; header overwritten on downstream calls) |
| Assurance SoR: LOA1–4, permission tiers, dual-control upgrades | **BUILT** | `services/identity-assurance-service/.../core/{AssuranceLevel,AssurancePolicy,AssuranceService}.java` |
| Step-up engine (TOTP / SMS_OTP / BIOMETRIC / SUPERVISOR), citizen BFF proxy | **BUILT** | `services/tshepo-authz-service/.../stepup/*`, `service/StepUpService.java`; `experience-bff/.../controller/CitizenStepUpController.java` (G-CZO-04) |
| Delegation (R4): persisted relationships, LOA floor, anti-self-grant, PDP consumption | **BUILT** | `services/mvumo-service/.../service/DelegationService.java`; PolicyEngine Step 4.5 `evaluateDelegation()` on `X-Subject-ID` (G-CZO-03) |
| Break-glass with TTL + mandatory post-hoc review | **BUILT** | `services/tshepo-authz-service/.../service/BreakGlassService.java`; PolicyEngine Step 3 |
| Emergency/break-glass purposes bypass consent gate (audited) | **BUILT** | PolicyEngine `requiresConsent()` exempts `EMERGENCY`/`BREAK_GLASS` |
| **Reachable rung (R1)**: verified-contact identity tier | **ABSENT** | No contact-verified attestation in identity-assurance (searched `AttestationController`/`AssurancePolicy` for contact/phone/email pathways); no phone-OTP login door (G-CZO-11); Keycloak realms have `registrationAllowed:false`, no `otpPolicy`/custom `authenticationFlows`/ACR step-up (grep both realm JSONs: 0 hits) |
| Real SMS delivery | **ABSENT** | `services/notification-service/.../provider/SmsStubProvider.java` is the default; step-up OTP delivery fails closed without a configured gateway |
| Semantic intent preservation | **PARTIAL** | Only `returnTo` path replay: `ui/one-ui-shell/src/lib/resolve-post-login-destination.ts` + `middleware.ts`; no intent primitive, no resumable drafts (G-CZO-09) |
| Trust-escalation mediation (Nompilo) | **PARTIAL** | Widgets exist (`ui/one-ui-shell/src/components/intelligent/Nompilo*.tsx`) and guidance-service (8260) is wired, but `/internal/v1/guidance/**` is authenticated-only — no public explain lane |
| Guest tier | **ABSENT** | In-code comments "tshepo guest tier undefined" (`src/app/meet/join`, `InviteLinkButton.tsx`); guests = pre-account `/welcome` visitors only |

## 3. Pillar-by-pillar map

Statuses per pillar: *service capability* / *authenticated citizen surface* / *public (R0) surface*.

### P1 Get care — BUILT / BUILT / ABSENT
- Services: `pct-service` (`TelehealthController /v1/telehealth`, referrals), `booking-service` (`BookingController /v1/bookings`, `AppointmentController /v1/appointments` incl. `/citizen/{cpid}`), `referral-service`, `rtc-gateway-service`, `khuluma-service`.
- BFF: `Citizen{Telehealth,Appointment,Booking}Controller` under `/internal/v1/mobile/citizen/**` (CITIZEN role). Web: `/home/bookings`, `/my/telehealth`, `/telemedicine` (provider), mobile telehealth screens.
- Public: none — `/discover/**` is `guard: auth`; no anonymous booking-interest lane.

### P2 My health — BUILT / BUILT / PARTIAL
- BFF: `Citizen{ClinicalRecords,Records,Results,Consent,HealthSummary,Timeline,Profile}Controller`; consent lifecycle `/internal/v1/consent/**`; VITO client registry.
- Public: patient-share claim (`/internal/v1/public/patient-shares/**` permitAll; web `/share/claim`) — the one working public pattern for citizen-mediated sharing. Records/results/consent correctly authenticated.

### P3 Health information — PARTIAL / PARTIAL / ABSENT
- Services: `clinical-knowledge-platform-service` (8270: assistant, pathways, EDLIZ ingestion), `guidance-service` (8260: ask/education/reminders + Nompilo context), `learning-service` (Fundo catalog/lessons/media).
- Public: **no unauthenticated content API of any kind** (searched BFF SecurityConfig permitAll list and all `guidance`/`clinical`/`learning` route rules — all `authenticated`). No citizen-language health-information CMS; content models exist (knowledge curation, Fundo authoring) but no public read lane.

### P4 Find or verify a service — BUILT / PARTIAL / PARTIAL
- Facility search: tuso `PublicFacilityController /v1/public/facilities/search` + `/{id}/profile` — **service-complete but not exposed through the BFF** (not in permitAll; the only public tuso lane is certificate verification). `/discover/{providers,facilities,services}` in the shell is auth-gated.
- Facility licence verify: **BUILT end-to-end public** (BFF `PublicCertificateVerificationBffController` → tuso; web `/verify/facility-certificate`).
- Professional verify: **ABSENT publicly** — varapi is entirely `/v1/internal/**` (searched `services/varapi-service` controllers for `public`: none); `/verify/credential` web page rides credential-verification-service via Envoy `/v1/public/verify` (credential documents, not registration-number lookup of the professional register).
- Location: ndila tiles public; indawo (8150) + search-service (8230) authenticated.

### P5 Health cover & payments — BUILT / BUILT / ABSENT
- coverage-service (8140): full model — `MemberCoverage`, `Eligibility`, `Preauth`, `Claim`, `MemberContributions`, `Subsidy` (+`SubsidyEnrolmentService`), `Appeal`, `CoveragePlan`, `ProviderContract`, `Remittance`; enrolment DTOs exist.
- COSTA (`costing-engine-service`): `BillController /costa/v1/bills` (draft→invoice→apply-coverage→payment-intent), `PatientAccountController`, `FinancialDocumentController` (receipts), `WaiverController`, `EmergencyReconciliationController`. MusheX rails + `mushe-wallet-service`.
- BFF/web: `Citizen{Coverage,Costa,Wallet}Controller`, `/coverage`, `/citizen/wallet`.
- Public: no plan-browse or cost-estimate lane (searched permitAll list: none). NHI-readiness: purchasing-model configurability and benefit-package versioning **unverified** against doctrine §6.2 — treat as open verification item, not a claim.

### P6 Feedback & complaints — BUILT / BUILT / ABSENT
- rito-quality-safety-service: `RitoCaseController /internal/v1/rito/cases` (create/timeline/lifecycle), signals, surveys. BFF `RitoPersonaController` (citizen submit + track); web `/my-life/feedback`, mobile rito screens.
- Public/anonymous: **no anonymous intake** (searched rito controllers + BFF for permitAll/anonymous complaint paths: none). No claim-code tracking for unauthenticated complainants. Patient-safety has a public reports lane (`PatientSafetyPublicController`) — the nearest existing pattern.

### P7 Applications & licensing — BUILT / BUILT / ABSENT
- varapi: `ProviderApplicationController` (submit→committee→decision), `LicenseController`, `CertificateController`, `CpdController`, and registrant self-service `PortalController /v1/portal/{me,cpd,certificates}`.
- tuso HPA: `FacilityRegulatoryController` (applications/documents/checklists/inspections/enforcement), `HpaRegulatoryOperationsController` (application-types, requirement-sourcing, RFIs, council reviews); BFF `HpaRegulatoryBffController`; web establishment guide (`/marketplace/establishment-guide`).
- Public: requirements/categories/fee reads are not public (all `/v1/internal/**`); public *outputs* only via facility-certificate verify (P4).

### P8 Find health products & suppliers — BUILT / BUILT / ABSENT
- Current to the just-landed msika completion wave: msika storefront lane (`V006` listings/storefronts/media + seller verification + moderation `fa172c622`), requirement-sourcing + curated categories (`V007 76cb6cd71`, resolve/categories API `72de58dfa`), restricted-category publish gate + denial audits (`7b74fcd41`), completion-wave depth (`ead284785`); msika-flow transactional spine with **real MusheX payment handoff, order completion, Nhume dispatch, vendor/ops + rx lanes** (`V004 5f2142b98`), plus `RxController` prescription attach/substitutions.
- BFF: `Citizen{Marketplace,Prescription,Delivery}Controller`, commerce cart/order lanes (authenticated citizens can buy). Web `/marketplace/**`; mobile marketplace screens.
- Public: no anonymous catalog/listing browse (searched permitAll: none). Note `AssurancePolicy` grants `marketplace-browse` at LOA1 — the policy floor is already minimal; the missing piece is the R0 lane, not the policy.

### P9 Emergency help — BUILT / PARTIAL / ABSENT
- daidzai `EmergencyController /internal/v1/daidzai` (`POST /requests` + triage, incidents/dispatch/missions/handoff/death-outcome), `DisasterController`; nhume dispatch/fleet/courier; dispatch-service.
- Citizen SOS exists but is **auth-gated**: web `/emergency/sos` (`guard: auth`, posts `/internal/v1/daidzai/requests`); BFF `/internal/v1/emergency/**` = CLINICAL_ROLES, citizen SOS lanes = CITIZEN role; mobile SOS components likewise.
- Public: `/welcome/emergency` static numbers/guidance only. **No anonymous emergency intake endpoint** (searched daidzai + BFF for public/permitAll intake: none) — the doctrine §7 gap with the highest human stakes.

## 4. Gap summary (feeds the GW clause register)

| Gap | Doctrine clause | Reuses / mints |
|---|---|---|
| No public facility search through BFF (service-side exists) | GW-02, GW-04 | wire tuso `PublicFacilityController`, no new capability |
| No public professional verification | GW-02 (P4) | mint: varapi public verify controller mirroring tuso pattern |
| No public health-information lane | GW-02 (P3) | guidance + clinical-knowledge own content; SoR proof required before any new CMS |
| No Reachable (R1) rung / phone-OTP door / real SMS | GW-04 | G-CZO-11; attestation in identity-assurance; provider swap in notification-service |
| No semantic intent primitive or resumable drafts | GW-04 (law 3) | G-CZO-09; extend `resolve-post-login-destination.ts` |
| No public Nompilo explain lane | GW-07 (§9) | guidance-service public read endpoint |
| No anonymous feedback intake / claim-code tracking | GW-06 (P6) | pattern: patient-shares claim codes |
| No anonymous emergency intake; SOS auth-gated | GW-05 (§7) | daidzai intake via public lane + abuse controls |
| No public cover plan-browse / cost estimates; §6.2 configurability unverified | GW-08 (§6) | coverage-service read lane; verification item |
| No anonymous marketplace browse | GW-02 (P8) | msika catalog read lane (post-MSIKA-GATE) |
| Envoy prod/compose public-bypass divergence | GW-03 | reconcile before any new public route |
| Brochure/app seam ungoverned (source outside repo) | GW-01 | PD-1 product decision |
