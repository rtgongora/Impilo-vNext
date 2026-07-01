# Mobile Costa BFF Contract (Citizen Gap)

**Status (updated 2026-07-01):** Citizen **pending-charges route is now IMPLEMENTED and verified** —
`GET /internal/v1/mobile/citizen/costa/charges/pending` (`CitizenCostaController` → COSTA outstanding
bills), consumed by `financeService.fetchPendingCharges`. Quotes / quote-detail / estimate / receipts /
coverage-status routes remain **not yet implemented**.  
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

## Mobile behaviour (updated 2026-07-01 — pending-charges live)

- `financeService.fetchPendingCharges()` now calls `GET /internal/v1/mobile/citizen/costa/charges/pending`
  and returns `{ charges, blocked: false }`, adapting the real COSTA `OutstandingBillDto` rows onto
  `PendingCharge` — **no fabricated rows**; fields COSTA does not provide (service name, dates) are
  left empty rather than invented.
- `FinanceSection` renders the live pending-charge rows (or an honest "No pending charges" empty state).
  The `costa-blocked-state` card only shows if a future caller re-sets `blocked`.
- Service registry: Costa `dataMode: native` (citizen pending-charges live); quotes/receipts still pending.
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

**Implemented + verified 2026-07-01:** after bootstrapping the local Maven reactor (installing the
internal SNAPSHOT libs into `~/.m2`), `experience-bff` compiles in-session and
`CitizenCostaController` was added exactly as above, backed by
`CostaServiceClient.getFinancePatientOutstanding`. Verified by `CitizenCostaControllerTest`
(MockMvc, 2 tests: real pass-through + fail-soft-to-empty) and on mobile by
`financeService.costa.test.ts` + `FinanceSection.test.tsx` (citizen-app typecheck + 6 tests green).
Remaining COSTA citizen routes (quotes / estimate / receipts / coverage-status) are still open.
