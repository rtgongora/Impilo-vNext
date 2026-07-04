-- Preview tariff seed — AHFOZ-indicative rates.
--
-- costa_tariffs was empty in every environment, so the TARIFF cost engine
-- priced every bill line at zero and no bill could ever carry an amount.
--
-- Zimbabwe's funders bill against the AHFOZ (Association of Healthcare
-- Funders of Zimbabwe) tariff schedule. These rows are INDICATIVE preview
-- placeholders in AHFOZ style — NOT the licensed schedule. The governed path
-- for the real schedule is the tariff import pipeline
-- (POST /costa/v1/tariffs/import + costa_tariff_upload_batches review flow),
-- which supersedes these rows on approval.
--
-- Idempotent via WHERE NOT EXISTS on (tenant, msika_code).

INSERT INTO costa_tariffs
    (tenant_id, tariff_code, msika_code, description, price, currency,
     rules, effective_from, version, status, created_at, updated_at)
SELECT '00000000-0000-4000-8000-000000000001'::uuid,
       t.tariff_code, t.msika_code, t.description, t.price, 'USD',
       '{"provenance":"preview-seed","schedule":"AHFOZ-indicative","note":"replace via governed AHFOZ import"}',
       CURRENT_DATE, 1, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('AHFOZ-IND-GP-CONS',   'CONSULT-GP',  'General practitioner consultation (AHFOZ-indicative)', 25.00),
    ('AHFOZ-IND-PATH-FBC',  'LAB-FBC',     'Full blood count — pathology (AHFOZ-indicative)',       12.50),
    ('AHFOZ-IND-RAD-CXR',   'XRAY-CHEST',  'Chest X-ray — radiology (AHFOZ-indicative)',            40.00),
    ('AHFOZ-IND-GP-TELE',   'TELECON-GP',  'Teleconsultation, GP (AHFOZ-indicative)',               18.00)
) AS t(tariff_code, msika_code, description, price)
WHERE NOT EXISTS (
    SELECT 1 FROM costa_tariffs c
    WHERE c.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND c.msika_code = t.msika_code
);
