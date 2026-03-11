package zw.gov.mohcc.impilo.dispatch.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID jobId,
        UUID tenantId,
        String facilityRef,
        String requestRef,
        String status,
        String assignedAgentRef,
        String assignedVehicleRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
