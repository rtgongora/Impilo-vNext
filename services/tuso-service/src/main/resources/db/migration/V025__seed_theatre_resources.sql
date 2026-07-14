-- tuso-service V025 — seed theatre resources for two central hospitals (Wave 2).
-- Idempotent + coexisting: facilities, service points, spaces, beds, equipment and
-- instrument sets are all inserted with ON CONFLICT DO NOTHING / WHERE NOT EXISTS,
-- so a Wave-0 agent seeding a few THEATRE service_points does not collide.

DO $$
DECLARE
    v_tenant   UUID := '00000000-0000-0000-0000-000000000001';
    v_fac      RECORD;
    v_fid      BIGINT;
    v_sp       UUID;
    v_space    UUID;
    v_theatre  TEXT;
    v_theatres TEXT[] := ARRAY['Theatre 1','Theatre 2','Theatre 3'];
    v_caps     JSONB[] := ARRAY['["GENERAL","EMERGENCY"]','["ORTHOPAEDIC","GENERAL"]','["OBSTETRIC","GENERAL"]']::jsonb[];
    i          INT;
BEGIN
    FOR v_fac IN SELECT * FROM (VALUES
            ('HRE-CENTRAL', 'Harare Central Hospital',           'Harare', 'Harare'),
            ('PARI-GROUP',  'Parirenyatwa Group of Hospitals',   'Harare', 'Harare')
        ) AS f(code, name, province, district)
    LOOP
        INSERT INTO tuso.facility (tenant_id, facility_code, name, province, district)
        VALUES (v_tenant, v_fac.code, v_fac.name, v_fac.province, v_fac.district)
        ON CONFLICT (tenant_id, facility_code) DO NOTHING;
        SELECT id INTO v_fid FROM tuso.facility WHERE tenant_id = v_tenant AND facility_code = v_fac.code;

        -- Three theatres, each paired 1:1 with a THEATRE service_point.
        FOR i IN 1 .. array_length(v_theatres, 1) LOOP
            v_theatre := v_theatres[i];
            IF NOT EXISTS (SELECT 1 FROM tuso.service_point
                           WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = v_theatre) THEN
                INSERT INTO tuso.service_point (tenant_id, facility_id, name, code, service_point_type, status)
                VALUES (v_tenant, v_fid, v_theatre, 'THR-' || i, 'THEATRE', 'ACTIVE');
            END IF;
            SELECT id INTO v_sp FROM tuso.service_point
                WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = v_theatre LIMIT 1;

            INSERT INTO tuso.clinical_space
                (tenant_id, facility_id, service_point_id, name, space_type, capacity, operating_hours, capability_tags)
            VALUES (v_tenant, v_fid, v_sp, v_theatre, 'OPERATING_THEATRE', 1,
                    '{"mon_fri":"08:00-17:00"}'::jsonb, v_caps[i])
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- Recovery / critical-care / ward spaces.
        INSERT INTO tuso.clinical_space (tenant_id, facility_id, name, space_type, capacity, capability_tags) VALUES
            (v_tenant, v_fid, 'PACU',            'PACU', 6,  '[]'::jsonb),
            (v_tenant, v_fid, 'ICU',             'ICU',  8,  '[]'::jsonb),
            (v_tenant, v_fid, 'HDU',             'HDU',  6,  '[]'::jsonb),
            (v_tenant, v_fid, 'Surgical Ward A', 'WARD', 30, '[]'::jsonb),
            (v_tenant, v_fid, 'Maternity Ward',  'WARD', 24, '["OBSTETRIC"]'::jsonb)
        ON CONFLICT DO NOTHING;

        -- Beds by class.
        SELECT id INTO v_space FROM tuso.clinical_space WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = 'PACU';
        FOR i IN 1 .. 6 LOOP
            INSERT INTO tuso.bed (tenant_id, clinical_space_id, code, bed_class, status)
            VALUES (v_tenant, v_space, 'PACU-' || i, 'RECOVERY', 'AVAILABLE') ON CONFLICT DO NOTHING;
        END LOOP;
        SELECT id INTO v_space FROM tuso.clinical_space WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = 'ICU';
        FOR i IN 1 .. 8 LOOP
            INSERT INTO tuso.bed (tenant_id, clinical_space_id, code, bed_class, status)
            VALUES (v_tenant, v_space, 'ICU-' || i, 'ICU', 'AVAILABLE') ON CONFLICT DO NOTHING;
        END LOOP;
        SELECT id INTO v_space FROM tuso.clinical_space WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = 'HDU';
        FOR i IN 1 .. 6 LOOP
            INSERT INTO tuso.bed (tenant_id, clinical_space_id, code, bed_class, status)
            VALUES (v_tenant, v_space, 'HDU-' || i, 'HDU', 'AVAILABLE') ON CONFLICT DO NOTHING;
        END LOOP;
        SELECT id INTO v_space FROM tuso.clinical_space WHERE tenant_id = v_tenant AND facility_id = v_fid AND name = 'Surgical Ward A';
        FOR i IN 1 .. 30 LOOP
            INSERT INTO tuso.bed (tenant_id, clinical_space_id, code, bed_class, status)
            VALUES (v_tenant, v_space, 'SWA-' || i, 'WARD', 'AVAILABLE') ON CONFLICT DO NOTHING;
        END LOOP;

        -- Theatre equipment with realistic maintenance states.
        INSERT INTO tuso.equipment_asset (tenant_id, facility_id, name, equipment_type, maintenance_state, status) VALUES
            (v_tenant, v_fid, 'Anaesthetic Machine A',   'ANAESTHETIC_MACHINE', 'OPERATIONAL',    'AVAILABLE'),
            (v_tenant, v_fid, 'Anaesthetic Machine B',   'ANAESTHETIC_MACHINE', 'DUE_SERVICE',    'AVAILABLE'),
            (v_tenant, v_fid, 'C-Arm Image Intensifier', 'C_ARM',               'OPERATIONAL',    'AVAILABLE'),
            (v_tenant, v_fid, 'Diathermy Unit',          'DIATHERMY',           'OPERATIONAL',    'AVAILABLE'),
            (v_tenant, v_fid, 'Diathermy Unit (spare)',  'DIATHERMY',           'OUT_OF_SERVICE', 'AVAILABLE')
        ON CONFLICT DO NOTHING;

        -- Instrument sets across the sterilisation lifecycle.
        INSERT INTO tuso.instrument_set (tenant_id, facility_id, name, sterilisation_state, sterile_until) VALUES
            (v_tenant, v_fid, 'General Surgery Set 1', 'STERILE',      now() + interval '14 days'),
            (v_tenant, v_fid, 'General Surgery Set 2', 'REPROCESSING', NULL),
            (v_tenant, v_fid, 'Orthopaedic Major Set', 'STERILE',      now() + interval '7 days'),
            (v_tenant, v_fid, 'Laparotomy Set',        'IN_USE',       NULL),
            (v_tenant, v_fid, 'Caesarean Set',         'EXPIRED',      now() - interval '2 days')
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;
