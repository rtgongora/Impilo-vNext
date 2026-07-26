# Preview the two mobile apps (interactive, from Windows)

Live, touch-controllable mirror of **Impilo** (citizen) and **Impilo Provider** running
against the preview backend. Nothing needs installing on the VM — the `impilo-redroid`
Android fixture is already running with both APKs installed
(see [redroid-android-sandbox-runbook.md](../runbooks/redroid-android-sandbox-runbook.md)).

You need: a Windows machine with PowerShell and SSH access to the preview VM.

---

## Step 1 — Open the tunnel (PowerShell window #1)

```
ssh -L 15555:127.0.0.1:15555 robert@41.57.127.235 -p 2276
```

Logs you into the VM normally. **Leave this window open** — closing it kills the tunnel.

## Step 2 — Get scrcpy (PowerShell window #2)

Download from <https://github.com/Genymobile/scrcpy/releases/latest> — pick
**`scrcpy-win64-v3.x.x.zip`**. Extract to `C:\scrcpy`, then:

```
cd C:\scrcpy
```

## Step 3 — Clear stale adb, connect

```
.\adb.exe kill-server
```

```
.\adb.exe connect 127.0.0.1:15555
```

Expect `connected to 127.0.0.1:15555`.

## Step 4 — Verify

```
.\adb.exe devices
```

Must show `127.0.0.1:15555    device`. If `offline`, wait 10s and repeat Step 3.
If empty, the tunnel is down — check window #1.

## Step 5 — Mirror

```
.\scrcpy.exe --max-size 900 --max-fps 15 --no-audio --video-encoder=OMX.google.h264.encoder
```

Flags matter over SSH: size/fps keep it smooth, `--no-audio` skips an audio channel
redroid doesn't provide (otherwise ~5s stall at startup).

## Step 6 — Open the apps

Both are installed and appear in the launcher:

| App | Package |
|---|---|
| Impilo (citizen) | `zw.gov.impilo.citizen.dev` |
| Impilo Provider | `zw.gov.impilo.provider.dev` |

Press ○ (home) → swipe up for the app drawer → tap either. Use ▢ (recents) to flip
between them. One device, both apps — same as a real phone.

To launch from the VM side instead:

```
adb -s 127.0.0.1:15555 shell am start -n zw.gov.impilo.citizen.dev/.MainActivity
```

---

## What works / what doesn't

**Works end-to-end:** the anonymous lane — *Health info* (live guidance from the preview
backend), *Verify*, *Track SOS* — plus full navigation and both app shells.

**Does not complete:** *Sign in with Impilo*. The preview edge exposes no Keycloak route
for mobile PKCE — infrastructure gap, not an app defect. See
[MOBILE_RECOVERY_REPORT.md](MOBILE_RECOVERY_REPORT.md) §10.

## Troubleshooting

| Error | Fix |
|---|---|
| `Could not find encoder` | Drop the flag: `.\scrcpy.exe --max-size 900 --max-fps 15 --no-audio` |
| `Server connection failed` | Re-run Step 3, then Step 5 |
| `adb server version doesn't match` | `.\adb.exe kill-server`, then Step 3 |
| Black window | Wait ~10s; else close and rerun Step 5 |
| Device gone entirely | On the VM: `scripts/mobile/redroid-docker-fixture.sh status` (and `start` if down) |

## Optional — both apps side by side (two devices)

On the VM, start a second instance:

```
REDROID_NAME=impilo-redroid-2 REDROID_PORT=15556 REDROID_VOLUME=impilo-redroid-data-2 \
  bash scripts/mobile/redroid-docker-fixture.sh start
```

Install the APKs onto it:

```
REDROID_PORT=15556 bash scripts/mobile/redroid-runtime.sh install
```

Tunnel both ports (replaces Step 1):

```
ssh -L 15555:127.0.0.1:15555 -L 15556:127.0.0.1:15556 robert@41.57.127.235 -p 2276
```

```
.\adb.exe connect 127.0.0.1:15556
```

Then one scrcpy window per device:

```
.\scrcpy.exe -s 127.0.0.1:15555 --max-size 900 --max-fps 15 --no-audio --window-title "Impilo Citizen"
```

```
.\scrcpy.exe -s 127.0.0.1:15556 --max-size 900 --max-fps 15 --no-audio --window-title "Impilo Provider"
```
