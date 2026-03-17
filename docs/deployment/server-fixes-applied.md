# Impilo vNext — Fixes Applied for Server Deployment

**Date**: 2026-03-17

---

## Fix 1: Experience BFF Docker Build Context

**File**: `docker-compose.runtime.yml` (line ~413)

**Problem**: The `experience-bff` service in `docker-compose.runtime.yml` used `context: ./services/experience-bff` as the build context, but the Dockerfile inside that service references parent-level paths:
```dockerfile
COPY services/pom.xml services/pom.xml
COPY libs/tech-companion/pom.xml libs/tech-companion/pom.xml
COPY libs/shared-kernel-java/pom.xml libs/shared-kernel-java/pom.xml
```
These paths are relative to the workspace root, not the service directory.

**Fix**:
```yaml
# Before
build:
  context: ./services/experience-bff
  dockerfile: Dockerfile

# After
build:
  context: .
  dockerfile: services/experience-bff/Dockerfile
```

---

## Fix 2: Server Deployment Script

**File**: `scripts/server-deploy.sh` (NEW)

**Problem**: Only `scripts/dev-runtime.sh` existed, which is a local dev wrapper. No script existed for server deployment with prerequisite checks, phased startup, and health validation.

**Fix**: Created comprehensive `scripts/server-deploy.sh` with commands:
- `discover` — server environment inspection
- `prereqs` — install Docker, Compose, git if missing; set up .env
- `build` — Maven build + UI build + Docker image build
- `up` — phased service startup (infra → keycloak → edge → services → UI)
- `down` — stop all services
- `status` — container health overview
- `logs` — tail service logs
- `health` — HTTP health checks for all services
- `full` — complete deployment pipeline

---

## Fix 3: Server Environment File

**File**: `.env.server` (NEW)

**Problem**: Only `.env.example` existed with placeholder `changeme` values. A server deployment needs production-appropriate credentials.

**Fix**: Created `.env.server` with unique passwords and secrets for all services. The deploy script auto-copies this to `.env` if no `.env` file exists.

---

## Fix 4: Keycloak Realm Redirect URIs

**File**: `tools/auth/impilo-realm.json`

**Problem**: All OIDC client redirect URIs and web origins only included `localhost` entries. When deployed on 197.221.242.150, Keycloak would reject redirect flows from the server's IP.

**Fix**: Added server IP-based redirect URIs and web origins for all three public clients:
- `experience-ui`: Added `http://197.221.242.150:3020/*` and `http://197.221.242.150/*`
- `one-ui-shell`: Added `http://197.221.242.150:3000/*`
- `citizen-portal`: Added `http://197.221.242.150:3003/*`
