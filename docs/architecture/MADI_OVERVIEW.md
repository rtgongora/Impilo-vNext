# MADI — National Blood Transfusion Service

> MADI (MoHCC blood transfusion bounded context) is Impilo's sovereign blood
> donation, processing, inventory, clinical ordering, transfusion, and
> haemovigilance system.

| Item | Value |
|------|-------|
| **Display name** | MADI |
| **Formal name** | MoHCC Blood Transfusion Service |
| **Module** | `services/madi-service` |
| **Service port (local dev)** | 8292 (see port allocation runbook) |
| **API base (domain)** | `/internal/v1/madi/*` |
| **Mobile BFF citizen** | `/internal/v1/mobile/citizen/madi/*` |
| **Mobile BFF provider** | `/internal/v1/mobile/provider/madi/*` |
| **Database schema** | `madi` |
| **Java package** | `zw.gov.mohcc.impilo.madi` |
| **Plane** | Clinical (primary) |

## Scope

MADI covers the full blood lifecycle:

1. **Donor engagement** — registration, eligibility screening, deferrals, communication preferences, feedback, drives near me.
2. **Donation drives** — publish, register, screen, collect, close.
3. **Processing** — component separation, labelling, quarantine.
4. **Blood bank stock** — facility inventory balances, unit status.
5. **Clinical orders** — crossmatch, reserve, issue linked to OROS orders.
6. **Transfusion** — episode start, observations, completion, verification.
7. **Haemovigilance** — adverse reaction reporting and case investigation.
8. **Central bank** — inter-facility coordination and national reserves.
9. **Dashboards** — programme KPIs and stock visibility.

## Architectural placement

| Plane | MADI contribution |
|-------|-------------------|
| Trust | Every request via Envoy → TSHEPO; purpose-of-use and facility context enforced |
| Registry | VITO (person CPID for donors/patients), TUSO (facilities), Ndila (drive locations) |
| Clinical | BUTANO (transfusion documentation), OROS (orders), NHUME (blood product logistics) |
| Experience | BFF mobile surfaces + planned web `/madi/*` routes in one-ui-shell |
| Data | Outbox events for surveillance and reporting roll-ups |

## Core transaction alignment

| Transaction type | Journey |
|------------------|---------|
| `BLOOD_DONATION` | Person: FIND_CARE → ACCESS_SERVICE → GIVE_FEEDBACK |
| `BLOOD_ORDER` | Provider: ORDER_ACTIONS → COMPLETE_TRANSACTION |
| `TRANSFUSION` | Provider: DELIVER_CARE → ORDER_ACTIONS |
| `HAEMOVIGILANCE` | Platform: EMIT_EVENTS_AND_AUDIT → FEED_REPORTING_AND_INTELLIGENCE |

See [`contracts/core-transaction.ts`](../../contracts/core-transaction.ts) and companion docs in this folder.

## Mobile parity (this wave)

| App | Surface |
|-----|---------|
| Citizen | `Personal → Blood Donor` hub (`MadiDonorHubScreen`) |
| Provider | `Clinical Tools → Blood Orders / Transfusion / Blood Drives / Haemovig.` |

## Related documents

- [`MADI_DOMAIN_MODEL.md`](./MADI_DOMAIN_MODEL.md)
- [`MADI_INTEGRATION_POINTS.md`](./MADI_INTEGRATION_POINTS.md)
- [`MADI_PERMISSIONS.md`](./MADI_PERMISSIONS.md)
- [`MADI_FRONTEND_ROUTES.md`](./MADI_FRONTEND_ROUTES.md)
- [`MADI_TESTS_AND_GATES.md`](./MADI_TESTS_AND_GATES.md)
