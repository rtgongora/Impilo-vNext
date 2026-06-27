# Mobile Preview Access (218 → 235)

**From:** `impilo-mobile-android-sandbox` (`41.57.127.218`)  
**To:** `impilo-web-preview` API (`http://41.57.127.235`)  
**Status:** `NOT VERIFIED` from 218  
**Updated:** 2026-06-27

## Expected checks

Run from Maestro VM after repo clone:

```bash
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
curl -sf -o /dev/null -w "%{http_code}" http://41.57.127.235/
curl -sf http://41.57.127.235/health/version || true
# BFF health if routed:
curl -sf http://41.57.127.235/actuator/health || true
```

## Results

| Endpoint | HTTP | Notes |
|----------|------|-------|
| `/` | NOT RUN | |
| `/health/version` | NOT RUN | |
| BFF health | NOT RUN | |

Mobile apps must reach preview BFF through ingress at `41.57.127.235` unless another endpoint is explicitly provided.
