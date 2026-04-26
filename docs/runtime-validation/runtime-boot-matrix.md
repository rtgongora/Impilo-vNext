# Runtime Boot Matrix

## Environment
- Docker CLI: v29.2.1
- Docker Compose: v5.0.2
- Docker Daemon: **NOT AVAILABLE** (no socket at /var/run/docker.sock)

## Boot Results

| Service/Infra | Boot Attempted | Healthy | Health Endpoint | Blocker | Mitigation |
|---------------|---------------|---------|----------------|---------|------------|
| PostgreSQL 16 | No | — | pg_isready | BLOCKED_EXTERNAL: No Docker daemon | Use docker-compose.runtime.yml when Docker available |
| Redis 7 | No | — | redis-cli ping | BLOCKED_EXTERNAL | Same |
| Kafka 3.7 (KRaft) | No | — | broker-api-versions | BLOCKED_EXTERNAL | Same |
| Keycloak 25 | No | — | /health/ready | BLOCKED_EXTERNAL | Same |
| MinIO | No | — | :9000 TCP | BLOCKED_EXTERNAL | Same |
| HAPI FHIR R4 | No | — | /fhir/metadata | BLOCKED_EXTERNAL | Same |
| OPA 0.68 | No | — | /health | BLOCKED_EXTERNAL | Same |
| Envoy 1.31 | No | — | :9901/ready | BLOCKED_EXTERNAL | Same |
| TSHEPO (8081) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| VITO (8082) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| VARAPI (8083) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| TUSO (8084) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| ZIBO (8085) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| PCT (8088) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| OROS (8089) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| Experience BFF (8160) | No | — | /actuator/health | BLOCKED_EXTERNAL | Same |
| One UI Shell (3000) | No | — | HTTP GET / | BLOCKED_EXTERNAL | Same |

## Blocker Detail
The container environment does not provide a Docker daemon. The Docker CLI is installed (v29.2.1) and Docker Compose is available (v5.0.2), but there is no Docker socket at `/var/run/docker.sock`.

All runtime boot attempts require Docker Compose to orchestrate infrastructure (Postgres, Kafka, Redis, Keycloak) and backend services. Without a Docker daemon, no services can be started.

## How to Execute When Docker Is Available
```bash
# Step 1: Build
docker compose -f docker-compose.build.yml up --build

# Step 2: Boot
docker compose -f docker-compose.runtime.yml up -d

# Step 3: Wait for health
./scripts/runtime-validation/boot-runtime.sh

# Step 4: Run all validation
./scripts/runtime-validation/run-all.sh
```

## Evidence
```
$ docker ps -a 2>&1
failed to connect to the docker API at unix:///var/run/docker.sock; check if the path is correct and if the daemon is running
```
