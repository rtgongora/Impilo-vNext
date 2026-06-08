-- Enrich native Fundo seed lessons with video embed and structured practical checklist.

UPDATE lrn_course_lesson
SET content_type = 'VIDEO',
    content_ref = 'https://www.youtube.com/watch?v=ImpiloEhrIntro',
    content_body = 'Watch the Impilo EHR orientation video before completing the module quiz.',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0002-0001-0000-000000000001'::uuid
  AND tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

UPDATE lrn_course_lesson
SET content_body = '["Review flagged duplicate records with the facility data steward","Document corrective action in the facility DQ log","Confirm resolution in the weekly DQ huddle"]',
    content_format = 'PLAIN_TEXT'
WHERE id = 'cccccccc-0004-0002-0000-000000000001'::uuid
  AND tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND content_type = 'PRACTICAL_TASK';
