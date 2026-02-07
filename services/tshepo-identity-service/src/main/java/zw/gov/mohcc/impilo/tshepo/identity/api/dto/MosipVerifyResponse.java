package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.util.UUID;

/**
 * Result of a MOSIP link verification.
 */
public record MosipVerifyResponse(
        UUID healthId,
        boolean verified,
        String verificationStatus
) {}
