package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(UUID ticketId, UUID tenantId, String title, String description,
                              String category, String priority, String status, String reporterRef,
                              String assigneeRef, String facilityRef, String resolution,
                              String requestId, UUID correlationId,
                              int escalationLevel, OffsetDateTime escalatedAt, String escalatedBy,
                              OffsetDateTime slaBreachedAt,
                              int version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              OffsetDateTime resolvedAt) {}
