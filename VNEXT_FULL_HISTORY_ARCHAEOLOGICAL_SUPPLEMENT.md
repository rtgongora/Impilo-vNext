# Impilo vNext — Full History Archaeological Supplement

**Scope:** First commit → latest (`484c899b` → `b3407039`)  
**Total commits:** 1,078  
**Calendar span:** 2026-02-07 → 2026-05-29 (~113 days)  
**Parent audit:** [`VNEXT_HISTORICAL_FUNCTIONALITY_AND_DOCTRINE_REGRESSION_AUDIT.md`](VNEXT_HISTORICAL_FUNCTIONALITY_AND_DOCTRINE_REGRESSION_AUDIT.md)  
**Pass date:** 2026-05-29

This supplement records the **full-history pass** requested after the initial HEAD-anchored audit. It uses era segmentation, complete deletion-commit inventory, pickaxe sweeps, and per-era snapshots of `services/pom.xml` and `EXPECTED_ROUTE_COUNT`.

---

## 1. Methodology (full pass)

### Commands executed across entire history

```bash
git rev-list --count HEAD
git log --reverse --oneline | head -1
git log --oneline -1
git log --diff-filter=D --summary --all --format="%h %ad %s" --date=short
git log --diff-filter=D --oneline --all
git log --diff-filter=D --summary --all -- '**/pom.xml'
git log --oneline --all -- services/pom.xml
git log --oneline --all -- ui/one-ui-shell/src/lib/routes.ts
git log --oneline --all -- contracts/core-transaction.ts
git log -S "<term>" --oneline --all   # 40+ doctrine/service terms
git show <era-commit>:services/pom.xml | grep -c "<module>"
git show <era-commit>:ui/one-ui-shell/src/lib/routes.ts | grep EXPECTED_ROUTE_COUNT
git show 8c561460 --stat
git show 1f26349e --stat
git shortlog -sn --all
```

### Limitations

- Pickaxe (`-S`) counts **commits where string appears/disappears**, not line churn volume.
- Deletion analysis uses **28 deletion commits**; bulk deletes (e.g. GAP-010) bundle thousands of files in one commit.
- No per-file blame across all 1,078 commits for every high-risk path.
- Monthly histogram used `git log` on current reachable history; April 2026 dominates (~607 commits) as the main AI implementation wave.

### Contributors (all branches)

| Author | Commits |
|--------|---------|
| Claude | 801 |
| Robert Tawanda Gongora | 280 |
| peter | 72 |
| Others | 8 |

---

## 2. Era segmentation (484c899b → b3407039)

| Era | Date range | Anchor commit | Maven modules | Route count (one-ui-shell) | Theme |
|-----|------------|---------------|---------------|----------------------------|-------|
| **E0 Scaffold** | 2026-02-07 | `484c899b` | **20** | N/A (`routes.ts` not yet) | 6-plane scaffold; `one-ui-shell` exists from day 1 |
| **E1 Sovereign registry** | 2026-02-07 | `873990cf` | ~20+ | — | Tshepo 6-way split; Vito depth; Tuso/Varapi |
| **E2 Registry consolidation** | 2026-02-07 | `683821bc` | ~22 | — | **product-registry-service → msika-service**; ubomi added |
| **E3 Experience fork born** | 2026-03-11 | `0501e103` | **63** | — | **`ui/experience` fork created** (parallel to one-ui-shell) |
| **E4 BFF skeleton** | 2026-03-11 | `319b2d3e` | **63** | — | Experience BFF with v1.1 enforcement, outbox |
| **E5 Health OS doctrine** | 2026-04-11 | `b6776009` | **79** | — | Identity model, header contract, gap matrix |
| **E6 BFF pure proxy** | 2026-04-13 | `8c561460` | ~79 | — | **Removed BFF DB: 40 Flyway scripts, 123 tables, 23 entities** |
| **E7 SecurityConfig strip** | 2026-04-13 | `2b82d766` | ~79 | — | **33 SecurityConfig.java deleted** (compile fix) |
| **E8 Route registry born** | 2026-04 (mobile wave) | `04b2a0cb` | ~85+ | **252** | `routes.ts` introduced in one-ui-shell |
| **E9 Core transaction runtime** | 2026-05-16 | `47de393f` | **98** | **321** | `contracts/core-transaction.ts` introduced; doctrine runtime |
| **E10 Journey shell** | 2026-05-16 | `12a7c572` | 98 | ~321+ | Journey-aware shell stabilization |
| **E11 Logistics plane** | 2026-05 (mid) | `c58b3f21`, `2fad91b7` | 98+ | growing | **Ndila** + **Nhume** services added |
| **E12 GAP-010 convergence** | 2026-05-28 | `1f26349e` | **103** | **374** | **`ui/experience` deleted (~989 files)** |
| **E13 No-stub + route guard** | 2026-05-28/29 | `d8cfe5e2` | 103 | **400** | EXPECTED_ROUTE_COUNT enforcement wave |
| **E14 Wave 20 sovereign** | 2026-05-29 | `9a52cd34` | 103 | 400+ | Sovereign compose; surveillance V003 deleted |
| **E15 HEAD** | 2026-05-29 | `b3407039` | **103** | **417** | Logistics UX unification; mobile core-tx parity |

### Monthly commit volume

| Month | Commits (reachable history) |
|-------|----------------------------|
| 2026-02 | 223 |
| 2026-03 | 202 |
| 2026-04 | 607 |
| 2026-05 | 46* |

\*May count on sampled log may under-represent branch tips; recent May work is confirmed by milestone commits through 2026-05-29.

---

## 3. Growth curves

### Maven reactor (`services/pom.xml` `<module>` count)

```
484c899b (scaffold)     20 modules
0501e103 / 319b2d3e    63 modules   (+215% from scaffold)
b6776009 (Health OS)   79 modules
47de393f (core tx)     98 modules
1f26349e / b3407039   103 modules   (+415% from scaffold)
```

**Finding:** Monotonic module growth. **Zero `pom.xml` deletions** in entire history. Removals are **module renames/swaps** (e.g. product-registry → msika), not silent excision.

### Web route invariant (`EXPECTED_ROUTE_COUNT` in one-ui-shell)

| Commit | Date | Count | Delta driver |
|--------|------|-------|--------------|
| `04b2a0cb` | 2026-04 | **252** | Route registry introduced |
| `47de393f` | 2026-05-16 | **321** | Core transaction + journey routes |
| `1f26349e` | 2026-05-28 | **374** | GAP-010 shadow route registration |
| `d8cfe5e2` | 2026-05-28 | **400** | Gap follow-ups / no-stub policy |
| `b3407039` | 2026-05-29 | **417** | Logistics + parity CI guards |

**Finding:** Route count **only increased** at sampled era boundaries. No evidence of silent route deletion in `one-ui-shell` after `routes.ts` existed. The **`ui/experience` fork deletion** is separate (parallel tree removed after merge).

### `contracts/core-transaction.ts`

- **Introduced:** `47de393f` (2026-05-16) — single introducing commit in history.
- **Implication:** Core transaction contract is **young** relative to repo (~13 days before HEAD). Most platform work predates formal state machine file.

---

## 4. Complete deletion-commit inventory (28 commits)

All commits in history with `--diff-filter=D` (any path):

| Commit | Summary | Archaeological significance |
|--------|---------|----------------------------|
| `27329e90` | MSIKA Core implementation | Replaced older registry patterns |
| `95e2883e` | V11PatientsController; eliminate local event types | Event type consolidation |
| `a130e8db` | rules-service v2 upgrade | Rules registry/versioning |
| `5ee81683` | Wave-12 Integration Platform | Connectors, forms, search |
| `98a38f12` | bug fixes | Hygiene |
| `39ae8cbc` | wellness compose/Helm | Mock drop in citizen e2e path |
| `4ae38b10` | citizen monitoring security | Trust bar alignment |
| `756b5210` | real monitoring devices API | **Dropped mock citizen e2e** |
| `af52aacf` | clinical-plane OAuth2 | Security hardening |
| `3292c340` | mushex settlement state machine | Finance extension |
| `5fe9c28a` | Public Health UI | Surveillance/campaigns pages (in fork era) |
| `44336dad` / `7faf8aa5` | supply-plane Kafka events | Pharmacy/IoT wiring |
| `acfadd05` | AI model registry + OAuth2 on guidance/rules/forms/search | Intelligence plane |
| **`8c561460`** | **BFF pure proxy** | **5,367 lines removed** — see §5 |
| `11bb7bbd` / `062d827c` | BFF compile blockers | Duplicate cleanup |
| `c5259ae7` | BFF perf optimization | Proxy cleanup continuation |
| `a50d4dfe` | build log docs | Documentation |
| **`2b82d766`** | **33 SecurityConfig.java** | Compile fix; see §5 |
| `6d33c75e` | Medscape clinical-tools integration | Surface replacement |
| `c9112683` | bug fixes | Hygiene |
| `e61aa667` | learning orchestration staging | Integration cleanup |
| `7c5b67de` | staging remediation hygiene | Deletions bundled |
| `04b2a0cb` | mobile/BFF/CI deepen | `one-ui-shell/next.config.js` removed |
| **`1f26349e`** | **GAP-010 Phase 6** | **`ui/experience` ~989 files** — see §5 |
| `9a52cd34` | Wave 20 sovereign | **surveillance V003 migration** deleted |

**Finding:** Deletions cluster in **four archetypes**: (1) intentional architecture correction, (2) fork retirement, (3) compile/security hygiene, (4) feature replacement with newer implementation. **No pattern of silent service excision** via POM removal.

---

## 5. Major historical removals — deep analysis

### 5.1 `ui/experience` fork retirement (`1f26349e`, 2026-05-28)

| Metric | Value |
|--------|-------|
| Files deleted | ~989 (commit stat: 868 files in diff summary, ~165,963 lines) |
| Lifespan | 2026-03-11 (`0501e103`) → 2026-05-28 (~78 days) |
| Replacement | `ui/one-ui-shell` (canonical from scaffold; enriched Phases 1a–1f) |
| CI | Playwright lifted Phase 3; `deprecated-surface-guard.yml` prevents resurrection |

**Assessment:** Largest single deletion in repo history. **Intentional merge**, not abandonment. Residual risk is **unmigrated stragglers** — convergence inventory (`c1e23126`) claims full lift; **spot-check recommended** for routes only in deleted tree.

**Deleted capability classes (sample):** full EHR router tree, admin plane, ERP pages, clinical-tools, finance flows, e2e specs — all categories that **exist or have analogues** in `one-ui-shell` post-merge.

### 5.2 Experience BFF pure proxy refactor (`8c561460`, 2026-04-13)

| Removed | Lines / scale |
|---------|---------------|
| Flyway migrations V1–V40 | 40 scripts |
| Database tables | 123 |
| JPA entities + repositories | 23 + 23 |
| BFF-local OutboxService | BFF no longer publishes as SoR |
| PostgreSQL / Flyway / Spring Data JPA deps | pom.xml |

| Added | Purpose |
|-------|---------|
| ResilientServiceClient | Circuit breaker / bulkhead / retry |
| CacheService + CacheEvictionConsumer | Redis cache-aside |
| Virtual threads | Scale posture |

**Assessment:** **Doctrine-aligned improvement** — Experience BFF ceased being a duplicate truth store. **Not a regression** unless downstream code depended on BFF-local tables (grep suggests intentional migration to sovereign services).

### 5.3 SecurityConfig mass deletion (`2b82d766`, 2026-04-13)

- **33 files** referencing Spring Security without `spring-boot-starter-security` dependency.
- **Later commits re-added** SecurityConfig on many services with OAuth2 resource-server pattern.
- **Still missing** on `dispatch-service`, `nhume-service` at HEAD.

**Assessment:** Compile-fix wave with **incomplete restoration** on newer logistics services.

### 5.4 product-registry-service → msika-service (`683821bc`, 2026-02-07)

- Early canonical registry correction.
- `product-registry-service` module swapped for `msika-service` in parent POM.
- **Equivalent replacement** under Msika branding (Products & Services Registry).

### 5.5 surveillance `V003__outbox_companion_columns.sql` (`9a52cd34`, 2026-05-29)

- Only Flyway migration deletion in recent history besides BFF V1–V40.
- **Requires human/DBA review** — may be superseded or may be regression.

### 5.6 Mock removal examples (positive)

| Commit | Change |
|--------|--------|
| `756b5210` | Drop mock citizen e2e; real monitoring devices API |
| `d8cfe5e2` | No-stub UI policy CI enforcement |
| `admin/data-export/page.test.tsx` pattern | Tests assert not `MOCK_JOBS` |

**Counter-example (regression risk):** `AIDiagnosticAssistant.tsx` still uses `MOCK_*` fallbacks — introduced or retained outside deletion commits.

---

## 6. Pickaxe sweep — concept persistence across full history

Commits touching each string at least once (`git log -S`):

| Concept | Commits | First-era presence | Regression risk |
|---------|---------|-------------------|-----------------|
| kafka | 204 | E0+ | Low — pervasive |
| FHIR | 180 | E0+ | Low |
| pharmacy | 232 | E0+ | Low |
| payment | 162 | E0+ | Low |
| claims | 147 | E0+ | Low |
| coverage | 153 | E0+ | Low |
| openapi | 125 | E0+ | Low |
| prescription | 127 | E0+ | Low |
| inpatient | 96 | E0+ | Low |
| flyway | 94 | E0+ | Medium — BFF flyway removed |
| Tshepo | 84 | E1 | Low |
| keycloak | 83 | E0+ | Low |
| PACS | 83 | Mid | Low |
| Tuso | 77 | E1 | Low |
| Vito | 76 | E1 | Low |
| telemedicine | 69 | Mid | Low |
| break-glass | 64 | E1+ | Low |
| Varapi | 63 | E1 | Low |
| Msika | 55 | E2 | Low |
| X-Purpose-Of-Use | 50 | E5+ | Low |
| DICOM | 50 | Mid | Low |
| Zibo | 49 | E1+ | Low |
| Butano | 46 | E1+ | Low |
| MusheX | 41 | Mid | Low |
| Costa | 53 | Mid | Low |
| skipTests | 38 | CI era | **Medium** — still present |
| Nompilo | 29 | E5+ | Medium — BFF stubs at HEAD |
| hapi-fhir | 27 | E0+ | Low |
| Fundo | 26 | Mid | Low |
| Indawo | 35 | Mid | Low |
| Ubomi | 34 | E2 | Medium — not wired UI |
| SecurityConfig | 92 | E0+ | Medium — strip/regrow pattern |
| EXPECTED_ROUTE_COUNT | 13 | E8+ | Low — monotonic growth |
| ui/experience | 80 | E3–E12 | N/A — fork retired |
| Ndila | 11 | E11 | New service |
| Nhume | 12 | E11 | New service |
| workflow_dispatch | 4 | E9+ | New |
| Core Transaction | 5 | E9+ | Young contract |
| core.transaction.events | **1** | E9 only (`47de393f`) | **High** — single introduction commit |
| webrtc | **1** | Rare | RTC intentionally minimal |
| websocket | 5 | Rare | Low |
| spring-security | 7 | Sparse vs SecurityConfig 92 | Medium |
| liquibase | **0** | Never | N/A — Flyway only |

**Key archaeological insight:** `core.transaction.events` appears in only **one** historical introducing commit — the entire event bus convergence for core transactions is **extremely recent** and may not have propagated to all services before further AI waves continued.

---

## 7. Parallel UX fork timeline (`ui/experience` vs `one-ui-shell`)

| Milestone | one-ui-shell | ui/experience |
|-----------|--------------|---------------|
| Repo birth | **Exists** (`484c899b`) | — |
| 2026-03-11 | Continues | **Fork created** (`0501e103`) |
| 2026-04 | `routes.ts` at 252 (`04b2a0cb`) | Parallel route growth |
| 2026-05-16 | Core tx routes (321) | Still active pre-merge |
| 2026-05-28 Phases 1a–1f | Lift target | Source of lifts |
| 2026-05-28 Phase 6 | Canonical | **Deleted** |

**Finding:** For ~78 days the repo ran **two web orchestration codebases**. GAP-010 is the critical convergence event. Any audit that only inspects `one-ui-shell` before 2026-05-28 **misses** work that lived only in the fork unless lift commits are verified.

---

## 8. Per-era capability verdict table

| Era | Preserved | Improved | Replaced (equivalent) | Weakened | Lost (unverified) |
|-----|-----------|----------|----------------------|----------|-------------------|
| E0–E2 | Sovereign registry spine | Msika replaces product-registry | product-registry → msika | — | — |
| E3–E4 | Dual UI + BFF | Experience fork accelerates UX | — | Duplicate truth risk | — |
| E5 | Health OS doctrine | Header contract | — | — | — |
| E6 | BFF proxy doctrine | Removes BFF SoR tables | DB-backed BFF → proxy | Services depending on BFF DB | — |
| E7 | Compile stability | — | Broken SecurityConfig → stripped | Security until re-added | dispatch/nhume still open |
| E8–E10 | Route registry; core tx | Journey shell | — | Contract younger than features | — |
| E11 | Logistics services born | Ndila/Nhume | — | Runtime stability | — |
| E12 | one-ui-shell unified | CI guards | experience → shell | Transient dual maintenance | Unmigrated fork routes? |
| E13–E15 | Route growth to 417 | Sovereign demo stack | — | **useCoreTransactionExperience never in history?** | surveillance V003 |

---

## 9. Updated §17.4 timeline (replaces abbreviated version)

| Period / Commit Range | Major Additions | Major Removals | Major Refactors | Risk Notes |
|----------------------|-----------------|----------------|-----------------|------------|
| 2026-02-07 `484c899b`–`873990cf` | Scaffold 20 modules; Vito; Tshepo 6-service split; Tuso/Varapi | — | product-registry → msika (`683821bc`) | Foundation; one-ui-shell from birth |
| 2026-02–03 waves | 43+ new services; portal/ops consoles | Local event types (`95e2883e`) | Shared-kernel wiring | Rapid skeleton expansion |
| 2026-03-11 `0501e103` | **ui/experience fork**; 63 modules | — | Parallel UX stack begins | **Duplication risk** |
| 2026-03-11 `319b2d3e` | Experience BFF skeleton | — | v1.1 enforcement | BFF still DB-backed |
| 2026-04 `b6776009` | Health OS doctrine; 79 modules | — | Identity/header alignment | doctrine-gap-matrix dated here |
| 2026-04-13 `8c561460` | Resilience/cache proxy | **BFF 40 migrations, 123 tables, 23 entities** | Pure proxy architecture | **Doctrine win** |
| 2026-04-13 `2b82d766` | — | **33 SecurityConfig** | Security dep fix | Incomplete restore logistics |
| 2026-04 `04b2a0cb` | routes.ts **252**; mobile/BFF deepen | next.config.js | Route registry invariant born | — |
| 2026-05-16 `47de393f` | **core-transaction.ts**; 98 modules; routes **321** | — | Doctrine runtime + dual-emit start | Single intro commit for event topic |
| 2026-05 mid `c58b3f21`/`2fad91b7` | Ndila, Nhume services | — | Logistics plane | Port doc drift |
| 2026-05-28 `1f26349e` | Shadow routes; shell lifts | **ui/experience ~989 files** | GAP-010 convergence | Largest deletion ever |
| 2026-05-28 `d8cfe5e2` | No-stub CI; routes **400** | — | Policy enforcement | — |
| 2026-05-29 `9a52cd34` | Sovereign compose overlay | surveillance V003 | Wave 20 hardening | Demo-only logistics |
| 2026-05-29 `b3407039` | Logistics UX; routes **417** | — | Mobile core-tx parity | **Missing web hook** |

---

## 10. Full-history answers to audit questions

| Question | Full-history answer |
|----------|---------------------|
| What existed before? | Feb scaffold with 20 modules; Mar fork with 63; Apr Health OS with 79; May core tx with 98–103 |
| What exists now? | 103 Maven modules; 417 routes; no ui/experience; BFF pure proxy |
| What disappeared? | ui/experience tree; BFF local DB; 33 SecurityConfigs (partially restored); product-registry module name; surveillance V003 |
| What was replaced? | product-registry→msika; experience→one-ui-shell; DB-backed BFF→proxy |
| Replacement quality? | Msika/BFF proxy/shell merge = **equivalent or better**; event envelope/raw dual-emit = **weaker**; SecurityConfig strip = **partial** |
| Backend surfaced in web/mobile? | Improving trend but 83/87 services still unknown-or-partial wiring |
| Doctrine preserved? | **Yes in docs**; **partial in runtime** (events, Nompilo stubs, missing hook) |
| Build/deploy aligned? | Monotonic module growth; compose still covers ~23 services |
| What to restore? | See parent audit §17.13; add: verify GAP-010 lift completeness, surveillance V003 |

---

## 11. Recommended follow-up (machine-assisted)

1. **GAP-010 diff script:** `git diff 1f26349e^:ui/experience/src/app -- ui/one-ui-shell/src/app` route path coverage.
2. **Full reactor compile job** on `services/pom.xml` at every era tag (CI simulation).
3. **Flyway inventory diff** at `8c561460^` vs `8c561460` for BFF — document migration retirement ADR.

---

## 12. Pass 1 correction (full-history verification)

**`useCoreTransactionExperience.ts` was incorrectly flagged as missing in Pass 1.**

| Fact | Evidence |
|------|----------|
| Introduced | `55e9a983` (2026-05-16) — 94 lines |
| Extended | `d8cfe5e2` (2026-05-28) — workflow/dispatch feeds added |
| Deleted? | **No** — `git log --diff-filter=D` returns empty for this path |
| At HEAD | Present at `ui/one-ui-shell/src/hooks/queries/useCoreTransactionExperience.ts` |

Exports include `useCoreTransactionFeed`, `useDispatchOperatorFeed`, `useWorkflowOperatorFeed`, and mutation hooks for actions/handoff.

---

*Supplement complete. Merge findings into parent audit §17.2 (methodology) and §17.4 (timeline).*
