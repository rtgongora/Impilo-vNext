# Steel Thread Results

## Environment
- Docker Daemon: NOT AVAILABLE
- Status: All threads BLOCKED_EXTERNAL — scripts implemented, awaiting runtime environment

## Results

### Steel Thread 1 — Provider Operational Flow
| Field | Value |
|-------|-------|
| Components | Keycloak → TSHEPO → VARAPI |
| Steps | 1. Auth dr.mapfumo via Keycloak 2. GET /api/v1.1/providers with trust headers 3. Verify header enforcement 4. Verify error envelope 5. Check outbox |
| Result | BLOCKED_EXTERNAL |
| Script | test/integration/steel-thread-a-provider.sh |
| Blocker | No Docker daemon — services cannot be started |
| Evidence | Script implemented and syntax-validated |

### Steel Thread 2 — Citizen Flow
| Field | Value |
|-------|-------|
| Components | Keycloak → Experience BFF → VITO |
| Steps | 1. Auth citizen.moyo 2. BFF login 3. VITO patient query 4. Verify correlation_id |
| Result | BLOCKED_EXTERNAL |
| Script | test/integration/steel-thread-b-citizen.sh |
| Blocker | No Docker daemon |
| Evidence | Script implemented and syntax-validated |

### Steel Thread 3 — Support Escalation Flow
| Field | Value |
|-------|-------|
| Components | Support Service |
| Steps | 1. Auth support.agent1 2. Create ticket 3. Verify correlation_id chain 4. Check outbox |
| Result | BLOCKED_EXTERNAL |
| Script | test/integration/steel-thread-c-support.sh |
| Blocker | No Docker daemon. Additionally, support-service not in docker-compose.runtime.yml |
| Evidence | Script implemented and syntax-validated |

### Steel Thread 4 — Eventing Flow
| Field | Value |
|-------|-------|
| Components | VITO → event_outbox → Kafka |
| Steps | 1. Register patient in VITO 2. Query outbox table 3. Verify EventEnvelope fields 4. Check Kafka topic |
| Result | BLOCKED_EXTERNAL |
| Script | test/integration/steel-thread-e-eventing.sh |
| Blocker | No Docker daemon |
| Evidence | Script implemented and syntax-validated |

### Steel Thread 5 — Federation Flow
(Federation chosen over Offline — rationale: federation-connector lib is COMPLETE with 13 src/4 tests, TSHEPO has explicit FederationControlController, GoldenContractSuite already tests federation authority. Offline-sync is only ADEQUATE.)
| Field | Value |
|-------|-------|
| Components | TSHEPO federation endpoints |
| Steps | 1. Auth admin.central 2. Call with X-Pod-ID=national → 2xx 3. Call with X-Pod-ID=private-harare → 403 4. Verify error envelope |
| Result | BLOCKED_EXTERNAL |
| Script | test/integration/steel-thread-f-federation.sh |
| Blocker | No Docker daemon |
| Evidence | Script implemented and syntax-validated |

## Summary
| Thread | Status | Script Ready |
|--------|--------|-------------|
| Provider Flow | BLOCKED_EXTERNAL | Yes |
| Citizen Flow | BLOCKED_EXTERNAL | Yes |
| Support Escalation | BLOCKED_EXTERNAL | Yes |
| Eventing | BLOCKED_EXTERNAL | Yes |
| Federation | BLOCKED_EXTERNAL | Yes |
