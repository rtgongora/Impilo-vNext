# Backend → Experience wiring — traceability matrix

Abbreviations: **BE** = backend service, **M** = migration/seed, **API** = primary HTTP surface, **BFF** = experience-bff, **Web** = one-ui-shell / experience, **Mob** = mobile, **Nav** = Start / command palette / local menu.

| Service | Capability | Backend domain / artifact | M/Seed | API Endpoint | BFF / client | Web surface | Mob | Nav / search | Role | Events / audit | Real data? | Mock/stub? | Status | Gap | Fix applied | Tests |
|---------|------------|----------------------------|--------|--------------|-------------|------------|-----|--------------|------|----------------|------------|------------|--------|-----|------------|------|
| costing-engine | Tariff **library** lists | `CostaTariffListEntity` | V007, V010 | `GET /api/costa/tariff-lists` | `GET /internal/v1/finance/costa-intel/tariff-lists` | `/finance/tariffs`, `/finance/costa` | TBD | cmd `cmd-finance-tariff-library`, finance hub | FINANCE | Outbox (where wired) | Yes (DB) | Removed wrong endpoint-only view | **Fixed** | Was wired to legacy `TariffEntity` only | Y — intel + grouped UI | Vitest + unit grouping |
| costing-engine | Legacy tariff **lines** | `TariffEntity` | V001+ | `GET /costa/v1/tariffs` | `GET /internal/v1/finance/tariffs` | secondary table on `/finance/tariffs` | TBD | — | FINANCE | — | Yes | — | **Partial** | Empty if no imports | Shown when present | — |
| costing-engine | Cost estimate / billing | `CostaCostEstimate`, `BillingDecision` | V007+ | `POST /api/costa/cost-estimate` | costa-intel proxy | `/finance/costa` | TBD | cmd `cmd-finance-costa` | FINANCE | persisted rows | Yes | — | **Partial** | Full encounter UX | Probe UI | — |
| costing-engine | Ops billing guard | `assertCanBill` | — | N/A (service) | N/A | Reference banner; server throws | — | — | TARIFF_APPROVER override | decision rows | — | — | **Live** | — | — | Unit (service) |
| mushex | Settlement / payment intent | `PaymentIntent` / finance | — | service ports | BFF `mushex-platform`, finance | `/finance/payments`, `settlements` | TBD | `cmd-finance-settlements` (shell) | FINANCE | Kafka / DB | Yes | — | **Live** | — | — | — |
| experience-bff | Intel proxy | — | — | — | `CostaIntelBffController` | all `/internal/v1/finance/costa-intel/*` | — | — | `FinancePlaneAuthorizationService` | — | — | — | **Live** | — | — | — |
| mvumo | Consent / proof | domain entities | — | mvumo API | BFF `Mvumo*` | summary, proxy | TBD | consent keywords | CLINICAL | audit | Yes | — | **Live** | Mobile | See Mvumo audit |
| pct | Queue / journey | PCT | — | REST | PCT client | ehr, pct-web | TBD | queue cmd | CLINICAL | — | Yes | Stubs in dev only | **Live** | Control tower | — | — |

*(Matrix is representative; expand per release.)*
