# 09 — Persona-Based E2E Test Plan (A–J)

Each persona is a journey assertion. Where automatable: RTL (web), MockMvc + WireMock (BFF/services).
Where not: documented manual steps. The **trust assertions** are the point — every persona checks that
access matches identity + assurance + consent + risk.

| Persona | Journey | Key trust assertion | Automatable as | Status today |
|---------|---------|---------------------|----------------|--------------|
| **A** Ordinary adult w/ phone | public → signup → request → temp → continue verify | guest reaches landing w/o login; temp ID can book but cannot open sensitive records | RTL (landing, register) + MockMvc (policy deny on clinical at LOA2) | ❌ blocked by G-CZO-01/02 |
| **B** Returning verified | Health ID + OTP/passkey → verified My Health | LOA3 session opens results per consent; download requires step-up | RTL + MockMvc (allow clinical, 401 on export) | ⚠️ step-up UI missing (G-CZO-04) |
| **C** No Health ID, existing record | request → possible match → safe confirmation/assisted review | match prompt uses **only demographic** fields; never clinical | MockMvc on `MatchingEngine` (assert no clinical inputs) | ✅ assertion passes (demographic-only) |
| **D** Low connectivity | start → lose connection → resume w/o losing progress | resumable draft persists | RTL (draft restore) | ❌ G-CZO-09 |
| **E** Disability | uses a11y options, completes request | contrast/text/lang controls reachable; form completable via keyboard+SR | RTL (a11y panel toggles; axe checks) | ❌ controls not exposed (G-CZO-08) |
| **F** No smartphone | facility-assisted onboarding | `PROVIDER_CAPTURED` path issues temp ID; audited | MockMvc (verification-state transition + audit) | ⚠️ partial |
| **G** Parent/guardian/caregiver | delegated; sees only authorised | "acting for X" scope enforced + audited + revocable | MockMvc (authz on behalf) | ❌ not built (G-CZO-03) |
| **H** Provider who is also citizen | Work vs My Professional vs My Life | provider perms never leak into citizen record reads, and vice-versa | RTL (home role switch) + MockMvc (policy per context) | ✅ separation built — add regression test |
| **I** Suspicious/risky login | only low-risk actions or step-up | high risk → 401 step-up on sensitive; clear reason | MockMvc (riskScore≥61 + sensitive → STEP_UP_REQUIRED) | ⚠️ policy ok, UI missing |
| **J** Temp-ID holder at facility | receives care, no full sensitive record | LOA2 allowed to attach encounter but denied sensitive history read | MockMvc (allow encounter create, deny sensitive read) | ❌ blocked by G-CZO-01 |

## The two keystone automated proofs (write these first)

### Proof 1 — LOA propagation (closes G-CZO-01, unblocks A/J)
1. Seed identity-assurance: actor at LOA1.
2. PolicyEngine: request a `min_loa=3` clinical resource → **DENY**.
3. Upgrade actor to LOA3 via `AssuranceService.decideUpgrade` (dual-control).
4. BFF populates `X-Assurance-Level: LOA3`; re-request → PolicyEngine reads effective LOA3 → **ALLOW**.
5. Assert the dashboard query that was empty at LOA1 now returns records.
   → MockMvc/WireMock integration test spanning identity-assurance + tshepo-authz; **this is the
   acceptance test the mission demands** ("upgrade → policy sees new level → dashboard unlocks").

### Proof 2 — Temp-ID safety ceiling (closes the golden rule, covers J)
- At LOA2: assert ALLOW on `appointment/book` and encounter attach; assert DENY on sensitive clinical
  read (`min_loa=3` / `account_assurance_required`). Proves a temporary ID is useful for care but never
  exposes sensitive records.

## Identity-safety regression (Persona C)
Assert `MatchingEngine` scoring inputs are exactly `{given_name, family_name, date_of_birth, sex, phone_hash}`
and that no clinical field name appears in the matching package — locks in the identity-safety doctrine.

## Provider/citizen separation regression (Persona H)
RTL: render `/home` with `identity.hasProfessionalAccess && hasWorkAccess && !isCitizenOnly` → assert Work
tab present and citizen record cards scoped to the person anchor only; render `isCitizenOnly` → assert no
Work surfaces. MockMvc: same actor, provider purpose vs citizen purpose → different policy outcomes.
