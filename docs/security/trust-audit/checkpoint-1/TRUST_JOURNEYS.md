# Trust journeys — Checkpoint 1 (≥20 human/system + S2S flow classes)

Classification against intended production design. "Live path" = what happens on `impilo.mohcc.gov.zw` today.

## Human / system journeys (≥20)

| # | Journey | Steps (intended) | Live classification | Notes |
|---|---|---|---|---|
| H1 | Citizen public browse (info/search/maps) | Public lane, no auth | **ENFORCED** (as designed) | Public-first preserved |
| H2 | Citizen anonymous SOS / feedback | Allow-listed anonymous POST | **ENFORCED** (as designed) | |
| H3 | Citizen browser login | BFF OIDC + PKCE → Redis session | **ENFORCED** | MFA cohort |
| H4 | Citizen progressive enrollment | Low friction → step-up later | **PARTIAL** | enrollment real; progressive challenge UX incomplete |
| H5 | Citizen clinical record view | Auth → consent → BFF → SHR | **BYPASSABLE** | BFF display-only consent; BUTANO no consent; estate OAuth off |
| H6 | Citizen consent grant/revoke (Mvumo/privacy) | Mvumo → tshepo-consent | **PARTIAL** | capture real; gating not enforced |
| H7 | Citizen delegation grant | Mvumo delegation + PDP 4.5 | **ACTIVE_NOT_ENFORCED** | needs ext_authz + X-Subject-ID |
| H8 | Provider browser login + MFA | OIDC + AAL2 factors | **ENFORCED** (authn) / **ACTIVE_NOT_ENFORCED** (authz use of AAL) | |
| H9 | Provider Work Home context select | Mint duty token against Vashandi | **ENFORCED** (mint) | binding SHADOW |
| H10 | Provider clinical write with duty token | PDP bind + resource authz | **DISCONNECTED** | PDP off-path |
| H11 | Provider break-glass | Request + fresh AAL2 + audit | **ACTIVE_NOT_ENFORCED** | coded; off-path; recovery-code can satisfy AAL2 |
| H12 | Provider recovery-code login | Should yield restricted state | **BYPASSABLE** | grants full AAL2 |
| H13 | Two-person lost-device recovery | AAL3 requester+approver → revoke+reenroll | **ENFORCED** | does not mint session |
| H14 | Regulatory duty session | Org-registry appointment proof → mint | **ENFORCED** (mint) | |
| H15 | Provider mobile login | PKCE + SecureStore | **ENFORCED** | |
| H16 | Citizen mobile sign-up auto-login | establishFromTokenResponse | **PARTIAL** | non-PKCE residue |
| H17 | Step-up for sensitive action | TrustChallenge STEP_UP_REQUIRED | **ABSENT** (unified UX) / **PARTIAL** (PDP machinery) | |
| H18 | Consent-required clinical action | CONSENT_REQUIRED challenge | **DISCONNECTED** | PDP contract broken + off-path |
| H19 | Suspended provider attempt | Revocation store deny | **ACTIVE_NOT_ENFORCED** | needs X-Provider-ID + PDP on path |
| H20 | Privileged admin action | AAL3 + roles | **PARTIAL** | lost-device path ENFORCED; broader admin cohort open |
| H21 | Offline provider high-risk action | Online-only denylist | **ENFORCED** (offline credential service) | |
| H22 | Identity/PDP outage | TEMPORARILY_UNAVAILABLE fail-closed | **PARTIAL** | Envoy fail-closed in source; Envoy not on path; BFF fails closed without JwtDecoder |

## Service-to-service flow classes

| # | Flow class | Credential pattern | Classification |
|---|---|---|---|
| S1 | External actor ingress (Traefik→BFF) | Browser session / bearer | **ENFORCED** at BFF; Envoy **DISCONNECTED** |
| S2 | BFF → domain service (user present) | Fwd user JWT → else minted CC | **ACTIVE_NOT_ENFORCED** (callees often OAuth-disabled) |
| S3 | BFF → domain service (background) | Minted CC or none | **BYPASSABLE** |
| S4 | Domain → domain (inbound-token-only) | Fwd JWT or none | **BYPASSABLE** |
| S5 | pct/mvumo service-originated | Optional minted CC | **ACTIVE_NOT_ENFORCED** |
| S6 | Async outbox → Kafka → consumer | No auth | **ABSENT** |
| S7 | Scheduler → HTTP callee | Often no token | **BYPASSABLE** |
| S8 | Keycloak admin (BFF) | Dedicated admin token | **ENFORCED** |
| S9 | Audit event ingest | permitAll + Kafka | **PARTIAL** (trust assumes mesh) |
| S10 | External SMS/SMTP/LLM | API keys / log-mode in preview | **PARTIAL** |
| S11 | Direct pod→pod to ClusterIP | Plain HTTP, no NetPol | **BYPASSABLE** |
| S12 | Shared `impilo-backend` client across services | Shared secret | **BYPASSABLE** (no unique workload identity) |
