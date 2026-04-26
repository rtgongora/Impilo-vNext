# Impilo vNext — Server Runtime Status

**Last Updated**: 2026-03-17
**Status**: ⏳ NOT YET DEPLOYED (server unreachable from build environment)

---

## Expected Runtime When Deployed

### Infrastructure Layer
| Service | Port | Expected Status |
|---------|------|----------------|
| PostgreSQL 16 | 5432 | Should start first, healthcheck via pg_isready |
| Redis 7 | 6379 | Should start alongside postgres, healthcheck via redis-cli ping |
| Kafka 3.7.1 (KRaft) | 9092 | Should start with 15s start_period, healthcheck via broker-api-versions |
| MinIO | 9000 (API), 9001 (Console) | Should start with pg, no dependencies |
| Keycloak 25 | 8080 | Depends on postgres, imports impilo-realm.json on first start |
| HAPI FHIR 7.4 | 8090 | Depends on postgres, connects to butano database |

### Edge Layer
| Service | Port | Expected Status |
|---------|------|----------------|
| OPA | 8181 | Loads rego policies from tools/ops/gateway/opa/ |
| Envoy 1.31 | 10000 (public), 9901 (admin) | Depends on OPA, routes to all backend services |

### Backend Services
| Service | Port | Health Endpoint | Expected Status |
|---------|------|----------------|----------------|
| TSHEPO | 8081 | /actuator/health | Depends on postgres, redis, kafka |
| VITO | 8082 | /actuator/health | Depends on postgres, redis, kafka |
| VARAPI | 8083 | /actuator/health | Depends on postgres, redis, kafka |
| TUSO | 8084 | /actuator/health | Depends on postgres, redis, kafka |
| ZIBO | 8085 | /actuator/health | Depends on postgres, redis, kafka |
| PCT | 8088 | /actuator/health | Depends on postgres, redis, kafka |
| OROS | 8089 | /actuator/health | Depends on postgres, redis, kafka |
| Experience BFF | 8160 | /actuator/health | Depends on postgres |

### Experience Layer
| Service | Port | Expected Status |
|---------|------|----------------|
| One UI Shell | 3000 | Next.js SSR, depends on Experience BFF |

---

## Verification Flows (to run after deployment)

### 1. Infrastructure Health
```bash
# PostgreSQL
docker compose -f docker-compose.runtime.yml exec postgres pg_isready -U impilo

# Redis
docker compose -f docker-compose.runtime.yml exec redis redis-cli ping

# Kafka
docker compose -f docker-compose.runtime.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### 2. Service Health Endpoints
```bash
curl http://localhost:8081/actuator/health  # TSHEPO
curl http://localhost:8082/actuator/health  # VITO
curl http://localhost:8083/actuator/health  # VARAPI
curl http://localhost:8084/actuator/health  # TUSO
curl http://localhost:8085/actuator/health  # ZIBO
curl http://localhost:8088/actuator/health  # PCT
curl http://localhost:8089/actuator/health  # OROS
curl http://localhost:8160/actuator/health  # Experience BFF
curl http://localhost:3000                  # One UI Shell
```

### 3. Auth Flow (Keycloak Token)
```bash
# Get token via direct access grant
curl -X POST http://localhost:8080/realms/impilo/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=experience-ui" \
  -d "username=admin.central" \
  -d "password=test123"
```

### 4. Gateway Flow (via Envoy)
```bash
# Health via gateway
curl http://localhost:10000/actuator/health

# Client registry lookup (requires auth token)
TOKEN=$(curl -s -X POST http://localhost:8080/realms/impilo/protocol/openid-connect/token \
  -d "grant_type=password" -d "client_id=experience-ui" \
  -d "username=admin.central" -d "password=test123" | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:10000/api/v1/clients
```

### 5. FHIR Health
```bash
curl http://localhost:8090/fhir/metadata
```

### 6. OPA Policy Check
```bash
curl -X POST http://localhost:8181/v1/data/impilo/gateway/headers \
  -H "Content-Type: application/json" \
  -d '{"input":{"attributes":{"request":{"http":{"path":"/api/v1/clients","headers":{}}}}}}'
```
