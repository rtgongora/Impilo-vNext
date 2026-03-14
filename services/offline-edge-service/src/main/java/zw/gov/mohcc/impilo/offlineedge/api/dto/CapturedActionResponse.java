package zw.gov.mohcc.impilo.offlineedge.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CapturedActionResponse(UUID actionId, UUID entitlementId, UUID tenantId, String actorId,
                                       String patientRef, String workflowType, String actionType,
                                       String status, OffsetDateTime capturedAt, int sequenceNum,
                                       OffsetDateTime replayedAt, String replayError) {}
