# 08 — Accessibility Audit

**Acceptance principle:** a disabled citizen must be able to request a Health ID and access care
WITHOUT an insecure workaround.

## State of play (verified)

| Capability | Status | Evidence | Gap ID |
|------------|--------|----------|--------|
| High-contrast theme | ✅ **exposed (authed) + now public** | CORRECTION (probe): `useAccessibilityPreferences` toggle is mounted via `ShellAccessibilityMenu` in `ShellTaskbar:283` for authed users; Slice 6 added `PublicAccessibilityMenu` for guests too | G-CZO-08 (fixed) |
| Larger text / font scaling | ⚠️ partial | data-governance `display-settings` (theme/font/density) persisted (`PrivacyRightsController:142-148`) but no citizen-facing control wired | G-CZO-08 |
| Screen-reader / ARIA | ⚠️ partial | some semantic markup; not audited end-to-end; forms lack consistent labels/roles | G-CZO-08 |
| Keyboard navigation | ⚠️ unverified | Radix primitives give baseline focus management; custom flows (QR, step-up token inputs) unverified | G-CZO-08 |
| Language (Shona / Ndebele) | ❌ design-ready only | no i18n message catalogues for citizen flows | — |
| Low-data mode | ❌ | no text-first/deferred-image mode | G-CZO-10 |
| Resumable forms | ❌ | no draft persistence in `health-id/request` | G-CZO-09 |
| SMS fallback for auth | ❌ as login | SMS-OTP only a step-up adapter, not a primary door | G-CZO-11 |
| Offline continuation | ❌ | no offline auth/request path | G-CZO-09 |

## What this means for the personas

- **Persona E (disability):** can technically complete the request form, but cannot raise contrast,
  scale text, or switch language from the UI. The capability exists in the data layer (display-settings)
  and CSS — it is an **exposure** gap, not a build-from-scratch gap. This is the cheapest high-impact a11y win.
- **Persona D (low connectivity):** loses progress on disconnect (no resumable draft) and has no low-data
  mode → effectively excluded on poor networks. Violates the inclusion principle.
- **Persona F (no smartphone):** facility-assisted onboarding partially supported via `PROVIDER_CAPTURED`
  + `/kiosk`, but no audited assisted-onboarding journey.

## Minimum a11y bar for "done" (this wave)

1. **Expose** an accessibility settings panel (high-contrast toggle + text size + language stub) wired to
   the existing `display-settings` persistence — turns dormant capability into a usable control.
2. **Resumable Health-ID request** (local draft + resume) so a dropped connection doesn't restart the journey.
3. **Document** the SMS-fallback and offline gaps as explicitly deferred with a tracked owner (not silently absent).

Full WCAG conformance is out of scope for this wave; the bar is *no insecure workaround required* for the
core request-and-access journey, plus honest documentation of what remains.
