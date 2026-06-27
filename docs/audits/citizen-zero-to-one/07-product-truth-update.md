# 07 — Product Truth Update (honest delta)

Nothing here is claimed complete without real impl + visible/documented access. This is the honest
state of the citizen zero-to-one surface as of 2026-06-25, before Phase-1 fixes.

## What is REAL and ACCESSIBLE (claimable)

- **No-Health-ID signup** — web (`auth/register`) + mobile (`SignUpScreen`), readiness-gated, Health ID optional.
- **Health ID request + QR** — `citizen/health-id/{request,qr}`, real Vito proxy.
- **Vito identity lifecycle + dedup** — `IdentityStatus`, `MatchingEngine` (demographic-only, identity-safe),
  operator queue `/v1/match/pending`.
- **identity-assurance LOA + reviewed upgrade** — persisted, dual-control, monotonic (`AssuranceService`, V002).
- **Tshepo policy** — ABAC + clinical-consent gating (fail-closed) + risk + step-up decisioning (`PolicyEngine`).
- **Step-up backend** — supervisor-approval runtime-proven; TOTP/SMS-OTP real (fail-closed); biometric seam.
- **Mobile clinical records** — immunizations/care-plans/referrals **fully wired** to `CitizenClinicalRecordsController`.
- **Provider/citizen context separation** — `app/home` Work/My-Professional/My-Life switch.
- **data-governance** — DSR + privacy/display preferences persisted.

## What must NOT be claimed complete (honest negatives, with evidence)

| Claim that would be FALSE | Reality | Evidence |
|---------------------------|---------|----------|
| "Trust level changes what the citizen can access" | Only **role** changes access; **assurance level does not reach policy** | G-CZO-01 |
| "An ordinary person can discover Impilo and get started" | No public entry exists; everything is auth-gated | G-CZO-02 |
| "Sensitive actions require step-up" (as a usable flow) | Backend enforces; **no citizen UI** to complete the challenge | G-CZO-04 |
| "Caregivers/guardians can act on behalf" | **Not built** — stub only | G-CZO-03 |
| "Consent is captured and viewable" | Clinical consent **gates**; policy-consent capture is **not persisted**; status/history are `[]` | G-CZO-06/07 |
| "Accessible to disabled/low-data citizens" | Contrast/text exist in code but **not exposed**; no low-data/resumable/offline | G-CZO-08/09/10 |
| "Mobile shows trust state" | **No assurance banner**; session never reads assurance level | G-CZO-05 |

## Gate vs. reality

The deterministic product-truth gate (`scripts/guard/check-product-truth.sh`) cannot detect any of the
citizen gaps above — they are **semantic** (code compiles, persists, returns success while the trust
boundary is unenforced or the surface is simply absent). Per the standing finding
([[product-truth-scanner-is-heuristic]]), "0 gaps" here would be file-existence theatre. These rows live in
the register, not the gate, and are appended to `docs/audits/product-truth-full-gap-register.md`.

## Product-truth posture for this wave

Each Phase-1 slice updates this doc + the register row to REAL only when it has: real impl, wired through
BFF/policy, a visible/documented access path, and a passing proof test. The keystone proof is the
LOA-propagation integration test (Proof 1, [09](09-persona-e2e-test-plan.md)).
