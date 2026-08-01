# East–west communication graph — Checkpoint 1

## Live ingress (north–south) — Envoy not in path

```
Internet
  → Traefik LoadBalancer (TLS)
      ├─ /internal,/actuator,/health → experience-bff:8160
      ├─ /                           → one-ui-shell:3000
      ├─ /realms,/resources          → keycloak:8080
      └─ /.well-known                → public-website:8080
Envoy:10000 exists but has no IngressRoute and no ext_authz filter.
```

## East–west (inside namespace)

```
experience-bff
  │  (Fwd user JWT → else minted client_credentials → else none)
  │  + trust headers (X-Service-Id=experience-bff, X-Access-Mode=INTERNAL)
  ├─→ ~90 domain services by ClusterIP DNS (http://)
  ├─→ tshepo-authz:8081 (decision API; NOT on ingress path)
  ├─→ tshepo-consent:8182
  ├─→ mvumo:8195
  ├─→ tshepo-audit:8183
  └─→ keycloak (admin token path, un-intercepted)

pct-service ─(optional CC)→ oros, clinical-knowledge
mvumo-service ─(fwd JWT|CC)→ tshepo-consent
domain A ─(fwd inbound JWT only)→ domain B   ## background: no credential
* ─(outbox)→ Kafka PLAINTEXT ─→ consumers (no ACLs)

All pods share SA=default. 0 NetworkPolicies. No mTLS.
```

## Credential legend

| Edge style | Meaning |
|---|---|
| Fwd JWT | Propagates inbound Authorization if present |
| Minted CC | Keycloak client_credentials (shared `impilo-backend` for BFF/pct) |
| None | Unauthenticated HTTP |
| Trust headers | Context only — not authentication |
