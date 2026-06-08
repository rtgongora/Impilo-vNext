-- Governed MOHCC national training catalogue content (replaces bootstrap placeholder prose).

UPDATE lrn_course
SET description = 'National orientation for regulated and facility staff using Impilo vNext. Covers trust headers, consent, person-centred identity, and safe navigation of the One Experience Shell.',
    estimated_duration_minutes = 45
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001'::uuid;

UPDATE lrn_course
SET description = 'Foundational course for clinicians and registry staff: workspace zones, patient search, command palette, and documentation boundaries under TSHEPO policy.',
    estimated_duration_minutes = 60
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Digital health in Zimbabwe connects citizens, providers, and public-health programmes through governed identity, consent, and longitudinal records. Impilo vNext is the national Health Operating System shell — not a single app — that orchestrates regulated workflows while keeping clinical truth in sovereign services.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0001-0001-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Impilo provides one person anchor (Health ID), many role activations (Provider ID, Staff ID), and context headers (facility, workspace, purpose of use). Every production request is authorized through Envoy → TSHEPO before reaching domain services.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0001-0001-0000-000000000002'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Lawful processing requires a documented purpose of use, valid consent or legal basis, and audit traceability. Learners must never copy patient identifiable information into informal channels; PII stays in VITO and clinical identifiers in BUTANO use CPID only.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0001-0002-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_type = 'VIDEO',
    content_ref = 'https://www.youtube.com/watch?v=ImpiloEhrIntro',
    content_body = 'Orientation video: zones, role journeys, facility context, and the command palette. Complete the checklist in the next module before attempting the EHR navigation quiz.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0002-0001-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Press Ctrl+K (or use the shell search affordance) to open the global command palette. Start workflows from search results rather than bookmarking deep URLs — the shell adapts visibility by active role and facility context.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0002-0002-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Clinical tools (orders, results, documents, prescribing where licensed) are surfaced by role and context. If a tool is disabled, check Provider ID activation, facility affiliation, and TSHEPO policy — do not bypass authorization.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0002-0002-0000-000000000002'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Capture demographics against the Health ID anchor. Verify identity assurance level before merging records. Use the intake deduplication rail when suggested matches appear — never create duplicate sovereign identities.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0003-0001-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Consent templates and capture flows live in the registry/Mvumo boundary. Document who consented, which version applied, and the lawful basis. Withdrawal and restriction flags must be respected in downstream clinical views.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0003-0001-0000-000000000002'::uuid;

UPDATE lrn_course_lesson
SET content_body = 'Daily: reconcile new registrations and discharges. Weekly: review duplicate-suspect queue with the data steward. Monthly: export facility KPIs and close corrective actions with named owners.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0004-0001-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = '["Review flagged duplicate records with the facility data steward","Document corrective action in the facility DQ log","Confirm resolution in the weekly DQ huddle"]',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0004-0002-0000-000000000001'::uuid
  AND content_type = 'PRACTICAL_TASK';
