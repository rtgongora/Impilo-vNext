# Reporting and Intelligence Reality Check

## Status

| Capability | Web | Mobile | Class |
|---|---|---|---|
| Report generation | Real routes/hooks | Present in tools screen | `WEB_REAL_MOBILE_REAL` (partial parity) |
| Operational dashboards | Real web pages | Limited equivalent | `WEB_REAL_MOBILE_PARTIAL` |
| Intelligence queries | Web present | Limited direct parity | `WEB_REAL_MOBILE_PARTIAL` |

## Findings

- Reporting is substantially wired on web with partial mobile parity.
- Intelligence breadth differs between platforms.
- Continue auditing hard-coded KPI risk in route-specific cards and chart helpers.

## Next Steps

1. Add explicit fixture/prototype labels wherever dashboard cards are static.
2. Add mobile parity routes or document intentional limitations.
3. Add tests for export button behavior and API error surfaces.
