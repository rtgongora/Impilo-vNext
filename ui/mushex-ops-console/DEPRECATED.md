# DEPRECATED — `ui/mushex-ops-console`

## Status

This sidecar UI is **retained for reference and parity only**. It is **not** the canonical experience layer for MusheX operations (claims, fraud, manual review, adapter administration).

- New feature work **must not** be added here.
- Bug fixes and dependency bumps in this sidecar are permitted only if a corresponding capability already exists (or is being implemented) in the canonical surface listed below; drift from the canonical experience is not acceptable.
- This folder must not be deleted, moved, or renamed by ad-hoc work. Retirement of the folder is tracked separately under audit gap **G-6** in [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) and as **RR-02** in [`docs/retirement/retirement-readiness-ledger.md`](../../docs/retirement/retirement-readiness-ledger.md). The retirement criteria and the telemetry signals required to satisfy them are defined in [`docs/retirement/telemetry-signals.md`](../../docs/retirement/telemetry-signals.md).

## Why deprecated

Impilo vNext doctrine commits to **one unified Health OS experience layer** for the web. The canonical web shell is [`ui/one-ui-shell`](../one-ui-shell). MusheX ops surfaces — claims processing, fraud queues, manual reviews, and the MusheX platform admin (custodial wallet, remittance, card profile, reversal admin APIs) — must surface inside that shell, not in standalone sidecars; see:

- [`docs/doctrine/health-os-doctrine.md`](../../docs/doctrine/health-os-doctrine.md) — one unified experience layer.
- [`docs/doctrine/mushex-gateway-neutrality.md`](../../docs/doctrine/mushex-gateway-neutrality.md) — MusheX dual-mode operating doctrine; defines the canonical MusheX surfaces, including the Mode A platform admin surface.
- [`docs/doctrine/costa-mushex-billing-timing.md`](../../docs/doctrine/costa-mushex-billing-timing.md) — COSTA / MusheX costing, billing-timing, and settlement separation.

This sidecar predates the consolidation onto `one-ui-shell` and its pages mirror functionality that now belongs in the canonical shell.

## Canonical replacement surfaces (in `ui/one-ui-shell`)

| This sidecar page | Canonical replacement (in `ui/one-ui-shell`) |
|--------------------|-----------------------------------------------|
| `/claims` | `/finance/payer-claims` |
| `/fraud` | `/finance/payer-ops` (fraud queues belong in payer-ops) |
| `/reviews` | `/finance/payer-ops` (manual review queues belong in payer-ops) |
| `/adapters` | [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx) — canonical Mode A admin hub (read-only). Closed audit gap **G-2** in Stage 3.3. Surfaces custodial wallets, remittance transfers, card profiles, and reversal records via the GET routes on `FinanceMushexPlatformController`. Write/admin operations (create wallet, credit/debit, create remittance, create card profile, create reversal) remain backend-only on `mushex-service` for now — they must not be re-added to this sidecar. |

When the canonical replacement is incomplete relative to this sidecar (i.e. there is still a parity gap in `one-ui-shell`), that gap belongs in the canonical shell, not here. File the work against `ui/one-ui-shell` and the relevant BFF route family below.

## Relevant BFF route families

All canonical MusheX ops traffic must be reached via the Experience BFF (`/internal/v1/**`), with the v1.2 trust headers attached by `ui/one-ui-shell/src/lib/api-client.ts`. The route families relevant to this sidecar are:

- `/internal/v1/finance/payer-claims`
- `/internal/v1/finance/payer-ops`
- `/internal/v1/finance/mushex-platform` (custodial wallet, remittance, card profile, reversal admin APIs; canonical UI consumer is [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx), read-only, added in Stage 3.3 — gap **G-2** is closed)

The authoritative `/internal/v1` controller index is generated into [`docs/architecture/experience-bff-internal-routes.md`](../../docs/architecture/experience-bff-internal-routes.md). Downstream service base URLs (incl. `mushex-base-url`) are in [`docs/architecture/experience-bff-downstream-route-map.md`](../../docs/architecture/experience-bff-downstream-route-map.md).

## What to do instead

1. **For new features:** build them in `ui/one-ui-shell` against the canonical pages above and the corresponding BFF route family.
2. **For parity gaps:** raise the gap against `ui/one-ui-shell`. Adapter administration is now visible read-only at [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx) (gap **G-2** closed in Stage 3.3); new write actions on those routes must be added to the canonical page, not here. For everything else, cite gap **G-6** (sidecar retirement) plus the canonical page.
3. **For backend wiring:** the BFF controllers for each route family above are already indexed in the generated routes file (`FinanceMushexPlatformController`, `PayerClaimsController`, `PayerOpsController`). Do not introduce a new downstream service from this sidecar.
4. **For configuration:** environment variables, base URLs, and trust headers are owned by `one-ui-shell` and the Experience BFF. Do not duplicate them here.

## Related

- [`ui/one-ui-shell`](../one-ui-shell) — canonical web shell.
- [`ui/one-ui-shell/src/app/finance`](../one-ui-shell/src/app/finance) — canonical finance pages.
- [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) — audit context, including gap **G-6**. Gap **G-2** is closed (Stage 3.3) by the canonical [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx) page.
