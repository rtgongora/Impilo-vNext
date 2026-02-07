package zw.gov.mohcc.impilo.docstore.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for a generated pre-signed URL.
 *
 * The URL provides time-limited direct access to the object in MinIO
 * without requiring further authentication. The token is recorded in
 * the signed_urls table for audit purposes.
 */
public record SignedUrlResponse(
        String url,
        String token,
        OffsetDateTime expiresAt
) {
}
