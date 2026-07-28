-- The first ORDER_SET step-type content (W7b) — the Sepsis Six bundle on ED_SEPSIS.
--
-- pathway_steps.step_type has never been a constrained vocabulary (no CHECK exists on it, and
-- PathwaySessionService treats it as opaque metadata it only echoes back), so 'ORDER_SET' needs no
-- schema change on this service at all — this migration is content only. The real new schema for
-- order sets is on the pct side (emergency_order_set_instance/_item, same wave), because CKP holds
-- what a bundle IS and pct tracks what actually happened when a clinician invoked one for a patient.
--
-- Added as step 3 on the existing ED_SEPSIS pathway (id f0000001-...-0101) rather than editing step 2
-- (TREATMENT, repaired in W4b) — step 2's narrative guidance stays as-is; this is the structured,
-- machine-readable version of the same bundle a pct emergency_order_set_instance is created from.
--
-- Six items, matching the well-known "Sepsis Six" — cultures before antibiotics, lactate, and
-- fluids/oxygen/antibiotics/urine-output as the six actions within the first hour. Cites the same
-- EMS.SSC26.SCREENING standard already declared for the sepsis screen (see W4b, section 0205).

INSERT INTO clinical.pathway_steps
    (pathway_id, step_order, step_type, prompt, data_capture_schema, branch_logic, recommendation_logic, source_section_id)
VALUES
('f0000001-0001-4001-8001-000000000101', 3, 'ORDER_SET',
 'Sepsis Six bundle — order or explicitly decline each item (a decline requires a reason).',
 $j${
   "order_set_code": "SEPSIS_SIX_BUNDLE",
   "order_set_name": "Sepsis Six Bundle",
   "items": [
     {"order_code": "BLOOD_CULTURES", "order_label": "Blood cultures (before antibiotics)", "order_type": "LAB"},
     {"order_code": "LACTATE", "order_label": "Serum lactate", "order_type": "LAB"},
     {"order_code": "URINE_OUTPUT_MONITORING", "order_label": "Hourly urine output monitoring", "order_type": "OBSERVATION"},
     {"order_code": "HIGH_FLOW_OXYGEN", "order_label": "High-flow oxygen to target saturation", "order_type": "TREATMENT"},
     {"order_code": "BROAD_SPECTRUM_ABX", "order_label": "Broad-spectrum IV antibiotics per local protocol", "order_type": "MEDICATION"},
     {"order_code": "CRYSTALLOID_30ML_KG", "order_label": "30 mL/kg crystalloid for hypotension or lactate >= 4", "order_type": "MEDICATION"}
   ]
 }$j$::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000205')
ON CONFLICT (pathway_id, step_order) DO NOTHING;
