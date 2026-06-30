package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.ExternalSystem;
import zw.gov.mohcc.impilo.inventory.domain.SyncEntityType;

/** Request to enqueue a record for external sync. */
public record EnqueueSyncRequest(
        @NotNull ExternalSystem system,
        @NotNull SyncEntityType entityType,
        @NotBlank String entityRef,
        String payloadJson
) {}
