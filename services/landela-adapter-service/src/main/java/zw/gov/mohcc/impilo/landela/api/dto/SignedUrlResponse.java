package zw.gov.mohcc.impilo.landela.api.dto;

import java.time.Instant;

/**
 * Response containing a pre-signed URL for direct document download.
 */
public record SignedUrlResponse(
        String url,
        Instant expiresAt
) {}
