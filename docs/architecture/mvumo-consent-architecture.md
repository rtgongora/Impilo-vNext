# Mvumo — Digital Consent Architecture (Impilo vNext)

**Service (technical):** `mvumo-service`  
**Product name:** **Mvumo** — national digital consent, authorisation, acknowledgement, assent, proxy/guardian approval, withdrawal, and consent-proof orchestration for Impilo vNext.

**Meaning:** “Mvumo” frames *agreement / consent / permission* in product language. It is **not** a checkbox, a single PDF, or one e-signature channel.

**Positioning:** **Mvumo is a sovereign service** — same **Ring 0 / authoritative DPI** class and integration expectations as **Tshepo** (authz, consent, audit, …), **VITO**, and the other national spine modules: national boundary, governed operations, called through the BFF like any other downstream sovereign URL. Within that class, Mvumo orchestrates *how* consent is obtained and proven; **Tshepo Consent** still holds FHIR enforcement and *whether* access is permitted given directives and policy.

---

## 1. Relationship with `tshepo-consent-service` (chosen model: **Option B**)

| Layer | Service | Responsibility |
|--------|---------|----------------|
| **Product & care orchestration** | **Mvumo** (`mvumo-service`) | Consent **templates**; **requests**; **remote sessions**; **adaptive method selection** (portal, token, OTP, PIN, USSD, assisted, offline, paper-to-digital, witnessed, etc.); explanations; Q&A; guardian/proxy/witness/interpreter flows; capture of grant/refusal/partial/withdrawal; **proof** artefacts and history; workflow integration; **requirement evaluation** (what assurance, who may sign, which channels). |
| **Trust & enforcement** | **Tshepo Consent** (`tshepo-consent-service`) | **Policy evaluation** inputs; **FHIR R4 Consent** storage; **consent evaluation** for access decisions (`GET /v1/consent/evaluate`); purpose-of-use / scope-bound **enforcement data**; share-link governance; delegation/revocation semantics; Redis-cached evaluation. |
| **Authorization** | **Tshepo AuthZ** (`tshepo-authz-service`) | Uses consent **verdicts** from Tshepo Consent with ABAC/RBAC, break-glass, step-up. |
| **Audit** | **Tshepo Audit** | Immutable evidence of override and sensitive actions. |

**Non-goals for Mvumo:** duplicate FHIR Consent evaluation logic that already lives in Tshepo Consent; replace Tshepo AuthZ; store raw biometrics.

**Integration contract (target):** When a consent is **granted** and must affect data access, Mvumo **emits** (Kafka + outbox) events and/or calls Tshepo Consent APIs to **materialise or update** a `Consent` directive consistent with national policy. FHIR Gateway continues to call Tshepo Consent for **enforce** decisions (see `ConsentEnforcementService` in `fhir-gateway-service`).

---

## 2. Adaptive consent assurance

Mvumo applies **adaptive consent assurance**: the **method** is selected from context (risk, identity assurance, consent type, channel, facility, connectivity, literacy, language, legal rules, guardian/witness needs). **No single method is mandatory** for all people or contexts.

Assurance **levels** (0–4) are defined in code (`ConsentAssuranceLevel`) and documented in the audit pack. They guide **minimum** assurance; actual method may be higher.

---

## 3. Major integrations (downstream / upstream)

| Domain | Integration |
|--------|-------------|
| Identity | Vito (client), Varapi (provider), Impilo ID / OIDC |
| Registries | Tuso (facility), Zibo (terminology/templates) |
| Record / orders | Butano, OROS, PACS, referrals, PCT |
| Trust | Tshepo TrustContext, Tshepo Consent, Tshepo AuthZ, Tshepo Audit, Tshepo Keys |
| Channels | Notification, channels-service (SMS/USSD/etc.) |
| Documents | Document service / object storage for proofs and scans |
| Rules | `rules-service` for requirement engine |
| Experience | experience-bff, One UI Shell, citizen/provider mobile apps |

Mvumo **must not** become a silo: BFFs and workflows call **`/internal/v1/mvumo/...`** (or future public consent-host routes behind policy).

---

## 4. Storage and evidence

- **PostgreSQL:** structured consent requests, templates, session metadata, lifecycle, `event_outbox`, `consent_event` audit rows, optional `tshepo_consent_id` link to the trust-layer directive.
- **Redis:** short-lived **raw** remote consent token → session metadata (`RemoteConsentTokenRegistry`, key `mvumo:rt:{token}` with TTL aligned to the session). DB stores only `token_hash`. Additional keys: `mvumo:rl:*` (rate limit windows) and `mvumo:pin:fail/*` + `mvumo:pin:lock/*` (`MvumoRateLimiter`, `MvumoPinGuard` for challenge abuse / lockout).
- **Kafka + outbox:** `MvumoOutboxPublisher` ships `mvumo.event_outbox` to `mvumo.outbox.topic` (default `platform.mvumo.events`) with JSON envelope `eventType`, `aggregateId`, `payload`.
- **Tshepo Consent HTTP:** on first **grant** / **partial grant**, `TshepoConsentClient` POSTs `CreateConsentRequest` to `mvumo.tshepo-consent.base-url` (`/v1/consent`) with a minimal valid FHIR R4 `Consent` JSON (`FhirMvumoConsentBuilder`). **Withdraw / refuse** revokes the directive when `tshepo_consent_id` is set. Trust headers and `Authorization` are forwarded from the inbound request; if there is **no** `Authorization` (e.g. no servlet context, async path), `mvumo.tshepo-consent.client-credentials` can obtain a **client_credentials** token from `token-uri` and set `Authorization: Bearer` (see `ClientCredentialsTokenProvider` and second `RestTemplate` interceptor). Outbound create/revoke wrapped in **Micrometer spans** `tshepo.consent.create` / `tshepo.consent.revoke` when a `Tracer` is present.
- **OTEL / export:** add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`. Set `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` (e.g. `http://otel-collector:4318/v1/traces`) to enable OTLP export; `management.tracing.sampling.probability` controls sampling.
- **HAPI contract test:** `FhirMvumoConsentBuilderHapiTest` (always-on). **Dev-instance IT:** set `MVUMO_IT_TSHEPO_BASE` to run `TshepoConsentDevInstanceIT` against a live `tshepo-consent-service`; optional `MVUMO_IT_BEARER_TOKEN` for protected endpoints.
- **Document service:** PDFs, scanned paper, signature images — **references and hashes** in Mvumo; **no** casual storage of raw biometrics.

---

## 5. Standards mapping

- **FHIR R4 Consent / Provenance / DocumentReference / Questionnaire(Response)** — Tshepo Consent remains FHIR authority; Mvumo may hold questionnaire responses and link to FHIR resources.
- **W3C Verifiable Credentials** — optional future pattern for portable proofs.

---

## 6. API surface

Product-facing orchestration is exposed under **`/internal/v1/mvumo/...`** (see OpenAPI on the service). The Experience BFF forwards the same path prefix to `mvumo-service` via `MvumoServiceProxyController` using `impilo.services.mvumo-base-url` (default `http://localhost:8195`), so UIs and mobile clients that already call `/internal/v1/...` on the BFF can reach Mvumo without a second base URL.

For **chart visibility** (banner, patient summary, emergency strip), the BFF also exposes **`GET /internal/v1/summary/patient/{patientId}`**, which merges PCT longitudinal data with **`GET .../mvumo/consent-summary?patientRef=Patient/{cpid}`** so consent is not buried in history-only views. Configure **`MVUMO_BASE_URL`** wherever the BFF runs (Docker Compose `ops/runtime/docker-compose.operations.yml`, Helm `experience-bff`, or local `application.yml`).

---

## 7. References

- `docs/audits/mvumo-consent-current-state-audit.md` — repository consent audit.
- `services/tshepo-consent-service` — trust-layer consent.
- `services/mvumo-service` — Mvumo implementation.
- `helm/mvumo` — Kubernetes chart (port 8195, Postgres `mvumo`, Redis, Kafka, `TSHEPO_CONSENT_BASE_URL`).
