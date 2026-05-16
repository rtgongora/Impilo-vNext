# Component Integrity Audit

## High-Value Component Groups

| Group | Paths | Integrity status |
|---|---|---|
| Shell/Layout/Nav | `ui/one-ui-shell/src/components/navigation`, `.../shell` | Mostly wired; some discoverability gaps |
| Core Transaction | `ui/one-ui-shell/src/features/core-transaction` | Fixture-backed |
| Nompilo/Intelligent | `ui/one-ui-shell/src/components/intelligent`, `.../app/ask` | Partial, mixed real vs presentation-only |
| Provider Clinical | `ui/one-ui-shell/src/components/ehr`, `.../clinical` | Mostly real with selective placeholders |
| Payment/Finance | `ui/one-ui-shell/src/app/finance/*`, finance components | Mixed maturity; many real hooks |
| Mobile Citizen Personal | `apps/mobile/citizen-app/src/screens/personal/*` | Mixed with TODO placeholders |
| Mobile Provider Workspace | `apps/mobile/provider-app/src/screens/provider/*` | Mostly real/partial |

## Orphan/Disconnected Candidates

| Candidate | Evidence | Action |
|---|---|---|
| `apps/mobile/provider-app/src/screens/provider/BillingScreen.tsx` | No import references found during audit | Either wire into tools/workflow or remove from active surface map |
| Doctrine fixture components as production-like shell | Used by route pages but not BFF-backed | Keep only with explicit fixture maturity badge until wired |

## Component-Level Remediations Applied

- Added reusable web `FeatureMaturityBadge`.
- Added reusable mobile `FeatureMaturityBadge`.
- Applied badge usage to fixture/TODO surfaces for honesty.
