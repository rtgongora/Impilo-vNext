package zw.gov.mohcc.impilo.docstore.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for storing a new object in the document store.
 *
 * Sent as form metadata alongside the multipart file upload.
 * The mimeType is validated server-side against the actual file content.
 */
public record StoreObjectRequest(
        String originalFilename,
        @NotBlank(message = "mimeType is required")
        String mimeType,
        Map<String, String> metadata
) {
}
