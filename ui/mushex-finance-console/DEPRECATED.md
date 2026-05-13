# DEPRECATED — `ui/mushex-finance-console`

## Status

This sidecar UI is **retained for reference and parity only**. It is **not** the canonical experience layer for MusheX finance flows.

- New feature work **must not** be added here.
- Bug fixes and dependency bumps in this sidecar are permitted only if a corresponding capability already exists (or is being implemented) in the canonical surface listed below; drift from the canonical experience is not acceptable.
- This folder must not be deleted, moved, or renamed by ad-hoc work. Retirement of the folder is tracked separately under audit gap **G-6** in [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) and as **RR-01** in [`docs/retirement/retirement-readiness-ledger.md`](../../docs/retirement/retirement-readiness-ledger.md). The retirement criteria and the telemetry signals required to satisfy them are defined in [`docs/retirement/telemetry-signals.md`](../../docs/retirement/telemetry-signals.md).

## Why deprecated

Impilo vNext doctrine commits to **one unified Health OS experience layer** for the web. The canonical web shell is [`ui/one-ui-shell`](../one-ui-shell). MusheX must surface inside that shell, not in standalone sidecars; see:

- [`docs/doctrine/health-os-doctrine.md`](../../docs/doctrine/health-os-doctrine.md) — one unified experience layer.
- [`docs/doctrine/mushex-gateway-neutrality.md`](../../docs/doctrine/mushex-gateway-neutrality.md) — MusheX dual-mode operating doctrine; defines the canonical MusheX surfaces.
- [`docs/doctrine/costa-mushex-billing-timing.md`](../../docs/doctrine/costa-mushex-billing-timing.md) — COSTA / MusheX costing, billing-timing, and settlement separation.

This sidecar predates the consolidation onto `one-ui-shell` and its pages mirror functionality that now belongs in the canonical shell.

## Canonical replacement surfaces (in `ui/one-ui-shell`)

| This sidecar page | Canonical replacement (in `ui/one-ui-shell`) |
|--------------------|-----------------------------------------------|
| `/settlements` | `/finance/settlements` |
| `/reconciliation` | `/finance/reconciliation` |
| `/refunds` | `/finance/refunds` |
| `/ledger` | `/finance/ledger` |
| _(COSTA tariff / cost-estimate / charge-sheet entry points)_ | [`/finance/costa`](../one-ui-shell/src/app/finance/costa/page.tsx) — read-only COSTA hub (Stage 3.2, audit gap **G-1**). |
| _(MusheX platform custodial admin: wallets, remittance transfers, card profiles, reversals)_ | [`/finance/mushex-platform`](../one-ui-shell/src/app/finance/mushex-platform/page.tsx) — read-only Mode A admin hub (Stage 3.3, audit gap **G-2**). |

When the canonical replacement is incomplete relative to this sidecar (i.e. there is still a parity gap in `one-ui-shell`), that gap belongs in the canonical shell, not here. File the work against `ui/one-ui-shell` and the relevant BFF route family below.

## Relevant BFF route families

All canonical MusheX finance traffic must be reached via the Experience BFF (`/internal/v1/**`), with the v1.2 trust headers attached by `ui/one-ui-shell/src/lib/api-client.ts`. The route families relevant to this sidecar are:

- `/internal/v1/finance/settlements`
- `/internal/v1/finance/reconciliation`
- `/internal/v1/finance/refunds`
- `/internal/v1/finance/ledger`
- `/internal/v1/finance/costa-intel` — backs `/finance/costa`.
- `/internal/v1/finance/mushex-platform` — backs `/finance/mushex-platform` (read-only; admin write routes are deferred per Stage 3.3).

The authoritative `/internal/v1` controller index is generated into [`docs/architecture/experience-bff-internal-routes.md`](../../docs/architecture/experience-bff-internal-routes.md). Downstream service base URLs (incl. `mushex-base-url`) are in [`docs/architecture/experience-bff-downstream-route-map.md`](../../docs/architecture/experience-bff-downstream-route-map.md).

## What to do instead

1. **For new features:** build them in `ui/one-ui-shell` against the canonical pages above and the corresponding BFF route family.
2. **For parity gaps:** raise the gap against `ui/one-ui-shell` and reference audit gap **G-6** (sidecar retirement) plus the specific canonical page that needs the missing capability.
3. **For backend wiring:** the BFF controller for each route family above is already indexed in the generated routes file. Do not introduce a new downstream service from this sidecar.
4. **For configuration:** environment variables, base URLs, and trust headers are owned by `one-ui-shell` and the Experience BFF. Do not duplicate them here.

## Related

- [`ui/one-ui-shell`](../one-ui-shell) — canonical web shell.
- [`ui/one-ui-shell/src/app/finance`](../one-ui-shell/src/app/finance) — canonical finance pages.
- [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../../docs/audits/costa-mushex-experience-layer-wiring-audit.md) — audit context, including gap **G-6**.
