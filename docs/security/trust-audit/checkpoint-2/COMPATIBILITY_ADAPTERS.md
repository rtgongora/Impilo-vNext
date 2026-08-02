# Checkpoint 2 — Compatibility adapters

Additive adapters only. No enforcement activation. No endpoint/header retirement.

## Unresolved ownership (explicit)

Mvumo vs `tshepo-consent-service` authoritative grant/revocation SoR and the POST/GET evaluate
wire convergence are **not** resolved by these adapters. See Checkpoint 1
`CONSENT_CONTRACT_INCOMPATIBILITY.md`.

## Adapters

| Adapter | Owner | Proven callers (CP1) | Legacy shape | Canonical shape | Removal condition |
|---|---|---|---|---|---|
| `LegacyAuthenticationAssuranceAdapter` | tshepo-authz / trust-plane | KeycloakAdapter, PolicyEngine, AuthzInternalRequest, AuthorizeController | `dto.AuthenticationAssurance` | `v1.AuthenticationAssurance` + `RecoveryAuthenticationState` | All callers consume v1 with recovery state |
| `LegacyConsentDecisionAdapter` | consent / authz | ConsentClient, ConsentEvaluationController/Service, ConsentEnforcementService, MvumoService, PolicyEngine | `dto.ConsentDecision` | `v1.ConsentDecision` | Evaluate wire converged + SoR designated |
| `AuthzResponseChallengeAdapter` | tshepo-authz | PolicyEngine, AuthorizeController, ExtAuthzGrpcService, one-ui-shell `contracts.ts` / `usePolicyDecision` | `AuthzResponse` + `Verdict` (3) | `TrustChallengeOutcome` (12) | Challenge UX cohort complete |

## Authority-preserving rules

- Recovery AMR → `CONSTRAINED_RECOVERY` on toCanonical; toLegacy demotes AAL ≤ 1 (never invents ordinary AAL2).
- Canonical decisions beyond ALLOW/DENY/STEP_UP_REQUIRED cannot reverse-map into `AuthzResponse` (throws).
- Consent adapter does not invent lawful basis or choose evaluate verb.
