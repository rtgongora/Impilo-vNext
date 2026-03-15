package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentResponse(UUID assignmentId, UUID ticketId, String assigneeRef, String assignedBy,
                                  OffsetDateTime assignedAt) {}
