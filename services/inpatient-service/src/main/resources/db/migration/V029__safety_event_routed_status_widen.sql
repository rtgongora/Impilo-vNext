-- inpatient-service V029 — fix: widen procedure_safety_event.routed_status (Wave 3 clinical-safety fix).
--
-- DEFECT (caught live by the clinical-safety rig, J-CS-4 COUNT): recording a surgical-count discrepancy
-- while a peer owner (RITO) is unreachable throws HTTP 500 and rolls the whole @Transactional recordCount
-- back — so the discrepancy never persists, SIGN_OUT is not blocked and the RITO sentinel is lost.
--
-- ROOT CAUSE: V018 declared routed_status VARCHAR(16), but TheatreService writes the literal
-- 'OWNER_UNAVAILABLE' (17 chars) whenever the owner call returns null (RITO/PCT down). Postgres rejects
-- it with "value too long for type character varying(16)". The unit tests never caught it because they
-- run on H2 with hibernate ddl-auto (the entity has no length constraint → column becomes VARCHAR(255)).
--
-- FIX: widen to VARCHAR(24) so the fail-safe OWNER_UNAVAILABLE projection persists. Idempotent.
ALTER TABLE inpatient.procedure_safety_event
    ALTER COLUMN routed_status TYPE VARCHAR(24);
