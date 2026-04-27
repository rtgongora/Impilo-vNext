-- Additional COSTA tariff lists: upload-channel examples (metadata) + one retired row for UI grouping.
-- Doctrine: operational lists must have reference_only = false and approved_for_billing = true unless clearly reference-only.

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-COUNTRY-DEMO', 'Country-uploaded tariff (demo)', 'Example country upload — synthetic items to be replaced by real import pipeline.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'country', 'provenance', 'seed', 'note', 'Demonstration only until national file is imported and approved.')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-PROVIDER-DEMO', 'Provider-uploaded tariff (demo)', 'Example facility/provider schedule — replace with governed import.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'provider', 'provenance', 'seed')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-PAYER-DEMO', 'Payer-uploaded tariff (demo)', 'Example payer schedule for adjudication alignment — not a national price list.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'payer', 'provenance', 'seed')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-DONOR-DEMO', 'Donor-uploaded tariff (demo)', 'Example programme/donor-aligned schedule for grants and vertical programmes.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'donor', 'provenance', 'seed')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-PROGRAMME-DEMO', 'Programme-uploaded tariff (demo)', 'Example disease programme schedule (HIV/TB/etc.) — replace with approved national pack.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'programme', 'provenance', 'seed')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'UPL-ORG-DEMO', 'Organisation-uploaded tariff (demo)', 'Example NGO / employer organisation schedule — synthetic until contract import.', 'operational_upload', 'operational', 'uploaded_rate', 'operational', 'pending_review', false, true, 'USD', DATE '2024-01-01',
    jsonb_build_object('upload_channel', 'organisation', 'provenance', 'seed')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

-- Retired example: was reference, now closed — blocks new billing via effective_to + approved_for_billing false
INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, effective_to, metadata)
SELECT NULL, id, 'LEGACY-REF-2023', 'Legacy reference pack (retired)', 'Superseded reference pack kept for audit only.', 'legacy', 'reference', 'reference_rate', 'retired', 'superseded', true, false, 'USD', DATE '2018-01-01', DATE '2023-12-31',
    jsonb_build_object('retired', true, 'note', 'Do not use for new billing or MusheX handoff.')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';
