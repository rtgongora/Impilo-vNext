package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request to record a failed sync attempt. */
public record MarkSyncFailedRequest(
        @NotBlank String errorMessage
) {}
