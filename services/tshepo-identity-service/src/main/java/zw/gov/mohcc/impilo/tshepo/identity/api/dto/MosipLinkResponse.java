package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a MOSIP link operation.
 */
public record MosipLinkResponse(
        UUID healthId,
        String verificationStatus,
        Instant linkedAt
) {}
