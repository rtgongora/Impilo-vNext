package zw.gov.mohcc.impilo.workflow.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DefinitionResponse(UUID definitionId, UUID tenantId, String code, String name,
                                   String description, String category, int version, String status,
                                   Object schema, Object steps,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime publishedAt) {}
