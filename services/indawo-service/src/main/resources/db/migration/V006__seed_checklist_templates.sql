-- ============================================================================
-- indawo-service V004 — Seed inspection checklist templates (system tenant)
-- NOTE: Seeded under tenant_id = 00000000-0000-0000-0000-000000000000 and
-- used as fallback templates for any tenant.
-- ============================================================================

DO $$
DECLARE
  sys_tenant UUID := '00000000-0000-0000-0000-000000000000';
  tpl_food   UUID := gen_random_uuid();
  tpl_school UUID := gen_random_uuid();
  tpl_market UUID := gen_random_uuid();
  tpl_abattoir UUID := gen_random_uuid();
BEGIN
  -- Food premises (INITIAL)
  INSERT INTO ind_checklist_templates(template_id, tenant_id, code, version, name, applicable_site_category, applicable_inspection_type, active_flag)
  VALUES (tpl_food, sys_tenant, 'FOOD_PREMISES_INITIAL', 1, 'Food Premises - Initial Inspection', 'FOOD_PREMISES', 'INITIAL', TRUE)
  ON CONFLICT DO NOTHING;

  INSERT INTO ind_checklist_items(item_id, template_id, section, code, requirement_text, severity, evidence_type, pass_criteria, critical_flag, display_order)
  VALUES
    (gen_random_uuid(), tpl_food, 'Premises', 'FOOD-PREM-01', 'Food preparation areas are clean and free from pests.', 'CRITICAL', 'PHOTO', 'No visible pest activity; surfaces clean.', TRUE, 10),
    (gen_random_uuid(), tpl_food, 'Water & Sanitation', 'FOOD-WASH-01', 'Handwashing facilities available with soap and clean water.', 'CRITICAL', 'PHOTO', 'Handwashing station present and functional.', TRUE, 20),
    (gen_random_uuid(), tpl_food, 'Temperature Control', 'FOOD-TEMP-01', 'Cold chain maintained for perishable foods.', 'MAJOR', 'NOTE', 'Cold storage operating within safe temperature range.', FALSE, 30),
    (gen_random_uuid(), tpl_food, 'Records', 'FOOD-REC-01', 'Basic cleaning schedule documented.', 'MINOR', 'DOC', 'Schedule exists and is up to date.', FALSE, 40);

  -- School/ECD (ROUTINE)
  INSERT INTO ind_checklist_templates(template_id, tenant_id, code, version, name, applicable_site_category, applicable_inspection_type, active_flag)
  VALUES (tpl_school, sys_tenant, 'SCHOOL_ECD_ROUTINE', 1, 'School / ECD - Routine Inspection', 'SCHOOL_ECD', 'ROUTINE', TRUE)
  ON CONFLICT DO NOTHING;

  INSERT INTO ind_checklist_items(item_id, template_id, section, code, requirement_text, severity, evidence_type, pass_criteria, critical_flag, display_order)
  VALUES
    (gen_random_uuid(), tpl_school, 'Sanitation', 'SCH-SAN-01', 'Toilets are functional, clean, and separated as required.', 'CRITICAL', 'PHOTO', 'Facilities are usable, clean, and appropriately segregated.', TRUE, 10),
    (gen_random_uuid(), tpl_school, 'Water', 'SCH-WAT-01', 'Safe drinking water available to learners.', 'CRITICAL', 'PHOTO', 'Water source present and accessible.', TRUE, 20),
    (gen_random_uuid(), tpl_school, 'Waste', 'SCH-WST-01', 'Waste disposal is safe and does not attract vectors.', 'MAJOR', 'PHOTO', 'Bins present; waste managed appropriately.', FALSE, 30);

  -- Market/public premises (ROUTINE)
  INSERT INTO ind_checklist_templates(template_id, tenant_id, code, version, name, applicable_site_category, applicable_inspection_type, active_flag)
  VALUES (tpl_market, sys_tenant, 'MARKET_PUBLIC_ROUTINE', 1, 'Market / Public Premises - Routine Inspection', 'MARKET_PUBLIC', 'ROUTINE', TRUE)
  ON CONFLICT DO NOTHING;

  INSERT INTO ind_checklist_items(item_id, template_id, section, code, requirement_text, severity, evidence_type, pass_criteria, critical_flag, display_order)
  VALUES
    (gen_random_uuid(), tpl_market, 'Hygiene', 'MRK-HYG-01', 'Public areas are maintained in hygienic condition.', 'MAJOR', 'PHOTO', 'No excessive waste; cleaning evident.', FALSE, 10),
    (gen_random_uuid(), tpl_market, 'Drainage', 'MRK-DRN-01', 'Drainage is functional with no stagnant water.', 'CRITICAL', 'PHOTO', 'No stagnant water accumulation.', TRUE, 20),
    (gen_random_uuid(), tpl_market, 'Vendors', 'MRK-VND-01', 'Food vendors adhere to basic hygiene practices.', 'MAJOR', 'NOTE', 'Observed compliance with hygiene practices.', FALSE, 30);

  -- Abattoir (SPECIAL)
  INSERT INTO ind_checklist_templates(template_id, tenant_id, code, version, name, applicable_site_category, applicable_inspection_type, active_flag)
  VALUES (tpl_abattoir, sys_tenant, 'ABATTOIR_SPECIAL', 1, 'Abattoir - Special Inspection', 'ABATTOIR', 'SPECIAL', TRUE)
  ON CONFLICT DO NOTHING;

  INSERT INTO ind_checklist_items(item_id, template_id, section, code, requirement_text, severity, evidence_type, pass_criteria, critical_flag, display_order)
  VALUES
    (gen_random_uuid(), tpl_abattoir, 'Hygiene', 'ABA-HYG-01', 'Carcass handling areas are sanitized and controlled.', 'CRITICAL', 'PHOTO', 'Sanitation controls in place; no cross-contamination.', TRUE, 10),
    (gen_random_uuid(), tpl_abattoir, 'Waste', 'ABA-WST-01', 'Offal and waste are managed safely.', 'CRITICAL', 'PHOTO', 'Waste stored/disposed safely.', TRUE, 20),
    (gen_random_uuid(), tpl_abattoir, 'Cold Chain', 'ABA-TEMP-01', 'Cold chain maintained for storage/transport.', 'MAJOR', 'NOTE', 'Temperatures logged and within safe limits.', FALSE, 30);
END $$;

