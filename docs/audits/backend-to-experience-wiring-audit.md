# Backend → Experience layer wiring audit

**Scope:** Impilo vNext — service-to-UI completeness (One UI Shell, `ui/experience`, BFF, mobile).  
**Last updated:** 2026-04-23  
**Method:** Codebase review, route/BFF mapping, and targeted fix for **COSTA / MusheX tariff library** (primary case).

## Doctrine

A capability is not complete until: domain + persistence/seed (where needed) + API + **BFF/contract** + **web/mobile surface** + **discoverable navigation** + role/permission story + **loading/empty/error** + **no fake production data** + tests + documentation.

## Legend — service status

| Value | Meaning |
|-------|--------|
| **live** | Built service with APIs in repo |
| **skeleton** | Module / placeholders |
| **unknown** | Not verified in this pass |

## Ring 0 / trust / registry (sample rows)

| Service | Module | Status | Key wiring | UI / BFF | Gaps (priority) | Fix status |
|---------|--------|--------|------------|----------|-----------------|------------|
| tshepo-authz-service | `services/tshepo-authz-service` | live | ext_authz, PDP | BFF `FinancePlaneAuthorizationService` optional PDP | Harden `require-tshepo-authorize` in prod (P1) | Documented |
| tshepo-identity-service | `services/tshepo-identity-service` | live | CPID / MOSIP | Registry UIs, BFF | — | — |
| tshepo-consent-service | `services/tshepo-consent-service` | live | FHIR Consent | Mvumo + BFF | Full Mvumo UX parity (P1) | Ongoing (see `patient-care-consent-surface.md`) |
| tshepo-audit / keys / offline | various | live | | Ops / audit consoles | Broader cross-UI surfacing (P2) | Partial |
| mvumo-service | `services/mvumo-service` | live | Consent / proof | BFF proxy, summary | Mobile parity (P1) | Documented |
| vito / varapi / tuso / zibo | `services/*` | live | Registries | Registry plane, BFF | — | — |
| butano-service | `services/butano-service` | live | SHR / FHIR | ehr, BFF | — | — |
| ubomi-service | `services/ubomi-service` | live | CRVS | **unknown** surface | Dedicated workflow UI if required (P2) | Open |

## Clinical

| Service | Status | API / BFF | Web | Mobile | Gap / priority |
|---------|--------|-----------|-----|--------|----------------|
| pct-service | live | PCT, BFF summary | ehr, pct-web, experience | Provider app TBD | Control tower deep links (P2) |
| oros-service | live | ORO | oros-web | TBD | Result ack in all charts (P2) |
| referral / telemedicine | mixed | Controllers in clinical apps | `telemedicine` routes (where present) | TBD | 7-stage full parity (P1) — **see traceability** |
| inpatient / scheduling / pharmacy | live–skeleton | Per module | UIs | TBD | Schedule + workforce surfacing (P2) |
| pacs-adapter / fhir-gateway | skeleton–live | / limited | imaging surfaces | TBD | Chart + referral embedding (P1) |

## Enterprise / finance — **COSTA & MusheX (deep-dive)**

| Service | Status | Domain / seed | API | Experience |
|---------|--------|----------------|-----|------------|
| **costing-engine-service (COSTA)** | live | V007+ migrations, V010 upload examples | `/costa/v1/*`, `/api/costa/*` | BFF: `FinanceController` + `CostaIntelBffController` (`/internal/v1/finance/costa-intel/**`) |
| **mushex-service** | live | finance tables | payments / intents | BFF, finance pages, `settlements` |

### COSTA — fix applied (2026-04-23)

**Symptom:** Seeded `costa_tariff_lists` (Zimbabwe PoC, WHO, international refs, etc.) did **not** appear in Experience **Tariff** page.

**Root cause:** `/finance/tariffs` called **only** `GET /internal/v1/finance/tariffs` → COSTA **legacy** `GET /costa/v1/tariffs` (`TariffEntity` line table), not **tariff library lists** from `GET /api/costa/tariff-lists`.

**Fix:** Experience + One UI Shell **Tariff library** page now loads `GET /internal/v1/finance/costa-intel/tariff-lists` (BFF → `/api/costa/tariff-lists`), groups lists (default demo, WHO, international, uploads, retired), shows **reference** banner + Zimbabwe PoC disclaimer, keeps legacy `TariffEntity` table as secondary when data exists. Seed extended in **V010** (upload channel demos + retired example).

**Operational billing / MusheX:** Server-side `assertCanBill` in `CostaTariffIntelService` blocks invoice/claim from **reference-only** or **not approved** lists unless `force_operational_billing` + `TARIFF_APPROVER` actor.

| Domain object (expected) | Model / schema | Exposed via API | Experience |
|----------------------------|---------------|-----------------|------------|
| TariffLibrary | `TariffLibraryEntity`, V007 | `GET /api/costa/tariff-libraries` | `/finance/costa` raw; library context in list rows |
| TariffList / items | V007, V010 | `tariff-lists`, `tariff-lists/{id}` | **Tariff library** page + COSTA page selector |
| Tariff upload batch | V008+ | `POST /api/costa/tariff-upload` + pipeline | **COSTA** page (probes); full wizard TBD (P2) |
| Charge sheet | V007 | `charge-sheets` | COSTA + finance workspace (P1 expand) |
| Cost estimate / billing decision / invoice / handoff | services | `cost-estimate`, `invoices/from-cost-estimate`, `payment-handoff` | `/finance/costa`, reports |

See: [`costa-mushex-experience-layer-wiring-audit.md`](./costa-mushex-experience-layer-wiring-audit.md), [`costa-tariff-library-ui-coverage.md`](./costa-tariff-library-ui-coverage.md).

## UI applications (summary)

| App | Path | Audited | Notes |
|-----|------|---------|--------|
| one-ui-shell | `ui/one-ui-shell` | Y | Aligned with experience for tariffs |
| experience | `ui/experience` | Y | BFF rewrites `/internal` → 8160 |
| ehr, portal, self-service, butano-web, pct-web, oros-web, pharmacy-web, inventory-web, msika-*, costa-console, mushex-* | under `ui/` | Partial | **costa-console** = ops; primary actor flow = **experience / shell** |

## Mobile

| Area | Path | Status |
|------|------|--------|
| apps/mobile, citizen/provider | `apps/*` (if present) | See [`mobile-parity-traceability-matrix.md`](./mobile-parity-traceability-matrix.md) |

## Global gaps (this repository pass)

1. **Telemedicine 7-stage** — route and contract audit still needed (P1).
2. **PACS** — deep chart / referral links (P1).
3. **Dictation** — provider surfaces share contracts; implementation per app (P2).
4. **Cross-app mock** — see [`production-mock-stub-removal-audit.md`](./production-mock-stub-removal-audit.md).

## Related documents

- [`backend-to-experience-wiring-traceability-matrix.md`](./backend-to-experience-wiring-traceability-matrix.md)
- [`service-api-client-coverage-matrix.md`](./service-api-client-coverage-matrix.md)
- [`patient-summary-service-contribution-map.md`](./patient-summary-service-contribution-map.md)
- [`start-search-command-coverage-map.md`](./start-search-command-coverage-map.md)
