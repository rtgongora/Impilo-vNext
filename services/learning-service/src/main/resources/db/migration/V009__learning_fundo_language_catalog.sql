-- Phase 6D -- Expanded database-backed language options for Fundo authoring.

INSERT INTO lrn_fundo_language_option (code, label, native_label, active, sort_order) VALUES
    ('en', 'English', 'English', TRUE, 10),
    ('sn', 'Shona', 'ChiShona', TRUE, 20),
    ('nd', 'Ndebele', 'isiNdebele', TRUE, 30),
    ('ndc', 'Ndau', 'ChiNdau', TRUE, 40),
    ('ny', 'Chewa / Nyanja', 'Chichewa / Chinyanja', TRUE, 50),
    ('ve', 'Venda', 'Tshivenda', TRUE, 60),
    ('ts', 'Tsonga / Shangani', 'XiTsonga / Shangani', TRUE, 70),
    ('nau', 'Nambya', 'ChiNambya', TRUE, 80),
    ('kck', 'Kalanga', 'TjiKalanga', TRUE, 90),
    ('xho', 'Xhosa', 'isiXhosa', TRUE, 100),
    ('sna', 'Sena', 'ChiSena', TRUE, 110),
    ('toi', 'Tonga', 'ChiTonga', TRUE, 120),
    ('st', 'Sotho', 'Sesotho', TRUE, 130),
    ('tn', 'Tswana', 'Setswana', TRUE, 140),
    ('fr', 'French', 'Français', TRUE, 150),
    ('pt', 'Portuguese', 'Português', TRUE, 160)
ON CONFLICT (code) DO UPDATE SET
    label = EXCLUDED.label,
    native_label = EXCLUDED.native_label,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
