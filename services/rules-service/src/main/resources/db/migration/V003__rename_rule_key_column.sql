-- "key" is reserved in H2; use a non-reserved physical column name (PostgreSQL + H2 / Flyway tests).
ALTER TABLE rs_rules RENAME COLUMN key TO rule_key;

DROP INDEX IF EXISTS idx_rs_rules_tenant_key;
CREATE UNIQUE INDEX idx_rs_rules_tenant_key ON rs_rules (tenant_id, rule_key) WHERE rule_key IS NOT NULL;
