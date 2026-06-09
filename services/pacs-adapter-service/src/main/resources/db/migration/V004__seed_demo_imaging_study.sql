-- Demo imaging study for golden patient (compose smoke / e2e).

INSERT INTO pacs.imaging_study (
    tenant_id,
    patient_cpid,
    study_uid,
    modality,
    description,
    study_date,
    status,
    orthanc_id
) VALUES (
    '00000000-0000-4000-8000-000000000001',
    'CPID-ZW-00001',
    '1.2.840.IMPILO.DEMO.CHEST.XRAY.00001',
    'DX',
    'Chest X-ray — demo study',
    now(),
    'AVAILABLE',
    'demo-chest-xray-orthanc-001'
) ON CONFLICT (study_uid) DO NOTHING;
