# Production-like E2E Smoke Report

Generated: 2026-05-27 19:58:45 +02:00

| Area | Check | Status | Detail |
|---|---|---|---|
| Runtime | Docker daemon | PASS | Docker API reachable. |
| Runtime | Compose up | FAIL |  Image runtime-learning-service Building  |
| Runtime | Stack health sweep | FAIL | Skipped: Docker daemon unavailable. |
| Auth/Session | Keycloak token grant | FAIL | Skipped: runtime stack unavailable. |
| Gateway | Header enforcement deny | FAIL | Skipped: runtime stack unavailable. |
| Gateway | Header enforcement allow | FAIL | Skipped: runtime stack unavailable. |
| Journey API | Learning studio API journey | FAIL | Skipped: runtime stack unavailable. |
| Adapters | Nompilo and Comms adapters | FAIL | Skipped: runtime stack unavailable. |
| Load | Adapter load checks | FAIL | Skipped: runtime stack unavailable. |
| Web Journey | One UI route probes | FAIL | Skipped: runtime stack unavailable. |
| Mobile Journey | Provider app critical path (manual) | TODO | Run after runtime stack is healthy. |
| Mobile Journey | Citizen app critical path (manual) | TODO | Run after runtime stack is healthy. |

- PASS: 1
- FAIL: 9
- WARN: 0
- TODO: 2

## Manual Mobile Closure

1. Start provider dev client and authenticate (provider-app).
2. Run learning assignment journey (create assignment, open, progress).
3. Start citizen dev client and authenticate (citizen-app).
4. Verify notification receipt and learning completion evidence.
5. Capture screenshots + timestamps and append here for final sign-off.
