# Backend–frontend parity gate report

**Date:** 2026-05-31  
**Commit baseline:** `61ab9c50` + parity enforcement work

## Capabilities discovered

**22** user-facing domains in canonical registry (`scripts/frontend/generate-parity-docs.mjs`), including Vito, Varapi, Tuso, Butano, Nhume, MusheX, Fundo, Nompilo, Ndila, telemedicine, social, UBOMI, ZIBO, etc.

## Frontend surfaces

- **Web:** `ui/one-ui-shell` — 370+ routes in `routes.ts`
- **Clients:** domain hooks under `src/hooks`, `src/lib`

## Summary

| Status | Count (approx) |
|--------|----------------|
| Live | 2 |
| Partial | 18 |
| Not Wired | 1 (UBOMI) |
| Fixture/Blocked | documented in matrix |

## Gate status

| Item | Status |
|------|--------|
| `check-backend-frontend-parity.sh` | Implemented |
| `check-frontend-mocks-and-stubs.sh` | Strengthened (new-file blocking) |
| `check-api-client-surfacing.sh` | Strengthened |
| `config/parity-allowlist.yml` | Created |
| CI job `Backend-to-Frontend Parity Gate` | Added |
| VM pipeline phase | `parity-web` blocking |
| Change-safety | Included |

## Immediate priorities

1. UBOMI — honest not-wired until BFF bridge (allowlisted).
2. High-priority partials: Vito, Varapi, Tuso, Nhume, MusheX, Nompilo, core transaction.
