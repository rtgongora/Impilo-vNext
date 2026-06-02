-- Phase 6D -- Fundo authoring metadata options.
-- Database-backed language options for native Fundo course authoring.

CREATE TABLE IF NOT EXISTS lrn_fundo_language_option (
    code            VARCHAR(16)  PRIMARY KEY,
    label           VARCHAR(128) NOT NULL,
    native_label    VARCHAR(128),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 100,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO lrn_fundo_language_option (code, label, native_label, active, sort_order) VALUES
    ('en', 'English', 'English', TRUE, 10),
    ('sn', 'Shona', 'ChiShona', TRUE, 20),
    ('nd', 'Ndebele', 'isiNdebele', TRUE, 30),
    ('ny', 'Chewa / Nyanja', 'Chichewa / Chinyanja', TRUE, 40),
    ('st', 'Sotho', 'Sesotho', TRUE, 50),
    ('pt', 'Portuguese', 'Português', TRUE, 60)
ON CONFLICT (code) DO UPDATE SET
    label = EXCLUDED.label,
    native_label = EXCLUDED.native_label,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
