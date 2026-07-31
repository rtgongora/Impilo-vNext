# Emergency pack — handover

## For the coordinator / next lane

1. Read [`completion-register.md`](completion-register.md) and the
   [`honest-gap register`](../../audits/emergency-pack-honest-gap-register.md) before declaring
   the pack complete.
2. **Do not** quietly build a mental-health image to clear the undeployed gap without an
   explicit deploy authorization and digest discipline.
3. W14 content requires hashed source documents under `docs/reference/who-emergency-care-toolkit/`
   before any syndrome tranche is authored.
4. W19 realtime phase 2 is **DONE** — see [`w19-realtime.md`](w19-realtime.md). Remaining pack gaps are W14 content, MH undeployed, Envoy/compose MH wiring (named in the honest gap register).

## Local verification

```bash
bash scripts/dev/emergency-drive-rig.sh up
bash scripts/runtime-proof/emergency-episode-journeys.sh
cd ui/one-ui-shell
PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://localhost:3007 \
  npx playwright test e2e/emergency-pack-w18.spec.ts --project=chromium --workers=1
bash scripts/dev/emergency-drive-rig.sh down
```

## Dual-VM reminder

- Engineering / gates / preview: `impilo.mohcc.gov.zw` (235)
- Android Maestro only: `41.57.127.218` (218)
- No backend deploy from 218; no emulator load on 235
