package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(UUID ticketId, UUID tenantId, String title, String description,
                              String category, String priority, String status, String reporterRef,
                              String assigneeRef, String facilityRef, String resolution,
                              int version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              OffsetDateTime resolvedAt) {}
