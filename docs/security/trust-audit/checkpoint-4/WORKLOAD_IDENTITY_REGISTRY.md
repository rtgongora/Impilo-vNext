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
| Deployment | 114 |
| Job | 14 |
| CronJob | 2 |
| StatefulSet | 0 |
| **Total nodes** | **130** |

130, not the 129 of the Checkpoint 1 east–west graph. The delta is real cluster churn, not a
counting error: `opa` was deployed after Checkpoint 1 was captured (OPA shadow), and the
generated `estate-health-watch` / `postgres-backup` Job instances roll on their CronJob schedule,
so the exact Job names differ between any two captures. The gate fails if a live workload has no
row, if a row names a workload that no longer exists, or if the registry ever carries secret
material — all three failure modes were proven RED by breaking them.

Consequence worth knowing before reading a gate failure as a defect: `estate-health-watch` fires
every 10 minutes and its Job names carry the schedule minute, so the gate has a ~10-minute
freshness window against ephemeral Jobs. A `missing`/`stale` pair that differs only in an
`estate-health-watch-<n>` / `postgres-backup-<n>` suffix is CronJob rollover, not drift — regenerate
and re-run. Nothing else in the registry moves when it happens: every derived value below was
byte-identical across two captures ten minutes apart.

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
| ServiceAccount `default` | **125 / 130** (the other 5 are 4 `estate-health-watch` jobs + `opa`) |
| SA token automounted | **128 / 130** (only `keycloak` and `opa` set `automountServiceAccountToken: false`) |
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
| `INFRASTRUCTURE` | 13 |
| `EDGE_COMPOSITION` | 3 |

## Derived fields

Three fields were previously `UNKNOWN` on every row. They are now derived by the generator, and
each carries a sibling evidence field naming *how* — a derived value with no stated derivation is
indistinguishable from a guess.

### `owner` — plane/domain ownership, 130 / 130 resolved

`owner.plane` and `owner.domain` are the canonical plane/domain pair the workload belongs to.
`UNKNOWN` on 0 rows.

| `owner.source` | Rows | Meaning |
|---|---|---|
| `SERVICES_REGISTRY` | 100 | `primary_plane` + `domain` from `docs/registry/services-registry.yaml` |
| `DERIVED_FROM_PARENT_WORKLOAD:<parent>` | 16 | A generated Job (`postgres-backup-29757570`, `keycloak-h2-export-mfa-…`) inherits the parent workload it operates on; the parent is named in the value |
| `FULL_BOOT_SERVICE_CLASSIFICATION` | 9 | `plane` + `domain` from `config/full-boot-service-classification.yml` (infrastructure and frontend rows the services registry does not carry) |
| `INFRA_OWNER_MAP` | 5 | `livekit`, `livekit-egress`, `orthanc`, `public-website`, `estate-health-watch` — in neither registry; each carries a named artefact in `owner.source_evidence` (a peer classification row, or the manifest that deploys it) |

Plane distribution: integration 38, clinical 22, trust 20, enterprise 16, experience 14, data 12,
registry 8.

### `owner.team` — UNKNOWN on all 130, and that is the finding

`owner.team` is `UNKNOWN` with `owner.team_source: NO_TEAM_SOURCE_IN_REPO` on every row. **There
is no human-team ownership source anywhere in this repository.** Searched: `CODEOWNERS` at every
path (none exists, including under `.github/`); `docs/registry/services-registry.yaml`, which sets
`owner_team_default: TBD` and `owner_team: TBD` on all 104 service rows; the k8s object labels in
the live namespace, which carry only `app.kubernetes.io/*` and `impilo.io/environment`;
`config/full-boot-service-classification.yml`, which has no team field at all.

Plane + domain is emitted instead because it is a real, checkable ownership statement. `TBD` was
not, and repeating `TBD` into 130 rows would have dressed a missing input as a filled field.

### `human_delegation_required` — 130 / 130 decided, 1 UNKNOWN

Whether the workload ever acts on behalf of a human, as opposed to only acting as itself.

| Value | Rows |
|---|---|
| `true` | 68 |
| `false` | 61 |
| `UNKNOWN` | 1 |

| `human_delegation_evidence` | Rows | Derivation |
|---|---|---|
| `READS_ACTOR_CONTEXT` | 68 | The workload's own source reads the acting human's trust context — `CompanionHeaders`/`TrustHeaders` actor constants, a quoted `X-Actor-*` / `X-Purpose-Of-Use` header, `TrustContextHolder`, `@PreAuthorize`, `SecurityContextHolder`, or `@AuthenticationPrincipal` |
| `NO_ACTOR_CONTEXT_FOUND` | 32 | Same grep over the same tree, no hit. These services never read who they are acting for |
| `KIND_BATCH` | 16 | Job/CronJob — no human session exists by construction |
| `INFRASTRUCTURE` | 13 | envoy, hapi-fhir, kafka, keycloak, livekit, livekit-egress, matcher-engine, minio, ndila-martin, opa, orthanc, postgres, redis |
| `NO_SOURCE_TREE_IN_REPO` | 1 | `public-website` — see below |

The 32 `NO_ACTOR_CONTEXT_FOUND` services are a finding in their own right, not a clean bill: a
service that never reads the acting human cannot enforce any of the ten access dimensions that
depend on one. It is the correct input for cohorting — these are the workloads whose calls are
genuinely self-directed and can therefore move to a workload credential without a delegation
story — but each remains subject to Checkpoint 7's caller enumeration.

### `credential.audience_current` — 130 / 130 = `NONE_CONFIGURED`

`NONE_CONFIGURED` and `UNKNOWN` are deliberately different statements. `NONE_CONFIGURED` means
*inspected, and absent*. `UNKNOWN` means *could not inspect*. No row is `UNKNOWN`.

Two independent inspections, both by the generator:

1. **Live container env** — every `Deployment`/`Job`/`CronJob` container and initContainer in the
   namespace. No `*_AUDIENCE` variable exists on any workload; no
   `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES` is set anywhere. Five workloads set
   `…_JWT_ISSUER_URI` and two set `…_JWK_SET_URI`; issuer and signing key are configured,
   audience is not.
2. **Keycloak realm import** (`deploy/helm/impilo-vnext/files/realm-impilo-preview.json`) — all 11
   clients and every client scope. **Not one `oidc-audience-mapper` exists in the realm.** Keycloak
   only mints an `aud` claim when such a mapper is attached, so no token issued by this realm
   carries an audience for a resource server to validate.

| `credential.audience_evidence` | Rows | Meaning |
|---|---|---|
| `NO_AUDIENCE_ENV_AND_NO_AUDIENCE_MAPPER_IN_REALM` | 128 | No audience env, and the realm mints none for anyone |
| `NO_AUDIENCE_ENV_AND_NO_REALM_AUDIENCE_MAPPER:impilo-backend` | 2 | `experience-bff` and `pct-service` name a Keycloak client; that client has no audience mapper |

This is the mechanism behind the CP1 matrix's `JWT audience validation: ABSENT`: audience
validation is absent because there is no audience to validate. A token minted for any client is
accepted by every resource server in the estate.

## Retained UNKNOWNs

One field on one row. It is recorded as `UNKNOWN` rather than guessed.

| Field | Rows | Why it is UNKNOWN |
|---|---|---|
| `human_delegation_required` | 1 (`public-website`) | The workload has no source tree in this repository. It is an image-only Deployment (`impilo/public-website@sha256:…`) applied from `deploy/tls/mohcc-gov/public-website.yaml`; there is no `ui/public-website` and no build path for it in `scripts/`. With no source there is nothing to grep, and "it is a public site so presumably anonymous" is an assumption, not evidence. Its `owner` (`experience/ui-workspace`) and `audience_current` (`NONE_CONFIGURED`) are both resolved. |

`owner.team` is `UNKNOWN` on all 130 rows, but for a different reason and it is recorded above as
its own finding: the input does not exist in the repository at all.

## Destination evidence

`destinations.measured_from_env` is derived from in-cluster service URLs in each workload's own
environment, and is labelled `MEASURED_CONFIG_ONLY`. **Configuration is not traffic.** A workload
configured to call a peer may never call it, and a call made by a hardcoded URL or by a Kafka
topic will not appear here. `declared_consumes_from` / `declared_exposes_to` are copied from the
services registry and are declarations, not evidence.

30 rows have no measured destination. That is a finding, not a completion: it means their callees
are not expressed as env URLs, so Checkpoint 7's caller enumeration cannot rely on this field
alone.

## What this registry does not establish

- It does **not** prove any workload's caller set. Every Checkpoint 1 bypass row still reads
  `Known callers: BFF + peers (unenumerated)` with `Consumer evidence: PARTIAL`. Closing that is
  Checkpoint 7's gate, and no cohort enters enforcement until it is closed.
- It does **not** change runtime behaviour. Nothing here is deployed; `migration_cohort` is
  `UNASSIGNED` on all 129 rows until a cohort is selected from evidence.
