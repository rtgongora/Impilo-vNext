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

## Implementation-ready note (verified 2026-07-01)

The citizen pending-charges route is **backed by an existing, real COSTA client method** —
no new downstream contract is required:

- `CostaServiceClient.getFinancePatientOutstanding(cpid)` →
  `GET {costa}/costa/v1/finance/patient-accounts/{cpid}/outstanding` (also `getFinancePatientAccount`
  and `getFinancePatientTransactions(cpid, page, size, dateFrom, dateTo)` for receipts/history).

**Recommended implementation** (mirror `CitizenHealthSummaryWebController` / `CitizenVisitStatusController`):
add a `CitizenCostaController` at `/internal/v1/mobile/citizen/costa/charges/pending` that resolves
the citizen `cpid` from `X-Actor-ID` and returns `costaClient.getFinancePatientOutstanding(actorId)`
under `{ data, meta }`. Add a MockMvc controller test (mocked `CostaServiceClient`) alongside
`CitizenAppointmentControllerTest`. Then point mobile `financeService.fetchPendingCharges()` at the
new route and set `costa` `frontend_wiring_status: partiallyWired` (citizen) in
`apps/mobile/packages/mobile-registry/src/wiring.ts`.

**Deferred in the 2026-07-01 MVP drive (honest seam, not faked):** the experience-bff Java module
cannot be compiled/tested in the current web-session environment (no local Maven artifact repo for
the multi-module estate; a full reactor build is required). To avoid shipping unverified Java to the
shared branch and to avoid pointing mobile at an undeployed route (a dead route), citizen Costa
remains `blocked` and `financeService.fetchPendingCharges()` keeps returning
`{ blocked: true, charges: [] }` with no fabricated rows. Implement + verify in an environment with a
working backend build.
