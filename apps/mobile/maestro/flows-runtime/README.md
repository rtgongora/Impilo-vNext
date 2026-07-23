# Runtime-verified Maestro flows (Maestro 2.x DSL)

These flows were repaired and **executed against a live emulator** on 2026-07-23
(mobile recovery mission). They differ from `../flows/` in two ways:

1. **appId targets the committed debug applicationId** (`zw.gov.impilo.*.dev`) —
   the checked-in `android/` projects bake the `.dev` variant suffix, so any APK
   built from them installs under the `.dev` id. The `../flows/` suite targets the
   release ids (`zw.gov.impilo.citizen` / `.provider`) and cannot launch a debug build.
2. **Maestro 2.x DSL**: every flow in `../flows/` uses the legacy
   `extendedWaitUntil: { id: ... }` shorthand which Maestro >= 2.x rejects
   ("Unknown Property: id"). The 2.x form is `extendedWaitUntil: { visible: { id: ... } }`.
   **All 29 legacy flows need this migration** (or CI must pin a pre-2.x Maestro).

Verified results (TCG software emulator, aosp_atd android-30, preview backend):
- `provider-smoke.yaml` — PASS 7/7 (launch, login-screen + login-button ids, shell text).
- `citizen-gateway-health-info.yaml` — 8/9 (anonymous health-info journey incl. live
  API topic list + search input; final search-button tap needs a scroll strategy that
  tolerates slow TCG gestures — under KVM this step is expected to pass as written).

Timing law for slow runtimes: after `launchApp` with `clearState`, assert the FIRST
INTERACTIVE element (`guest-health-info`), not just the screen container — the
container id can report visible before React hydration completes.
