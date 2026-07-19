# Registrar-General / CRVS Outbound Adapter — design (Identity Journey Program, X2)

**Status:** DESIGN. No code exists — ubomi has **no outbound HTTP client today**
(verified 2026-07-19: no WebClient/RestTemplate/Feign anywhere in the module).
Implementation is a bounded follow-on behind a feature flag; nothing here
changes ubomi's existing inbound behaviour.
**Decision basis:** Identity Journey Program X2 (PO ruling 2026-07-19) — UBOMI
is the CRVS *gateway*, not a Client Registry and not an HID generator. The
Registrar-General (RG) **link** is net-new; everything else about civil-identity
handling stays where it already lives.

## Ownership justification (production guardrail)

> "Before introducing a new service, prove no existing service already owns that
> capability."

No new service. The adapter is a module **inside ubomi-service (port 8087)**,
which already owns the CRVS boundary:

| Capability | Owner today | X2 change |
|---|---|---|
| Birth/death notification capture | ubomi (`BirthNotificationService`, `DeathNotificationService`) | unchanged — becomes the *source* of outbound pushes |
| Inbound civil-reg verification | ubomi (`VerificationService`, `GET /v1/verifications/{registrationNumber}`) | unchanged — native fallback path |
| MOSIP link store | tshepo-identity (`MosipLinkService`) | unchanged — NOT ubomi's concern |
| eSignet token validation | tshepo-authz (`ESignetAdapter`) | unchanged |
| Proofing sufficiency decision | TSHEPO (assurance vector) | unchanged — consumes the events below |
| **Outbound RG connectivity** | **nobody** | **net-new: `ubomi/integration/rg/`** |

## Boundary

```text
ubomi-service (integration plane)                     Registrar-General (external)
  BirthNotificationService ─┐
  DeathNotificationService ─┤ outbox rows                RG API (mTLS, allow-listed)
                            ▼                                ▲
  event_outbox ──▶ RgOutboundRelay ──▶ RegistrarGeneralClient┘
                        │                    │
                        │                    ▼ minimal verified response
                        │              RgVerificationResult
                        ▼
  governed events: civil.identity.verified / civil.birth.confirmed /
                   civil.death.confirmed / civil.identity.corrected /
                   civil.identity.disputed
  (VITO stores proofing status + opaque external ref; TSHEPO decides sufficiency)
```

## Components (all inside `services/ubomi-service`, package `…ubomi.integration.rg`)

1. **`RegistrarGeneralClient`** — the only class that talks to the RG. Plain
   `RestTemplate`/`WebClient` bean created **only when
   `ubomi.rg.enabled=true`** (`@ConditionalOnProperty`); ubomi gains no HTTP
   client bean otherwise. mTLS material via tshepo-keys custody (client cert +
   truststore refs, never inline). Endpoint allow-list in config; any URL not
   on it is a startup error, not a runtime surprise.
2. **`RgOutboundRelay`** — scheduled drain of ubomi's existing
   `event_outbox` rows of the RG-relevant types (birth/death notifications,
   identity-correction requests). Follows the estate outbox law: pre-serialized
   JSON strings, `KafkaTemplate<String,String>` + StringSerializer for any
   re-emit; per-row failure isolation with retry counters — a failing RG call
   must never head-of-line-block unrelated events (the EventEnvelope/outbox
   stall class).
3. **`RgVerificationService`** — outbound National-ID / passport /
   birth-registration verification, called from proofing flows via a narrow
   interface. Returns a **minimal** verified response (match yes/no + opaque
   `civilIdentityToken` + verified attributes actually needed for the assurance
   decision) — never the full RG record. Response minimisation is enforced by
   the DTO shape, not by discipline.
4. **Governed events** — on confirmed outcomes the relay emits
   `civil.identity.verified`, `civil.birth.confirmed`, `civil.death.confirmed`,
   `civil.identity.corrected`, `civil.identity.disputed` (v1.1 envelopes,
   cpid-free, PII-minimal: event carries the opaque token + status, not civil
   PII). VITO consumes to update proofing status + store the opaque ref;
   TSHEPO's assurance vector consumes for record-link confidence.

## Trust & failure posture

- **Fail-open-for-care:** an RG outage never blocks registration, care, or the
  assisted/in-person proofing paths (Wave G). RG verification only *raises*
  assurance; its absence leaves the native ladder available. Timeouts are
  short (2s connect / 5s read), retries bounded with jittered backoff, then the
  attempt parks as `RG_PENDING` for reconcile.
- **Reconcile loop:** a scheduled sweep re-drives `RG_PENDING` verifications
  and expires them to `RG_UNAVAILABLE` after a governed TTL — honest status,
  never a fabricated verification.
- **Idempotency:** outbound pushes carry the notification's stable business key
  (registration number + event id); the relay tolerates at-least-once redelivery.
- **Audit:** every outbound call and every state transition writes the standard
  audit trail (actor = `ubomi-rg-relay`, purpose = `CIVIL_REGISTRATION`), and
  `VerificationLogEntity` keeps doing what it does for inbound.

## Config surface

```yaml
ubomi:
  rg:
    enabled: false          # flag-gated; default off everywhere
    base-url: ""            # must be on the allow-list below
    allowed-hosts: []       # startup-validated endpoint allow-list
    mtls:
      key-ref: ""           # tshepo-keys custody refs, never inline PEM
      truststore-ref: ""
    connect-timeout-ms: 2000
    read-timeout-ms: 5000
    pending-ttl-hours: 72
```

## Non-code prerequisites (tracked, not built by us)

Data-sharing legal authority; RG API specification; sandbox + mTLS material;
field mapping sign-off; go-live approval. Until these exist the adapter stays
`enabled: false` and the native fallback (ubomi local-notification verify +
Wave G assisted/video/in-person proofing) carries the journeys — this is the
X2 native-first stance.

## Acceptance (EXTERNAL_INTEGRATION tier)

Real RG sandbox: NID verify hit/miss; birth-registration confirm; death
confirm flows to VITO status + relay event; response-minimisation test (full RG
record never persisted/emitted); outage drill (care journey unaffected,
`RG_PENDING` → reconcile → verified); idempotent double-push; audit
completeness. House patterns to mirror: `tshepo-identity MosipLinkService`
(external link store discipline), `tshepo-authz ESignetAdapter` (external
token validation posture).
