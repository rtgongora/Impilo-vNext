# Workload identity registry — Checkpoint 4

**Branch:** `claude/tshepo-trust-completion-Yypyl` · **Base:** `dc875e22f`
**Namespace observed:** `impilo-full-preview` · **Captured:** 2026-08-02
**Artefact:** [`workload-identity-registry.yaml`](./workload-identity-registry.yaml)
**Generator:** `scripts/security/generate-workload-identity-registry.py`
**Gate:** `scripts/guard/check-workload-identity-registry.sh`

Doctrine basis: trust-plane doctrine §6 — *"Workloads authenticate as themselves… no shared
estate-wide service token"* — and §2, *"Network position / namespace / internal header ≠ identity."*

## Coverage

| Kind | Count |
|---|---|
| Deployment | 113 |
| Job | 14 |
| CronJob | 2 |
| StatefulSet | 0 |
| **Total nodes** | **129** |

Matches the Checkpoint 1 east–west graph node count exactly. The gate fails if a live workload
has no row, if a row names a workload that no longer exists, or if the registry ever carries
secret material — all three failure modes were proven RED by breaking them.

## Canonical identity

```
urn:impilo:workload:<environment>:<cluster>:<namespace>:<service-account>:<workload>
```

The `<service-account>` segment is the **target** SA — the workload's own name, one identity per
workload. It is deliberately not the current SA: encoding `default` into 125 canonical IDs would
make the shared-identity defect look like the design.

## Measured posture (not intent)

| Field | Measurement |
|---|---|
| ServiceAccount `default` | **125 / 129** (the other 4 are `estate-health-watch` jobs) |
| SA token automounted | **128 / 129** (only `keycloak` sets `automountServiceAccountToken: false`) |
| `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` | **96** |
| Shared Keycloak client observed in env | **2** (`experience-bff`, `pct-service` — both `impilo-backend`) |
| Unique per-workload credential | **0** |
| mTLS | **ABSENT** estate-wide |
| NetworkPolicy | **NOT_ENFORCED_BY_CLUSTER** (see `NETWORK_POLICY_ENFORCEMENT_DEPENDENCY.md`) |

The 96 OAuth-bypass count was derived independently here from live container env, and it
reproduces the Checkpoint 1 `BYPASS_INVENTORY_EXPANDED.md` figure. Two independent measurements
agreeing is the reason to trust it.

### Flow categories

| Category | Count |
|---|---|
| `SYNCHRONOUS_SERVICE` | 98 |
| `BATCH` (Jobs + CronJobs) | 16 |
| `INFRASTRUCTURE` | 12 |
| `EDGE_COMPOSITION` | 3 |

## Retained UNKNOWNs

These are recorded as `UNKNOWN` rather than guessed. Each is a real gap, not a formatting choice.

| Field | Rows | Why it is UNKNOWN |
|---|---|---|
| `owner` | 129 | `docs/registry/services-registry.yaml` carries `owner_team: TBD` estate-wide |
| `human_delegation_required` | 129 | Requires per-endpoint analysis of whether a call acts for a human; not derivable from deployment config |
| `credential.audience_current` | 129 | No audience is set anywhere today — `JWT audience validation` is `ABSENT` in the CP1 matrix |

## Destination evidence

`destinations.measured_from_env` is derived from in-cluster service URLs in each workload's own
environment, and is labelled `MEASURED_CONFIG_ONLY`. **Configuration is not traffic.** A workload
configured to call a peer may never call it, and a call made by a hardcoded URL or by a Kafka
topic will not appear here. `declared_consumes_from` / `declared_exposes_to` are copied from the
services registry and are declarations, not evidence.

29 rows have no measured destination. That is a finding, not a completion: it means their callees
are not expressed as env URLs, so Checkpoint 7's caller enumeration cannot rely on this field
alone.

## What this registry does not establish

- It does **not** prove any workload's caller set. Every Checkpoint 1 bypass row still reads
  `Known callers: BFF + peers (unenumerated)` with `Consumer evidence: PARTIAL`. Closing that is
  Checkpoint 7's gate, and no cohort enters enforcement until it is closed.
- It does **not** change runtime behaviour. Nothing here is deployed; `migration_cohort` is
  `UNASSIGNED` on all 129 rows until a cohort is selected from evidence.
