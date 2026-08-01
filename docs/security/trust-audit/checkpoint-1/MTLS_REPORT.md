# mTLS report — Checkpoint 1

| Route class | Transport | Classification |
|---|---|---|
| Internet → Traefik | TLS 1.2+ (Let's Encrypt) | **ENFORCED** |
| Traefik → BFF/UI/Keycloak | plaintext inside cluster | **ACTIVE_NOT_ENFORCED** (one-way edge only) |
| Pod → Pod (HTTP ClusterIP) | plaintext | **ABSENT** (no mTLS) |
| Pod → Kafka | PLAINTEXT | **ABSENT** |
| Envoy upstream TLS | n/a (Envoy not on path; no upstream TLS contexts for services) | **ABSENT** |
| cert-manager service mesh | manifests target wrong ingress class/namespaces | **DISCONNECTED** |
| Service mesh (Istio/SPIRE) | not present | **ABSENT** (do not introduce without ADR) |

**Recommendation for later checkpoints:** inventory every listener; introduce mTLS in monitored/permissive mode using existing Envoy/cert capabilities before strict enforcement; do not enable global strict mTLS in this checkpoint.
