# UI Surfacing Hotspot Register

> Generated: 2026-06-09. Regenerate: `node scripts/frontend/generate-ui-surfacing-hotspot-register.mjs`

Surfaces that need meaningful product UI (not route parity alone). Aligned with [GAP_CLOSURE_RULES.md](./GAP_CLOSURE_RULES.md).

## Summary

| Metric | Count |
|--------|-------|
| Pages scanned | 505 |
| Hotspots (P0/P1 or QRP) | 0 |
| P0 thin shells (5+ QRP) | 0 |
| P1 mixed/fixture | 0 |

## Priority legend

| Priority | Meaning |
|----------|---------|
| P0-thin-shell | 5+ QueryResultPanel instances — operator console, not product UI |
| P1-mixed | 1–4 QRP or fixture/mock risk — extend in place |
| P2-live-candidate | Hooks present, no QRP — verify chain + doctrine |
| P3-review | Manual review |

## Hotspot table

| route | qrpCount | priority | file |
|-------|----------|----------|------|


## High-value flows (plan phase 3)

1. Workspace selection — facility context + operations hub
2. Registration — `/auth/register` chain
3. Control tower / queue — `/clinical/control-tower`, worklist
4. Finance ops — `/finance/payer-ops`, `/finance/workspace`

## Related

- [PLANE_CAPABILITY_LEDGER.md](../architecture/PLANE_CAPABILITY_LEDGER.md)
- [REMAINING_FRONTEND_GAPS.md](./REMAINING_FRONTEND_GAPS.md)
- [BACKEND_NOT_SURFACED_REGISTER.md](../audits/BACKEND_NOT_SURFACED_REGISTER.md)
