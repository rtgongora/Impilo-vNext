# Impilo vNext — Server Deployment Report

**Date**: 2026-03-17
**Target Server**: 197.221.242.150:7557 (SSH)
**Deployer**: Claude Code (Principal DevOps Engineer)
**Branch**: `claude/review-project-manifest-jb5O0`

---

## Server Discovery

### Connection Status
- **SSH to 197.221.242.150:7557**: ❌ BLOCKED — network proxy blocks outbound connections to this host
- **Root cause**: The sandbox environment's network proxy returns `Host not allowed` for the target IP, and direct TCP connections time out on all tested ports (22, 7557)
- **Attempted**: SSH via sshpass, curl, nc (netcat) — all connections timed out or were proxy-blocked

### What Was Done Instead
Since direct server access was blocked, a comprehensive deployment preparation was completed:
1. Full repository audit and inspection
2. Build infrastructure fixes
3. Deployment scripts and configuration files created
4. Deployment documentation and runbook prepared
5. All changes committed and pushed for server-side execution

---

## Repository Audit Findings

### Architecture
- **68+ microservices** (Java 21 / Spring Boot 3.3.6)
- **1 One UI Shell** (Next.js 14.2.x / React 18)
- **1 Experience BFF** (Java/Spring Boot)
- **Infrastructure**: PostgreSQL 16, Redis 7, Kafka 3.7.1 (KRaft), Keycloak 25, HAPI FHIR 7.4, MinIO, OPA, Envoy 1.31
- **Multi-module Maven build** with parent POM at `services/pom.xml`
- **30 Dockerfiles** across services

### Deployment Stack
- `docker-compose.yml` — local dev infrastructure only
- `docker-compose.build.yml` — Maven + UI build phase
- `docker-compose.runtime.yml` — full runtime with 8 core services + infrastructure + edge + UI
- `scripts/dev-runtime.sh` — dev convenience wrapper

### Data Centre Sandbox Posture

The data centre sandbox is not a single-server Docker Compose deployment. Compose remains useful for local development, bootstrap, and narrow runtime validation only.

Authoritative data-centre guidance now lives in:

- `docs/deployment/data-centre-sandbox-deployment.md`
- `docs/deployment/service-classification-matrix.md`
- `docs/acceptance/data-centre-enforcement-gates.md`

Any DevOps change that affects deployment, Helm, Kubernetes, gateway/security, service persistence, eventing, observability, or CI/CD must either update those documents or state that they were reviewed and no update was required.

### Core Runtime Services (from docker-compose.runtime.yml)
| Service | Port | Type |
|---------|------|------|
| TSHEPO (Trust & Governance) | 8081 | Java/Spring Boot |
| VITO (Client Registry) | 8082 | Java/Spring Boot |
| VARAPI (Provider Registry) | 8083 | Java/Spring Boot |
| TUSO (Facility Registry) | 8084 | Java/Spring Boot |
| ZIBO (Terminology) | 8085 | Java/Spring Boot |
| PCT (Patient Care) | 8088 | Java/Spring Boot |
| OROS (Orders & Results) | 8089 | Java/Spring Boot |
| Experience BFF | 8160 | Java/Spring Boot |
| One UI Shell | 3000 | Next.js |
| Envoy Gateway | 10000 | Envoy Proxy |
| Keycloak | 8080 | Identity |
| HAPI FHIR | 8090 | FHIR Server |
| OPA | 8181 | Policy Engine |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache |
| Kafka | 9092 | Event Bus |
| MinIO | 9000/9001 | Object Storage |

---

## Issues Found and Fixed

### 1. Experience BFF Dockerfile Build Context Mismatch
**Problem**: `docker-compose.runtime.yml` set build context to `./services/experience-bff` but the Dockerfile references paths relative to workspace root (`services/pom.xml`, `libs/`).
**Fix**: Changed build context to `.` (project root) and dockerfile path to `services/experience-bff/Dockerfile`.
**File**: `docker-compose.runtime.yml:413-415`

### 2. Missing Server Deployment Script
**Problem**: Only `scripts/dev-runtime.sh` existed, which is a dev convenience wrapper.
**Fix**: Created `scripts/server-deploy.sh` with full deployment lifecycle: discover → prereqs → build → up → health.
**File**: `scripts/server-deploy.sh` (new)

### 3. Missing Server Environment File
**Problem**: Only `.env.example` existed with placeholder values.
**Fix**: Created `.env.server` with production-appropriate values for the target server.
**File**: `.env.server` (new)

### 4. Keycloak Realm Missing Server Redirect URIs
**Problem**: Keycloak realm only had localhost redirect URIs, which would block OIDC flows from the server IP.
**Fix**: Added `http://197.221.242.150:*` redirect URIs and web origins for all clients (`experience-ui`, `one-ui-shell`, `citizen-portal` — OIDC identifiers for the same web origin policy, not separate UX products).
**File**: `tools/auth/impilo-realm.json`

---

## Deployment Verdict

### ⚠️ PARTIAL — Prepared but Not Executed

**Reason**: Network connectivity to target server (197.221.242.150:7557) is blocked from this environment. All deployment artifacts are ready and pushed to the repository.

### To Complete Deployment on the Server

SSH into the server and run:
```bash
# Clone/update repo
cd ~ && git clone <repo-url> Impilo-vNext || cd ~/Impilo-vNext && git pull
cd ~/Impilo-vNext
git checkout claude/review-project-manifest-jb5O0

# Option A: Using server-deploy.sh (standalone, installs prereqs)
./scripts/server-deploy.sh full

# Option B: Using platformctl.sh (canonical orchestration, requires Docker pre-installed)
./scripts/runtime/platformctl.sh build
./scripts/runtime/platformctl.sh up server
./scripts/runtime/platformctl.sh bootstrap
./scripts/runtime/platformctl.sh verify server
./scripts/runtime/platformctl.sh smoke

# Step by step with server-deploy.sh:
./scripts/server-deploy.sh discover
./scripts/server-deploy.sh prereqs
./scripts/server-deploy.sh build
./scripts/server-deploy.sh up
./scripts/server-deploy.sh health
```

### Deployment Profiles

| Profile | Script | Services |
|---------|--------|----------|
| `server` | `platformctl.sh up server` | Core platform (infra + edge + Ring 0-1 + PCT/OROS + BFF/UI) |
| `lite` | `platformctl.sh up lite` | Same as server but with dev credentials |
| `full` | `platformctl.sh up full` | All 68+ services |
| `integration` | `platformctl.sh up integration` | Full + observability (Prometheus, Grafana, Jaeger) |
