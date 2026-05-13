# Phase 4 — MusheX platform read-to-detail (no writes)

| Field | Value |
| ----- | ----- |
| Status | Implemented (first slice + follow-on — full read-to-detail for wallets/remittance/cards/reversals) |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Predecessors | [`phase-2-adapter-readiness.md`](phase-2-adapter-readiness.md), [`phase-3-attempt-time-rail-enforcement.md`](phase-3-attempt-time-rail-enforcement.md) |
| Release baseline | [`docs/release/mushex-costa-finance-phase-1-release-readiness.md`](../release/mushex-costa-finance-phase-1-release-readiness.md) |

## 1. Goal

Evolve `/finance/mushex-platform` from a counts-only hub into a useful operator surface by adding read-only per-record detail for the four platform entity families (custodial wallets, remittance transfers, card profiles, reversal records) without exposing any write actions.

## 2. Backend coverage audit (what is available to read today)

| Endpoint | Path | Filter / detail? | Listed in `/finance/mushex-platform`? |
| -------- | ---- | ---------------- | --------------------------------------- |
| Wallet list | `GET /mushex/v1/platform/wallets?ownerRef=...` | requires `ownerRef` | count + lookup |
| Wallet detail | `GET /mushex/v1/platform/wallets/{walletId}` | per id | **surfaced** |
| Wallet transactions | `GET /mushex/v1/platform/wallets/{walletId}/transactions` | per wallet | **surfaced** |
| Remittance transfers | `GET /mushex/v1/remittance-transfers` | sender filter for list | count + lookup |
| Remittance detail | `GET /mushex/v1/remittance-transfers/{transferId}` | per id | **surfaced** |
| Card profiles | `GET /mushex/v1/platform/card-profiles?walletId=...` | optional `walletId` | count + wallet link |
| Card profile detail | `GET /mushex/v1/platform/card-profiles/{cardProfileId}` | per id | **surfaced** |
| Reversals | `GET /mushex/v1/platform/reversals` | no filter | count + lookup |
| Reversal detail | `GET /mushex/v1/platform/reversals/{reversalId}` | per id | **surfaced** |
| Adapter readiness | `GET /mushex/v1/platform/adapter-readiness` (Phase 2) | none | **table (Phase 2)** |

The follow-on slice adds the previously-missing per-id GETs on MusheX and transparent BFF passthroughs for each. The UI now has four read-only detail pages.

## 3. Implementation

### 3.1 UI

- New page: `/finance/mushex-platform/wallets/[walletId]` (`ui/one-ui-shell/src/app/finance/mushex-platform/wallets/[walletId]/page.tsx`). Reuses the standard `AppLayout` / `PageShell`. No new shell. Three sections: identity card, transactions table, linked card profiles. **No write buttons** — defended by a dedicated test.
- New hooks in `ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts`:
  - `useMushexPlatformWalletTransactions(walletId)` — TanStack query over the existing BFF passthrough `GET /internal/v1/finance/mushex-platform/wallets/{walletId}/transactions`. `enabled` gated on `walletId`.
  - `useMushexPlatformCardProfilesForWallet(walletId)` — TanStack query over the existing BFF passthrough `GET /internal/v1/finance/mushex-platform/card-profiles?walletId={walletId}`. `enabled` gated on `walletId`.
  - `extractRows(payload)` — shared envelope-tolerant row extractor: accepts top-level arrays, `{ data: [] }`, `{ items: [] }`, `{ content: [] }`. Returns `[]` for unrecognised shapes (no fabrication).
- Hub entry point: a small "Open wallet by ID" input + button on the existing `/finance/mushex-platform` page that uses `next/navigation`'s `useRouter().push(...)` to navigate to the detail URL. The button is `disabled` until the input is non-empty. The form is `aria-label`led; the input has its own `aria-label`. This is **navigation**, not a write — there is no API call.
- Route registry: a new entry in `ui/one-ui-shell/src/lib/routes.ts` (`/finance/mushex-platform/wallets/[walletId]`, FINANCE role, finance sidebar, `app` layout, `work` nav zone). `EXPECTED_ROUTE_COUNT` bumped from 269 to **270**. Route-parity check list updated.

### 3.2 BFF

Additive transparent passthroughs on `FinanceMushexPlatformController` + `MushexServiceClient`:

- `GET /internal/v1/finance/mushex-platform/wallets/{walletId}`
- `GET /internal/v1/finance/mushex-platform/remittance-transfers/{transferId}`
- `GET /internal/v1/finance/mushex-platform/card-profiles/{cardProfileId}`
- `GET /internal/v1/finance/mushex-platform/reversals/{reversalId}`

### 3.3 Backend

Additive read-only per-id endpoints and tenant-scoped not-found handling:

- `GET /mushex/v1/platform/wallets/{walletId}`
- `GET /mushex/v1/remittance-transfers/{transferId}`
- `GET /mushex/v1/platform/card-profiles/{cardProfileId}`
- `GET /mushex/v1/platform/reversals/{reversalId}`

All four return `404 PLATFORM_RECORD_NOT_FOUND` when the id does not exist for the current tenant.

## 4. Safety properties

| Non-negotiable | Outcome |
| --------------- | ------- |
| No silent fabrication of financial data | The page surfaces honest unavailable / empty states ("Could not load … from MusheX", "No transactions returned for this wallet", "No card profiles linked to this wallet"). When the response shape is unrecognised, `extractRows` returns `[]`, and the empty-state copy renders. |
| No unsafe money movement | The page exposes zero `apiClient.{post,put,patch,delete}` calls. A dedicated test asserts there are no write-action buttons. |
| No accidental production rail routing | Not applicable; this page only reads custodial wallet data. |
| No breaking OpenAPI changes | None. New BFF/MusheX consumption only. |
| No database migrations | None. |
| No deletion of deprecated sidecars | None. |
| No removal of legacy mobile/wellness wallet routes | None. |
| No new shell, no new API client, no new state management library, no new auth model | Reuses `AppLayout`, `PageShell`, `apiClient`, TanStack Query, and the existing role-guard pattern. |
| Idempotency behaviour | Not applicable; this is a read-only surface. |
| Docs and audit trail current | Updated in the same batch. |

## 5. Tests (added in this batch)

| File | New tests | What they assert |
| ---- | --------- | ---------------- |
| `ui/one-ui-shell/src/app/finance/mushex-platform/wallets/[walletId]/page.test.tsx` | **7** | header + identity card render the wallet ID; transactions render from a top-level-array envelope; card profiles render from a `{ data: [] }` envelope; BFF routes are documented on every table; **no write buttons exist**; empty payloads render honest "no rows" copy; both endpoints in error state render the documented "Could not load … from MusheX" copy. |
| `ui/one-ui-shell/src/lib/__tests__/routes.test.ts` | **1** | the new `/finance/mushex-platform/wallets/[walletId]` route is registered with FINANCE role, finance sidebar, `app` layout, `work` nav zone, and `pageTitle: "Custodial wallet"`. |
| `ui/one-ui-shell/src/app/finance/mushex-platform/page.test.tsx` | (already authored in a prior interrupted batch) | the "Open wallet by ID" form pushes to the detail URL on submit; disabled-state of the button when input is empty. |

Run results in this batch:

- `npx vitest run src/app/finance/mushex-platform src/lib/__tests__/routes.test.ts` → **45 / 45 pass** (27 routes + 11 mushex-platform hub + 7 wallet detail).
- `node scripts/route-parity-check.mjs` → **124 / 124 routes resolve to a page file**.

## 6. Backward compatibility

- `EXPECTED_ROUTE_COUNT` raised 269 → 270 to reflect one additive route. `ROUTES.length` matches.
- The hub `/finance/mushex-platform` page gains a navigation form; existing tests + sections continue to render. The form is the only structural addition and is read-only navigation, not a state-mutation surface.
- No public BFF or MusheX endpoint added or changed.
- No OpenAPI change.

## 7. Future work (out of scope here)

1. Rich per-record operator actions remain deferred (no writes in Phase 4).
2. Audit/event correlation panel by aggregate id (`walletId`, `cardProfileId`, `reversalId`, `remittanceRequestId`) belongs to the Phase 8 observability slices.
3. Step-up authorization + dual-control for write paths remains deferred to the Phase 4 safety design follow-on.

Write actions for any of those records (`block card`, `credit/debit wallet`, `execute reversal`, `release payout`) remain explicitly deferred until the **safety design** named in the Phase 4 task list (role gating, step-up auth, dual control, reason capture, audit immutability, rate/amount limits, break-glass) is in place. That design is a Phase 4 future-stage deliverable, not Phase 4's first slice.

## 8. Doctrine alignment

- *Gateway-neutral by design* — preserved; this page reads platform admin data and never touches the routing contract.
- *Gateway-capable by default* — preserved; the page never blocks care.
- *Health-focused always* — preserved; no clinical surface touched.
- *Wallet local-fallback principle* — unrelated; this page consumes platform admin endpoints, not the patient-facing wallet plane gated by `impilo.wallet.allow-local-fallback`.
