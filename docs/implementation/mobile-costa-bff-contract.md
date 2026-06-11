# Mobile Costa BFF Contract (Citizen Gap)

**Status:** Citizen routes **not implemented** in experience-bff as of closure wave.  
**Provider:** Partial — `GET /internal/v1/mobile/provider/billing/charges` exists and is consumed via `queueService.fetchCharges` in the provider app (`BillingScreen`, `FinanceOverviewScreen`).

## Citizen routes required

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/internal/v1/mobile/citizen/costa/charges/pending` | List pending charges for authenticated Health ID |
| GET | `/internal/v1/mobile/citizen/costa/quotes` | List cost quotes / estimates |
| GET | `/internal/v1/mobile/citizen/costa/quotes/{quoteId}` | Quote detail |
| POST | `/internal/v1/mobile/citizen/costa/quotes/estimate` | Request cost estimate for a service bundle |
| GET | `/internal/v1/mobile/citizen/costa/receipts` | Paid receipts |
| GET | `/internal/v1/mobile/citizen/costa/coverage-status` | Coverage / exemption markers |

## Response shape (pending charge item)

```json
{
  "id": "uuid",
  "clientId": "health-id or cpid",
  "facilityId": "uuid",
  "facilityName": "string",
  "serviceCode": "string",
  "serviceName": "string",
  "amount": 0.0,
  "currency": "ZAR",
  "status": "PENDING",
  "createdAt": "ISO-8601",
  "dueAt": "ISO-8601",
  "exemptionMarker": "optional",
  "coverageMarker": "optional"
}
```

## Mobile behaviour until routes exist

- `financeService.fetchPendingCharges()` returns `{ blocked: true, charges: [], blockedReason }` — **no fabricated rows**.
- `FinanceSection` renders `costa-blocked-state` with professional copy.
- Service registry: Costa `wiringStatus: partiallyWired` (provider), citizen surface remains blocked.
- MusheX wallet balance/transactions remain on `/internal/v1/wallet/me/*`.

## Backend references

- Provider BFF: `MobileProviderExtendedController` — `/billing/charges`, `/billing/charge`
- OpenAPI: `contracts/openapi/experience-bff.openapi.yaml` — `/internal/v1/mobile/provider/billing/charges`
- Audit: `docs/audits/costa-mushex-experience-layer-wiring-audit.md` (G-3 citizen finance dead routes)
