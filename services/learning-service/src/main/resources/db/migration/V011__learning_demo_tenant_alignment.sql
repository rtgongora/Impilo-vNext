-- Align built-in Fundo demo rows with the canonical local-dev tenant used by one-ui-shell.
-- This intentionally targets only deterministic demo seed rows from V007, not tenant data broadly.

UPDATE lrn_course
SET tenant_id = '00000000-0000-4000-8000-000000000001'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND id IN (
      'aaaaaaaa-0000-0000-0000-000000000001',
      'aaaaaaaa-0000-0000-0000-000000000002',
      'aaaaaaaa-0000-0000-0000-000000000003',
      'aaaaaaaa-0000-0000-0000-000000000004'
  );

UPDATE lrn_course_module
SET tenant_id = '00000000-0000-4000-8000-000000000001'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND id IN (
      'bbbbbbbb-0001-0000-0000-000000000001',
      'bbbbbbbb-0001-0000-0000-000000000002',
      'bbbbbbbb-0002-0000-0000-000000000001',
      'bbbbbbbb-0002-0000-0000-000000000002',
      'bbbbbbbb-0003-0000-0000-000000000001',
      'bbbbbbbb-0004-0000-0000-000000000001',
      'bbbbbbbb-0004-0000-0000-000000000002'
  );

UPDATE lrn_course_lesson
SET tenant_id = '00000000-0000-4000-8000-000000000001'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND id IN (
      'cccccccc-0001-0001-0000-000000000001',
      'cccccccc-0001-0001-0000-000000000002',
      'cccccccc-0001-0002-0000-000000000001',
      'cccccccc-0002-0001-0000-000000000001',
      'cccccccc-0002-0002-0000-000000000001',
      'cccccccc-0002-0002-0000-000000000002',
      'cccccccc-0003-0001-0000-000000000001',
      'cccccccc-0003-0001-0000-000000000002',
      'cccccccc-0004-0001-0000-000000000001',
      'cccccccc-0004-0002-0000-000000000001'
  );

UPDATE lrn_assessment
SET tenant_id = '00000000-0000-4000-8000-000000000001'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND id = 'dddddddd-0002-0000-0000-000000000001';

UPDATE lrn_fundo_pathway
SET tenant_id = '00000000-0000-4000-8000-000000000001'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
  AND id = 'ffffffff-0000-0000-0000-000000000001';
