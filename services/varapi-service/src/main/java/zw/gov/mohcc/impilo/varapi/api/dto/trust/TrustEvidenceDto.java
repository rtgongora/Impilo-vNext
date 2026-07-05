package zw.gov.mohcc.impilo.varapi.api.dto.trust;

import java.math.BigDecimal;

/**
 * Evidence accompanying a trust transition (FROZEN CONTRACT — WS-D/WS-F compile
 * against this shape).
 *
 * @param type       evidence type, e.g. COUNCIL_REGISTRATION, EMPLOYMENT_RECORD;
 *                   {@code EVIDENCE_REVOKED} unlocks the (only) downgrade path
 * @param source     COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION
 * @param ref        opaque evidence reference (revocation reason on the revoke path);
 *                   NEVER echoed back unmasked — council numbers are matching
 *                   evidence, not identity
 * @param confidence 0.00–1.00 match confidence
 */
public record TrustEvidenceDto(String type, String source, String ref, BigDecimal confidence) {}
