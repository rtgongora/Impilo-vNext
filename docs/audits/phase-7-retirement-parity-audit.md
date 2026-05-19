# Phase 7E — Retirement parity audit (RR-01..RR-07)

| Field | Value |
| ----- | ----- |
| Status | Implemented (Phase 7E) |
| Inputs | [`docs/retirement/retirement-readiness-ledger.md`](../retirement/retirement-readiness-ledger.md), sidecar `DEPRECATED.md` files, canonical `ui/one-ui-shell` route map |
| Scope | Verify that each deprecated artefact has a documented canonical replacement and no net-new feature destination ambiguity |

## 1. Why this audit exists

`telemetry-signals.md` defines `CANONICAL_PARITY` as a required retirement signal. This document is the first parity sweep for the retirement ledger entries, so each RR entry has concrete evidence instead of only doctrine references.

## 2. Parity matrix

| Ledger id | Deprecated artefact | Canonical replacement | Parity result |
| --------- | ------------------- | --------------------- | ------------- |
| RR-01 | `ui/mushex-finance-console` | `/finance/settlements`, `/finance/reconciliation`, `/finance/refunds`, `/finance/ledger`, `/finance/costa`, `/finance/mushex-platform` | Pass — every listed sidecar family has a canonical route and documented BFF family |
| RR-02 | `ui/mushex-ops-console` | `/finance/payer-claims`, `/finance/payer-ops`, `/finance/mushex-platform`, `/admin/audit/**` | Pass — payer queue, ops queue, and platform admin are canonicalized; write admin remains intentionally deferred |
| RR-03 | `ui/mushex-payer-portal` | `/wallet`, `/finance/payer-claims`, `/finance/payer-ops`, `/finance/remittances` | Pass — payer/payment/remittance surfaces exist in canonical shell |
| RR-04 | `ui/experience` | `ui/one-ui-shell` | Pass (replacement identified) — folder remains telemetry-blocked for retirement |
| RR-05 | `ui/ehr` | `ui/one-ui-shell` `/ehr/**` | Pass (replacement identified) — folder remains telemetry-blocked for retirement |
| RR-06 | Legacy mobile wallet routes | `/internal/v1/wallet/me`, `/internal/v1/wallet/me/transactions` | Pass for functional parity; retirement remains telemetry-blocked |
| RR-07 | `costa-console` (catalog row) | `/finance/costa` | Pass — canonical route exists; artefact presence still needs repository verification before retirement status change |

## 3. Findings

- Canonical replacements are now explicit for all ledger entries; no entry remains without a mapped target surface.
- The main retirement blockers are operational evidence (`SIDECAR_UI`, `LEGACY_WEB_SHELL`, `LEGACY_BFF_ROUTE`) rather than parity ambiguity.
- RR-06 now has additive metric instrumentation in Experience BFF for legacy wallet route traffic (`impilo.legacy.route.requests`, `route_family=mobile_citizen_wallet`), reducing evidence-gathering risk for later retirement.

## 4. Out-of-scope

- No folder deletion, route retirement, or CI pipeline removal is performed by this audit.
- No dashboard wiring is performed here; this file is parity evidence, not telemetry evidence.
