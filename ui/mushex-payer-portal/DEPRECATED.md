# DEPRECATED — `ui/mushex-payer-portal`

## Status

This sidecar UI is **retained for reference and parity only**. It is **not** the canonical experience layer for MusheX payer / patient-facing payment flows.

- New feature work **must not** be added here.
- Bug fixes and dependency bumps in this sidecar are permitted only if a corresponding capability already exists (or is being implemented) in the canonical surface listed below; drift from the canonical experience is not acceptable.
- This folder must not be deleted, moved, or renamed by ad-hoc work. Retirement of the folder is tracked separately under audit gap **G-6** in [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) and as **RR-03** in [`docs/retirement/retirement-readiness-ledger.md`](../../docs/retirement/retirement-readiness-ledger.md). The retirement criteria and the telemetry signals required to satisfy them are defined in [`docs/retirement/telemetry-signals.md`](../../docs/retirement/telemetry-signals.md).

## Why deprecated

Impilo vNext doctrine commits to **one unified Health OS experience layer** for the web. The canonical web shell is [`ui/one-ui-shell`](../one-ui-shell). MusheX payer and patient-facing payment surfaces — payments, receipts, remittance views — must live inside that shell, not in standalone sidecars; see:

- [`docs/doctrine/health-os-doctrine.md`](../../docs/doctrine/health-os-doctrine.md) — one unified experience layer.
- [`docs/doctrine/mushex-gateway-neutrality.md`](../../docs/doctrine/mushex-gateway-neutrality.md) — MusheX dual-mode operating doctrine; defines the canonical MusheX surfaces (wallet, payer-claims, payer-ops, settlement, reconciliation, refunds, ledger).
- [`docs/doctrine/costa-mushex-billing-timing.md`](../../docs/doctrine/costa-mushex-billing-timing.md) — COSTA / MusheX costing, billing-timing, and settlement separation.

This sidecar predates the consolidation onto `one-ui-shell` and its pages mirror functionality that now belongs in the canonical shell.

## Canonical replacement surfaces (in `ui/one-ui-shell`)

| This sidecar page | Canonical replacement (in `ui/one-ui-shell`) |
|--------------------|-----------------------------------------------|
| `/payments` | `/wallet` (patient-facing pay flow) and `/finance/payer-claims` (payer-side payment lifecycle) |
| `/receipts` | `/finance/payer-claims` (claim-linked receipts) and `/wallet` (patient-side receipts) |
| `/remittance` | `/finance/payer-ops` (payer remittance operations); custodial remittance-transfer counts and card-profile/reversal admin views live in [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx) (Stage 3.3, audit gap **G-2**) |

When the canonical replacement is incomplete relative to this sidecar (i.e. there is still a parity gap in `one-ui-shell`), that gap belongs in the canonical shell, not here. File the work against `ui/one-ui-shell` and the relevant BFF route family below.

## Relevant BFF route families

All canonical MusheX payer traffic must be reached via the Experience BFF (`/internal/v1/**`), with the v1.2 trust headers attached by `ui/one-ui-shell/src/lib/api-client.ts`. The route families relevant to this sidecar are:

- `/internal/v1/wallet`
- `/internal/v1/finance/payer-claims`
- `/internal/v1/finance/payer-ops`

The authoritative `/internal/v1` controller index is generated into [`docs/architecture/experience-bff-internal-routes.md`](../../docs/architecture/experience-bff-internal-routes.md). Downstream service base URLs (incl. `mushex-base-url`) are in [`docs/architecture/experience-bff-downstream-route-map.md`](../../docs/architecture/experience-bff-downstream-route-map.md).

## What to do instead

1. **For new features:** build them in `ui/one-ui-shell` against the canonical pages above and the corresponding BFF route family.
2. **For parity gaps:** raise the gap against `ui/one-ui-shell` and cite audit gap **G-6** (sidecar retirement) plus the canonical page that needs the missing capability.
3. **For backend wiring:** the BFF controllers for each route family above are already indexed in the generated routes file (`WalletController`, `PayerClaimsController`, `PayerOpsController`). Do not introduce a new downstream service from this sidecar.
4. **For configuration:** environment variables, base URLs, and trust headers are owned by `one-ui-shell` and the Experience BFF. Do not duplicate them here.

## Related

- [`ui/one-ui-shell`](../one-ui-shell) — canonical web shell.
- [`ui/one-ui-shell/src/app/finance`](../one-ui-shell/src/app/finance) — canonical finance pages.
- [`ui/one-ui-shell/src/app/wallet`](../one-ui-shell/src/app/wallet) — canonical Mushe Wallet surface.
- [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) — audit context, including gap **G-6**.
