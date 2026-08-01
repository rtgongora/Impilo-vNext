# User-token propagation map — Checkpoint 1

| Hop | What carries identity | Propagation | Classification |
|---|---|---|---|
| Browser → BFF | `__Host-impilo_session` cookie (opaque) → Redis-held access token | Server-side only; no JS token | **ENFORCED** |
| Mobile → BFF | Bearer access token from SecureStore (PKCE) | Authorization header | **ENFORCED** |
| BFF → domain service (request context) | Forward inbound JWT if present; else mint `impilo-backend` client_credentials; else none | `ServiceClientConfig` interceptor | **ACTIVE_NOT_ENFORCED** |
| BFF → domain (background) | Minted CC or none + SYSTEM trust headers | No human subject | **BYPASSABLE** |
| Domain → domain | Copy inbound Authorization if present | No minting on most clients | **BYPASSABLE** |
| Async worker | Opaque execution reference intended; today often nothing | Outbox/Kafka plaintext | **ABSENT** |
| Headers alongside token | X-Actor-ID overridden at BFF from JWT; X-Assurance-Level / X-Provider-ID still client-influenced on preview path | | **PARTIAL** / **BYPASSABLE** |
