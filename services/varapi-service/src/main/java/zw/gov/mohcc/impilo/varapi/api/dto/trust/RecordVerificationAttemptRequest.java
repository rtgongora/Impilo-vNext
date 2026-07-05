package zw.gov.mohcc.impilo.varapi.api.dto.trust;

import java.math.BigDecimal;

/**
 * Body of {@code POST /v1/internal/providers/{providerId}/verification-attempts}
 * (FROZEN CONTRACT — WS-D/WS-F compile against this shape).
 *
 * @param type       evidence type, e.g. COUNCIL_REGISTRATION, EMPLOYMENT_RECORD
 * @param source     COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION
 * @param ref        opaque evidence reference — never echoed back unmasked
 * @param outcome    attempt outcome, e.g. VERIFIED, NOT_FOUND, FORMAT_INVALID
 * @param confidence 0.00–1.00 match confidence
 */
public record RecordVerificationAttemptRequest(String type,
                                               String source,
                                               String ref,
                                               String outcome,
                                               BigDecimal confidence) {}
