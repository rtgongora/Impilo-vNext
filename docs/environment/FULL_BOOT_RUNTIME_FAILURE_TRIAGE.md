# Full Boot Runtime Failure Triage

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Namespace:** `impilo-full-preview` (failed Helm rev 3; slice `impilo-preview` preserved)  
**Captured:** 2026-06-02 after first authorized full boot attempt  
**Status:** `FULL_BOOT_FAIL` — 7/22 deployments Available; 15/22 not Ready  

Do **not** treat deployment existence as success. Readiness and pod `Ready` state are required.

---

## Summary by failure group

| Group | Symptom | Root cause | Fix in this batch |
|-------|---------|------------|-------------------|
| Chart integrity | First apply rendered only 4 templates / 3 deploys | Truncated `deploy/helm/impilo-vnext` on disk | `scripts/deploy/check-helm-chart-integrity.sh` + guard in deploy script |
| Postgres init/auth | `postgres-init-databases` Job: `fe_sendauth: no password supplied` | `psql` without `PGPASSWORD` | Job sets `PGPASSWORD`, `PGHOST`, `PGPORT`; idempotent `CREATE DATABASE` |
| Shared microservice DB env | Many Java CrashLoop: `fe_sendauth` | Helm only set `SPRING_DATASOURCE_URL` to shared `impilo` DB; apps use `POSTGRES_*` + per-service DB | `microservice.yaml` + `values-full-preview.yaml` `database:` per service |
| Flyway / SQL | Vito V027, tshepo duplicate V012, tuso `gin_trgm_ops`, pct ordering | Invalid PG syntax, duplicate version, missing extension, dirty/partial DB | V027 constraint fix; V012→V013 rename; `pg_trgm` in init job; clean namespace for retest |
| Kafka single-node | CrashLoop: broker cannot register with controller (timeout `kafka:9093`) | Combined KRaft self-connect via Service DNS race | `KAFKA_CONTROLLER_QUORUM_VOTERS=1@127.0.0.1:9093`, longer startup/liveness |
| Keycloak probes | Running, not Ready; probe 404 on 8080 | KC 25 health on management port 9000 | Probes on `:9000` `/health/ready`, `KC_HEALTH_ENABLED`, `KC_HTTP_MANAGEMENT_PORT` |
| HAPI DB/config | H2 driver + postgres URL | Missing `driverClassName` / dialect; wrong username source | `spring.datasource.driverClassName`, Hibernate dialect, secret `POSTGRES_USER` |
| BFF Kafka autoconfigure | BFF CrashLoop when Kafka down | Kafka autoconfig before infra ready | BFF `SPRING_AUTOCONFIGURE_EXCLUDE` in values (Kafka + OAuth2 RS for preview) |
| Microservice Kafka wiring | varapi: `KafkaTemplate` bean missing | Template excluded Kafka for all microservices | Enable Kafka env when `kafka.enabled` globally unless service opts out |
| Smoke / completeness | False pass on deploy existence | Scripts counted Deployments/pods without Ready | Smoke + `check-full-boot-runtime-completeness.sh` require `readyReplicas` |

---

## Remaining blockers (not fully closed in chart-only batch)

| Service | Issue | Next action |
|---------|-------|-------------|
| butano-service | `NoUniqueBeanDefinitionException` for `FhirContext` | Application config: single primary `FhirContext` bean |
| fhir-gateway-service | `ClassNotFoundException: BearerTokenResolver` | Add/fix Spring Security OAuth2 resource server dependency |
| pct-service | V002: `pct_encounters` does not exist | Retest on clean DB after init; if persists, fix Flyway schema/search_path |
| Domain images | Flyway fixes in **vito** / **tshepo-authz** require **image rebuild** before second deploy | `FULL_BOOT_IMAGE_TAG=preview` rebuild or `FULL_BOOT_SKIP_BUILD=0` |

---

## Representative logs (first attempt)

### Postgres init

```
psql: error: connection to server at "postgres" ... fe_sendauth: no password supplied
```

### Kafka

```
Unable to register the broker because the RPC got timed out before it could be sent.
Disconnecting from node 1 due to socket connection setup timeout.
ERROR [BrokerLifecycleManager id=1] Shutting down because we were unable to register with the controller quorum.
```

### Keycloak

Readiness probe HTTP 404 on port 8080 (`/health/ready`); KC 25 serves health on management port **9000**.

### HAPI

```
Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://postgres:5432/hapi
```

### Vito (after DB auth worked on partial cluster)

```
Script V027__external_registration_identifiers.sql failed
ERROR: syntax error at or near "NOT"  -- ADD CONSTRAINT IF NOT EXISTS (invalid in PostgreSQL)
```

### Tshepo authz

```
Found more than one migration with version 012
```

### Tuso

```
ERROR: operator class "gin_trgm_ops" does not exist for access method "gin"
```

### Varapi

```
Parameter 1 of constructor in VarapiOutboxPublisher required a bean of type KafkaTemplate
```

---

## Verification before second deploy

1. `bash scripts/deploy/check-helm-chart-integrity.sh` → `CHART_INTEGRITY_PASS`
2. `export FULL_BOOT_IMAGE_TAG=preview`
3. `bash scripts/deploy/full-boot-preview-deploy.sh --preflight`
4. `bash scripts/deploy/full-boot-preview-deploy.sh --dry-run`
5. `bash scripts/guard/check-full-boot-runtime-completeness.sh` → expect `FULL_BOOT_PARTIAL` (no deploy yet)
6. User phrase: **`AUTHORIZE FULL BOOT PREVIEW DEPLOY`**
7. Command: `export FULL_BOOT_IMAGE_TAG=preview && bash scripts/deploy/full-boot-preview-deploy.sh`

**Do not** modify `impilo-preview`. **Do not** enable public ingress for full boot.
