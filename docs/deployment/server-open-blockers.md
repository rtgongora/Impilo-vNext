# Impilo vNext — Open Blockers

**Date**: 2026-03-18 (updated from 2026-03-17)

---

## Blocker 1: Network Unreachable from Build Environment (CRITICAL)

**Severity**: ❌ BLOCKING
**Impact**: Cannot execute deployment remotely
**Retry Status**: Re-verified 2026-03-18 — **STILL BLOCKED** (identical behavior)

**Description**: The Claude Code sandbox environment's egress proxy (`21.0.0.77:15004`) blocks all outbound connections to `197.221.242.150`. HTTP requests receive `403 host_not_allowed`; SSH connections time out on ports 22 and 7557.

**2026-03-18 Retry Results**:
- 6/6 external endpoints (ports 13020–13025): `HTTP 403, x-deny-reason: host_not_allowed`
- SSH port 22: Connection timed out
- SSH port 7557: Connection timed out
- The "network block resolved" report from engineers likely addressed server-side networking, not the Claude Code egress proxy

**Resolution**: Deploy directly from the server by:
1. SSH into `197.221.242.150:7557` as `rgongora` from a machine with direct network access
2. Clone/pull the repo on the server
3. Run `./scripts/server-deploy.sh full`

**Alternative**: Request Anthropic add `197.221.242.150` to the Claude Code egress proxy allowlist

---

## Blocker 2: Maven Build Requires Internet (MODERATE)

**Severity**: ⚠️ MODERATE
**Impact**: First build requires downloading ~500MB+ of Maven dependencies

**Description**: The Maven build uses `eclipse-temurin:21-jdk-alpine` and downloads dependencies into `vendor/m2/repository` for runtime/offline preparation. Normal connected local builds should use the standard user Maven cache (`~/.m2/repository`). If the target server has limited internet access, the first build will fail unless the vendored cache is pre-warmed.

**Mitigation**:
- Pre-warm the Maven cache: `cd services && mvn dependency:go-offline -Dmaven.repo.local=../vendor/m2/repository`
- Or: populate `vendor/m2/repository` from a machine with internet access and copy it to the server
- The `docker-compose.build.yml` mounts `vendor/m2/repository` as a volume for caching

---

## Blocker 3: Docker Image Pulls Require Internet (MODERATE)

**Severity**: ⚠️ MODERATE
**Impact**: First run requires pulling ~5GB+ of Docker images

**Description**: The runtime requires these images: `postgres:16-alpine`, `redis:7-alpine`, `apache/kafka:3.7.1`, `quay.io/keycloak/keycloak:25.0`, `hapiproject/hapi:v7.4.0`, `minio/minio:latest`, `orthancteam/orthanc:24.8.1`, `openpolicyagent/opa:0.68.0`, `envoyproxy/envoy:v1.31-latest`, `eclipse-temurin:21-jre-alpine`, `node:20-alpine`.

**Mitigation**:
- Pre-pull images: `docker compose -f docker-compose.runtime.yml pull`
- Or: use `docker save`/`docker load` for air-gapped transfer

---

## Blocker 4: Server Disk/Memory Requirements (INFORMATIONAL)

**Severity**: ℹ️ INFORMATIONAL
**Impact**: Deployment will fail if server resources are insufficient

**Minimum Requirements**:
- **RAM**: 16GB minimum (8 Java services × ~512MB + infrastructure)
- **Disk**: 20GB free minimum (Docker images + Maven cache + database)
- **CPU**: 4+ cores recommended

**Check**: Run `./scripts/server-deploy.sh discover` to verify
