-- =============================================================================
-- VITO — Client Registry Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Clients: 8 representative patients covering diverse demographics
-- Health IDs are deterministic UUIDs for reproducible development environments
-- NOTE: phone_hash is a placeholder SHA-256 hex (not a real HMAC in dev)
-- =============================================================================

\c vito

INSERT INTO vito.client
    (health_id, tenant_id, given_name, family_name, date_of_birth, sex, phone_hash, status)
VALUES
    ('b0000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     'Tatenda', 'Moyo', '1990-03-15', 'MALE',
     'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000001',
     'Rudo', 'Chikwanha', '1985-07-22', 'FEMALE',
     'b3a8e0e1f9ab1bfe3a36f231f676f78bb28a2d0acc01a6f4dc5c2b9f9d5b9e5d', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000003',
     '00000000-0000-4000-8000-000000000001',
     'Tinashe', 'Madziva', '2000-01-10', 'MALE',
     'c4f39d1b7a2e3f5a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000004',
     '00000000-0000-4000-8000-000000000001',
     'Nyasha', 'Sibanda', '1978-11-28', 'FEMALE',
     'd5e48f2c8b3d4e6b9c0a1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000005',
     '00000000-0000-4000-8000-000000000001',
     'Kudakwashe', 'Dube', '1995-05-03', 'MALE',
     'e6f59a3d9c4e5f7cabd12e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000006',
     '00000000-0000-4000-8000-000000000001',
     'Chiedza', 'Mutasa', '2019-06-15', 'FEMALE',
     'f7a60b4e0d5f6a8dbce23f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000007',
     '00000000-0000-4000-8000-000000000001',
     'Simba', 'Nyamukapa', '1965-09-30', 'MALE',
     'a8b71c5f1e6a7b9ecdf34a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000008',
     '00000000-0000-4000-8000-000000000001',
     'Mavis', 'Choto', '1952-02-14', 'FEMALE',
     'b9c82d6a2f7b8c0fde45a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7', 'ACTIVE'),

    ('b0000000-0000-4000-8000-000000000010',
     '00000000-0000-4000-8000-000000000001',
     'System', 'Admin', '1985-01-01', 'MALE',
     'c0d91e7b3f8a2b4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9', 'ACTIVE');

INSERT INTO vito.identity_alias
    (tenant_id, health_id, alias_type, lookup_hash, verifier, status)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000001',
     'IMPILO_ID',
     'hash_cpid_zw_00001_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_1$dev_verifier_cpid_zw_00001',
     'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000002',
     'IMPILO_ID',
     'hash_cpid_zw_00002_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_2$dev_verifier_cpid_zw_00002',
     'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000003',
     'IMPILO_ID',
     'hash_cpid_zw_00003_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_3$dev_verifier_cpid_zw_00003',
     'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000004',
     'IMPILO_ID',
     'hash_cpid_zw_00004_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_4$dev_verifier_cpid_zw_00004',
     'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000005',
     'IMPILO_ID',
     'hash_cpid_zw_00005_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_5$dev_verifier_cpid_zw_00005',
     'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000010',
     'IMPILO_ID',
     'hash_cpid_zw_admin_dev', '$argon2id$v=19$m=65536,t=3,p=4$dev_salt_admin$dev_verifier_cpid_zw_admin',
     'ACTIVE');
