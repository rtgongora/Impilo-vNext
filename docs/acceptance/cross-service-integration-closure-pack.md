# Cross-Service Integration Closure — Acceptance Pack

> Date: 2026-03-16
> Wave: Cross-Service Integration Closure
> Status: IMPLEMENTED — requires live runtime for full execution

## Acceptance Criteria

### AC-1: Deterministic Auth Bootstrap
- [x] Keycloak realm import JSON exists (`tools/auth/impilo-realm.json`)
- [x] Realm includes 5 clients, 8 roles, 6 test users
- [x] Bootstrap script exists and is idempotent (`scripts/integration-closure/bootstrap-auth.sh`)
- [x] Docker-compose updated for auto-import (`--import-realm`)
- [x] Design documented (`docs/integration-closure/auth-bootstrap-design.md`)

### AC-2: Runtime Orchestration Cleanup
- [x] UI port conflicts resolved (butano-web/support-console, developer-console/pct-web)
- [x] ui/ehr formally deprecated with DEPRECATED.md
- [x] Canonical port map documented for all 21+ UIs
- [x] Service port conflicts documented with mitigation
- [x] One canonical runtime path documented

### AC-3: Steel Thread Test Suite
- [x] Steel Thread A (Auth + Provider) — `test/integration/steel-thread-a-provider.sh`
- [x] Steel Thread B (Citizen Flow) — `test/integration/steel-thread-b-citizen.sh`
- [x] Steel Thread C (Support Escalation) — `test/integration/steel-thread-c-support.sh`
- [x] Steel Thread D (Messaging) — `test/integration/steel-thread-d-messaging.sh`
- [x] Steel Thread E (Eventing) — `test/integration/steel-thread-e-eventing.sh`
- [x] Steel Thread F (Federation) — `test/integration/steel-thread-f-federation.sh`
- [x] Common test helpers (`test/integration/_common.sh`)
- [x] Steel thread matrix (`docs/integration-closure/steel-thread-matrix.md`)

### AC-4: Eventing & Compliance Proof
- [x] EventEnvelope v1.1 validation in steel thread E
- [x] Outbox field verification (tenant_id, pod_id, correlation_id, schema_version, event_type)
- [x] Compliance cross-reference matrix maps each thread to v1.1 requirements
- [x] Kafka topic existence check

### AC-5: Orchestration Scripts
- [x] `scripts/integration-closure/bootstrap-auth.sh` — auth setup
- [x] `scripts/integration-closure/run-runtime-checks.sh` — health probes
- [x] `scripts/integration-closure/run-cross-service-tests.sh` — test runner
- [x] `scripts/integration-closure/run-steel-threads.sh` — alias
- [x] `scripts/integration-closure/run-all.sh` — master orchestrator

### AC-6: MINIMAL Component Disposition
- [x] tech-companion-harness (MINIMAL): Functions correctly as test base class, not on critical path
- [x] ui/self-service (MINIMAL): Not on any integration flow, documented
- [x] ui/ehr (FRAGILE → DEPRECATED): Formally deprecated

### AC-7: Open Blockers Documented
- [x] All residual gaps documented in `docs/integration-closure/open-blockers.md`
- [x] Each blocker has severity, file references, and next action
- [x] No major ambiguity undocumented

## Artifacts Produced

### Documentation
| File | Description |
|------|-------------|
| `docs/integration-closure/cross-service-integration-closure-report.md` | Main closure report |
| `docs/integration-closure/steel-thread-matrix.md` | Steel thread → service → compliance mapping |
| `docs/integration-closure/auth-bootstrap-design.md` | Auth bootstrap design |
| `docs/integration-closure/runtime-orchestration-cleanup.md` | Runtime cleanup report |
| `docs/integration-closure/open-blockers.md` | Residual blockers |
| `docs/acceptance/cross-service-integration-closure-pack.md` | This acceptance pack |

### Config/Auth Artifacts
| File | Description |
|------|-------------|
| `tools/auth/impilo-realm.json` | Keycloak realm import |
| `ui/ehr/DEPRECATED.md` | EHR deprecation notice |

### Scripts
| File | Description |
|------|-------------|
| `scripts/integration-closure/bootstrap-auth.sh` | Auth bootstrap |
| `scripts/integration-closure/run-runtime-checks.sh` | Health checks |
| `scripts/integration-closure/run-cross-service-tests.sh` | Test runner |
| `scripts/integration-closure/run-steel-threads.sh` | Alias |
| `scripts/integration-closure/run-all.sh` | Master runner |

### Test Suite
| File | Steel Thread |
|------|-------------|
| `test/integration/_common.sh` | Shared helpers |
| `test/integration/steel-thread-a-provider.sh` | A: Auth + Provider |
| `test/integration/steel-thread-b-citizen.sh` | B: Citizen flow |
| `test/integration/steel-thread-c-support.sh` | C: Support escalation |
| `test/integration/steel-thread-d-messaging.sh` | D: Messaging |
| `test/integration/steel-thread-e-eventing.sh` | E: Eventing |
| `test/integration/steel-thread-f-federation.sh` | F: Federation |

### Modified Files
| File | Change |
|------|--------|
| `docker-compose.runtime.yml` | Keycloak `--import-realm` + realm volume mount |
| `ui/support-console/package.json` | Port 3006 → 3019 |
| `ui/pct-web/package.json` | Port 3007 → 3021 |

## Execution Instructions

### Full Integration Closure (requires Docker)
```bash
# 1. Build service JARs
docker compose -f docker-compose.build.yml build

# 2. Start the runtime stack
docker compose -f docker-compose.runtime.yml up -d

# 3. Wait for services to be healthy (~60-90 seconds)

# 4. Run the full integration closure suite
./scripts/integration-closure/run-all.sh
```

### Expected Evidence When Executed
1. Keycloak realm `impilo` bootstrapped with all clients/users/roles
2. All 16 runtime services respond healthy
3. Steel thread A: VARAPI accepts requests with trust headers, rejects missing headers with proper error envelope
4. Steel thread B: BFF login returns session, VITO accepts patient queries
5. Steel thread C: Support ticket created with preserved correlation_id
6. Steel thread D: Notification sent with trust headers, error envelope on violations
7. Steel thread E: Patient registered in VITO, outbox row verified with all required fields
8. Steel thread F: Federation endpoint allows national pod, denies private pod with 403

## Verdict

**IMPLEMENTED** — All artifacts created, scripts are executable, tests are real (not stubs). Full execution requires the docker-compose runtime stack.

This pack supersedes the gap identified in the completeness audit: "No Keycloak realm import script (requires auth domain expertise)" — this gap is now closed.
