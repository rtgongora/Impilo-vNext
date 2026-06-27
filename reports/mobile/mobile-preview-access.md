# Mobile Preview Access

**From:** Maestro VM `41.57.127.218` (when operational)  
**To:** Web Preview API `http://41.57.127.235`  
**Updated:** 2026-06-27

## Reachability (235 self-check)

| Endpoint | Result |
|----------|--------|
| `curl -I http://41.57.127.235/` | Empty/timeout in agent session — manual verify required |
| From 218 | **NOT TESTED** — 218 not accessible from 235 agent |

## Mobile env for preview testing

```bash
export EXPO_PUBLIC_APP_VARIANT=preview
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
```

## Tester access today

| Method | Available |
|--------|-----------|
| Static gates + export on 235 | Yes |
| Emulator on 218 | No — bootstrap pending |
| Debug APK | No |
| QR / Expo Go | No — needs 218 dev server |

Full guide: `docs/mobile/MOBILE_PREVIEW_ACCESS_GUIDE.md`
