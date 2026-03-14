package zw.gov.mohcc.impilo.connectorfhir.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RelayAuditResponse(UUID auditId, UUID tenantId, String bundleId, String resourceType,
                                   int resourceCount, UUID destinationId, String destinationUrl,
                                   String decision, String outcome, String errorMessage,
                                   String actorRef, OffsetDateTime createdAt, OffsetDateTime completedAt) {}
