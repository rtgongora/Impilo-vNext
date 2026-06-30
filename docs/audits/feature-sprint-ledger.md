# Feature Sprint Ledger — sequential pop-out workstreams

> **Read this FIRST in every pop-out feature session, after the universal preamble.** It is the shared
> memory across the separate sessions: what already exists, what each prior workstream extended/built,
> and the frozen allocations — so no workstream builds-on-top-and-duplicates the estate *or a prior
> workstream*. Workstreams run **sequentially, one at a time**; a later workstream can and must see what
> earlier ones already landed.
>
> **Pop-out sessions MUST sync to the coordinator branch first:** run
> `git fetch origin && git checkout -B feature/<name> origin/claude/crazy-merkle-3ad1a1`
> so you build on the latest integrated state (including all prior workstreams), not a stale base.

## Operating rules

- **Extend before creating.** Prove no existing service (or prior workstream row below) already owns the
  capability. If one does → extend it. A new service requires a documented "why no existing service owns this".
- **Single-writer for shared files.** `docs/registry/services-registry.yaml`, `docs/registry/system-of-record-map.md`,
  `docs/runbooks/port-allocation.md`, and shared `contracts/*` are **coordination-owned**. A workstream that
  needs to change them records the need under "coordination items" in its report — it does **not** edit them
  unilaterally.
- **No mocks in production paths.** Every card/action connects to a real backend/BFF capability or is documented as deferred.
- **BFF is stateless.** experience-bff is composition/orchestration only — persist in a sovereign service, never the BFF.
- **Append, don't rewrite.** Each completed workstream appends its row below. Do not edit other rows.

## Checkpoint & Anti-Hallucination Protocol (MANDATORY for every pop-out)

Two background agents have already lost in-process state on process exit. Work so it survives.

1. **Per-chunk commit + push.** Break the workstream into small named chunks (e.g. discovery → schema → service → controller → BFF → each web surface → mobile → tests → policy → docs). After EACH chunk: `git commit` **and** `git push origin feature/<name>`. Never batch to the end. A death then loses ≤1 in-progress chunk.
2. **Filesystem-overlay defense.** The Write tool can land files in a sparse overlay Bash can't see, on the wrong branch — producing FALSE "BUILD SUCCESS". After writing any file, confirm via Bash (`git status`/`ls`) it exists in the Bash-visible tree BEFORE trusting any build. If Write-tool files don't show in `git status`, author via Bash heredoc and do all compile/test/commit/push in that one tree.
3. **Never report an unrun number.** Only state a test/build result you actually ran THIS session on the real tree; quote the literal `Tests run: N` / `PASS: N/N` / tsc lines. If not run, write **NOT RUN** — never estimate or assume.
4. **Cite before asserting.** Before claiming ownership / "already exists" / "not implemented" / a negative, cite the grep/read that proves it (SoR-first).
5. **Coordinator re-verifies before merge.** The coordinator independently re-runs each agent's key tests (mvn / OPA / runtime-prove / tsc) on the real tree and never merges on report alone — because of (2)+(3), self-reports are not trusted.

## Canonical SoR reuse map (who owns what — extend these)

person/client identity, profile, relationships, corrections → **Vito** · Health-ID/anchor + trust/assurance/LoA →
**Vito + identity-assurance-service + Tshepo** · auth/session/context → **Tshepo + Keycloak/IAM** · consent →
**tshepo-consent-service** · delegated access / proxy / comms-preference orchestration → **mvumo-service** ·
clinical/FHIR/SHR summary → **Butano (+ PCT)** · care journeys/encounters/referrals → **PCT** · orders/results →
**Oros (+ Butano/PCT)** · provider identity → **Varapi** · facility/site → **Tuso/Indawo** · civil registration/vital
events → **Ubomi** · bills/costing → **Costa** · payments/rails → **MusheX** · marketplace → **Msika** ·
messaging/notifications/delivery → **Khuluma (+ notification-service)** · guidance (Nompilo) → **guidance-service** ·
workforce/roster/assignment → **Vashandi** · learning/CPD → **Fundo** · commodity/stock/ledger → **Dura** ·
maps/location/routing → **Ndila** · dispatch → **Nhume**.

## Workstream roster (8 — sequential)

| # | Workstream | Source | Owning-service decision | Status |
|---|-----------|--------|-------------------------|--------|
| 1 | Mushe Personal Health Wallet | PO (ran first) | experience layer over existing owners — no new SoR | ✅ done (`b3fd638bc`) |
| — | _TBD (other PO features)_ | PO | — | pending |
| — | Wellness depth (diet/sleep/fitness/clubs/coaching) | suggested | — | pending |
| — | Health marketplace (products/services lane) | suggested | — | pending |
| 5 | Assets / devices / equipment + IoT | suggested | **EXTENDED asset-registry-service** (+ iot-ingestion-service) — no new SoR | ✅ done (`feature/assets-devices-iot`) |
| — | Nompilo as a first-class intelligent layer | suggested | — | pending |
| — | Unified person health wallet | suggested → **done as #1** | — | ✅ |

> Ordering is the sequence we run them in; adjust as the PO directs.

## Completed-workstream status (each session appends one row)

| # | Workstream | Branch | Owning service(s) | Migrations | Ports | Routes added | Tests | Parity | Commit | Coordination items |
|---|-----------|--------|-------------------|------------|-------|--------------|-------|--------|--------|--------------------|
| 1 | Mushe Personal Health Wallet | `feature/person-health-wallet` | experience-bff (CitizenWalletController + WalletOverviewService + 2 Mvumo client reads), one-ui-shell (8 `/citizen/wallet/**` pages + `useWallet`), citizen-app mobile (WalletOverviewSection), OPA `impilo.wallet` policy — **no new service, no new SoR** | none | none | `GET /internal/v1/citizen/wallet/overview`, `POST .../wallet/profile/correction`; 8 web `/citizen/wallet/**`; mobile Wallet tab | BFF 10/10, OPA 11/11, web 3/3 (tsc clean) | routes 636/636; be↔fe pass; **mobile vitest blocked** (`apps/mobile` uses pnpm `workspace:*`; npm install EUNSUPPORTEDPROTOCOL — section written + verified vs exports, not run) | `b3fd638bc` | none (no SoR/registry/port/contract changes) |
| 2 | Nompilo Intelligent Layer | `feature/nompilo-intelligent-layer` | **EXTENDED guidance-service** (Nompilo SoR): V004 `guidance.guidance_item` registry + `guidance.guidance_dismissal`, `ContextGuidanceService` + `ContextGuidanceController` (`/internal/v1/guidance/nompilo/**`). experience-bff: `NompiloGuidanceController` (`/internal/v1/nompilo/**`) + `NompiloSignalService`. one-ui-shell: `useNompilo`, `NompiloContextualGuidance`, `/nompilo` page, wallet panel. citizen-app mobile: `NompiloGuidanceSection` + service. OPA `impilo.nompilo`. **No new service, no new SoR** | guidance-service **V004** (`guidance_item` + `guidance_dismissal`), runtime-proven (V001–V004 apply, 9 items seeded) | none | `POST/GET /internal/v1/nompilo/{context,next-actions,explain-locked,dismiss,follow-up}` + `/fundo/{id}`; guidance-service `/internal/v1/guidance/nompilo/{context,explain-locked,dismiss}`; web `/nompilo` | guidance-svc 26/26 (8 new), BFF 8/8 (6 new), OPA 95/95 (14 new), web 38/38 (tsc clean) | routes 640/640; be↔fe pass; **mobile vitest blocked** (apps/mobile pnpm `workspace:*`; no node_modules — written + verified vs exports, not run) | `feature/nompilo-intelligent-layer` HEAD | wired BFF→real guidance-service handoff lifecycle (the prior `CoreTransactionCompositionService.requestNompiloHandoff` still echoes ACCEPTED — candidate follow-up to point it at the real endpoint too) |
| 3 | Simba Wellness Depth | `feature/simba-wellness-depth` | **EXTENDED simba-service** (wellness SoR; port **8125** — note: registry/port table still lists 8161, see coordination): V005 `wellness_plans`/`plan_tasks`/`plan_enrollments`/`enrollment_tasks`, `habit_check_ins`, `coaching_relationships`/`coaching_touchpoints`, `care_linkages`, `wellness_access_audit`. `PlanService`/`HabitService`/`CoachingService`/`CareLinkageService` + `SimbaEventEmitter`/`WellnessAuditService` + 4 controllers. experience-bff: `WellnessHomeController` (`/internal/v1/wellness/home/**`) + extended `SimbaServiceClient`. one-ui-shell: `useSimbaDepth`, `/wellness/plans`, `/wellness/care`, real habit check-ins on `/wellness/coaching`, commodity→Dura relabel. citizen-app mobile: `wellnessDepthService` + `WellnessJourneysSection`. OPA `impilo.simba`. Nompilo: guidance-service **V005** seeds domain=`simba`. **No new service, no new SoR — Simba is wellness, NOT inventory (Dura owns stock)** | simba-service **V005** (8 tables incl. plans/enrolments/habits/coaching/care-linkage/audit) + guidance-service **V005** (6 `simba` guidance seeds); both runtime-proven on PG16 | none (used existing 8125) | `GET/POST /internal/v1/wellness/{programs,enrollments,enrollments/tasks/*/complete,habits/check-ins,coaching/relationships,care-linkages}`; BFF `GET /internal/v1/wellness/home/overview` + `POST .../home/care-routing`; web `/wellness/plans`, `/wellness/care` | simba 5 new IT + suite green, BFF 5/5 new, OPA 118/118 (23 new), web routes 31/31 + plans 2/2 (tsc clean) | routes 642/642; be↔fe pass; **mobile vitest blocked** (apps/mobile pnpm `workspace:*`; no node_modules — written + verified vs exports, not run) | `feature/simba-wellness-depth` HEAD | (1) registry/port doc lists Simba at 8161 but `simba-service/application.yml` + BFF default are **8125**; align in port-allocation.md/services-registry.yaml (coordination-owned). (2) `/wellness/commodities` was stale Simba-as-inventory; relabelled to Dura at UI — registry note welcome. |
| 4 | Msika Health Marketplace | `feature/msika-health-marketplace` | **EXTENDED msika-service** (marketplace SoR) with the buyer **storefront lane** over the existing offering/catalog registry: V006 `msika_storefronts`/`msika_listings`/`msika_listing_media`/`msika_listing_favourites`/`msika_listing_audit`. `ListingService`/`StorefrontService`/`FavouriteService`/`ListingAuditService` + `ListingController`(`/v1/listings/**`) + `StorefrontController`(`/v1/storefronts/**`); reuses existing outbox + change-log. experience-bff: `MarketplaceStorefrontController` (`/internal/v1/marketplace/store/**`) + `SellerCentreController` (`/internal/v1/marketplace/seller/**`); extended `MsikaServiceClient`; added `KhulumaServiceClient`. one-ui-shell: `useMsikaStore`, `/marketplace/store{,/search,/listing/[id],/activity}` + `/marketplace/seller{,/listings,/listings/new,/moderation}`. citizen-app mobile: `marketplaceStoreService` + `MarketplaceStoreSection` (wired into PersonalScreen). OPA `impilo.msika`. Nompilo: guidance-service **V006** seeds domain=`msika`. **No new service, no new SoR — Msika owns DISCOVERY/listings only; orders→msika-flow, billing→Costa, payment→MusheX, stock→Dura/inventory, clinical→PCT/OROS, comms→Khuluma, feedback→Rito (composed/linked, never duplicated)** | msika-service **V006** (5 storefront-lane tables) + guidance-service **V006** (8 `msika` guidance seeds); both runtime-proven on PG16 (V001–V006 apply clean) | none (used existing 8086) | msika `/v1/listings/**` (search/detail/favourite/create/submit/media/seller/moderation/approve/reject/suspend/publish/unpublish) + `/v1/storefronts/**` (create/verify/get/list); BFF `/internal/v1/marketplace/store/**` (home/search/listing/favourite/activity/feedback→Rito/notify→Khuluma) + `/internal/v1/marketplace/seller/**` (storefront/listings/moderation); web 8 `/marketplace/{store,seller}/**` | msika-service 30/30 (10 new `ListingServiceTest`), BFF 10/10 (7 new `MarketplaceStorefrontControllerTest`), OPA 162/162 (44 new), web routes 31/31 + store page 2/2 (tsc clean modulo pre-existing serviceBranding) | routes 642→650; be↔fe pass; **mobile vitest blocked** (apps/mobile pnpm `workspace:*`; npm EUNSUPPORTEDPROTOCOL — written + structurally verified vs exports, NOT test-run) | `feature/msika-health-marketplace` HEAD | (1) **"Dura" alias**: no `dura` service exists — **inventory-service (8098)** is the stock SoR and msika-flow holds reservations (`mf_reservations`); registry/SoR-map note clarifying Dura→inventory-service would prevent future forks (coordination-owned). (2) New msika-service roles referenced in `@PreAuthorize` (`MARKETPLACE_SELLER`, `FACILITY_ADMIN`) and OPA actions (`msika.*`) — fold into the central role/policy registry + seed `policy_rule` rows if the live Java PolicyEngine becomes the enforcement path for `/v1/listings/**`. (3) Khuluma base URL in BFF defaults to **8390** (matches Khuluma comms-hub); confirm vs registry. |
| 5 | Assets, Devices, Equipment & IoT | `feature/assets-devices-iot` | **EXTENDED asset-registry-service** (asset/equipment/device/IoT SoR, port **8310**) with the equipment-operations lifecycle over the existing `asr_equipment`/`asr_assets` registry: V006 `asr_equipment_lifecycle_event`/`asr_equipment_transfer`/`asr_maintenance_task`/`asr_calibration_record`/`asr_fault_report`/`asr_iot_alert`/`asr_readiness_profile`+`_requirement`/`asr_deployment_kit`+`_item`/`asr_asset_audit`+`_item` (+ criticality/class/regulated/custodian/tag/clinical-device cols on `asr_equipment`). `EquipmentOperationsService` (salvaged + completed) + `EquipmentOperationsController` (`/internal/v1/equipment/**`); reuses existing outbox + idempotency + TrustContext + lifecycle-event audit. experience-bff: `EquipmentOperationsBffController` (`/internal/v1/equipment/**`) + extended `AssetRegistryServiceClient`; composes Khuluma (request-only notify) + guidance-service (domain=assets Nompilo). one-ui-shell: `useEquipment`, rewired `/operations/equipment` (was decorative) + `/operations/equipment/{[equipmentId],maintenance,calibration,readiness,iot,deployment,audit}`. provider-app mobile: `equipmentService` + `EquipmentToolsScreen` (search/maintenance/fault). OPA `impilo.assets`. Nompilo: guidance-service **V007** domain=`assets`. **No new service, no new SoR — asset-registry owns operational asset/equipment/device truth; stock→inventory-service (Dura), facility→Tuso/Indawo, workforce→Vashandi, clinical record→Butano, safety→Rito, alerts→Khuluma, telemetry ingest→iot-ingestion-service (referenced/composed, never duplicated; NO fabricated telemetry)** | asset-registry **V006** (12 equipment-ops tables/extensions) + guidance-service **V007** (8 `assets` guidance seeds); both runtime-proven on PG16 (asset V001–V006 clean = 16 `asr_` tables; guidance V001–V007 clean) | none (used existing 8310/8330) | `/internal/v1/equipment/**` — list/register/detail, metadata/status, transfers(initiate/approve/receive), maintenance(open/update/complete/due/open-list), calibration(record/due), faults(report/link-rito), alerts(raise/resolve/active/khuluma), readiness(profiles/compute), deployment-kits(create/deploy/return/list), audits(open/items/complete); BFF mirrors + `/alerts/{id}/notify`(Khuluma) + `/readiness/guidance`(Nompilo); web 7 new `/operations/equipment/**` | asset-registry 25/25 (11 new `EquipmentOperationsMockMvcTest`), BFF 6/6 new (`EquipmentOperationsBffControllerTest`), OPA 199/199 (37 new `impilo.assets`), web 38/38 (7 new `useEquipment` + routes.test; tsc clean modulo pre-existing serviceBranding) | routes 650→657; be↔fe pass; **mobile not test-run** (apps/mobile pnpm `workspace:*`; deps not installable — written + structurally verified vs design-system exports + BFF contract, NOT test-run) | `feature/assets-devices-iot` HEAD | (1) New asset roles referenced in OPA (`asset_facility_admin`/`asset_technician`/`asset_biomedical_engineer`/`asset_field_officer`/`platform_admin`) + `assets.*` actions — fold into the central role/policy registry + seed `policy_rule` rows if the live Java PolicyEngine becomes the enforcement path for `/internal/v1/equipment/**`. (2) IoT alert **ingest substrate**: alerts are derived/recorded honestly but the offline/threshold *derivation from live telemetry* is currently driven by the existing `AssetEventConsumer` heartbeat loop + the explicit `/alerts` ingest API; a dedicated rule that converts iot-ingestion threshold breaches into `asr_iot_alert` rows is a candidate follow-up (documented gap, no fabrication). (3) Mobile **camera/QR scan** substrate not wired in provider-app — manual tag/serial search implemented instead (honest gap). |

### WS#5 gap closure (`feature/ws5-gap-closure`, 2026-06-30)

Closed two of WS#5's three documented backend gaps (the mobile camera/QR-scan gap is an
environment block, out of scope):

- **GAP-1 — live policy enforcement for asset/equipment endpoints (was coordination item #1).**
  Added **tshepo-authz V026** (`asset_equipment_policy_rules`) — **21 `policy_rule` rows** seeding
  the `impilo.assets` OPA matrix at the **live ext_authz DB-rule PDP** (OPA is shadow-only). Before
  this the `/internal/v1/equipment/**` + `/internal/v1/assets/**` routes had no matching rule →
  fail-closed DENY for every user purpose. 15 ALLOW (facility-admin/admin/system-admin/biomedical-
  engineer/technician/field-officer cadres + provider equipment-use by facility context + registry
  register/list/detail), pinned by `path_contains`, `min_loa>=2` on writes; 6 DENY (citizen,
  stock-as-asset, retire/dispose by non-admin cadres) — DENY-wins via `effect DESC` ordering.
  Contextual OPA denies that need per-request runtime facts (technician-by-assignment, cross-facility
  scope, elevated-approval presence, link-device-to-clinical) remain in-service (V021/V022 precedent).
  6 new `PolicyEngineTest` cases; tshepo-authz suite **129 green**; V001–V026 runtime-proven on PG16.
- **GAP-2 — IoT threshold-breach alert derivation (was coordination item #2).** Added **asset-registry
  V007** (`asr_iot_threshold_rule` + `asr_iot_breach_state` + a cold-chain seed: `temperature_c GT 8.0`,
  `min_count 3`, CRITICAL, route MADI). `EquipmentOperationsService.evaluateThresholdReading` derives
  `THRESHOLD_BREACH` alerts from the **real** `metric_value` already published on
  `telemetry.iot.device.reading`, tracks a per-device consecutive-breach streak (respects `min_count`,
  resets on a within-threshold reading), and raises via the **existing** `raiseAlert` dedupe +
  lifecycle-event + outbox (`impilo.asset.iot.alert.raised.v1`) path; cold-chain breaches carry
  `route_to=MADI`. `AssetEventConsumer` extended to derive on the reading topic; iot-ingestion now
  includes `tenant_id` in the published reading payload (was absent). No telemetry fabricated. 4 new
  `IotThresholdBreachTest`; asset-registry **29 green**, iot-ingestion **11 green**; V001–V007 PG16-proven.

Coordination items raised by this closure: (a) add new realm roles **BIOMEDICAL_ENGINEER /
ASSET_TECHNICIAN / ASSET_FIELD_OFFICER** to `infra/keycloak/realm-impilo-production.json` +
`contracts/trust/seeds/role-templates.json` (single-writer files — V026 seeds the policy side and
reconciles the OPA `role_template`s to these, mirroring V021's REGULATOR precedent).

## Coordination items (deferred — for the roadmap, not a workstream)

- **Wallet brand taxonomy (resolved at UI level).** `Mushe Personal Health Wallet` = the broad person
  anchor (`/citizen/wallet`, health/identity/care/consent). `MusheX Wallet` = the money wallet
  (`/wallet`, balance/cards/send/deposit). The person wallet home cross-links a **Money — MusheX Wallet**
  tile to `/wallet`; both keep their own systems-of-record.
- **DEFERRED (invasive):** the financial wallet's internal service is still named `mushe-wallet-service`.
  Aligning it to a `musheX`-named service (package, DB schema, Kafka topics, registry, ports) is a real
  service rename and must be planned as its own change — it should NOT ride on a UI label change.
- **Simba port mismatch (WS#3).** `simba-service` runs on **8125** (`application.yml` + experience-bff
  `simba-base-url`/`wellness-base-url` defaults + `WellnessServiceProxyController`). The doctrine port
  table / registry summary still lists Simba/wellness at **8161** (the old deprecated `wellness-service`).
  Update `docs/runbooks/port-allocation.md` + `docs/registry/services-registry.yaml` to 8125 (coordination-owned).
- **Simba ≠ inventory (WS#3, doctrine correction).** The only stale Simba-as-inventory surface found was the
  web route `/wellness/commodities`, which already pointed at inventory-service/Dura but was *labelled*
  "Wellness Commodities (SIMBA plane)". Relabelled to "Programme commodities (Dura)" with corrected copy
  (no functional change — still embeds Dura's `StockManagementPanel`). No stock/inventory tables, endpoints,
  or routes exist under simba-service. A registry note clarifying that wellness-programme commodities are a
  Dura concern (Simba only links out) would be welcome.
