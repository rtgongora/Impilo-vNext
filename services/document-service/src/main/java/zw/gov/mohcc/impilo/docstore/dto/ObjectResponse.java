package zw.gov.mohcc.impilo.docstore.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO containing metadata for a stored object.
 *
 * Returned by all object retrieval and creation endpoints.
 * Never contains the actual binary content — use the signed URL
 * or download endpoint for that.
 */
public record ObjectResponse(
        UUID objectId,
        UUID tenantId,
        String bucket,
        String objectKey,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        String hashSha256,
        String scanStatus,
        Map<String, String> metadata,
        String createdBy,
        OffsetDateTime createdAt
) {
}
