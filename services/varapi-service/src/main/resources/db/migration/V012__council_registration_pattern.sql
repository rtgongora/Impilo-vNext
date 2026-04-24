-- Optional per-council registration number format (Java regex) for collaboration validation.

ALTER TABLE varapi.councils
    ADD COLUMN IF NOT EXISTS registration_number_pattern VARCHAR(512);

COMMENT ON COLUMN varapi.councils.registration_number_pattern IS
    'Optional Java regex for council registration numbers; used when validating external collaboration profiles.';
