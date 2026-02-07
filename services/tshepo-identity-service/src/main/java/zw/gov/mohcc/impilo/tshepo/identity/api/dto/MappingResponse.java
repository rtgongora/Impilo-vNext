package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a Health ID ↔ CPID mapping lookup.
 */
public record MappingResponse(
        UUID healthId,
        UUID cpid,
        UUID crid,
        String mappingStatus,
        Instant createdAt
) {}
