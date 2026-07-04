package zw.gov.mohcc.impilo.docstore.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request DTO for adopting an object that was written to MinIO by another
 * platform component (e.g. an rtc-gateway recording egress) into the
 * document catalogue.
 *
 * The binary already exists in storage — registration verifies presence
 * (statObject) and creates the same metadata row multipart ingest would,
 * so the object gets signed-url playback, audit, and lifecycle handling.
 *
 * sha256 is optional because the writer may not have hashed the content;
 * when absent the catalogue row stores an empty hash (content-hash dedup
 * simply does not apply to that object).
 */
public record RegisterExternalObjectRequest(
        @NotBlank(message = "bucket is required")
        String bucket,
        @NotBlank(message = "key is required")
        String key,
        @NotBlank(message = "contentType is required")
        String contentType,
        Long sizeBytes,
        String sha256,
        @NotBlank(message = "ownerService is required")
        String ownerService,
        String title,
        Map<String, String> tags
) {
}
