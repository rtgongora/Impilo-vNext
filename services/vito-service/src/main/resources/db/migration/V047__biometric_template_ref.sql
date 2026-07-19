-- V047 — ABIS boundary (D6): opaque template pointer on the biometric profile.
-- VITO stops being the biometric template SoR; the encrypted template moves behind the
-- dedicated ABIS service (services/abis-service, port 8186). VITO keeps consent/status/
-- governance plus this opaque reference into ABIS.
--
-- NOTE: vito.biometric_template.template_data is deliberately NOT dropped here — VITO
-- remains the interim template holder until the ABIS cutover full-boot (flag
-- impilo.abis.enabled defaults off). Deploy queues for the next authorized full-boot.

ALTER TABLE vito.biometric_profile
    ADD COLUMN IF NOT EXISTS template_ref VARCHAR(128);

COMMENT ON COLUMN vito.biometric_profile.template_ref IS
    'Opaque ABIS subject/template pointer (abis.template.subject_ref). Never a Health ID; '
    'never template bytes. TSHEPO holds the biometric-subject → HID mapping.';
