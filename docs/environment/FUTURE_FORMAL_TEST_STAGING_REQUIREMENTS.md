# Future Formal Test/Staging Requirements

**Not implemented on the current VM.**

The VM at `41.57.127.235` is for:

- Remote Development Workspace
- Dev Preview Sandbox only

Formal production-like Test/Staging requires **separate infrastructure**:

## Requirements (Future)

- Separate VM set or multi-node cluster (RKE2, full K8s, OpenShift)
- HA PostgreSQL, Kafka, Redis
- Ingress + TLS + DNS
- Secrets management (Vault, Sealed Secrets)
- Private image registry (GHCR/Harbor)
- Keycloak HA / production auth posture
- Network policies, quotas, RBAC
- Prometheus/Grafana/Loki full observability
- Backup/restore validation
- Load, security, and UAT gates
- Release candidate promotion flow

## Promotion Path (Conceptual)

```
dev preview (this VM) → formal staging cluster → production
```

Do not conflate preview sandbox acceptance with formal staging sign-off.
