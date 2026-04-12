-- Health Connect parity uses longer vital_type tokens than the original V26 VARCHAR(50).
ALTER TABLE wellness_vitals_log
    ALTER COLUMN vital_type TYPE VARCHAR(80);
