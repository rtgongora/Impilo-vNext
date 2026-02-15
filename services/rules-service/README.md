# Rules Service

Rule registry with versioning, activation lifecycle, evaluation audit, and decision logging for Impilo vNext.

## Purpose

The Rules Service stores boolean expression rules as versioned artifacts, manages their activation lifecycle, evaluates incoming facts against the active version of a named rule, and writes a tamper-evident audit trail. It is v1.1-native: all endpoints enforce mandatory headers, idempotency, federation authority, and timeout propagation via `tech-companion`.

## Rule DSL

The service includes a safe, sandboxed expression evaluator (`SimpleRuleEvaluator`) supporting:

- **Fact references**: `facts.age`, `facts.country`, `facts.status`
- **Comparisons**: `==`, `!=`, `>`, `>=`, `<`, `<=`
- **Logical operators**: `AND`, `OR`
- **Parentheses**: `(facts.age >= 18 OR facts.vip == true) AND facts.active == true`
- **Types**: numbers (integer/decimal), strings (single-quoted), booleans (true/false)

Example: `facts.age >= 18 AND facts.country == 'ZW'`

## Lifecycle

```
CREATE rule (key) → CREATE version(s) (DSL text) → ACTIVATE version N → EVALUATE → DEACTIVATE
```

1. **Create Rule** — registers a named rule container with a unique key per tenant
2. **Create Version** — attaches a DSL expression to the rule (auto-incrementing version number)
3. **Activate** — makes a specific version the current active version (closes any prior activation)
4. **Evaluate** — evaluates facts against the active version, writes audit row with SHA-256 input hash
5. **Deactivate** — sets rule to INACTIVE; evaluations return 422

## Endpoints

### POST /internal/v1/rules (national-only)

Create a rule container.

```bash
curl -X POST http://localhost:8112/internal/v1/rules \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: rule-$(uuidgen)" \
  -d '{"key": "age-eligibility", "name": "Age Eligibility Rule"}'
```

### GET /internal/v1/rules (any pod)

List rules for the current tenant.

### POST /internal/v1/rules/{key}/versions (national-only)

Create a new version with DSL text.

```bash
curl -X POST http://localhost:8112/internal/v1/rules/age-eligibility/versions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: ver-$(uuidgen)" \
  -d '{"dslText": "facts.age >= 18 AND facts.country == '\''ZW'\''"}'
```

### POST /internal/v1/rules/{key}/activate?version=N (national-only)

Activate a specific version. Closes any prior activation window.

```bash
curl -X POST "http://localhost:8112/internal/v1/rules/age-eligibility/activate?version=1" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: act-$(uuidgen)"
```

### POST /internal/v1/rules/{key}/deactivate (national-only)

Deactivate a rule. Subsequent evaluations return 422.

### POST /internal/v1/rules/{key}/evaluate (any pod)

Evaluate facts against the active version. Writes audit row and emits outbox event.

```bash
curl -X POST http://localhost:8112/internal/v1/rules/age-eligibility/evaluate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: eval-$(uuidgen)" \
  -d '{"facts": {"age": 25, "country": "ZW"}}'
```

Response:
```json
{
  "ruleKey": "age-eligibility",
  "version": 1,
  "outcome": "MATCH",
  "reason": "Expression evaluated to true",
  "auditId": "a1b2c3d4-..."
}
```

## Event Types Emitted

| Event Type | Trigger |
|---|---|
| `impilo.rules.rule.created.v1` | Rule container created |
| `impilo.rules.version.created.v1` | New version attached to rule |
| `impilo.rules.rule.activated.v1` | Version activated |
| `impilo.rules.rule.deactivated.v1` | Rule deactivated |
| `impilo.rules.evaluated.v1` | Evaluation completed (includes outcome + input hash) |

## Data Model

- **rs_rules**: Rule containers (key, name, status, tenant_id)
- **rs_rule_versions**: Versioned DSL expressions (rule_id, version, dsl_text)
- **rs_rule_activations**: Activation windows (rule_id, version_id, active_from, active_to)
- **rs_evaluation_audit**: Evaluation audit trail (rule_key, version, input_hash, result_json)
- **rs_decision_logs**: Legacy v1 evaluation outcomes (kept for backward compat)
- **rs_event_outbox**: v1.1 outbox events pending Kafka publication
- **idempotency_keys**: Idempotency deduplication (managed by tech-companion)

## Running Tests

```bash
cd services
mvn -pl rules-service -am clean test
```
