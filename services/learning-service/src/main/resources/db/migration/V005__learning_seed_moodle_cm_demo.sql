-- Demo: map a catalog row to a fictional Moodle CM id for WS sync smoke tests (tenant seed).
UPDATE lrn_learning_resource
SET moodle_cm_id = 91001
WHERE id = '11111111-1111-1111-1111-111111111001'
  AND tenant_id = '00000000-0000-0000-0000-000000000001';
