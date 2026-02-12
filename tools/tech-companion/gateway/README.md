# Impilo v1.1 Gateway + OPA Enforcement Artifacts

## Architecture Pattern

```
Client → Envoy (Ingress) → ext_authz (OPA / TSHEPO) → Upstream Service
                                  ↓
                        Policy Decision Headers injected:
                          X-Policy-Decision: ALLOW/DENY
                          X-Policy-Version: v1.1
                          X-Decision-Reason: <reason>
```

## Files

| File | Purpose |
|------|---------|
| `envoy/envoy-ext-authz-reference.yaml` | Reference Envoy config showing Ingress → ext_authz → OPA pattern |
| `opa/impilo_headers.rego` | OPA policy enforcing required v1.1 headers |
| `opa/impilo_caching.rego` | OPA policy for Class A/B/C cache guidance |
| `opa/impilo_federation.rego` | OPA policy for federation guardrails (deny private-pod writes to national data) |

## How to Wire a Service Behind the Gateway

### Step 1: Add Service Route to Envoy

In your Envoy `envoy.yaml`, add a route entry under the virtual host:

```yaml
routes:
  - match:
      prefix: "/internal/v1/your-service/"
    route:
      cluster: your_service_cluster
      timeout: 30s
  - match:
      prefix: "/external/v1/your-service/"
    route:
      cluster: your_service_cluster
      timeout: 30s
```

Add the corresponding cluster:

```yaml
clusters:
  - name: your_service_cluster
    type: STRICT_DNS
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: your_service_cluster
      endpoints:
        - lb_endpoints:
            - endpoint:
                address:
                  socket_address:
                    address: your-service
                    port_value: 8080
```

### Step 2: Add tech-companion Dependency

In your service's `pom.xml`:

```xml
<dependency>
    <groupId>zw.gov.mohcc.impilo</groupId>
    <artifactId>tech-companion</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 3: Register Filters

Create a filter configuration class in your service:

```java
@Configuration
public class CompanionFilterConfig {

    @Bean
    @Order(10)
    public FilterRegistrationBean<V11HeaderFilter> v11HeaderFilter() {
        FilterRegistrationBean<V11HeaderFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new V11HeaderFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(10);
        return reg;
    }

    @Bean
    @Order(11)
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
            IdempotencyService idempotencyService) {
        FilterRegistrationBean<IdempotencyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdempotencyFilter(idempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(11);
        return reg;
    }

    @Bean
    @Order(12)
    public FilterRegistrationBean<TimeoutEnforcementFilter> timeoutFilter() {
        FilterRegistrationBean<TimeoutEnforcementFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TimeoutEnforcementFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(12);
        return reg;
    }

    @Bean
    public IdempotencyService idempotencyService(IdempotencyRepository repo) {
        return new IdempotencyService(repo);
    }

    @Bean
    public IdempotencyRepository idempotencyRepository(JdbcTemplate jdbc) {
        // Use your service's schema-prefixed table name
        return new JdbcIdempotencyRepository(jdbc, "your_schema.idempotency_keys", 24);
    }
}
```

### Step 4: Add Idempotency Migration

Copy `companion-idempotency-ddl.sql` into your Flyway migrations:

```sql
-- V0XX__add_idempotency_keys.sql
CREATE TABLE IF NOT EXISTS your_schema.idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
```

### Step 5: Deploy OPA Policies (Production)

```bash
# Start OPA sidecar
opa run --server --addr=:9191 \
    --set=decision_logs.console=true \
    tools/tech-companion/gateway/opa/

# Test a policy
echo '{"attributes":{"request":{"http":{
  "method":"POST",
  "path":"/internal/v1/patients",
  "headers":{
    "x-tenant-id":"moh-zw",
    "x-pod-id":"national",
    "authorization":"Bearer eyJ..."
  }
}}}}' | opa eval -d tools/tech-companion/gateway/opa/ \
    -i /dev/stdin "data.impilo.gateway.headers.allow"
```

### Step 6: Verify with Contract Harness

```bash
# Run the golden contract tests against your service
mvn test -pl libs/tech-companion-harness \
    -Dtest="GoldenContractSuite" \
    -Dcompanion.base-url=http://localhost:YOUR_PORT
```

## Local Development

For local dev, the existing `infra/envoy/envoy.yaml` routes through TSHEPO gRPC ext_authz.
OPA policies are for production/staging environments where fine-grained policy is needed.
The tech-companion library enforces the same headers at the service level regardless.
