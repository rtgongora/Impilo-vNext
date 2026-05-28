# Production-like E2E Checklist

## Objective

Validate production-like end-to-end behavior for:

- Full runtime wiring (DB, Kafka, gateway, envoy, auth/session, BFF, learning).
- External adapter behavior under load (Comms bridge and Nompilo/LLM path).
- Real user-journey continuity (web + mobile).

## Automated runner

Use:

`powershell -ExecutionPolicy Bypass -File scripts/runtime/e2e-production-like-smoke.ps1 -StartStack`

Optional flags:

- `-LoadIterations 25` for stronger adapter/load pressure.
- `-RequestTimeoutSec 20` for slower machines.
- `-StopStack` to tear down after run.

Outputs:

- `docs/operations/PRODUCTION_LIKE_E2E_REPORT.md`

## Pass criteria

- `FAIL = 0` in the generated report.
- Runtime section confirms all core services reachable/healthy.
- Gateway section confirms deny-without-headers and allow-with-headers.
- Journey API section confirms create/readiness/notifications round-trip.
- Adapter section confirms:
  - Nompilo assist endpoint succeeds.
  - Comms dispatch bridge returns a dispatch object.
- Load section shows high success ratio and acceptable latency.

## Manual closure criteria (mobile)

- Provider app:
  - Sign-in works.
  - Learning hub opens.
  - Assignment/action journey succeeds.
- Citizen app:
  - Sign-in works.
  - Learning notifications visible.
  - Assigned learning opens and progress updates.

Attach evidence (screenshots/timestamps) to the report before sign-off.
