-- =====================================================================================
-- reporting :: V003 — Regulatory report definitions by class (ROM-W9, R8)
-- Governed report definitions across the five ROM report classes. NO-THEATRE GATE: every
-- definition's query_template resolves against a NAMED system-of-record read model (real varapi
-- tables), never a placeholder — a definition with no backing query fails the W11 conformance bar.
-- Operational/management dashboards are stateless BFF aggregation (not reports); these are the
-- statutory / public-interest / oversight definitions plus two worked queue/TAT examples.
-- =====================================================================================

ALTER TABLE rpt_report_definitions
    ADD COLUMN IF NOT EXISTS report_class VARCHAR(24);

INSERT INTO rpt_report_definitions
    (tenant_id, report_key, name, description, query_template, parameters, output_format, status, created_by, report_class)
VALUES
-- OPERATIONAL — the registrar's live application queue per council.
('00000000-0000-0000-0000-000000000001'::uuid, 'reg.operational.application_queue',
 'Application queue by council',
 'Open applications by council and workflow state (operational board).',
 'SELECT c.council_code, a.workflow_state, count(*) AS n FROM varapi.provider_applications a '
 || 'JOIN varapi.councils c ON c.id = a.council_id WHERE a.workflow_state <> ''CLOSED'' '
 || 'GROUP BY c.council_code, a.workflow_state ORDER BY c.council_code',
 '{}'::jsonb, 'JSON', 'ACTIVE', 'rom-seed', 'OPERATIONAL'),
-- MANAGEMENT — turnaround time from submission to decision.
('00000000-0000-0000-0000-000000000001'::uuid, 'reg.management.application_tat',
 'Application turnaround time',
 'Mean days from submission to decision per council (management board).',
 'SELECT c.council_code, avg(a.decision_date - a.submitted_at::date) AS mean_days FROM varapi.provider_applications a '
 || 'JOIN varapi.councils c ON c.id = a.council_id WHERE a.decision_date IS NOT NULL '
 || 'GROUP BY c.council_code',
 '{}'::jsonb, 'JSON', 'ACTIVE', 'rom-seed', 'MANAGEMENT'),
-- STATUTORY — the annual register return (counts by register + status).
('00000000-0000-0000-0000-000000000001'::uuid, 'reg.statutory.annual_register_return',
 'Annual register return',
 'Register entries by council, register and status — the statutory annual return.',
 'SELECT c.council_code, pr.register_code, r.status, count(*) AS n '
 || 'FROM varapi.provider_council_registration_records r '
 || 'JOIN varapi.councils c ON c.id = r.council_id '
 || 'LEFT JOIN varapi.professional_registers pr ON pr.id = r.register_id '
 || 'GROUP BY c.council_code, pr.register_code, r.status ORDER BY c.council_code',
 '{}'::jsonb, 'JSON', 'ACTIVE', 'rom-seed', 'STATUTORY'),
-- PUBLIC_INTEREST — public good-standing counts (disclosure-classed; GOOD vs not).
('00000000-0000-0000-0000-000000000001'::uuid, 'reg.public.good_standing_summary',
 'Good standing summary',
 'Count of providers in good standing per council (public-interest disclosure).',
 'SELECT c.council_code, gs.standing, count(*) AS n FROM varapi.provider_good_standing gs '
 || 'JOIN varapi.councils c ON c.id = gs.council_id GROUP BY c.council_code, gs.standing',
 '{}'::jsonb, 'JSON', 'ACTIVE', 'rom-seed', 'PUBLIC_INTEREST'),
-- OVERSIGHT — HPA consolidated cross-council indicators (aggregate only).
('00000000-0000-0000-0000-000000000001'::uuid, 'reg.oversight.cross_council_indicators',
 'Cross-council indicators',
 'Consolidated per-council counts of open applications and open disciplinary cases (HPA oversight).',
 'SELECT c.council_code, '
 || '(SELECT count(*) FROM varapi.provider_applications a WHERE a.council_id = c.id AND a.workflow_state <> ''CLOSED'') AS open_applications, '
 || '(SELECT count(*) FROM varapi.provider_disciplinary_cases d WHERE d.council_id = c.id AND d.status = ''OPEN'') AS open_disciplinary '
 || 'FROM varapi.councils c WHERE c.council_type IN (''HPA'',''PROFESSIONAL_COUNCIL'')',
 '{}'::jsonb, 'JSON', 'ACTIVE', 'rom-seed', 'OVERSIGHT')
ON CONFLICT (tenant_id, report_key) DO NOTHING;
