# Integration tests — selective, infrastructure-aware execution

The estate deliberately has **no global maven-failsafe binding**. Ordinary builds run only
the fast Surefire unit tests (`*Test`). The 16 integration tests (`*IT`) require real
infrastructure (PostgreSQL, Docker/Testcontainers, WireMock, or a live dependency) and are
opted in per module via `it-*` Maven profiles. `mvn test` and `mvn verify` **never** scan or
run `*IT` — only an explicit `-P<profile>` does.

Failsafe is *managed* (version + `integration-test`/`verify` executions) in the parent
`services/pom.xml` `pluginManagement` but is **not activated** there; each affected module
inherits it only inside its `it-*` profile.

## Inventory (16 integration tests, 3 categories)

| Category | Profile | Module → IT | Infra |
|---|---|---|---|
| PostgreSQL-only | `it-postgres` | costing-engine-service → `CostaGoldenContractIT` | real PG (`text[]`/`enum` DDL) |
| | | share-slip-service → `ShareSlipGoldenContractIT` | real PG (PG-specific DDL) |
| | | tshepo-audit-service → `TshepoAuditGoldenContractIT` | real PG (`TIMESTAMPTZ`) |
| | | mvumo-service → `MvumoCrossServiceFlowIT` | real PG (`RETURNING`/`jsonb`/`TIMESTAMPTZ`) |
| Runtime-proof | `it-runtime-proof` | data-access-governance-service → `PermitEnforcementRuntimeProofIT` | external PG (`-Dit.pg.url`) |
| | | identity-assurance-service → `AssuranceWorkflowRuntimeProofIT` | external PG |
| | | tshepo-authz-service → `GdhcnReadinessRuntimeProofIT`, `StepUpVerificationIT`, `TrustAuthorityRegistryRuntimeProofIT` | external PG |
| | | tshepo-keys-service → `SigningRuntimeProofIT` | external PG |
| | | mvumo-service → `TshepoConsentDevInstanceIT` | live TSHEPO (`MVUMO_IT_TSHEPO_BASE`) |
| Testcontainers/WireMock | `it-containers` | search-service → `SearchPgvectorAnnIT` | Docker: PG + pgvector |
| | | clinical-knowledge-platform-service → `ClinicalKnowledgePathwayApiIT` | Docker: PG |
| | | experience-bff → `GoldenContractIT`, `ImagingExperienceWireMockIT`, `MobileProviderTier2ResponseShapeIT` | Docker: Redis (+ in-process WireMock) |

The PostgreSQL-only tests keep PostgreSQL semantics (`text[]`, `enum`, `TIMESTAMPTZ`,
`RETURNING`, `jsonb`) and are **not** diluted to run on H2 — they run the real Flyway
migrations against a real database.

## Execution modes

- **Mode 1 — Testcontainers** (`it-containers`): the test starts and controls its own
  isolated database/container (deterministic image, migrations applied automatically, clean
  teardown). Self-skips cleanly via `@Testcontainers(disabledWithoutDocker=true)` /
  `DockerOrExternalPostgresCondition` when Docker is unavailable. For `SearchPgvectorAnnIT`
  the image is `pgvector/pgvector:pg16`.
- **Mode 2 — External PostgreSQL** (`it-postgres`, `it-runtime-proof`): the test points its
  datasource at a Postgres supplied via system properties, no dependency on a developer's
  existing database. Credentials are injected, never hardcoded:
  - `-Dit.pg.url=jdbc:postgresql://HOST:PORT/DB`
  - `-Dit.pg.user=USER`
  - `-Dit.pg.pass=PASS`
  Runtime-proof / PostgreSQL-only ITs self-skip when `it.pg.url` is absent.
  `TshepoConsentDevInstanceIT` additionally needs `MVUMO_IT_TSHEPO_BASE` (a live TSHEPO).

## Local developer commands

```bash
# 1. Normal fast tests (Surefire only — never runs *IT). This is the default path.
cd services && mvn test

# 2. All selectively-enabled integration tests (start a throwaway Postgres first, below).
cd services && mvn verify -Pit-postgres,it-runtime-proof,it-containers \
  -Dit.pg.url=jdbc:postgresql://127.0.0.1:55432/it_db -Dit.pg.user=it -Dit.pg.pass=it

# 3. Testcontainers-only integration tests (needs a working Docker engine).
cd services && mvn verify -Pit-containers

# 4. External-PostgreSQL runtime-proof tests.
cd services && mvn verify -Pit-runtime-proof \
  -Dit.pg.url=jdbc:postgresql://127.0.0.1:55432/it_db -Dit.pg.user=it -Dit.pg.pass=it

# 5. A single integration-test class (via Surefire's by-name override; -Dit.pg.url as needed).
cd services && mvn test -pl tshepo-keys-service -Dtest=SigningRuntimeProofIT \
  -Dit.pg.url=jdbc:postgresql://127.0.0.1:55432/it_db -Dit.pg.user=it -Dit.pg.pass=it \
  -Dsurefire.failIfNoSpecifiedTests=false

# 6. A single affected module (Failsafe).
cd services && mvn verify -pl costing-engine-service -Pit-postgres \
  -Dit.pg.url=jdbc:postgresql://127.0.0.1:55432/it_db -Dit.pg.user=it -Dit.pg.pass=it
```

### Starting a throwaway Postgres for Mode 2

```bash
docker run -d --name it-pg -e POSTGRES_USER=it -e POSTGRES_PASSWORD=it -e POSTGRES_DB=it_db \
  -p 55432:5432 pgvector/pgvector:pg16
# ... run the commands above ...
docker rm -f it-pg
```

> **Environment note.** Some hosts run a Docker engine whose minimum API version the bundled
> Testcontainers docker-java client cannot negotiate; there, `it-containers` tests self-skip.
> Use Mode 2 (`-Dit.pg.url`, a CLI-started Postgres) to exercise the PostgreSQL-only and
> runtime-proof tests locally, and rely on Docker-capable CI for the Testcontainers ones.

## Guard — no silently-skipped green

A CI integration job must not pass with the tests skipped. After a Failsafe run, assert the
expected classes actually executed:

```bash
bash scripts/test/assert-integration-tests-ran.sh --category postgres
bash scripts/test/assert-integration-tests-ran.sh --category runtime-proof
bash scripts/test/assert-integration-tests-ran.sh --category containers
# or explicit classes:
bash scripts/test/assert-integration-tests-ran.sh SigningRuntimeProofIT MvumoCrossServiceFlowIT
```

It parses `services/*/target/failsafe-reports/*.xml` and fails if any expected class is
missing, all-skipped, or failed (handling the `GoldenContractSuite` `@Nested` reports the
golden ITs emit).

## CI

`.github/workflows/integration-tests.yml` runs two jobs after `mvn install -DskipTests`:

- **integration-postgres** — provisions a `pgvector/pgvector:pg16` service and runs
  `-Pit-postgres,it-runtime-proof` with `it.pg.url`, then the guard.
- **integration-containers** — uses the runner's Docker engine and runs `-Pit-containers`,
  then the guard.

Reports upload as artifacts. Neither job deploys anything, touches Kubernetes, or uses a
production/shared database.
