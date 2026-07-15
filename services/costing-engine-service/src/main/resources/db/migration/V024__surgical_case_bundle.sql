-- Wave 4 §5 — Surgical-case bundle tariff seed (AHFOZ-indicative).
--
-- The surgical-case bundle (InpatientCostingService.postSurgicalCaseBundle) composes theatre time +
-- anaesthesia + implant + consumables + blood as itemised lines over these tariff codes. The TARIFF cost
-- engine matches on msika_code, so these codes MUST equal the codes the bundle poster emits:
--   THEATRE-TIME, ANAESTHESIA-GA, THEATRE-IMPLANT, THEATRE-CONSUMABLE, THEATRE-BLOOD.
-- Without a matching tariff a line prices to zero and pending_pricing blocks finalisation.
--
-- INDICATIVE preview placeholders in AHFOZ style — NOT the licensed schedule. The governed real-schedule
-- path (POST /costa/v1/tariffs/import + costa_tariff_upload_batches review) supersedes these on approval.
-- Idempotent via WHERE NOT EXISTS on (tenant, msika_code).
--
-- THEATRE-TIME / ANAESTHESIA-GA are priced per MINUTE (the bundle posts qty = theatre minutes);
-- IMPLANT / CONSUMABLE / BLOOD are priced per UNIT (qty = usage count).

INSERT INTO costa_tariffs
    (tenant_id, tariff_code, msika_code, description, price, currency,
     rules, effective_from, version, status, created_at, updated_at)
SELECT '00000000-0000-4000-8000-000000000001'::uuid,
       t.tariff_code, t.msika_code, t.description, t.price, 'USD',
       '{"provenance":"preview-seed","schedule":"AHFOZ-indicative","bundle":"SURGICAL_CASE","note":"replace via governed AHFOZ import"}',
       CURRENT_DATE, 1, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('AHFOZ-IND-THEATRE-MIN',  'THEATRE-TIME',       'Theatre time, per minute (AHFOZ-indicative)',            3.50),
    ('AHFOZ-IND-ANAES-GA-MIN', 'ANAESTHESIA-GA',     'General anaesthesia, per minute (AHFOZ-indicative)',     2.00),
    ('AHFOZ-IND-IMPLANT',      'THEATRE-IMPLANT',    'Surgical implant, per unit (AHFOZ-indicative)',        120.00),
    ('AHFOZ-IND-CONSUMABLE',   'THEATRE-CONSUMABLE', 'Theatre consumable, per unit (AHFOZ-indicative)',        8.00),
    ('AHFOZ-IND-BLOOD-UNIT',   'THEATRE-BLOOD',      'Blood product, per unit (AHFOZ-indicative)',            45.00)
) AS t(tariff_code, msika_code, description, price)
WHERE NOT EXISTS (
    SELECT 1 FROM costa_tariffs c
    WHERE c.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND c.msika_code = t.msika_code
);
