# COSTA / MusheX — Experience layer wiring audit

## Architecture

- **COSTA** (`costing-engine-service`, port **8101**): canonical costing, tariff intel (`/api/costa/**`), legacy bills (`/costa/v1/**`).
- **MusheX** (`mushex-service`): payments, intents, settlement — integrated via BFF and Kafka from bill lifecycle.
- **BFF:** `CostaIntelBffController` → `http://costa/api/costa/...` with v1.2 trust headers.

## Issue & resolution (tariff library)

| Item | Before | After |
|------|--------|--------|
| `/finance/tariffs` data source | Legacy `GET /costa/v1/tariffs` only | **Primary:** `GET .../costa-intel/tariff-lists` → `costa_tariff_lists` |
| Seeded lists visible | Often **no** (empty `tariff` table) | **Yes** — grouped Default / WHO / International / Uploads / Retired |
| Reference warnings | N/A | Banner + per-row for reference-only; ZW PoC disclaimer |
| Billing / MusheX for refs | Server already blocks | UI explains; `assertCanBill` unchanged |

## Endpoints (operator checklist)

| Operation | COSTA | BFF |
|-----------|--------|-----|
| List libraries | `GET /api/costa/tariff-libraries` | `GET /internal/v1/finance/costa-intel/tariff-libraries` |
| List tariff lists | `GET /api/costa/tariff-lists` | `GET /internal/v1/finance/costa-intel/tariff-lists` |
| Cost estimate | `POST /api/costa/cost-estimate` | `POST /internal/v1/finance/costa-intel/cost-estimate` |
| Charge sheet | `POST /api/costa/charge-sheets` | same via proxy path |
| Invoice from estimate | `POST /api/costa/invoices/from-cost-estimate` | same |
| MusheX handoff | `POST /api/costa/payment-handoff` | same |

## Gaps (next)

1. **Full upload UI** (CSV/XLSX/JSON) in Experience — today: COSTA API + **COSTA** finance page partial probes.
2. **Charge sheet** end-to-end from encounter CTA (not only COSTA page).
3. **Patient summary** line for open balance / MusheX status — BFF DTO extension.

## Related

- `services/costing-engine-service/.../V007__costa_tariff_intel_vertical_slice.sql`
- `V010__costa_tariff_operational_upload_and_retired_seed.sql`
- `shared-ui/lib/finance/tariff-library-groups.ts` — UI grouping
