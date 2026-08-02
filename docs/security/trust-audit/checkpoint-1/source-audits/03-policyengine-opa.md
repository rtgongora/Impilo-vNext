# Source audit — PolicyEngine, OPA, assurance separation

Branch `claude/tshepo-trust-cp1-truth-audit`, commit `f190318e1`.
Source: [Audit authz PolicyEngine and OPA](6fea2634-df62-4d5f-8069-81352c498454).
Classification against **intended production design**.

## Headline

`tshepo-authz-service` is a real default-deny PDP with all ten access dimensions hooked, fail-closed consent (when called), break-glass doctrine, and transactional audit. **It is not on the live preview ingress path** (`envoy.extAuthz.enabled: false`). OPA is a designed shadow comparator that is **OFF everywhere** and **not deployed in the Helm chart**. Identity assurance and authentication assurance are correctly separated in code. The legacy monolith PDP survives only for residual `/external/v1/` and three proxied evaluate endpoints.

## Classification

| Capability | Classification |
|---|---|
| PolicyEngine default-deny RBAC/ABAC (in-service) | **ENFORCED** |
| ext_authz — compose/local path | **ENFORCED** |
| ext_authz — k8s full-preview | **ACTIVE_NOT_ENFORCED / DISCONNECTED** |
| 10-dimension access model | **PARTIAL** (hooks exist; several need duty token + ENFORCE mode) |
| OPA decision path | **SHADOW (designed), effectively DISCONNECTED** (`opaMode=OFF`, no Helm deploy) |
| OPA doctrine corpus | **DOCUMENTED_ONLY / SHADOW-loaded** (compose only) |
| Identity vs authentication assurance separation | **ENFORCED** (code path) |
| `min_aal` / phishing-resistant / auth-age | **PARTIAL** |
| Break-glass doctrine | **ENFORCED** (in-service; off-path at runtime) |
| Confidentiality lane (Step 4.7) | **ENFORCED (config ENFORCE)** conditional on ratified pack |
| Work-context duty binding | **SHADOW** |
| Signed decision envelope | **ACTIVE_NOT_ENFORCED** |
| Authz audit emission | **ENFORCED** (outbox write; delivery needs runtime check) |
| Legacy monolith PDP on authorize path | **DISCONNECTED** |
| Legacy residual `/external/v1/` + 3 evaluate proxies | **PARTIAL** |
| Public anonymous lane | **ENFORCED (as designed bypass)** |
| Soft-fail on invalid JWT (continues on client headers) | **BYPASSABLE** (hardcoded) |

## Bypass / mode flags (defaults)

| Flag | Default / live | Effect |
|---|---|---|
| `envoy.extAuthz.enabled` | **false** (chart + live ConfigMap) | PDP absent from ingress |
| `tshepo.authz.opa-mode` | **OFF** | OPA never authoritative; not even shadowing |
| `TSHEPO_WORK_CONTEXT_MODE` | **SHADOW** (live confirmed) | Duty mismatch audits, never denies |
| `TSHEPO_AUTHZ_CONFIDENTIALITY_MODE` | application.yml **ENFORCE** (confirm live) | Conditional on ratified pack |
| `TSHEPO_DECISION_ENVELOPE_ENABLED` | **false** | Envelope not minted |
| `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` | false on authz/audit; **true** on ~96 others | Estate backstop collapsed |
| Invalid bearer soft-fail | hardcoded | Continues on client headers with empty roles |

## Runtime confirmations (2026-08-01)

| Question | Answer |
|---|---|
| Is ext_authz off on live preview? | **Yes** — deployed Envoy ConfigMap has 0 `ext_authz` refs; Traefik routes past Envoy |
| `TSHEPO_WORK_CONTEXT_MODE` on authz pod | **SHADOW** |
| `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` on authz | **false** |
| OPA in `impilo-full-preview` | **Absent** (no OPA workload in namespace) |
| experience-bff `ALLOW_ANONYMOUS` / `AUTH_FALLBACK` | **false / false** |

Still open: confidentiality pack RATIFIED state; policy_rules ACTIVE coverage for `min_aal`/modes; outbox backlog; legacy route hit counts; DTO source visibility under `libs/tshepo-contracts`.
