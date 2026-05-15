package zw.gov.mohcc.impilo.pct.api.dto;

/**
 * Request to update encounter linkage to care pathway/protocol references.
 */
public record UpdateEncounterPathwayProtocolRequest(
        String pathwayRef,
        String protocolRef
) {}

