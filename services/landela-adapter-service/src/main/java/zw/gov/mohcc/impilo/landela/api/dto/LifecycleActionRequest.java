package zw.gov.mohcc.impilo.landela.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request payload for performing a lifecycle action on a document.
 * Supported actions: ARCHIVE, REVOKE, SUPERSEDE, DELETE.
 */
public record LifecycleActionRequest(
        @NotBlank String action,
        String reason,
        UUID supersededByDocumentId
) {}
