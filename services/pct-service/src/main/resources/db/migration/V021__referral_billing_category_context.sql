-- Billing context on telemedicine referrals: patient_category and facility_category.
-- Captured at referral time (composed by the BFF from coverage/registry) and propagated
-- to COSTA on the TELECONSULT_COMPLETED value-trigger so charging rules keyed on these
-- dimensions (exemptions, waivers, surcharges) can fire when the teleconsult is priced.

ALTER TABLE pct_referrals
    ADD COLUMN IF NOT EXISTS patient_category  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS facility_category VARCHAR(64);
