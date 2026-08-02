# Checkpoint 7 cohort gate — caller enumeration

**Captured:** 2026-08-02 · **Namespace:** `impilo-full-preview`
**Tool:** `scripts/security/build-service-caller-graph.py`
**Data:** [`reports/estate/service-caller-graph.json`](../../../../reports/estate/service-caller-graph.json)

## Why this gate exists

Every row of the Checkpoint 1 bypass inventory reads `Known callers: BFF + peers (unenumerated)`
with `Consumer evidence: PARTIAL`. Enabling authentication on a service whose callers are unknown
is how an estate discovers a caller by breaking it.

**116 services, 363 edges**, from three separately-tracked sources:

| Source | Meaning |
|---|---|
| `SOURCE_DEFAULT` | a `${X_BASE_URL:http://localhost:PORT}` default in the caller's own config |
| `SOURCE_DIRECT` | a literal `http://<service>:<port>` in the caller's source |
| `RUNTIME_ENV` | a URL in the caller's **live container environment** |

Absence of an edge is **not** proof of no caller: Kafka topics, runtime-built URLs and
gateway-mediated calls are invisible to this method. `reporting-service` (3 `@KafkaListener`) and
`analytics-pipeline-service` (1) have inbound edges this graph cannot see.

## The finding that decides cohort selection

`experience-bff` declares **98 downstream targets**. Only **16 are reachable live.**
The other **80 have no URL in the live environment**, so they fall back to the
`http://localhost:PORT` default — which, inside the BFF pod, is the BFF itself.

Not inferred. Observed, right now:

```
error on GET request for "http://localhost:8122/internal/v1/community/social/feed"
error on GET request for "http://localhost:8122/internal/v1/community/social/groups"
```

Port 8122 is `community-service`. The BFF is calling itself and failing.

### Consequence

The 43 services that passed the naive filter — single-caller, non-PHI, no human delegation
(`ai-model-registry-service`, `schema-registry-service`, `product-registry-service`,
`developer-portal-service`, …) — are **all in the unreachable 80**. Nothing actually calls them.

**Enforcing authentication there would succeed trivially and prove nothing.** A green result
would be actively misleading, in exactly the way enforcing against a `STALE` image would be. A
cohort has to be a service that is genuinely called, or the enforcement is theatre.

## The 16 live-reachable services

| Service | Plane | Inbound callers | Provenance | Human delegation |
|---|---|---|---|---|
| `workforce-governance-service` | enterprise | **2** | STALE | true |
| `guidance-service` | clinical | **1** | STALE | true |
| `scheduling-service` | clinical | 1 | STALE | true |
| `booking-service` | experience | 1 | STALE | true |
| `abis-service` | trust | 1 | STALE | true |
| `identity-assurance-service` | trust | 1 | STALE | true |
| `fhir-gateway-service` | clinical | 2 | STALE | true |
| `workforce-governance-service` | enterprise | 2 | STALE | true |
| `vashandi-workforce-service` | enterprise | 3 | STALE | true |
| `tshepo-consent-service` | trust | 4 | STALE | true |
| `vito-service` | registry | 4 | STALE | true |
| `notification-service` | integration | 6 | STALE | false |
| `varapi-service` | registry | 9 | STALE | true |
| `tuso-service` | registry | 11 | STALE | true |
| `tshepo-authz-service` | trust | 5 | **IN_BRANCH** | true |
| `keycloak` | trust | 103 | IN_BRANCH | false |

**There is no risk-free first cohort.** The services that are safe are not called; the services
that are called are trust-, clinical- or registry-critical. That is the honest state of the
estate, and it is a finding rather than an obstacle to route around.

## Recommendation

**`workforce-governance-service`** — the least-bad genuine cohort:

- **Live-reachable** (`RUNTIME_ENV` edge from the BFF), so enforcement is actually exercised
- **2 inbound callers**, both enumerated — the smallest caller set among reachable services
- **Non-PHI**: workforce governance, not patient data
- **enterprise plane** — outside the clinical and trust critical paths

Preconditions before the flip, each re-verified at enforcement time rather than inherited:

1. **Rebuild to `IN_BRANCH`** — it is currently `STALE`. Targeted rebuild of this service only;
   no fullboot.
2. Confirm the 2 callers against live traffic, not just the graph.
3. Unique ServiceAccount + audience-restricted token (the SA already exists, unused).
4. Zero unexplained OPA-shadow denials for its paths.
5. Recorded rollback digest.

`guidance-service` has only 1 caller but sits on the clinical plane; it is the fallback if
workforce-governance proves to have unlisted callers.

## Separate defect this uncovered

**80 of the BFF's 98 declared downstream targets are misconfigured in preview** and silently fail
to `localhost`. That is not a trust finding — it is an estate-coherence one, and it means a large
part of the BFF's declared surface is dead in this environment. It needs its own remediation
(populate `values-full-preview-bff-env.generated.yaml`, or remove the dead declarations), and it
should be fixed before any claim that "the estate works" is made from BFF behaviour.

## Reproduce

```bash
python3 scripts/security/build-service-caller-graph.py --output reports/estate/service-caller-graph.json
```
