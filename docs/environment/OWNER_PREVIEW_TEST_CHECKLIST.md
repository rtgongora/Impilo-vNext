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

## Error Capture

- Browser devtools → Network tab for failed API calls
- Screenshot + URL + timestamp
- `kubectl logs -n impilo-preview -l app=experience-bff --tail=50`

## Report Missing UI for Backend Features

If API works in logs/Postman but UI missing: file issue with route, BFF path, and commit SHA.
