package zw.gov.mohcc.impilo.inventory.api.dto;

/** Request to mark a sync record acknowledged by the external system. */
public record MarkSyncedRequest(
        String externalRef
) {}
