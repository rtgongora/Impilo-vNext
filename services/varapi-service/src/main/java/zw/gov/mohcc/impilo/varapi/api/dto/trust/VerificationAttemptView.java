package zw.gov.mohcc.impilo.varapi.api.dto.trust;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A verification attempt as exposed by the trust API (FROZEN CONTRACT — WS-D/WS-F
 * compile against this shape).
 *
 * @param type        evidence type
 * @param source      COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION
 * @param outcome     recorded outcome (e.g. TRUST_UPGRADED, TRUST_REVOKED, RECORDED)
 * @param confidence  match confidence
 * @param ref         MASKED evidence reference (first 4 chars + "***"); council numbers
 *                    are matching evidence, not identity, and never appear in payloads
 * @param attemptedBy actor id that recorded the attempt
 * @param attemptedAt when the attempt was recorded
 */
public record VerificationAttemptView(String type,
                                      String source,
                                      String outcome,
                                      BigDecimal confidence,
                                      String ref,
                                      String attemptedBy,
                                      Instant attemptedAt) {}
