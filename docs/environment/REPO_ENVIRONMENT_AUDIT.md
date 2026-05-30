# Repository Environment Audit

**Repo:** https://github.com/rtgongora/Impilo-vNext  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**VM path:** `/opt/impilo/repos/Impilo-vNext`

## 1. Services Discovered

60+ Java Spring Boot microservices under `services/`, including:

- **Experience:** `experience-bff` (port 8160)
- **Trust:** `tshepo-service`, `tshepo-authz-service`, identity/consent/audit/keys
- **Registry:** `vito`, `varapi`, `tuso`, `zibo`
- **Clinical:** `pct`, `oros`, `pharmacy`, `inpatient`, `wellness`, `document-service`, etc.
- **Integration:** `integration-hub`, `jobs`, `notification`, `offline-sync`

## 2. Frontend Apps

- **Primary shell:** `ui/one-ui-shell` (canonical orchestration layer, port 3000)
- **Workspaces:** npm workspaces in `ui/package.json` (EHR, portal, ops-console, domain UIs)
- **Mobile:** `apps/mobile` (pnpm workspace, separate from main UI)

## 3. Backend Build

- **Java 21**, Spring Boot 3.3.x
- **Maven** (`services/pom.xml` parent, 93+ `pom.xml` files)
- No Gradle in primary backend path

## 4. Infrastructure Dependencies

PostgreSQL 16, Redis 7, Kafka 3.7, Keycloak 25, Envoy gateway, MinIO, Orthanc — per architecture docs.

## 5. Ports (summary)

See `docs/runbooks/port-allocation.md`. Preview MVP uses 80 (ingress), 8160 (BFF internal), 3000 (UI internal).

## 6. Build Tools Required

| Tool | Reason |
|------|--------|
| Java 21 (Temurin) | Backend services |
| Maven | `services/` multi-module build |
| Node.js 20 LTS | `ui/` npm workspaces |
| npm 10.x | `package-lock.json`, `packageManager: npm@10.8.2` |
| pnpm | Optional — `apps/mobile` only |
| Docker | Image builds for preview |
| k3s + kubectl + Helm | Dev preview sandbox |

## 7. Docker Support

- Per-service Dockerfiles (30+)
- `ui/one-ui-shell/Dockerfile` (build from repo root)
- `docker-compose.yml`, `docker-compose.runtime.yml` — **local dev only**, not DC/preview runtime

## 8. Kubernetes / Helm

- Per-service Helm charts under `services/*/helm/` and `helm/`
- New umbrella preview chart: `deploy/helm/impilo-vnext/`

## 9. Tests

- Backend: JUnit 5, Maven (`mvn test`)
- Frontend: Vitest, Playwright in `ui/one-ui-shell`
- CI: `.github/workflows/ci.yml`

## 10. Deployment Blockers (Preview MVP)

| Blocker | Notes |
|---------|-------|
| Full stack size | 60+ services — deploy MVP slice first |
| Keycloak | Not in initial preview chart (`keycloak.enabled: false`) — auth flows limited |
| Kafka | Not in MVP chart — BFF may log Kafka warnings |
| Downstream services | BFF proxies many services — many routes return upstream errors until expanded |
| GHCR images | Charts default to `ghcr.io/mohcc/impilo/*` — preview uses locally built `impilo/*` tags |

## 11. Recommended Sequences

**Dependencies:** `scripts/dev/install-dependencies.sh`  
**Build:** `scripts/dev/build-all.sh`  
**Test:** `scripts/dev/run-tests.sh`  
**Preview deploy:** `preview-build-images.sh` → `preview-deploy.sh` → `preview-smoke-test.sh`
