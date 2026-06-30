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

## Coordination items (deferred — for the roadmap, not a workstream)

- **Wallet brand taxonomy (resolved at UI level).** `Mushe Personal Health Wallet` = the broad person
  anchor (`/citizen/wallet`, health/identity/care/consent). `MusheX Wallet` = the money wallet
  (`/wallet`, balance/cards/send/deposit). The person wallet home cross-links a **Money — MusheX Wallet**
  tile to `/wallet`; both keep their own systems-of-record.
- **DEFERRED (invasive):** the financial wallet's internal service is still named `mushe-wallet-service`.
  Aligning it to a `musheX`-named service (package, DB schema, Kafka topics, registry, ports) is a real
  service rename and must be planned as its own change — it should NOT ride on a UI label change.
