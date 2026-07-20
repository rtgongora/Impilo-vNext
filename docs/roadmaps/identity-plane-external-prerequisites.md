# Identity Plane — external-prerequisites register

**Purpose.** The four Identity-Plane production capabilities reached **SOFTWARE_CONTRACT_GREEN**
(2026-07-20): the code, seams, adapters, migrations and tests exist and are proven with stubs/rigs,
flag-gated OFF for their real external dependency. This register is the single tracked list of what
is *not code* — the vendor / hardware / legal / institutional prerequisites that gate
**EXTERNAL_INTEGRATION_GREEN** and **NATIONAL_PRODUCTION_GREEN**. Nothing below is buildable by the
engineering team alone; each needs a named owner and a procurement / legal / operations action.

Verdict model: SOFTWARE_CONTRACT_GREEN → EXTERNAL_INTEGRATION_GREEN → NATIONAL_PRODUCTION_GREEN.

---

## 0. Biometric authentication (login / provider step-up / registration enrolment)

**Software-contract state:** inline biometric **enrolment** at new-patient registration (seeds ABIS
templates), **provider biometric step-up** gating Provider-ID activation, and native **Keycloak WebAuthn
passkey login** (citizen + provider, biometric-bound, Keycloak-minted) are built and tested — the
biometric-login stub is now the real flow. **ABIS 1:N scan-to-login** is built flag-gated OFF with a strict
single-strong-candidate gate + full audit. Enrolment + provider step-up are live now (no external gate);
login needs the rows below.

| # | Prerequisite (EXTERNAL_INTEGRATION) | Owner | Gate it lifts |
|---|---|---|---|
| AUTH-1 (L1) | **Keycloak WebAuthn passwordless config applied** to the running IdP (the realm JSON is committed; needs a realm import at fullboot) + set `impilo.auth.passkey.enabled=true`. | Security + platform | passkey login |
| AUTH-2 (L3) | **Keycloak token-exchange + impersonation** grant for the experience-bff service-account client (so a verified biometric identity mints a session without a password); `impilo.auth.biometric-login.enabled=true`. | Security | scan-to-login mint |
| AUTH-3 (L3, NATIONAL) | Deployed ABIS + enrolled templates (L4 seeds them) + **real capture devices** + **1:N false-accept governance sign-off** for the LOGIN reason (threshold/margin calibration, attended/kiosk policy). | Identity architect + registry ops | scan-to-login go-live |

## 1. ABIS — biometric matching

**Software-contract state:** real fingerprint (SourceAFIS) + iris (Gabor/Hamming) matching; real face
cosine (ONNX extraction model-gated, fail-closed); abis-service registered for deploy; enrol-time
dedup, dual-control adjudication + duplicate-investigation, quality gating, template versioning,
stats, and the enrolment/adjudication console UI all built. Consumers reach it via one shared verify
seam.

| # | Prerequisite (EXTERNAL_INTEGRATION) | Owner | Gate it lifts |
|---|---|---|---|
| A1 | **NIST/ISO-evaluated vendor matcher** swapped in behind the `matcher-engine` seam (fingerprint + face + iris). SourceAFIS/Gabor are mature but not certified. | Procurement + Identity architect | certified accuracy |
| A2 | **Production face/iris ONNX models** vendored to `MATCHER_FACE_MODEL_PATH` (+ iris model). Today face extraction fails closed with no model. | Vendor + platform | face/iris 1:1 & 1:N |
| A3 | **Capture devices + SDKs** (fingerprint scanners, cameras, iris) integrated at the enrolment/verify edge. Web capture today is image-upload. | Field-ops + integration | real capture |
| A4 | **PAD / liveness** (presentation-attack detection) engine — currently fail-closed. | Vendor | anti-spoof |
| A5 (NATIONAL) | **NFIQ2/ISO quality** scoring (today a size proxy); **ROC / FMR-FNMR threshold calibration** against a labelled national corpus; **ANN index + capacity/sharding + DR** for national 1:N (today a bounded in-memory linear scan). | Identity architect + SRE | national scale/accuracy |
| A6 (NATIONAL) | **Manual-adjudication SOPs** + reviewer training for the console; per-tenant threshold governance. | Registry operations | operational readiness |

## 2. CRVS / National-ID (UBOMI → Registrar-General)

**Software-contract state:** UBOMI outbox publisher live; flag-gated RG outbound adapter
(`ubomi.rg.enabled=false`) — RegistrarGeneralClient, RgVerificationService (minimal DTO), reconcile
loop, `civil.*` events; VITO lights `AUTHORITATIVELY_VERIFIED` on `civil.identity.verified`;
minimisation + fail-open proven by WireMock rigs.

| # | Prerequisite (EXTERNAL_INTEGRATION) | Owner | Gate it lifts |
|---|---|---|---|
| C1 | **Data-sharing legal authority** / MOU with the Registrar-General; named data controllers; agreed permitted purposes. | Legal + MoHCC | authority to connect |
| C2 | **RG API specification** + **sandbox access** + field-mapping sign-off. | RG + integration | contract certainty |
| C3 | **mTLS material** (client cert + truststore) provisioned into tshepo-keys custody; endpoint allow-list populated. | Security + platform | secure transport |
| C4 (NATIONAL) | Rate-limit / anti-enumeration agreement; photo-availability & authorised-use rules; downtime/reconciliation SOP; production go-live approval. | RG + operations | production go-live |

*Flip sequence:* set `ubomi.rg.{enabled,base-url,allowed-hosts,mtls.*}` → the adapter goes live;
until then the native fallback (local verify + assisted/in-person proofing) carries journeys.

## 3. De-identification (NDR release zone)

**Software-contract state:** the de-id engine is wired to a genuinely-enforced NDR release zone
(tokenise + strip + generalise + k-anon + policy-gate; deny-by-default), with tshepo-keys-backed
dataset-secret custody and a PII-smuggle regression bar over a sample gold→release build.

| # | Prerequisite | Owner | Gate it lifts |
|---|---|---|---|
| D1 (EXTERNAL) | Activate custody + scheduler in the deployed env: `IMPILO_DEID_SECRET_SOURCE=tshepo-keys`, register each `deid_dataset.secret_ref` in tshepo-keys, `NDR_RELEASE_SCHEDULER_ENABLED=true`. | Platform | live release builds |
| D2 (NATIONAL) | **Independent re-identification-risk assessment** on a real release dataset; **disclosure-control governance sign-off** before any external/public release; data-use policy authoring per dataset. | Data governance board | authority to release |

## 4. Card / QR key custody

**Software-contract state:** QR/card signing can route through tshepo-keys (JWKS + rotation overlap +
kid), seed fallback preserved; PKCS11/HSM custody provider (JWS-on-HSM, no key export) + KEK-source
seam; ceremony/rotation runbook (`docs/runbooks/key-ceremony.md`).

| # | Prerequisite | Owner | Gate it lifts |
|---|---|---|---|
| K1 (EXTERNAL) | **Production HSM/KMS** procured + `impilo.keys.custody-provider=pkcs11` configured with the PKCS11 lib; **Vault/KMS-sourced KEK** (`impilo.keys.kek-source=vault`). | Security + platform | hardware key custody |
| K2 (EXTERNAL) | Provision `QR_SIGNING` / `CARD_ASSERTION` keys in tshepo-keys, then flip `vito.qr.signing-source` / `card-print.qr.signing-source=tshepo-keys`. | Security | signing via custody |
| K3 (NATIONAL) | **Witnessed key ceremony** per the runbook (dual control, non-exportable generation, backup/recovery); do NOT mass-issue physical cards until rotation-overlap is exercised on the production keys. | Security officer + witnesses | production issuance |

---

## Owner action summary
- **Legal/MoHCC:** C1 (RG data-sharing authority) — the long-pole for the whole CRVS link.
- **Procurement:** A1 certified matcher, A3 capture devices, K1 HSM/KMS.
- **Security:** C3 mTLS, K1–K3 key custody + ceremony.
- **Data governance board:** D2 re-id risk + disclosure sign-off.
- **SRE/platform:** A5 capacity/DR, D1 activation, config flips.

When a row's dependency lands, flip its flag/config, run the workstream's EXTERNAL_INTEGRATION
acceptance (WireMock → real endpoint), and advance that capability's verdict.
