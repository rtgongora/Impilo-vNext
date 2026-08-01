# MFA preview release evidence — 2026-08-01

## Outcome

The preview MFA trust-plane correction is deployed and verified by targeted release.
No destructive fullboot, namespace deletion, Helm uninstall, PVC deletion, or data
rewrite was used. The implementation branch is `codex/mfa-production`; release code
through `b8d29a6533cbf91b59e18605444173d9890c8f8a` is pushed.

Keycloak 26.7 runs on PostgreSQL with the canonical public issuer
`https://impilo.mohcc.gov.zw/realms/impilo`. The 42 enabled human accounts, their IDs,
roles, attributes, password hashes, and active credentials survived the controlled
H2 to PostgreSQL migration. Existing sessions were deliberately invalidated once.

## Preservation and rollback

- Original `keycloak-data` PVC retained: 2 GiB.
- Migration snapshot retained on `keycloak-migration-backup` PVC: 5 GiB.
- PostgreSQL data and backup PVCs retained: 50 GiB and 30 GiB.
- Restore rehearsal evidence and encrypted pre-upgrade dump retained under
  `/home/robert/impilo-backups/keycloak/rehearsals/20260801T013548Z/`.
- Source identity signature: `c126a4eeb91040fc129dac939aaf1818a7a9ed69f5a3aae82b267c95aeec7bbb`.
- Encrypted dump SHA-256: `705811479e618e00a0c50fa6282c6ffd2c2301ab3f14ef34e74f6a539aa2d91b`.
- The independent restore rehearsal passed at Keycloak 25/PostgreSQL and after the
  Keycloak 26.7 schema upgrade. See
  `docs/security/evidence/keycloak-migration-rehearsal-20260801.md`.

Pre-MFA rollback image digests retained in the operator record:

| Component | Rollback digest |
|---|---|
| experience-bff | `sha256:f0373f964fe5ef6df4ef0f8039409c27da4e13b6a2fa407df22155c9c4a28759` |
| one-ui-shell | `sha256:00864d399a6d313da48a059bc48f60f99fea8c0b3b3d4b70ce2296c3005a50ee` |
| tshepo-authz-service | `sha256:01844fca9310eb8bfb85a1a25f79be78906f6ddd47b45f60aee4665a1f4663e8` |
| tshepo-audit-service | `sha256:3a647f04648e2dfe38ff1eeaf84b09cae9e91654a55d92dd16415f48a11c5345` |
| Keycloak | `quay.io/keycloak/keycloak:25.0` plus the retained H2 snapshot |

## Deployed immutable images

Registry references, pod image IDs, and requested digests matched for the target
cohort. Every listed pod was ready with zero restarts at final capture.

| Component | Source commit | Deployed digest |
|---|---|---|
| Keycloak 26.7 | `304152be6` | `sha256:70f0af3d5a9352c1d62cf6ea059430faaa10ed772bb63bea690c99cd2a4836bc` |
| experience-bff | `486b3a4ff` | `sha256:1948d8d355b5a3456ed0bbdf1feb195143ff4f45b348b8dcb85b1d41b3ea763b` |
| one-ui-shell | `304152be6` | `sha256:d264a0c1ebbf11fe675d893f90473de02bed5fb8dc64100746622eddab0f2b2e` |
| tshepo-authz-service | `07b8674a8` | `sha256:4da33b6f60ae10e647261fc908b2687c3dfb9c08ef1a4110f2c4b8a41a058e82` |
| tshepo-audit-service | `3f42627f7` | `sha256:bba09c3926e5106fa72358a2d731bef2f91a154b9c92f87499d83a7327b38bd5` |

Helm release `impilo-full-preview` remained deployed at revision 9. The MFA changes
were deliberately applied as narrowly filtered, digest-pinned target manifests; the
rest of the estate was not reconciled or restarted. The per-component source commits
above, rather than a misleading single global commit, are the release truth.

## Realm and authentication evidence

- Realm users: 42 enabled humans preserved.
- Desired/live realm hash:
  `9c903e22f394ec812ff412331fedcd18a3bae49a4bb8c17e6b4c70d4bb5209d8`.
- Policy version: `1.0.0`; governed flow state: `governed-v1`.
- A final plan/apply/plan cycle returned the same hash and no drift.
- All 38 workforce accounts have native Keycloak MFA/recovery required actions.
- Public page: HTTP 200.
- Anonymous BFF session inspection: HTTP 401 `NO_ACTIVE_SESSION`.
- BFF authorization endpoint: HTTP 200 after redirect to the Keycloak login page.
- Legacy password login endpoint: HTTP 401, fail closed.
- Token issuer: `https://impilo.mohcc.gov.zw/realms/impilo`.
- Retired Tshepo TOTP endpoint: authenticated HTTP 410 directing callers to native
  Keycloak enrollment/step-up.
- Tshepo Authz and Audit run with the preview OAuth bypass explicitly disabled.

## Audit evidence

Native Keycloak events are ingested by the dedicated event-reader identity. The final
governed release smoke returned HTTP 200 and `intact=true` after verifying 218 events;
the final post-push acceptance repeated the proof across all 352 events then present,
from genesis through the stored chain head.

The release also corrected a historical precision defect: old events were hashed with
nanosecond timestamps before PostgreSQL stored microseconds. Verification now recovers
the bounded PostgreSQL rounding window without rewriting events; new events hash the
persisted microsecond value. Unit tests prove legacy recovery and continued detection
of changed entry hashes, broken links, and chain-head mismatch.

## Web and mobile verification

- Browser uses BFF-mediated authorization code plus PKCE and an opaque HttpOnly
  session; public redirect smoke reaches the live Keycloak 26.7 login page.
- Mobile typecheck: citizen and provider passed.
- Mobile tests: citizen 239/239; provider 422/422; registry 4/4.
- Mobile parity, wiring, and no-production-mock guards: passed across 21 canonical
  services and 567 scanned source files.
- Both release APKs were built with the HTTPS issuer, package-verified, installed on
  Redroid, launched, and exercised through Maestro citizen and provider smokes.

| APK | SHA-256 |
|---|---|
| `Impilo-preview-0.1.0-b8d29a653.apk` | `27b15e5f3f246d56bc4f1dd823ffb86879eaaca64d09110fdef4e95c9f1fd94e` |
| `Impilo-Provider-preview-0.1.0-b8d29a653.apk` | `8935dd81a01cd112f76234143180c15118a7b8616524ee91499769e5713f1f2d` |

## Activation boundary

Preview activation is complete for the targeted MFA cohort. Production activation
still fails closed on the locked external prerequisites: a real MoHCC SMTP relay,
approved hardware AAGUIDs, Apple Team ID, and Android release signing fingerprint.
SMS/email OTP and trusted-device bypass remain disabled. Wider protected-service OAuth
enforcement remains a controlled service-cohort operation under the existing
work-context and Envoy cutover interlock; it must not be converted into a destructive
fullboot.
