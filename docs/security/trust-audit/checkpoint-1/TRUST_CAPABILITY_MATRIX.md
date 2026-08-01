# Trust capability matrix — Checkpoint 1

**Branch:** `claude/tshepo-trust-cp1-truth-audit` · **Base commit:** `f190318e1`  
**Classification standard:** intended production design (not preview posture).  
**Evidence sources:** `runtime-evidence/*`, `source-audits/01`–`05`.

Status vocabulary: `ENFORCED` · `ACTIVE_NOT_ENFORCED` · `SHADOW` · `PARTIAL` · `BYPASSABLE` · `DISCONNECTED` · `DOCUMENTED_ONLY` · `ABSENT` · `INSUFFICIENT_EVIDENCE`.

| # | Capability area | Capability | Status | Evidence |
|---|---|---|---|---|
| 1 | Authentication | Browser OIDC auth-code + PKCE via BFF | **ENFORCED** (preview) | 05-bff; web-session enabled live |
| 2 | Authentication | Encrypted Redis sessions + `__Host-` cookies + CSRF | **ENFORCED** | 05-bff |
| 3 | Authentication | Legacy ROPC / password login reachable | **DISCONNECTED** (denyAll) / code residue PARTIAL | 05-bff; 04-audit-recovery |
| 4 | Authentication | Mobile PKCE + SecureStore + replay protection | **ENFORCED** | 04-audit-recovery |
| 5 | Authentication | Mobile residual password-grant (sign-up) | **PARTIAL** | 04-audit-recovery |
| 6 | Authentication assurance | Keycloak ACR→AAL mapping | **ENFORCED** (code) | 03-policyengine |
| 7 | Authentication assurance | Recovery codes yield restricted recovery state | **BYPASSABLE** (grants full AAL2) | 04-audit-recovery |
| 8 | Authentication assurance | Workforce MFA activation (enforcement) | **ACTIVE_NOT_ENFORCED** | MFA evidence; programme gate |
| 9 | Identity assurance | IAL/LoA via identity-assurance-service + BFF stamp | **PARTIAL** | 03-policyengine; preview path client can still send X-Assurance-Level |
| 10 | Workload identity | Per-service K8s SA + unique Keycloak client | **ABSENT** | runtime WORKLOAD_AND_TOKEN; 01-east-west |
| 11 | Workload identity | Minted client_credentials (BFF/pct/mvumo) | **ACTIVE_NOT_ENFORCED** | 01-east-west |
| 12 | Active context | Work-context mint against Vashandi/org-registry | **ENFORCED** (at mint) | 02-consent-context |
| 13 | Active context | Duty-token binding at PDP | **SHADOW** + **DISCONNECTED** | live TSHEPO_WORK_CONTEXT_MODE=SHADOW; ext_authz off |
| 14 | Authority | Role alone insufficient (policy rules) | **PARTIAL** | PolicyEngine default-deny; many dimensions need duty token |
| 15 | Authority | Licence/council standing on protected actions | **PARTIAL** | 02-consent-context |
| 16 | Authority | Delegation (single-level) | **ACTIVE_NOT_ENFORCED** | 02-consent-context |
| 17 | Authority | Delegation chains | **ABSENT** | 02-consent-context |
| 18 | Consent / lawful basis | Mvumo capture + lifecycle | **PARTIAL** | 02-consent-context |
| 19 | Consent / lawful basis | tshepo-consent evaluation engine | **ENFORCED** (in-service) | 02-consent-context |
| 20 | Consent / lawful basis | PDP Step 5 consent | **DISCONNECTED** + broken POST/GET contract | 02-consent-context; 03-policyengine |
| 21 | Consent / lawful basis | FHIR/BUTANO/BFF clinical gating | **BYPASSABLE** / **ABSENT** | 02-consent-context |
| 22 | Consent / lawful basis | Direct-care / statutory engines | **ABSENT** | 02-consent-context |
| 23 | Policy decision | PolicyEngine default-deny PDP | **ENFORCED** (in-service) | 03-policyengine |
| 24 | Policy decision | Envoy → ext_authz on live path | **DISCONNECTED** | runtime; deployed envoy 0 ext_authz |
| 25 | Policy decision | OPA shadow / enforce | **DISCONNECTED** (`opaMode=OFF`; no Helm deploy) | 03-policyengine |
| 26 | Edge enforcement | Traefik TLS termination | **ENFORCED** (north-south) | runtime |
| 27 | Edge enforcement | Envoy as mandatory choke point | **BYPASSABLE by design** | Traefik→BFF |
| 28 | Edge enforcement | Public-lane header stripping | **DISCONNECTED** (Envoy off-path) | 05-bff |
| 29 | Application enforcement | Estate OAuth resource-server | **BYPASSABLE** (96/98 disabled) | BYPASS_INVENTORY |
| 30 | Application enforcement | JWT audience validation | **ABSENT** | 05-bff |
| 31 | Spoofing protection | Server-authoritative X-Actor-ID (BFF) | **ENFORCED** | 05-bff |
| 32 | Spoofing protection | Server-authoritative X-Assurance-Level / X-Provider-ID | **BYPASSABLE** (preview) | 05-bff |
| 33 | Recovery | Two-person lost-device recovery | **ENFORCED** | 04-audit-recovery |
| 34 | Break-glass | PDP break-glass doctrine | **ENFORCED** (in-service; off-path) | 03/04 |
| 35 | Transport | Inter-service mTLS | **ABSENT** | 01-east-west |
| 36 | Transport | Kafka auth/ACLs | **ABSENT** | PLAINTEXT |
| 37 | Transport | NetworkPolicy containment | **DISCONNECTED** / **ABSENT** live | 0 NetPols |
| 38 | Audit | Hash-chained ledger | **ENFORCED** | 04-audit-recovery |
| 39 | Audit | Keycloak event ingestion | **ACTIVE_NOT_ENFORCED**→preview ON | 04; MFA evidence |
| 40 | Audit | End-to-end decision_id / trace_id | **ABSENT** / correlation **PARTIAL** | 04-audit-recovery |
| 41 | Unified trust experience | Challenge continuations / TrustChallenge UX | **ABSENT** | programme §7 not started |
| 42 | Unified trust experience | Public-first progressive trust | **PARTIAL** | public lane ENFORCED; progressive challenges incomplete |

## Separate declarations (no broad "Tshepo complete" claim)

| Plane facet | Declaration |
|---|---|
| Authentication | Browser/mobile MFA foundation **ENFORCED** at BFF; workforce enforcement **not activated**; recovery-code policy **defective** |
| Workload trust | **ABSENT** as unique identities; minted tokens **ACTIVE_NOT_ENFORCED** |
| Context | Mint **ENFORCED**; binding **SHADOW** and off-path |
| Authority | Policy rules exist; licence/delegation **PARTIAL** / **ACTIVE_NOT_ENFORCED** |
| Consent | Capture real; clinical gating **ABSENT/BYPASSABLE**; PDP contract broken |
| Policy evaluation | PolicyEngine real; **not on live path**; OPA **DISCONNECTED** |
| Edge enforcement | TLS yes; Envoy/ext_authz **DISCONNECTED** |
| Application enforcement | **BYPASSABLE** estate-wide via OAuth-disable flag |
| Recovery | Lost-device **ENFORCED**; recovery-code state **BYPASSABLE** |
| Audit | Chain **ENFORCED**; correlation incomplete |
| User experience | OIDC login real; unified TrustChallenge UX **ABSENT** |
