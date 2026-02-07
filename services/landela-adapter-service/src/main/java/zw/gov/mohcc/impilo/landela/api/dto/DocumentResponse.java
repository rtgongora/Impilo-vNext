package zw.gov.mohcc.impilo.landela.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload representing document metadata.
 */
public record DocumentResponse(
        UUID documentId,
        UUID tenantId,
        String subjectType,
        String subjectId,
        String documentTypeCode,
        String source,
        String issuer,
        String confidentiality,
        String hashSha256,
        String mimeType,
        String fileName,
        Long fileSizeBytes,
        String storageProvider,
        String lifecycleState,
        Map<String, Object> tags,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer version
) {}
