# PDP / PEP enforcement map — Checkpoint 1

## Intended (production design)

```
Client → Traefik/Envoy (PEP: strip headers, ext_authz)
            → tshepo-authz PolicyEngine (PDP; OPA shadow→enforce)
            → regenerate trust headers
            → Application (PEP: resource-level) → Audit
```

## Actual (preview runtime)

```
Client → Traefik → experience-bff (PEP: Spring Security + OIDC session + ActorContextFilter)
                      ├─ compose/call domain services directly (often OAuth-disabled)
                      └─ optionally call tshepo-authz (not mandatory per request)
Envoy/ext_authz/OPA: not on path
Application PEPs: largely permitAll via IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true
```

## Component roles

| Component | Role | Live status |
|---|---|---|
| Traefik | TLS edge PEP | **ENFORCED** |
| Envoy | Edge/east-west PEP + header strip | **DISCONNECTED** |
| tshepo-authz PolicyEngine | PDP | **ENFORCED** in-service; **DISCONNECTED** from ingress |
| OPA | Target evaluator | **DISCONNECTED** |
| experience-bff | Session PEP + composition | **ENFORCED** for browser auth |
| Domain services | Resource PEP | **BYPASSABLE** (96/98 OAuth off) |
| FHIR gateway | Consent PEP | **BYPASSABLE** (not on `/fhir` path) |
| BUTANO | SHR store | **ABSENT** consent/authz |
| Mvumo | Consent experience | **PARTIAL** (not a gate) |
| tshepo-consent | Consent SoR + evaluate | **ENFORCED** when called |
| tshepo-audit | Evidence | **ENFORCED** chain |
