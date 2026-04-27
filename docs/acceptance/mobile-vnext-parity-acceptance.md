# Mobile vNext parity — acceptance checklist

**Related audits**: `docs/audits/mobile-parity-audit.md`, `docs/audits/mobile-parity-traceability-matrix.md`  
**Doctrine**: `docs/product/mobile-navigation-and-parity-doctrine.md`  
**Security**: `docs/security/mobile-security-privacy-notification-rules.md`

## Must-pass (release gate when mobile ships a capability)

1. Capability appears in the **traceability matrix** with classification (P/C/B/O/W/N/F) and reasoning.
2. **No production fake** clinical, consent, financial, queue, or support data in app `src` (see `docs/audits/mobile-no-fake-data-audit.md`).
3. **Trust headers** and auth/session behaviour match `@impilo/mobile-trust` + BFF expectations.
4. **Push** copy uses generic templates (`buildGenericHealthNotificationBody`) or equivalent server templates — no sensitive lock-screen text.
5. **Offline**: banner does not claim “synced” when queue shows `FAILED` or pending operations remain.
6. **Telemedicine**: provider can reach session list from **Clinical Tools → Telehealth**; citizen retains **Telehealth** tab / home quick action.
7. **Support / SOS**: citizen **Help** and **Consent** live under **My Health**; provider **Launch** exposes Comms, Telehealth, Support, and SOS guidance.

## Automated tests (incremental)

- `@impilo/mobile-trust`: `pushNotificationPrivacy` unit tests (run `pnpm --filter @impilo/mobile-trust test`).
- Provider / citizen: extend navigation and dashboard tests when Vitest resolves `@expo/vector-icons` in the test harness (known local resolution issue in some workspaces).

## Build notes

- Full `tsc` in mobile workspaces may require `expo-sqlite` types resolution in `mobile-offline` when dependencies are hoisted differently; track under CI configuration, not as a product fake-data issue.
