# Web / Mobile Surfacing Parity

> Updated: 2026-05-28. Supersedes summary-only `WEB_MOBILE_PARITY_MATRIX.md` for sweep detail.

| Capability | Web | Citizen mobile | Provider mobile | Offline | Notes |
|------------|-----|----------------|-----------------|---------|-------|
| Queue / triage | Live | Live | Live | Partial | Provider queue BFF |
| EHR summary / timeline | Live | Partial | Live | Partial | Citizen allergies/conditions partial |
| Core transaction | Partial | Partial | Partial | Partial | Web feed live; mobile shell shallow |
| Telehealth / telemedicine | Partial | Partial | Partial | No | RTC **Blocked** on web |
| Social timeline | Live | Live | Live | No | BFF social paths |
| Marketplace / Health OS launcher | Partial | Partial | Partial | No | Launcher BFF |
| Public health ops | Partial | Partial | Partial | Partial | Field tasks on provider outreach |
| Nhume / dispatch | Partial | Partial | Partial | Partial | Dual BFF: nhume + dispatch |
| Nompilo / guidance | Partial | Partial | Partial | Fallback | Mobile fallback when LLM down |
| Finance / wallet | Partial | Partial | Partial | No | MusheX via finance BFF |
| Learning / Fundo | Partial | Partial | Partial | No | Large learning API |
| Registry admin | Partial | Limited | Limited | No | Web registry hub deeper |
| UBOMI CRVS | Not wired | Not wired | Not wired | No | Honest placeholder |
| ZIBO terminology | Partial (zibo-web) | n/a | n/a | No | Satellite app |
| Integration hub | Partial | Partial | Partial | No | Status screens |
| Workflow / dispatch ops | Partial | Partial | Partial | Partial | Ops routes + Flow/Ops tab |

## Gap classes

- `WEB_REAL_MOBILE_REAL` — Both surfaces call BFF with trust headers
- `WEB_REAL_MOBILE_PARTIAL` — Mobile thinner depth
- `WEB_PARTIAL_MOBILE_MISSING` — Web only
- `BACKEND_ONLY` — BFF exists; UI sparse (reduced this sweep for workflow/dispatch)

## High-priority follow-ups

1. Citizen `ConditionsSection` / `AllergiesSection` → BUTANO BFF
2. Core transaction mobile journey shell depth
3. Unified dispatch vs Nhume operator UX
4. Provider hub native screens vs web deep link (interim: `EXPO_PUBLIC_WEB_SHELL_URL`)
