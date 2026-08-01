# Threat model and confirmed root causes — Checkpoint 1

## Confirmed root causes (evidence-backed)

1. **Envoy removed from the public path** — Traefik IngressRoutes target BFF/UI/Keycloak directly; deployed Envoy ConfigMap is a bare pass-through with `extAuthz.enabled=false`. Doctrine "Envoy → TSHEPO before any service" is therefore unrealized.
2. **Estate-wide OAuth disable flag** — `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` on 96 services collapses resource-server enforcement to permitAll, leaving BFF as the primary (and incomplete) gate.
3. **No unique workload identity** — shared `default` ServiceAccount + shared `impilo-backend` client credentials; background/domain hops often send no token.
4. **Trust headers treated as ambient context** — on the live path only `X-Actor-ID` is overridden; `X-Assurance-Level` and `X-Provider-ID` remain client-influenced.
5. **Consent enforcement path defects** — PDP consent client POST≠GET; `/fhir` bypasses FHIR gateway; BFF serves clinical data with display-only consent.
6. **Recovery-code policy defect** — recovery codes are ALTERNATIVE AAL2 factors, granting ordinary workforce authority (violates programme Checkpoint 4).
7. **OPA and work-context binding dormant** — `opaMode=OFF`, `TSHEPO_WORK_CONTEXT_MODE=SHADOW`, boundary rules seeded inactive.
8. **Kafka and east-west openness** — PLAINTEXT broker, 0 NetworkPolicies, no mTLS → lateral movement unconstrained once inside the namespace.

## Threat scenarios (selected)

| ID | Threat | Status |
|---|---|---|
| T1 | Spoof `X-Assurance-Level` / `X-Provider-ID` on BFF→service path | **Open** — BYPASSABLE on preview path |
| T2 | Direct pod→pod call to domain service without JWT | **Open** — OAuth disabled on most services |
| T3 | Mint arbitrary work-context token via identity service | **Open** — identity OAuth disabled; presence-only anchor check |
| T4 | Use recovery code as routine AAL2 / break-glass step-up | **Open** — BYPASSABLE by design of current realm flow |
| T5 | Read SHR FHIR without consent evaluation | **Open** — BUTANO path has no consent |
| T6 | Forge trust headers from outside | **Mitigated at BFF session** for Actor-ID; other headers not regenerated |
| T7 | Kafka topic injection / eavesdropping | **Open** — PLAINTEXT, no ACLs |
| T8 | Shared backend client credential theft → broad service call | **Open** — shared `impilo-backend` |
| T9 | PDP/Keycloak outage fail-open | **Mitigated in Envoy source** (`failure_mode_allow=false`) but Envoy off-path; BFF fails closed without decoder |
| T10 | Audit gap hiding unauthorized access | **Partial** — PDP pairs ENFORCED when PDP runs; BFF audit best-effort |

## Gate status (programme §1)

> No enforcement or migration starts until all high-risk flows have evidence and every bypass has identified legitimate consumers.

| Gate item | Status |
|---|---|
| High-risk flows evidenced | **Met for inventory** (this checkpoint) |
| Every bypass has identified legitimate consumers | **PARTIAL** — inventory exists; consumer justification not yet signed per bypass |
| Enforcement/migration | **BLOCKED** until PO reviews this checkpoint and authorizes Checkpoint 2+ |
