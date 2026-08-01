# Recovery-code → AAL2 proof — Checkpoint 1 closure

Programme requirement: recovery authentication yields a **constrained recovery state**, not ordinary workforce AAL2.

## Claim chain

```
Keycloak AAL2 subflow
  └─ auth-recovery-authn-code-form as ALTERNATIVE (runtime realm finding)
        ↓ issues token with acr=urn:impilo:aal2 (+ amr may include recovery)
BFF / tshepo-authz
  └─ map acr → AuthenticationAssurance.aal = 2
        ↓ no AMR exclusion for recovery methods
PolicyEngine
  └─ treats aal>=2 as ordinary AAL2 authority (incl. step-up / break-glass paths)
```

## Source interpretation (SOURCE_CONFIRMED)

`KeycloakAdapter.extractAuthenticationAssurance` maps ACR without consulting recovery semantics:

```188:204:services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/session/KeycloakAdapter.java
    private AuthenticationAssurance extractAuthenticationAssurance(JWTClaimsSet claims, String sessionId) {
        try {
            String acr = claims.getStringClaim("acr");
            int aal = switch (acr == null ? "" : acr) {
                case "0" -> 0;
                case "urn:mace:incommon:iap:bronze", "1", "urn:impilo:aal1" -> 1;
                case "urn:mace:incommon:iap:silver", "2", "urn:impilo:aal2" -> 2;
                case "urn:mace:incommon:iap:gold", "3", "urn:impilo:aal3" -> 3;
                default -> 1;
            };
            List<String> methods = Optional.ofNullable(claims.getStringListClaim("amr")).orElse(List.of());
            // ... phishingResistant from webauthn/hwk only — no recovery demotion
            return new AuthenticationAssurance(aal, methods, authTime, stepUpTime,
                    phishingResistant, sessionId, claims.getStringClaim("impilo_flow_id"));
```

Findings:

- `urn:impilo:aal2` → `aal=2` unconditionally.
- `amr` is copied through but **never** used to demote recovery to a constrained state.
- Legacy `AuthenticationAssurance` record has **no** `recoveryState` / `constrained` field.
- Committed realm export does not IaC-define the live AAL2 recovery ALTERNATIVE; that layout was observed via Keycloak admin/runtime in Checkpoint 1 source audit `04-audit-recovery-mobile.md`.

**Classification:** `SOURCE_CONFIRMED` defect (ordinary AAL2 on recovery ACR).

## Test proof (TEST_PROVEN?)

| Search | Result |
|---|---|
| Test asserting recovery → restricted state | **Not found** |
| Test asserting recovery AMR exclusion | **Not found** |
| Tests using `AuthenticationAssurance` AAL thresholds | Exist for step-up / min_aal — they treat numeric AAL, not recovery constraint |

**Classification:** **NOT TEST_PROVEN** as restricted recovery. The defect is therefore not contradicted by green tests; it is also not locked in by a positive “recovery equals AAL2” regression test in-repo.

## Deployed runtime proof (PREVIEW_ENFORCED?)

| Item | Status |
|---|---|
| Keycloak 26.7/PostgreSQL live | YES |
| Captured live token from recovery-code login decoded in this closure | **NO** |
| Captured PDP decision on recovery session for clinical/admin action | **NO** (PDP also off ingress path) |

**Classification:** **NOT PREVIEW_ENFORCED** (neither as restricted state nor as proven live bypass).  
Do **not** claim PREVIEW_ENFORCED for the bypass; retain **SOURCE_CONFIRMED**.

## Correct programme wording

> Recovery-code authentication is SOURCE_CONFIRMED to produce ordinary AAL2 in Tshepo’s claim interpretation (`acr=urn:impilo:aal2` with no recovery demotion). It is not TEST_PROVEN and not PREVIEW_ENFORCED in this pack. Checkpoint 4 must introduce a constrained recovery state before workforce MFA enforcement.

## Relation to lost-device recovery

Two-person lost-device recovery is a **different** flow (no session mint). It remains SOURCE_IMPLEMENTED / TEST_PARTIAL; PREVIEW_ENFORCED was demoted to UNKNOWN in `CAPABILITY_TRUTH_LAYERS.md` pending a fresh capture.
