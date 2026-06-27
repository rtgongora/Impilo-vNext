# 04 — Dashboard Variation Matrix (12 states)

What the citizen sees by identity + trust + risk. The shell that drives this is
`app/home/page.tsx` (Work / My Professional / My Life switch on `identity.hasWorkAccess` /
`isCitizenOnly` / `hasProfessionalAccess`, `:506-525,811-827`) + `CitizenHome` (`:1507-1700`) +
`IdentityAssuranceBanner`.

| # | State | Intended view | BUILT? | Evidence / Gap |
|---|-------|---------------|--------|----------------|
| 1 | **Guest / public** | Public landing, service finder, emergency, sign-in/create | ❌ | No public surface (G-CZO-02) |
| 2 | **Account-only (L1)** | "Finish setting up your Health ID" CTA, help, limited nav; no records | ⚠️ | `CitizenHome` + `SELF_REGISTERED` banner render, but nothing blocks record cards by LOA at the gate (G-CZO-01) |
| 3 | **Temporary Health ID (L2)** | Temp ID/QR, book selected services, notifications; no sensitive records | ⚠️ | QR built (`health-id/qr`); record suppression depends on policy = broken (G-CZO-01) |
| 4 | **Verification-pending** | "We're reviewing your details" + what to do next | ⚠️ | Banner `nextBestStep`/`reason` fields exist (`useAssuranceStatus`); no dedicated pending dashboard variant |
| 5 | **Verification-failed / assisted-review** | "Visit a facility / call an officer" assisted path | ⚠️ | Upgrade pathways in banner (`AssurancePolicy:51-64`); no failed-state dashboard copy |
| 6 | **Verified (L3)** | Health summary, appointments, results/Rx/referrals/care-plans per policy | ✅ | `CitizenHome` queries appointments/feed/wallet; mobile sections wired |
| 7 | **High-assurance unlocked (L4)** | Download/share, manage delegates, sensitive results, revoke devices | ❌ | No step-up UI to enter L4 (G-CZO-04); delegate mgmt not built (G-CZO-03) |
| 8 | **Delegated (L5)** | "Acting for X" banner; only authorised scope | ❌ | Not built (G-CZO-03) |
| 9 | **Provider-who-is-also-citizen** | Work / My Professional / My Life separation; no cross-leak | ✅ | `app/home/page.tsx:506-525,811-827` — the one strong state. Preserve. |
| 10 | **Suspicious-login / restricted** | Low-risk actions only or step-up; clear explanation | ⚠️ | Policy computes risk + step-up; no UI surfaces "restricted because…" |
| 11 | **Low-data** | Text-first, minimal payload, deferred images | ❌ | No low-data mode (G-CZO-10) |
| 12 | **Accessibility-enhanced** | High-contrast, larger text, screen-reader optimised, language | ⚠️ | Tokens exist; not user-exposed (G-CZO-08); language design-ready only |

## Summary

- **3 of 12 states are solid** (6 Verified, 9 Provider-also-citizen, and the verified record surfaces).
- **2 are blocking-dependent** (2 Account-only, 3 Temporary) — they *look* right but don't *enforce* the
  L1/L2/L3 boundary because of G-CZO-01.
- **7 are missing or cosmetic-only** (Guest, pending, failed, high-assurance, delegated, low-data, a11y).

The dashboard "changes by identity + trust" claim is **only true for role** (citizen vs provider), **not yet
true for assurance level**. Closing G-CZO-01 is what makes states 2/3/6/7 genuinely distinct.
