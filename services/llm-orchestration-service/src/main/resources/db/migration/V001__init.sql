CREATE TABLE IF NOT EXISTS llm.llm_interaction_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NULL,
    actor_id TEXT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    use_case TEXT NULL,
    risk_level TEXT NULL,
    input_hash TEXT NULL,
    output_hash TEXT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    requires_human_approval BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS llm.llm_tool_call_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NULL,
    actor_id TEXT NULL,
    provider TEXT NULL,
    tool_name TEXT NOT NULL,
    decision TEXT NULL,
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    requires_human_approval BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
