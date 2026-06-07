# Owner Preview Test Checklist

Expert-user validation for the **Dev Preview Sandbox** (not formal staging).

**Preview URL:** http://41.57.127.235/

## Before Testing

1. Confirm preview is deployed: `bash scripts/deploy/preview-status.sh`
2. Confirm version: `curl -s http://41.57.127.235/health/version`
3. Note environment should show **preview** / **sandbox**

## Checks

| # | Check | Pass? | Notes |
|---|-------|-------|-------|
| 1 | Preview URL loads in browser | | |
| 2 | Environment shows Preview/Sandbox | | |
| 3 | Branch + commit SHA visible (version endpoint or UI) | | |
| 4 | Login/auth page or auth fallback behavior | | Keycloak may be off in MVP |
| 4a | Citizen sign-up `/auth/register` completes or shows explicit error (not silent failure) | | Expect CITIZEN role; `ROLE_ASSIGNMENT_FAILED` must not leave orphan account |
| 4b | SUPER_ADMIN (or SYSTEM_ADMIN+DEVELOPER) sees Work/Admin/Finance nav zones | | Platform override roles expand sidebar visibility |
| 5 | Main navigation / shell loads | | |
| 6 | Registry workflows (if backend up) | | May 503 without registry services |
| 7 | Clinical workflows | | Partial without full stack |
| 8 | Enterprise plane areas | | |
| 9 | Data/intelligence areas | | |
| 10 | Nompilo | | |
| 11 | Fundo | | |
| 12 | Ndila | | |
| 13 | Nhume | | |
| 14 | MusheX | | |
| 15 | MADI — donor hub `/madi/donor` (register, drives, feedback) | | My Life zone |
| 16 | MADI — donation drives `/madi/drives` | | Facility context for ops |
| 17 | MADI — blood bank `/madi/blood-bank` (orders, stock, crossmatch, issue) | | |
| 18 | MADI — clinical order `/madi/orders` from EHR orders page | | |
| 19 | MADI — transfusion `/madi/transfusion` | | |
| 20 | MADI — haemovigilance `/madi/haemovigilance` | | |
| 21 | MADI — central bank `/madi/central-bank` | | National/regional view |
| 22 | Impilo Live hub `/live` loads (discover, replays, saved) | | My Life / Work nav |
| 23 | Impilo Live — professional CPD webinar journey | | `/live/discover` → register → room → attendance → CPD cert |
| 24 | Impilo Live — citizen health talk + Madi donor pathway | | `/live/discover` (CITIZEN) → Madi drive link → replay |
| 25 | Impilo Live — organiser manage + analytics | | `/live/manage` → moderation → `/live/event/{id}/analytics` |
| 26 | Impilo Live — resources tab in live room | | Host adds resource; attendees see list (no 404 on `/resources`) |
| 27 | Impilo Live — replay after event ends | | Status `PUBLISHED_REPLAY`; replay page tracks watch minutes |

## Impilo Live smoke notes

- Seed events: CPD webinar (PROFESSIONAL), citizen health talk, Madi-linked donor drive (see `live-service` `V002__live_seed.sql`).
- Media health: `GET /internal/v1/live/room/{eventId}/media-health` — `productionReady: false` in dev (`LOCAL_DEV` provider).
- Fundo CPD bridge: attendance certificate flow posts to `learning-service` `/internal/v1/learning/v11/sessions/live-completion`.
- Preview deploy required before browser verification; confirm commit via `/health/version`.

## Error Capture

- Browser devtools → Network tab for failed API calls
- Screenshot + URL + timestamp
- `kubectl logs -n impilo-preview -l app=experience-bff --tail=50`

## Report Missing UI for Backend Features

If API works in logs/Postman but UI missing: file issue with route, BFF path, and commit SHA.
