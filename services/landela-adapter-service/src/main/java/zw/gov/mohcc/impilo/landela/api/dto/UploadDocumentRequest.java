package zw.gov.mohcc.impilo.landela.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request payload for uploading a new document.
 * Sent as part of a multipart request alongside the binary file.
 */
public record UploadDocumentRequest(
        @NotBlank String subjectType,
        @NotBlank String subjectId,
        String documentTypeCode,
        String source,
        String issuer,
        String confidentiality,
        String retentionPolicyId,
        Map<String, Object> tags,
        String fileName
) {}
