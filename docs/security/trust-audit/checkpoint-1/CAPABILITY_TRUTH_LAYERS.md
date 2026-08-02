# Capability truth layers — Checkpoint 1 closure

**Branch:** `claude/tshepo-trust-cp1-truth-audit`  
**Classification standard:** intended production design.  
**Rule:** Do **not** call a capability `PREVIEW_ENFORCED` merely because source or tests exist.  
Every `PREVIEW_ENFORCED` cell must cite digest, source commit, runtime config, request path, enforcement point, and captured proof.

Layer vocabulary:

| Layer | Meaning |
|---|---|
| `SOURCE_IMPLEMENTED` | Code/config exists in the documenting branch (or cited SHA) |
| `TEST_PROVEN` | Automated test asserts the behaviour |
| `PREVIEW_DEPLOYED` | Artefact is present in `impilo-full-preview` |
| `PREVIEW_ENFORCED` | Live path actually enforces the capability against a real request |

Values: `YES` · `NO` · `PARTIAL` · `N/A` · `UNKNOWN`.

Provenance for trust images (where mapped) is in `runtime-evidence/IMAGE_DIGEST_PROVENANCE.md`. Preview images with labels were built from `codex/mfa-production`, **not** this documenting branch.

| # | Capability | SOURCE_IMPLEMENTED | TEST_PROVEN | PREVIEW_DEPLOYED | PREVIEW_ENFORCED | Citation / notes |
|---|---|---|---|---|---|---|
| 1 | Browser OIDC auth-code + PKCE via BFF | YES | PARTIAL | YES | YES | Digest `experience-bff@sha256:1948d8d3…` commit `486b3a4f…`; `IMPILO_AUTH_WEB_SESSION_ENABLED=true`; path `GET /internal/v1/auth/oidc/*`; PEP=`experience-bff` OIDC session; proof=`OPEN_QUESTION_ANSWERS` + live env |
| 2 | Encrypted Redis sessions + `__Host-` cookies + CSRF | YES | PARTIAL | YES | YES | Same BFF digest; cookie names in `WebAuthSessionStore`; CSRF filter live with secure cookie flag |
| 3 | Legacy ROPC / password login reachable | YES (residue) | YES (denyAll) | YES (code present) | NO | `SecurityConfig` denyAll on legacy `/auth/login`; not an enforced login path |
| 4 | Mobile PKCE + SecureStore + replay protection | YES | PARTIAL | UNKNOWN | UNKNOWN | Source in `apps/mobile/packages/mobile-auth`; no Redroid runtime proof in this closure window → not PREVIEW_ENFORCED |
| 5 | Mobile residual password-grant (sign-up) | YES | NO | UNKNOWN | NO | Source residual only |
| 6 | Keycloak ACR→AAL mapping | YES | PARTIAL | YES | PARTIAL | `KeycloakAdapter.extractAuthenticationAssurance`; mapping runs when tokens reach authz; authz not on live ingress path |
| 7 | Recovery codes → restricted recovery state | NO (defect) | NO | N/A | NO | See `RECOVERY_CODE_PROOF.md` — source grants ordinary AAL2; not PREVIEW_ENFORCED as restricted |
| 8 | Workforce MFA activation (enforcement) | PARTIAL | NO | PARTIAL | NO | Required actions may exist; enforcement gate not activated (`IMPILO_BOOTSTRAP_REQUIRE_MFA=false`) |
| 9 | IAL/LoA via identity-assurance + BFF stamp | PARTIAL | PARTIAL | PARTIAL | NO | Client can still supply `X-Assurance-Level` on preview path |
| 10 | Per-service K8s SA + unique KC client | NO | NO | NO | NO | Shared `default` SA; shared `impilo-backend` |
| 11 | Minted client_credentials (BFF/pct/mvumo) | YES | PARTIAL | YES | NO | Active issuance ≠ unique workload enforcement |
| 12 | Work-context mint against Vashandi/org-registry | YES | PARTIAL | YES | YES (at mint) | Mint path enforced at BFF/identity; binding not |
| 13 | Duty-token binding at PDP | YES | PARTIAL | YES (SHADOW) | NO | `TSHEPO_WORK_CONTEXT_MODE=SHADOW`; ext_authz off-path |
| 14 | Role alone insufficient | PARTIAL | PARTIAL | PARTIAL | NO | PolicyEngine rules exist; not on live path |
| 15 | Licence/council standing | PARTIAL | PARTIAL | PARTIAL | NO | Off-path |
| 16 | Delegation (single-level) | YES | PARTIAL | PARTIAL | NO | Not enforced on live path |
| 17 | Delegation chains | NO | NO | NO | NO | |
| 18 | Mvumo capture + lifecycle | YES | PARTIAL | YES | PARTIAL | Capture works; ownership conflict unresolved |
| 19 | tshepo-consent evaluation engine | YES | PARTIAL | YES | PARTIAL | In-service GET evaluate exists; not PDP-wired |
| 20 | PDP Step 5 consent | YES (client) | NO | YES (services) | NO | POST≠GET broken; PDP off live path — see `CONSENT_CONTRACT_INCOMPATIBILITY.md` |
| 21 | FHIR/BUTANO/BFF clinical consent gating | PARTIAL | PARTIAL | PARTIAL | NO | Bypassable / absent |
| 22 | Direct-care / statutory engines | NO | NO | NO | NO | |
| 23 | PolicyEngine default-deny PDP | YES | YES | YES | NO | Deployed but not on ingress path → not PREVIEW_ENFORCED for edge traffic |
| 24 | Envoy → ext_authz on live path | YES (source) | PARTIAL | PARTIAL (pod) | NO | Deployed Envoy ConfigMap: 0 ext_authz; no IngressRoute |
| 25 | OPA shadow / enforce | YES (code) | PARTIAL | NO | NO | `opaMode=OFF`; no OPA Helm deploy |
| 26 | Traefik TLS termination | YES | N/A | YES | YES | North-south TLS; enforcement point Traefik LB |
| 27 | Envoy as mandatory choke point | YES (intended) | NO | NO | NO | Traefik→BFF bypasses Envoy by design today |
| 28 | Public-lane header stripping | YES (source) | PARTIAL | NO | NO | Requires Envoy on path |
| 29 | Estate OAuth resource-server | YES | PARTIAL | YES (flag) | NO | 96/98 `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` → BYPASSABLE |
| 30 | JWT audience validation | NO | NO | NO | NO | |
| 31 | Server-authoritative X-Actor-ID (BFF) | YES | PARTIAL | YES | YES | BFF stamps actor from session |
| 32 | Server-authoritative X-Assurance-Level / X-Provider-ID | PARTIAL | PARTIAL | YES | NO | Preview still accepts client-supplied values for some headers |
| 33 | Two-person lost-device recovery | YES | PARTIAL | YES | UNKNOWN | Source/tests strong; no fresh PREVIEW_ENFORCED capture in this closure → not claimed enforced here |
| 34 | Break-glass PDP doctrine | YES | PARTIAL | YES | NO | Off-path |
| 35 | Inter-service mTLS | NO | NO | NO | NO | |
| 36 | Kafka auth/ACLs | NO | NO | NO | NO | PLAINTEXT |
| 37 | NetworkPolicy containment | YES (charts?) | NO | NO | NO | 0 NetPols live |
| 38 | Hash-chained audit ledger | YES | YES | YES | PARTIAL | Service enforces chain internally; end-to-end correlation incomplete |
| 39 | Keycloak event ingestion | YES | PARTIAL | YES | PARTIAL | Flag on in preview; not full trust-plane correlation |
| 40 | End-to-end decision_id / trace_id | NO | NO | NO | NO | |
| 41 | TrustChallenge UX / continuations | NO | NO | NO | NO | Checkpoint 2 contracts only start the types |
| 42 | Public-first progressive trust | PARTIAL | PARTIAL | PARTIAL | PARTIAL | Public lane real; progressive challenges incomplete |

## Corrections to prior single-status wording

| Prior claim | Corrected layered truth |
|---|---|
| “Browser OIDC **ENFORCED** (preview)” | SOURCE YES · PREVIEW_DEPLOYED YES · PREVIEW_ENFORCED YES (BFF session path only) · default-off in shipped config outside this preview |
| “BFF/mobile MFA foundation ENFORCED” | **Split** — see `MFA_FOUNDATION_TRUTH.md`. Browser session PREVIEW_ENFORCED; mobile PREVIEW_ENFORCED UNKNOWN; workforce NO |
| “PolicyEngine **ENFORCED** (in-service)” | SOURCE/TEST/DEPLOYED YES; PREVIEW_ENFORCED NO for live ingress (Envoy/ext_authz DISCONNECTED) |
| “Lost-device recovery ENFORCED” | SOURCE YES · TEST PARTIAL · PREVIEW_ENFORCED UNKNOWN in this closure (demoted from broad ENFORCED) |
| “Recovery codes BYPASSABLE” | Remains SOURCE_CONFIRMED defect; not PREVIEW_ENFORCED as restricted state — see `RECOVERY_CODE_PROOF.md` |
