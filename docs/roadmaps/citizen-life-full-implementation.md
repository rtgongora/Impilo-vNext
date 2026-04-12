# Roadmap — citizen life–linked UX (production-grade)

This document is the **product and contract gate** for citizen wellness, ambient intelligence, marketplace trust, and IoT/monitoring—sequenced in **waves** so web, mobile, and BFF do not diverge again. It complements the agent execution playbook in [`agent-led-fullstack-completeness-roadmap.md`](./agent-led-fullstack-completeness-roadmap.md) (ports, completeness, Phase F per-service greening).

---

## Goal

Citizen life-linked UX is **production-grade** (shared contracts, real persistence, consent/policy-aware), **intelligence is in the shell**—not only `/ask`—**marketplace friction** is data + UI + TSHEPO, and **IoT** is real readings and timeline, not only pair/sync stubs.

---

## Definition of done (release bar)

| Area | Bar |
|------|-----|
| **Wellness** | One BFF contract; web + mobile show the **same history** for the same person; **resilience** story for mobile. |
| **Intelligence** | Shell-level search (optional command palette later); **real guidance consent**; audit-friendly calls. |
| **Marketplace** | Line items carry **risk class**; `useMarketplaceFriction` drives checkout UX; APIs still **enforce via TSHEPO**. |
| **IoT** | Web `/monitoring` aligned with mobile `MonitoringSection`; **readings → timeline**; at least one **ingestion path** (spike or vendor) in non-prod. |
| **Quality** | BFF **contract tests**, smoke E2E on wellness + monitoring + cart, **observability** on BFF → guidance/commerce. |

---

## Waves (sequenced)

| Wave | Focus | Exit signal |
|------|--------|---------------|
| **0** | Contracts, ownership, inventory, flags | Written API contract **wellness v1** + **monitoring v1**; **CPID / patient scope** documented for web citizen flows |
| **1** | Wellness / diet / sleep data plane | Web log appears on mobile (and reverse); schema/API extended as needed |
| **2** | Ambient intelligence | Search from shell works; consent stub replaced; optional inline assistant on 1–2 citizen pages |
| **3** | Marketplace graduated trust | Catalog carries risk; friction hook wired into product/cart/checkout; audit metadata on orders |
| **4** | IoT + timeline | Monitoring web wired to BFF; readings persisted/federated; timeline shows device-sourced events |
| **5** | Hardening | Security / a11y / perf; runbooks; dashboards; release checklist |
| **6** | Continuous expansion | More vendors, locales, search domains—**without breaking W1–W4 contracts** |

**Parallelism:** Wave 3 (marketplace) can overlap 1–2 once **who owns product risk metadata** is clear. Wave 4 (IoT) should follow Wave 1 enough that **identity and timeline patterns** are stable.

**Mapping to Phases A–D** (from the agent-led roadmap): **A → Wave 1**, **B → Wave 2**, **C → Wave 3**, **D → Wave 4**; Waves **0**, **5**, and **6** wrap planning and post–v1 growth.

Calendar duration is intentionally not fixed (team size and parallel streams vary). **Treat Wave 0 as the gate** before large parallel execution so web / mobile / BFF do not diverge again.

---

## Wave 0 artifacts (this repo)

| Artifact | Location |
|----------|----------|
| Wellness v1 OpenAPI (BFF-forwarded paths, HC + citizen surfaces, `patientId` = CPID scope) | [`contracts/openapi/wellness.openapi.yaml`](../../contracts/openapi/wellness.openapi.yaml) |
| Monitoring v1 OpenAPI (device list / pair / sync; readings deferred to W4) | [`contracts/openapi/monitoring.openapi.yaml`](../../contracts/openapi/monitoring.openapi.yaml) |
| BFF → wellness wiring | [`docs/architecture/experience-bff-phase-c-domain-mapping.md`](../architecture/experience-bff-phase-c-domain-mapping.md) |

---

## Health Connect (HC) — wrap-up checklist

HC parity ingest and read-backs live on **`wellness-service`** (`WellnessHealthConnectController`, ingest/query services, Flyway tables). The BFF **proxies** `/internal/v1/wellness/**` and `/internal/v1/mobile/citizen/**` to `wellness-service`.

**Done for HC slice (handoff to Wave 1+):**

- [x] Typed changeset ingest + dedupe + extension passthrough (`POST .../wellness/connect/v1/changesets`)
- [x] Manifest for clients (`GET .../manifest`)
- [x] Read APIs: sleep segments, exercise sessions, ingest log, extension records
- [x] Unit tests under `services/wellness-service/src/test/.../connect/`
- [x] OpenAPI **wellness v1** documents HC paths and `HealthConnectChangeSetRequest` shape

**Explicitly not HC / deferred:**

- Cross-person authorization guarantees → **TSHEPO + BFF session binding** (documented in OpenAPI; enforce in W1/W5)
- Monitoring **readings → timeline** → **Wave 4** (see `monitoring.openapi.yaml` note)
- Mobile **offline resilience** narrative + tests → **Wave 1** release bar

---

## Quality commands (Wave 1)

| Check | Command |
|-------|---------|
| Wellness HTTP + Postgres (Flyway, real JDBC) | From `services/`: `mvn -pl wellness-service test` — `WellnessCitizenApiDockerIntegrationTest` runs when **Docker** is available; otherwise it is **skipped** (no mocks). |
| Full stack (BFF + wellness + UI) | `docker compose -f compose/experience/docker-compose.yml up` then open Experience UI on port **3020** (see compose file). |
| Playwright against **real** compose (no `page.route`) | From `ui/experience/`: `PLAYWRIGHT_COMPOSE_E2E=1 npm run e2e -- citizen-life-compose.spec.ts --project=chromium` — requires compose **up** first; uses `e2e/citizen-life-compose.spec.ts`. |

---

## Document history

| Date | Change |
|------|--------|
| 2026-04-12 | Initial roadmap + Wave 0 contract pointers + HC wrap-up checklist. |
| 2026-04-12 | Wave 1: BFF `WellnessServiceProxyControllerTest` + Playwright `citizen-life-smoke.spec.ts`; quality command table. |
| 2026-04-12 | Removed mock-based BFF/E2E tests; monitoring path aligned BFF↔wellness; Experience devices page calls real APIs; `WellnessCitizenApiDockerIntegrationTest` (Testcontainers Postgres, `@EnabledIfDockerAvailable`). |
| 2026-04-12 | Compose dev: `WELLNESS_ALLOW_ANONYMOUS` + `IMPILO_SECURITY_ALLOW_ANONYMOUS`; Playwright `citizen-life-compose.spec.ts` + `PLAYWRIGHT_COMPOSE_E2E` webServer skip. |
