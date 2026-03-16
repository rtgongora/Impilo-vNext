# Impilo vNext — Platform Orchestration Acceptance Pack

## Purpose

This document defines what constitutes a successful platform orchestration implementation and provides the evidence checklist for acceptance.

## Acceptance Criteria

### A. Canonical Runtime Manifest

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| A1 | Platform manifest exists | `ops/runtime/platform-manifest.yaml` | Delivered |
| A2 | All runnable components declared | 60+ components across 9 layers | Delivered |
| A3 | Each component has: name, type, layer, port, dependencies, healthcheck | See manifest YAML | Delivered |
| A4 | Profiles defined (dev-lite, dev-full, integration, pilot) | Profile field on each component | Delivered |

### B. Environment Profiles

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| B1 | dev-lite profile exists | `ops/runtime/environments/dev-lite.env` | Delivered |
| B2 | dev-full profile exists | `ops/runtime/environments/dev-full.env` | Delivered |
| B3 | integration profile exists | `ops/runtime/environments/integration.env` | Delivered |
| B4 | pilot profile exists | `ops/runtime/environments/pilot.env` | Delivered |
| B5 | Profile README exists | `ops/runtime/environments/README.md` | Delivered |

### C. Layered Runtime Structure

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| C1 | Infrastructure compose | `ops/runtime/docker-compose.infra.yml` | Delivered |
| C2 | Shared services compose | `ops/runtime/docker-compose.shared.yml` | Delivered |
| C3 | Edge compose | `ops/runtime/docker-compose.edge.yml` | Delivered |
| C4 | Kernel compose | `ops/runtime/docker-compose.kernel.yml` | Delivered |
| C5 | Operations compose | `ops/runtime/docker-compose.operations.yml` | Delivered |
| C6 | Apps compose | `ops/runtime/docker-compose.apps.yml` | Delivered |
| C7 | Observability compose | `ops/runtime/docker-compose.observability.yml` | Delivered |
| C8 | Shared Docker network | `impilo-network` across all files | Delivered |

### D. Bootstrap Scripts

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| D1 | Auth bootstrap (Keycloak) | `scripts/bootstrap/bootstrap-auth.sh` | Delivered |
| D2 | Topics bootstrap (Kafka) | `scripts/bootstrap/bootstrap-topics.sh` | Delivered |
| D3 | Schemas bootstrap | `scripts/bootstrap/bootstrap-schemas.sh` | Delivered |
| D4 | Seed data bootstrap | `scripts/bootstrap/bootstrap-seed-data.sh` | Delivered |
| D5 | App config bootstrap | `scripts/bootstrap/bootstrap-app-config.sh` | Delivered |
| D6 | All scripts idempotent | Each checks before acting | Delivered |
| D7 | All scripts environment-aware | Read from env vars with defaults | Delivered |

### E. Master Control Script

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| E1 | `platformctl.sh` exists | `scripts/runtime/platformctl.sh` | Delivered |
| E2 | `up lite` command | Starts dev-lite profile | Delivered |
| E3 | `up full` command | Starts dev-full profile | Delivered |
| E4 | `up integration` command | Starts integration profile | Delivered |
| E5 | `up pilot` command | Starts pilot profile | Delivered |
| E6 | `down` command | Stops all in reverse order | Delivered |
| E7 | `status` command | Shows container health | Delivered |
| E8 | `verify` command | Runs readiness checks | Delivered |
| E9 | `logs` command | Tails service logs | Delivered |
| E10 | `bootstrap` command | Runs all bootstrap scripts | Delivered |
| E11 | `smoke` command | Runs smoke test suite | Delivered |
| E12 | `build` command | Builds all services | Delivered |

### F. Readiness and Dependency Model

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| F1 | No sleep-only waiting | All checks use health endpoint polling | Delivered |
| F2 | Dependency-aware startup | Layer-by-layer with health gates | Delivered |
| F3 | Configurable timeouts | Per-service timeout in wait script | Delivered |
| F4 | Fail-fast with helpful output | Error messages with troubleshooting hints | Delivered |
| F5 | Wait script exists | `scripts/runtime/wait-for-readiness.sh` | Delivered |

### G. Smoke and Steel Thread Verification

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| G1 | Smoke suite exists | `scripts/runtime/run-smoke-suite.sh` | Delivered |
| G2 | Auth bootstrap check | Keycloak + token verification | Delivered |
| G3 | Ring 0 lookup check | TSHEPO health verification | Delivered |
| G4 | Edge availability check | OPA + Envoy + v1.1 header enforcement | Delivered |
| G5 | Command flow check | BFF accessibility | Delivered |
| G6 | Outbox/event proof | Kafka + TSHEPO outbox check | Delivered |
| G7 | Steel thread script exists | `scripts/runtime/run-steel-threads.sh` | Delivered |

### H. Documentation

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| H1 | Startup architecture doc | `docs/runtime/platform-startup-architecture.md` | Delivered |
| H2 | User guide | `docs/runtime/platform-startup-user-guide.md` | Delivered |
| H3 | Quickstart | `docs/runtime/platform-startup-quickstart.md` | Delivered |
| H4 | Bootstrap guide | `docs/runtime/platform-bootstrap-guide.md` | Delivered |
| H5 | Operations runbook | `docs/runtime/platform-operations-runbook.md` | Delivered |
| H6 | Component start order | `docs/runtime/platform-component-start-order.md` | Delivered |
| H7 | Troubleshooting guide | `docs/runtime/platform-troubleshooting-guide.md` | Delivered |
| H8 | This acceptance pack | `docs/acceptance/platform-orchestration-acceptance-pack.md` | Delivered |

## Verification Commands

To verify the orchestration layer is complete, run:

```bash
# 1. Verify all files exist
ls -la ops/runtime/platform-manifest.yaml
ls -la ops/runtime/environments/*.env
ls -la ops/runtime/docker-compose.*.yml
ls -la scripts/bootstrap/bootstrap-*.sh
ls -la scripts/runtime/platformctl.sh
ls -la scripts/runtime/wait-for-readiness.sh
ls -la scripts/runtime/run-smoke-suite.sh
ls -la scripts/runtime/run-steel-threads.sh
ls -la scripts/runtime/collect-runtime-evidence.sh
ls -la docs/runtime/platform-*.md

# 2. Verify scripts are executable
file scripts/bootstrap/bootstrap-*.sh scripts/runtime/*.sh | grep executable

# 3. Verify platformctl help works
./scripts/runtime/platformctl.sh help

# 4. Start platform (requires Docker)
./scripts/runtime/platformctl.sh up lite

# 5. Bootstrap
./scripts/runtime/platformctl.sh bootstrap

# 6. Smoke test
./scripts/runtime/platformctl.sh smoke

# 7. Steel threads
./scripts/runtime/platformctl.sh steel-threads

# 8. Evidence collection
./scripts/runtime/platformctl.sh evidence
```

## Expected Outputs

### `platformctl.sh up lite` succeeds when:
- All infrastructure containers healthy
- Keycloak healthy and realm accessible
- TSHEPO, VITO, VARAPI, TUSO, ZIBO healthy
- PCT, OROS healthy
- Experience BFF and UI accessible
- Service URLs printed

### `platformctl.sh smoke` succeeds when:
- Infrastructure ports reachable
- Service health checks pass
- OPA policies loaded
- v1.1 header enforcement working
- Experience layer accessible

### `platformctl.sh steel-threads` succeeds when:
- Auth flow (Keycloak → token → service) works
- Registry lookup accessible
- Clinical command path available
- Event bus reachable

## Known Limitations

1. **Runtime execution**: Full startup requires Docker with sufficient resources (8 GB+ RAM). In resource-constrained environments, the orchestration layer is structurally complete but services may not all start simultaneously.

2. **Seed data**: The seed data bootstrap depends on services exposing seed/import APIs. Currently, it validates endpoint accessibility rather than inserting specific records.

3. **Port conflicts**: Some services have overlapping default ports in their `application.yml`. Docker Compose resolves this via container networking, but bare-metal dev requires `SERVER_PORT` overrides. See the manifest's SPEC CONFLICT NOTES section.

4. **Observability stack**: Requires additional Docker resources. Not started in dev-lite or dev-full profiles.
