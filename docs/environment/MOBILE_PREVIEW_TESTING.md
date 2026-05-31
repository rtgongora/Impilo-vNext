# Mobile Preview Testing

## Goal

Exercise citizen and provider apps against the **Dev Preview Sandbox** BFF (`http://41.57.127.235`), not production.

## Steps

1. Confirm preview deploy matches intended commit (`/health/version`).
2. Point mobile env / EAS preview profile at preview API base URL.
3. Run `bash scripts/test/run-mobile-checks.sh` on VM.
4. Manual: install dev client or preview APK; smoke login and one BFF-backed screen per app.

## iOS

Document TestFlight/macOS requirements; do not claim iOS preview works until built on macOS with valid signing.

## Related

- [MOBILE_APP_AUDIT.md](./MOBILE_APP_AUDIT.md)
- [MOBILE_TEST_GATE.md](./MOBILE_TEST_GATE.md)
- [OWNER_PREVIEW_TEST_CHECKLIST.md](./OWNER_PREVIEW_TEST_CHECKLIST.md)
