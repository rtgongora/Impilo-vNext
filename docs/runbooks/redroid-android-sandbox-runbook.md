# redroid Android Sandbox — Operator Runbook

**What:** Android 13 running as a container (`redroid`) — the standing mobile-runtime fixture (adb-connectable device for APK installs, Maestro smoke, screenshot evidence). Replaces the emulator lane, which cannot run on this estate (no KVM on the Xen guests; see `docs/mobile/MOBILE_RECOVERY_REPORT.md` §15).

**Where (primary):** **docker-managed on the preview LB host** — `scripts/mobile/redroid-docker-fixture.sh`, container `impilo-redroid`, adb on `127.0.0.1:15555`, data in docker volume `impilo-redroid-data`, `--restart unless-stopped` for permanence (same pattern as the local registry at 127.0.0.1:5000). Registered in `docs/runbooks/port-allocation.md`.

**Where (future/disabled):** k8s chart `deploy/helm/impilo-vnext/templates/redroid.yaml` (`redroid.enabled: false`). The identical image boots perfectly under docker but k3s/containerd kills Android init: with `tty: true` the CRI shim closes the unattached pty → SIGHUP (exit 129 ~1s); without a tty, init dies at exec (exit 255); the original entrypoint with pipe stdio survives ~3 min then SIGHUPs. Root cause is in the k3s CRI runtime layer (pty/exec semantics for this exotic init), not the image — a future hardening pass can pick this up with the symlink-fixed image already in the registry.

## One-time host prerequisite (root)

redroid needs the host binder driver. The 6.17 kernel ships it as a module; load and persist:

```bash
sudo modprobe binder_linux devices="binder,hwbinder,vndbinder"
echo binder_linux | sudo tee /etc/modules-load.d/binder.conf
echo 'options binder_linux devices=binder,hwbinder,vndbinder' | sudo tee /etc/modprobe.d/binder.conf
ls /dev/binder*   # expect: /dev/binder /dev/hwbinder /dev/vndbinder (or /dev/binderfs)
```

Without this the pod crash-loops with binder open errors.

## Image

docker.io direct pulls are slow/unreliable from the preview VM, and — critically — **the upstream image cannot run under k3s/containerd as-is**: its Android rootfs uses absolute symlinks at `/` (`etc → /system/etc`, `bin → /system/bin`, …) which containerd's safe path resolution rejects (`CreateContainerError: openat etc/passwd: path escapes from parent`). Docker tolerates this; containerd does not.

Produce and publish the k3s-safe variant (rewrites all absolute symlinks to relative — identical runtime semantics):

```bash
docker pull redroid/redroid:13.0.0-latest
scripts/mobile/fix-redroid-image-for-containerd.sh   # → pushes 127.0.0.1:5000/redroid/redroid:13.0.0-k3s
```

Upstream digest (2026-07-23): `sha256:41e5f0c1ff27a4a474c474e5595168cedf6c40fc5dd102c5617f48c80f511e9e`; the chart consumes the `:13.0.0-k3s` tag.

## Deploy / operate (docker fixture — primary)

```bash
scripts/mobile/redroid-docker-fixture.sh start     # boots Android in 1-3 min
scripts/mobile/redroid-docker-fixture.sh status
scripts/mobile/redroid-docker-fixture.sh recreate  # fresh /data if state is wedged
```

`/data` lives in docker volume `impilo-redroid-data` — installed apps and Android state survive container restarts and host reboots (docker daemon + `--restart unless-stopped`). The preview hairpin is handled with `--add-host impilo.mohcc.gov.zw:10.50.1.67`.

## Use as a device

```bash
source scripts/mobile/android-env.sh
scripts/mobile/redroid-runtime.sh connect    # adb connect <clusterIP>:5555 + boot wait
scripts/mobile/redroid-runtime.sh install    # push+install both preview APKs from artifacts/mobile/<latest>/
scripts/mobile/redroid-runtime.sh smoke      # runtime-verified Maestro flows (flows-runtime/)
scripts/mobile/redroid-runtime.sh evidence   # launch + real-pixel screencaps
# or everything: pnpm --dir apps/mobile mobile:verify:runtime
```

Screencap returns **real pixels** (SwiftShader renders in-container — no qemu readback layer).

## Security stance

This is the chart's **first privileged workload** (`securityContext.privileged: true`) — required for binder + Android's device mounts. Accepted for the **single-node preview cluster only**; it must be revisited (userns / device-plugin / dedicated node) in the preview→prod auth-hardening wave before any promotion. adb is internal-only (ClusterIP, no Traefik route); anyone with cluster access has device access — same trust boundary as the preview databases.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Pod crash-loops, logs mention `binder` | Host module not loaded → run the modprobe step above |
| `adb connect` refused | Pod not Ready yet (tcp probe waits for adbd); check `kubectl logs` |
| Boots but `sys.boot_completed` never 1 | Check resources (needs the full 2-CPU request); `kubectl exec` + `logcat` |
| APK install fails INSTALL_FAILED_NO_MATCHING_ABIS | APK lacks x86_64 ABI — build with `-PreactNativeArchitectures=arm64-v8a,x86_64` (default in `scripts/mobile/build-apks.sh`) |
| App can't reach preview API | `hostAliases` maps `impilo.mohcc.gov.zw → 10.50.1.67` in the pod spec; verify it survived the last helm upgrade |
| Image pull backoff | Local registry missing the tag → re-run the mirror step |

## Fallbacks

- VM 218 (`facility@41.57.127.218:2027`) remains the designated KVM emulator sandbox if it is ever restored (`scripts/mobile/maestro-vm-bootstrap.sh`).
- Real devices over `adb connect` work with the same `redroid-runtime.sh` subcommands (set `REDROID_NAMESPACE`/address manually).
