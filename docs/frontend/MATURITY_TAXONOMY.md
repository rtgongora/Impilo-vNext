# Frontend Maturity Taxonomy

## Canonical labels (parity sweep)

| Label | Meaning | User-facing rule |
|-------|---------|------------------|
| **Live** | Typed client → BFF → sovereign service; real reads/writes for scoped role | No fixture injection on this surface |
| **Partial** | Some live endpoints; missing depth, writes, or mobile parity | Show what works; badge + explanation for gaps |
| **Fixture** | Demo/sample/local data only | Must not present as live metrics or counts |
| **Not wired** | UI route or tile exists; no API hook | Disable or route to honest empty state |
| **Blocked** | Policy/contract/RTC intentionally unavailable | Show reason; no fake success |

## Legacy badge mapping

Web (`FeatureMaturityBadge`) and mobile design system use extended enums. Map via `ui/one-ui-shell/src/lib/maturity.ts`:

| Legacy | Canonical |
|--------|-----------|
| `live`, `connected` | Live |
| `partial`, `prototype` | Partial |
| `fixture` | Fixture |
| `not_wired`, `requires_backend` | Not wired |
| `blocked` | Blocked |

## Launcher tile requirements

Every Health OS launcher entry (static `SHELL_APPS` or marketplace `LauncherApp`) must expose:

- Service name and short description
- Plane and primary journey (Person / Provider / Platform / cross-cutting)
- Required role and context (facility, workspace, tenant)
- Maturity label
- Disabled reason when not actionable
