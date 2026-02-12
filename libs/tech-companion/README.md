# Impilo Tech Companion Library

Manifest v1.1 / Tech Companion Spec enforcement primitives for all Impilo services.

## What's Inside

| Package | Purpose |
|---------|---------|
| `companion.context` | `RequestContext`, `RequestContextHolder`, `CompanionHeaders` |
| `companion.filter` | `V11HeaderFilter`, `IdempotencyFilter`, `TimeoutEnforcementFilter`, `CompanionExceptionHandler` |
| `companion.error` | `ErrorEnvelope`, `ErrorEnvelopeWriter`, `ErrorCodes` |
| `companion.idempotency` | `IdempotencyService`, `IdempotencyRepository`, `JdbcIdempotencyRepository`, `InMemoryIdempotencyRepository` |
| `companion.timeout` | `TimeoutInterceptor`, `TimeoutExceededException` |
| `companion.federation` | `FederationAuthority`, `FederationAuthorityViolationException` |
| `companion.observability` | `CompanionMetrics` (Micrometer tags + counters) |

## How to Adopt in an Existing Service

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>zw.gov.mohcc.impilo</groupId>
    <artifactId>tech-companion</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Register Filters

Create a `@Configuration` class:

```java
@Configuration
public class CompanionFilterConfig {

    @Bean
    public FilterRegistrationBean<V11HeaderFilter> v11HeaderFilter() {
        var reg = new FilterRegistrationBean<>(new V11HeaderFilter());
        reg.addUrlPatterns("/internal/v1/*", "/external/v1/*");
        reg.setOrder(10);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
            IdempotencyService idempotencyService) {
        var reg = new FilterRegistrationBean<>(new IdempotencyFilter(idempotencyService));
        reg.addUrlPatterns("/internal/v1/*");
        reg.setOrder(11);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<TimeoutEnforcementFilter> timeoutFilter() {
        var reg = new FilterRegistrationBean<>(new TimeoutEnforcementFilter());
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
        return new JdbcIdempotencyRepository(jdbc, "your_schema.idempotency_keys", 24);
    }
}
```

### Step 3: Add Idempotency Table (Flyway Migration)

```sql
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

### Step 4: Use Federation Authority

For national-only operations in your controllers:

```java
@PostMapping("/internal/v1/merge")
public ResponseEntity<?> merge(@RequestBody MergeRequest body) {
    FederationAuthority.requireNational(); // throws 403 if not national pod
    // ... proceed with merge
}
```

### Step 5: Use Timeout Interceptor (Optional)

For long-running operations:

```java
var result = TimeoutInterceptor.withTimeout(() -> {
    return expensiveComputation();
});
```

### Step 6: Access Request Context

```java
RequestContext ctx = RequestContextHolder.require();
String tenantId = ctx.tenantId();
String podId = ctx.podId();
```

### Step 7: Run Contract Tests

Add test dependency:

```xml
<dependency>
    <groupId>zw.gov.mohcc.impilo</groupId>
    <artifactId>tech-companion-harness</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

Create a test class:

```java
@SpringBootTest(classes = YourApp.class, webEnvironment = RANDOM_PORT)
public class V11ContractTest extends GoldenContractSuite {
    @Override protected String getHealthPath()     { return "/internal/v1/health"; }
    @Override protected String getCommandPath()    { return "/internal/v1/your-command"; }
    @Override protected String getFederationPath() { return "/internal/v1/your-national-op"; }
}
```

## What Does NOT Change

- Existing controllers and business logic are untouched
- Legacy `/v1/**` paths continue to work (filters skip them)
- Existing TrustContext from shared-core remains active for legacy paths
- No new database tables are required until you opt into idempotency

## Module Structure

```
libs/
  tech-companion/          ← Core library (this module)
  tech-companion-harness/  ← Golden contract test suite
  tech-companion-mock/     ← Demo mock service
  federation-connector/    ← Pod-to-spine federation connector
tools/
  tech-companion/gateway/  ← Envoy + OPA reference artifacts
```
