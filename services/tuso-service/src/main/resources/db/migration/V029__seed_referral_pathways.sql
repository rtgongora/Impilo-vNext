-- Durable referral-pathway derivation (promoted from scripts/seed/16-derive-referral-pathways.sql).
-- Runs after V027 seeds the master facilities. Idempotent (NOT EXISTS + metadata.derived=true);
-- never overwrites manually curated REFERS_TO edges. Structural registry truth by level/district.

-- Referral pathway derivation (REFERS_TO) over the absorbed master facility registry.
--
-- Zimbabwe referral doctrine, derived structurally (idempotent, never overwrites
-- manually curated relationships):
--   PRIMARY   -> nearest-by-name SECONDARY in the same district,
--                else any SECONDARY/PROVINCIAL/TERTIARY in the same province
--   SECONDARY -> PROVINCIAL/TERTIARY in the same province,
--                else CENTRAL/QUATERNARY nationally
--   PROVINCIAL/TERTIARY -> CENTRAL/QUATERNARY nationally
--
-- metadata.derived = true marks rows as derivable (safe to re-derive); manual
-- referral curation should omit that flag and will never be touched here.

-- PRIMARY -> same-district SECONDARY (first by name for determinism)
INSERT INTO tuso.facility_relationship (tenant_id, source_id, target_id, relationship, metadata, active)
SELECT DISTINCT ON (src.id)
    src.tenant_id, src.id, tgt.id, 'REFERS_TO',
    jsonb_build_object('derived', true, 'basis', 'same_district_secondary'), true
FROM tuso.facility src
JOIN tuso.facility tgt
  ON tgt.tenant_id = src.tenant_id
 AND tgt.district = src.district
 AND tgt.level = 'SECONDARY'
 AND tgt.id <> src.id
WHERE src.level = 'PRIMARY'
  AND NOT EXISTS (
      SELECT 1 FROM tuso.facility_relationship r
      WHERE r.tenant_id = src.tenant_id AND r.source_id = src.id AND r.relationship = 'REFERS_TO'
  )
ORDER BY src.id, tgt.name
ON CONFLICT DO NOTHING;

-- PRIMARY without a district secondary -> same-province SECONDARY/PROVINCIAL/TERTIARY
INSERT INTO tuso.facility_relationship (tenant_id, source_id, target_id, relationship, metadata, active)
SELECT DISTINCT ON (src.id)
    src.tenant_id, src.id, tgt.id, 'REFERS_TO',
    jsonb_build_object('derived', true, 'basis', 'same_province_higher'), true
FROM tuso.facility src
JOIN tuso.facility tgt
  ON tgt.tenant_id = src.tenant_id
 AND tgt.province = src.province
 AND tgt.level IN ('SECONDARY', 'PROVINCIAL', 'TERTIARY')
 AND tgt.id <> src.id
WHERE src.level = 'PRIMARY'
  AND NOT EXISTS (
      SELECT 1 FROM tuso.facility_relationship r
      WHERE r.tenant_id = src.tenant_id AND r.source_id = src.id AND r.relationship = 'REFERS_TO'
  )
ORDER BY src.id, CASE tgt.level WHEN 'SECONDARY' THEN 0 WHEN 'PROVINCIAL' THEN 1 ELSE 2 END, tgt.name
ON CONFLICT DO NOTHING;

-- SECONDARY -> same-province PROVINCIAL/TERTIARY, else national CENTRAL/QUATERNARY
INSERT INTO tuso.facility_relationship (tenant_id, source_id, target_id, relationship, metadata, active)
SELECT DISTINCT ON (src.id)
    src.tenant_id, src.id, tgt.id, 'REFERS_TO',
    jsonb_build_object('derived', true, 'basis', 'province_or_national_tertiary'), true
FROM tuso.facility src
JOIN tuso.facility tgt
  ON tgt.tenant_id = src.tenant_id
 AND (
      (tgt.province = src.province AND tgt.level IN ('PROVINCIAL', 'TERTIARY'))
      OR tgt.level IN ('CENTRAL', 'QUATERNARY')
 )
 AND tgt.id <> src.id
WHERE src.level = 'SECONDARY'
  AND NOT EXISTS (
      SELECT 1 FROM tuso.facility_relationship r
      WHERE r.tenant_id = src.tenant_id AND r.source_id = src.id AND r.relationship = 'REFERS_TO'
  )
ORDER BY src.id,
         CASE WHEN tgt.province = src.province THEN 0 ELSE 1 END,
         CASE tgt.level WHEN 'PROVINCIAL' THEN 0 WHEN 'TERTIARY' THEN 1 WHEN 'CENTRAL' THEN 2 ELSE 3 END,
         tgt.name
ON CONFLICT DO NOTHING;

-- PROVINCIAL/TERTIARY -> national CENTRAL/QUATERNARY
INSERT INTO tuso.facility_relationship (tenant_id, source_id, target_id, relationship, metadata, active)
SELECT DISTINCT ON (src.id)
    src.tenant_id, src.id, tgt.id, 'REFERS_TO',
    jsonb_build_object('derived', true, 'basis', 'national_apex'), true
FROM tuso.facility src
JOIN tuso.facility tgt
  ON tgt.tenant_id = src.tenant_id
 AND tgt.level IN ('CENTRAL', 'QUATERNARY')
 AND tgt.id <> src.id
WHERE src.level IN ('PROVINCIAL', 'TERTIARY')
  AND NOT EXISTS (
      SELECT 1 FROM tuso.facility_relationship r
      WHERE r.tenant_id = src.tenant_id AND r.source_id = src.id AND r.relationship = 'REFERS_TO'
  )
ORDER BY src.id, tgt.name
ON CONFLICT DO NOTHING;
