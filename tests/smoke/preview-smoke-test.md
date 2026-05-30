# Preview smoke tests

Executable: `scripts/deploy/preview-smoke-test.sh`

## Manual checks

| Test | Command / URL | Expected |
|------|---------------|----------|
| Frontend loads | `curl -I http://41.57.127.235/` | HTTP 200/307 |
| BFF health | `curl http://41.57.127.235/actuator/health` | UP or partial |
| Version endpoint | `curl http://41.57.127.235/health/version` | JSON with branch/commit |
| No crash loops | `kubectl get pods -n impilo-preview` | No CrashLoopBackOff |

## Run

```bash
bash scripts/deploy/preview-smoke-test.sh
```
