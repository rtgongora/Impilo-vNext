# redroid Lane Proof — Android-in-Container as Standing Infrastructure

**Date:** 2026-07-23 · **Fixture:** docker container `impilo-redroid` on the preview LB host · **Image:** `127.0.0.1:5000/redroid/redroid:13.0.0-k3s` (symlink-fixed variant) · **adb:** `127.0.0.1:15555` · **APK commit:** `7ffe90558`

## Claim

The Impilo mobile runtime now runs as permanent estate infrastructure — same operational class as redis/kafka/livekit — with **no KVM and no emulator**, on the same VM that could not host a stable emulator all day.

## Evidence (all captured this session)

| Proof | Result |
|---|---|
| Android 13 boots in container | `sys.boot_completed=1` in ~2 min (vs 30+ min TCG emulator) |
| Citizen APK install | `Success` in **<1 s** (`redroid-runtime.sh install`) |
| Provider APK install | `Success` in **~3 s** |
| Cold launch, both apps | Full-res (1080×2340) real-pixel screenshots: `artifacts/mobile/7ffe905…/redroid/screenshots/{citizen,provider}-launch.png` |
| Maestro: citizen anonymous health-info journey | **12/12 COMPLETED** — includes live preview-API topic fetch (via `--add-host` hairpin) and the search-tap step TCG could never pass |
| Maestro: provider smoke | **7/7 COMPLETED** |
| Restart survival (`docker restart`) | Container re-booted Android unattended; **both packages persisted** (volume `impilo-redroid-data`) |
| Permanence | `--restart unless-stopped` + docker daemon boot-start (same mechanism as `impilo-local-registry`, running 7 weeks) |

## Operate

```bash
scripts/mobile/redroid-docker-fixture.sh start|stop|status|recreate
scripts/mobile/redroid-runtime.sh connect|install|smoke|evidence|all
# or: pnpm --dir apps/mobile mobile:verify:runtime
```

Host prerequisite (already applied): `modprobe binder_linux` (binderfs kernel — redroid self-mounts it; no `/dev/binder*` nodes needed or present).

## Known limits / next

- **k8s chart variant disabled** (`redroid.enabled: false`): k3s CRI kills Android init (pty-close SIGHUP with tty; exit 255 at exec without). Image + template are ready in-repo for a future CRI-compat pass; findings in `docs/runbooks/redroid-android-sandbox-runbook.md`.
- **Auth journeys** still blocked on preview-edge Keycloak exposure (see `MOBILE_RECOVERY_REPORT.md` §10) — not a fixture limitation.
- WebRTC/LiveKit call QA still deserves a real device; SwiftShader rendering is smoke-grade.
