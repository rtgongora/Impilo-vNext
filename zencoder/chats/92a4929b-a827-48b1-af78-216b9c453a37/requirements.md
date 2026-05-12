# PRD: VITO Service API Integration in one-ui-shell

## Overview

**Feature**: Implement typed frontend API clients and React Query hooks for all VITO (Client Identity Registry) service endpoints in the `one-ui-shell` frontend.

**VITO** is the single source of truth for client identities on the Impilo platform — it manages Health IDs, smart cards, digital wallets (identity-linked health credits), biometric enrolment, deduplication, and identity recovery. It runs at `http://localhost:8082` and is accessed from the frontend exclusively via the Experience BFF at `http://localhost:8160` (path prefix `/internal/v1/vito/*`) or via Next.js API rewrites for citizen portal paths (`/api/v1/portal/*`).

---

## Background & Current State

### What already exists

| API Group | Current Coverage | Location |
|---|---|---|
| Portal (citizen self-service) | **Complete** — all 7 endpoints implemented | `src/lib/citizenPortalClient.ts` |
| Identity core (register, resolve) | **Partial** — register + search via BFF `/internal/v1/identity/*` | `src/hooks/queries/useIdentity.ts` |
| Client lookup (list, get) | **Partial** — different BFF path (`/internal/v1/client-registry/*`) that maps to a parallel client registry aggregate, not VITO direct | `src/hooks/queries/useClientRegistry.ts` |
| Issuance workflow | **Absent** — only ad-hoc mutations in `id-services/page.tsx`, no dedicated hook file | — |
| Smart cards (VITO) | **Absent** — `useMusheWallet.ts` has finance card hooks but they target MUSHeX (`/internal/v1/wallets/*`), not VITO cards | — |
| VITO Wallet (health credits) | **Absent** — `useMusheWallet.ts` targets the MUSHeX finance wallet; VITO identity wallet (`/v1/wallet/*`) has no dedicated hooks | — |
| Biometric | **Absent** as a VITO endpoint — `useBiometricPolicy.ts` targets TSHEPO policy only | — |
| Identity Match / Dedup | **Partial** — match candidates exposed through `useClientRegistry.ts` but no full match lifecycle hooks | — |
| Recovery (internal SHS) | **Absent** — only portal recovery in `citizenPortalClient.ts`; no `/v1/recovery/shs/*` hooks | — |
| Registry-Admin | **Absent** — `registry-admin/page.tsx` is a navigation hub only, no API hooks | — |
| Print (card print job) | **Absent** | — |
| QR resolution | **Absent** — citizen QR download exists in `citizenPortalClient.ts`; no staff/admin QR resolve hooks | — |
| Slips (PDF) | **Absent** — `shareSlipPublic.ts` is unrelated; no VITO emergency capsule or pickup slip hooks | — |

### Gap summary

~9 of 13 VITO API groups have no dedicated, typed hook file. The internal VITO wallet and VITO smart-card lifecycle are distinct from the MUSHeX finance wallet and must not be conflated.

---

## Goals

1. Provide **typed TypeScript hooks** (React Query) and raw API functions for every VITO endpoint group not already covered.
2. Extend existing partial implementations (`useIdentity.ts`, `useClientRegistry.ts`) where endpoints are missing rather than replacing them.
3. Preserve the established **routing convention**: all internal VITO calls go via `apiClient` to `/internal/v1/vito/{path}` through the BFF.
4. Citizen portal paths (`/api/v1/portal/*`) are already implemented and are out of scope unless gaps are found.
5. All TypeScript types must accurately reflect the VITO OpenAPI schema (`contracts/openapi/vito.openapi.yaml`).
6. Each hook file follows the existing pattern: `"use client"`, `@tanstack/react-query`, `apiClient` from `@/lib/api-client`, named exports.

---

## Routing Assumption

All new VITO API calls from the frontend use the **BFF proxy convention**:

```
Frontend (apiClient) → /internal/v1/vito/{vito-service-path}
  → BFF (localhost:8160) → VITO service (localhost:8082/v1/{vito-service-path})
```

For example:
- Frontend calls `/internal/v1/vito/clients` → BFF proxies to `http://localhost:8082/v1/clients`
- Frontend calls `/internal/v1/vito/cards/request` → BFF proxies to `http://localhost:8082/v1/cards/request`

**Note**: The BFF must expose these proxy routes. If the BFF does not yet have generic VITO proxying, that is a prerequisite backend task. This PRD covers the frontend hooks only; BFF route stubs may need to be added to `experience-bff` as a companion task.

---

## VITO API Endpoints — Full Inventory

### Tag: Portal (already implemented — out of scope)

| operationId | Path | Method | Status |
|---|---|---|---|
| portalRequestId | `/v1/portal/id/request` | POST | ✅ `citizenPortalClient.ts` |
| portalRecoveryStart | `/v1/portal/id/recovery/start` | POST | ✅ |
| portalRecoveryVerify | `/v1/portal/id/recovery/verify` | POST | ✅ |
| portalGetMe | `/v1/portal/me` | GET | ✅ |
| portalHealthIdQr | `/v1/portal/health-id/qr` | GET | ✅ |
| portalDelegatedPickupCreate | `/v1/portal/delegated-pickup/create` | POST | ✅ |
| portalDelegatedPickupRedeem | `/v1/portal/delegated-pickup/redeem` | POST | ✅ |

### Tag: Internal-Identity (partially implemented — extend `useIdentity.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| identityRegister | `/v1/identity/register` | POST | ✅ exists as `useRegisterClient` |
| identityResolve | `/v1/identity/resolve` | POST | ❌ missing |
| identityRotate | `/v1/identity/rotate` | POST | ❌ missing |

### Tag: Internal-Clients (extend `useIdentity.ts` or dedicated file)

| operationId | Path | Method | Status |
|---|---|---|---|
| listClients | `/v1/clients` | GET | ❌ missing (`/internal/v1/client-registry/clients` is a different BFF aggregate path) |
| getClient | `/v1/clients/{healthId}` | GET | ❌ missing |
| verifyClient | `/v1/clients/{healthId}/verify` | POST | ❌ missing |
| deactivateClient | `/v1/clients/{healthId}/deactivate` | POST | ❌ missing |
| markClientDeceased | `/v1/clients/{healthId}/deceased` | POST | ❌ missing |

### Tag: Internal-Issuance (new file: `useVitoIssuance.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| issuanceSubmit | `/v1/internal/issuance/submit` | POST | ❌ missing |
| issuanceProofing | `/v1/internal/issuance/{requestId}/proofing` | POST | ❌ missing |
| issuanceApprove | `/v1/internal/issuance/{requestId}/approve` | POST | ❌ missing |
| issuanceIssue | `/v1/internal/issuance/{requestId}/issue` | POST | ❌ missing |
| issuanceDeliver | `/v1/internal/issuance/{requestId}/deliver` | POST | ❌ missing |
| issuanceReject | `/v1/internal/issuance/{requestId}/reject` | POST | ❌ missing |
| issuanceQueue | `/v1/internal/issuance/queue` | GET | ❌ missing |
| issuanceGet | `/v1/internal/issuance/{requestId}` | GET | ❌ missing |

### Tag: Cards (new file: `useVitoCards.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| cardRequest | `/v1/cards/request` | POST | ❌ missing |
| cardPrint | `/v1/cards/{cardId}/print` | POST | ❌ missing |
| cardActivate | `/v1/cards/{cardId}/activate` | POST | ❌ missing |
| cardInactivate | `/v1/cards/{cardId}/inactivate` | POST | ❌ missing |
| cardRevoke | `/v1/cards/{cardId}/revoke` | POST | ❌ missing |
| cardGetActive | `/v1/cards/active/{healthId}` | GET | ❌ missing |
| cardHistory | `/v1/cards/history/{healthId}` | GET | ❌ missing |
| cardsByStatus | `/v1/cards/by-status/{status}` | GET | ❌ missing |

### Tag: Wallet (new file: `useVitoWallet.ts` — distinct from MUSHeX)

| operationId | Path | Method | Status |
|---|---|---|---|
| walletCreate | `/v1/wallet/create` | POST | ❌ missing |
| walletGet | `/v1/wallet/{healthId}` | GET | ❌ missing |
| walletTopup | `/v1/wallet/topup` | POST | ❌ missing |
| walletPay | `/v1/wallet/pay` | POST | ❌ missing |
| walletOffline | `/v1/wallet/offline` | POST | ❌ missing |
| walletJournal | `/v1/wallet/{walletId}/journal` | GET | ❌ missing |

### Tag: Biometric (new file: `useVitoBiometric.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| biometricEnroll | `/v1/biometric/enroll` | POST | ❌ missing |
| biometricGet | `/v1/biometric/{healthId}` | GET | ❌ missing |

### Tag: Match (new file: `useVitoMatch.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| matchTrigger | `/v1/match/{healthId}` | POST | ❌ missing |
| matchPending | `/v1/match/pending` | GET | ❌ missing |
| matchResolve | `/v1/match/{matchId}/resolve` | POST | ❌ missing |

### Tag: Recovery (new file: `useVitoRecovery.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| recoveryHandover | `/v1/recovery/handover` | POST | ❌ missing |
| recoverySHSCreate | `/v1/recovery/shs/create` | POST | ❌ missing |
| recoverySHSVerify | `/v1/recovery/shs/verify` | POST | ❌ missing |

### Tag: Registry-Admin (new file: `useVitoRegistryAdmin.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| registryMode | `/v1/registry/mode` | GET | ❌ missing |
| registryProvisionalIssue | `/v1/registry/provisional/issue` | POST | ❌ missing |
| registryProvisionalReconcile | `/v1/registry/provisional/{provisionalRef}/reconcile` | POST | ❌ missing |
| registryProvisionalPending | `/v1/registry/provisional/pending` | GET | ❌ missing |
| registryDedupPending | `/v1/registry/dedup/pending` | GET | ❌ missing |
| registryDedupResolve | `/v1/registry/dedup/{caseId}/resolve` | POST | ❌ missing |
| registryOpenCRMatch | `/v1/registry/opencr/match` | POST | ❌ missing |

### Tag: Print (add to `useVitoCards.ts` or `useVitoIssuance.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| printCardJob | `/v1/print/card/job` | POST | ❌ missing |

### Tag: QR (new file: `useVitoQr.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| qrResolve | `/v1/qr/resolve/{token}` | GET | ❌ missing |
| qrPublicKey | `/v1/qr/public-key` | GET | ❌ missing |

### Tag: Slips (new file: `useVitoSlips.ts`)

| operationId | Path | Method | Status |
|---|---|---|---|
| slipsEmergencyCapsule | `/v1/slips/emergency-capsule.pdf` | GET | ❌ missing |
| slipsPickup | `/v1/slips/pickup.pdf` | GET | ❌ missing |

---

## Deliverables

### New hook files (under `src/hooks/queries/`)

| File | Covers |
|---|---|
| `useVitoClients.ts` | Internal-Clients: listClients, getClient, verifyClient, deactivateClient, markClientDeceased |
| `useVitoIssuance.ts` | Internal-Issuance: full lifecycle (submit → proof → approve → issue → deliver/reject) + queue list + single get; plus printCardJob |
| `useVitoCards.ts` | Cards: full lifecycle (request → print → activate → inactivate → revoke) + queries (active, history, by-status) |
| `useVitoWallet.ts` | Wallet: create, get, topup, pay, offline voucher, journal — **distinct from MUSHeX finance wallet** |
| `useVitoBiometric.ts` | Biometric: enroll, get metadata |
| `useVitoMatch.ts` | Match: trigger, list pending, resolve |
| `useVitoRecovery.ts` | Recovery: handover, SHS create, SHS verify |
| `useVitoRegistryAdmin.ts` | Registry-Admin: mode, provisional CRUD, dedup queue, OpenCR match |
| `useVitoQr.ts` | QR: resolve token, get public key |
| `useVitoSlips.ts` | Slips: emergency capsule PDF, pickup slip PDF |

### Extensions to existing files

| File | Changes |
|---|---|
| `useIdentity.ts` | Add `useIdentityResolve` (identityResolve), `useIdentityRotate` (identityRotate) |

### TypeScript type exports

Each hook file exports its domain types. A shared barrel `src/lib/vito-types.ts` (or inline per-file) covers the VITO schema objects:
- `VitoClient` (from `Client` schema)
- `VitoIssuanceRequest` (from `IssuanceRequest`)
- `VitoProofingEvent` (from `ProofingEvent`)
- `VitoSmartCard` (from `SmartCard`)
- `VitoWallet` (from `Wallet`)
- `VitoWalletJournal` (from `WalletJournal`)
- `VitoBiometricMetadata` (from `BiometricMetadata`)
- `VitoMatchResult` (from `MatchResult`)
- `VitoDedupCase` (from `DedupCase`)
- `VitoDelegatedPickup` (from `DelegatedPickup`)
- `VitoAddress` (from `Address`)

---

## TypeScript Type Conventions

- All types use the `Vito` prefix to distinguish from conflicting names (e.g. `VitoWallet` vs MUSHeX wallet)
- Enum values are typed as TypeScript union string literals rather than `string` where the contract specifies an enum
- `ApiResponse<T>` wrapper from `@/lib/api-client` wraps all response types
- Paged responses use an inline type matching `{ content: T[]; page: number; size: number; totalElements: number; totalPages: number }`
- Binary (PDF) responses are fetched via `apiClient.getBlob()` and returned as `Blob`

---

## Out of Scope

- **Portal APIs** — already complete in `citizenPortalClient.ts`
- **MUSHeX wallet (`useMusheWallet.ts`)** — finance-plane wallet, not VITO; do not modify
- **TSHEPO biometric policy (`useBiometricPolicy.ts`)** — TSHEPO policy engine, not VITO enrolment
- **Client registry aggregate (`useClientRegistry.ts`)** — a separate BFF-level aggregate for governance tooling; the new `useVitoClients.ts` provides direct VITO client access. Both can coexist.
- **BFF implementation** — the new proxy routes (`/internal/v1/vito/*`) in the experience-bff are a backend prerequisite, not in scope for this frontend task. Hooks are written targeting those paths; actual BFF wiring is a separate task.
- **UI pages/components** — hook/client layer only; no new pages or visual components
- **Tags with no paths** — `Internal-Aliases`, `Internal-Dedup`, `Internal-Offline` are declared as tags in the VITO spec but have no path definitions; they are out of scope

---

## Acceptance Criteria

1. All VITO endpoints in `vito.openapi.yaml` are covered by a typed React Query hook or API function in one-ui-shell (except the explicit exclusions above).
2. No TypeScript type errors (`npm run type-check` passes in `ui/one-ui-shell`).
3. Lint passes (`npm run lint`).
4. Each hook follows the naming pattern `use{Action}{Domain}` (queries) or `use{Action}` (mutations).
5. Query keys are namespaced under `["vito", "{domain}", ...]` to prevent cache collisions.
6. Mutations call `queryClient.invalidateQueries` on the relevant domain query key on success.
7. `VitoWallet` and `useVitoWallet*` hooks do not touch `/internal/v1/wallets/*` (MUSHeX) paths.
8. PDF/binary endpoints use `apiClient.getBlob()`.

---

## Open Questions for User

The following decisions would benefit from clarification before implementation:

1. **BFF proxy convention**: Should the BFF path prefix be `/internal/v1/vito/{path}` (e.g. `/internal/v1/vito/clients`) or does the BFF already route `/internal/v1/clients` directly to VITO? The existing `useIdentity.ts` calls `/internal/v1/identity/*` which may mean VITO routes are already exposed under `/internal/v1/` without a `/vito/` segment.

2. **`useVitoClients.ts` vs extending `useClientRegistry.ts`**: The existing `useClientRegistry.ts` hits `/internal/v1/client-registry/*` (a different BFF aggregate). Should we create a separate `useVitoClients.ts` (new VITO direct path) or consolidate into the existing file? Recommendation: separate file to avoid confusion.

3. **VITO wallet naming conflict**: The VITO wallet (`/v1/wallet/*`) manages identity-linked health service credits. The MUSHeX wallet (`/internal/v1/wallet/me`) is used by `useMusheWallet.ts` for finance. Confirm this is indeed a different service/data store and not the same underlying wallet endpoint. If they are the same, the new wallet hooks should be skipped.

4. **Scope of "all APIs"**: Does this include the QR public-key endpoint and Slips PDF endpoints? These are utility endpoints rather than workflow hooks. Including them is low effort but worth confirming if they are needed by existing or planned UI pages.
