-- support-service: tickets, incidents, knowledge articles
-- Table prefix: sup_

CREATE TABLE sup_tickets (
    ticket_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    description     TEXT            NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    priority        VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(32)     NOT NULL DEFAULT 'OPEN',
    reporter_ref    VARCHAR(255)    NOT NULL,
    assignee_ref    VARCHAR(255),
    facility_ref    VARCHAR(255),
    resolution      TEXT,
    metadata_json   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    version         INT             NOT NULL DEFAULT 1
);

CREATE INDEX idx_sup_tickets_tenant ON sup_tickets (tenant_id);
CREATE INDEX idx_sup_tickets_status ON sup_tickets (status);
CREATE INDEX idx_sup_tickets_priority ON sup_tickets (priority);
CREATE INDEX idx_sup_tickets_reporter ON sup_tickets (reporter_ref);
CREATE INDEX idx_sup_tickets_updated ON sup_tickets (updated_at);

CREATE TABLE sup_knowledge_articles (
    article_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    body            TEXT            NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    author_ref      VARCHAR(255)    NOT NULL,
    tags            TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    version         INT             NOT NULL DEFAULT 1
);

CREATE INDEX idx_sup_articles_tenant ON sup_knowledge_articles (tenant_id);
CREATE INDEX idx_sup_articles_status ON sup_knowledge_articles (status);
CREATE INDEX idx_sup_articles_category ON sup_knowledge_articles (category);
