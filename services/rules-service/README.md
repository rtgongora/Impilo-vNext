# Rules Service

Simple rule storage and evaluation engine with decision logging for Impilo vNext.

## Purpose

The Rules Service stores boolean expressions as rules, evaluates incoming facts against active rules, and logs decisions. It is v1.1-native: all endpoints enforce mandatory headers, idempotency, federation authority, and timeout propagation via `tech-companion`.

## Rule DSL

The service includes a safe, sandboxed expression evaluator supporting:

- **Fact references**: `facts.age`, `facts.country`, `facts.status`
- **Comparisons**: `==`, `!=`, `>`, `>=`, `<`, `<=`
- **Logical operators**: `AND`, `OR`
- **Parentheses**: `(facts.age >= 18 OR facts.vip == true) AND facts.active == true`
- **Types**: numbers (integer/decimal), strings (single-quoted), booleans (true/false)

Example: `facts.age >= 18 AND facts.country == 'ZW'`

## Endpoints

### POST /internal/v1/rules (national-only)

Create or update a rule.

```bash
curl -X POST http://localhost:8112/internal/v1/rules \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: rule-$(uuidgen)" \
  -d '{
    "name": "age-eligibility",
    "expression": "facts.age >= 18 AND facts.country == '\''ZW'\''",
    "enabled": true
  }'
```

### GET /internal/v1/rules (any pod)

List rules for the current tenant.

```bash
curl http://localhost:8112/internal/v1/rules \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### POST /internal/v1/evaluate (any pod)

Evaluate facts against all active rules for the tenant.

```bash
curl -X POST http://localhost:8112/internal/v1/evaluate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: eval-$(uuidgen)" \
  -d '{
    "facts": {
      "age": 25,
      "country": "ZW",
      "status": "ACTIVE"
    }
  }'
```

**Error**: If a rule has an invalid expression, the response includes error code `RULE_EXPRESSION_INVALID` (400).

## Event Types Emitted

| Event Type | Trigger |
|---|---|
| `impilo.rules.rule.upserted.v1` | Rule created or updated |
| `impilo.rules.decision.recorded.v1` | Decision logged after evaluation |

## Data Model

- **rs_rules**: Rule definitions (name, expression, enabled)
- **rs_decision_logs**: Evaluation outcomes (rule_id, outcome, reason, facts)
- **rs_event_outbox**: v1.1 outbox events pending Kafka publication
- **idempotency_keys**: Idempotency deduplication (managed by tech-companion)

## Running Tests

```bash
cd services
mvn -pl rules-service -am clean test
```
