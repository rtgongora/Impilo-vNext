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
| — | Assets / devices / equipment + IoT | suggested | — | pending |
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
