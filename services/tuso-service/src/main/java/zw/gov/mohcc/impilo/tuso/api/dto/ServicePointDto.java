package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.Map;

/** Service-point read/write DTOs for the facility-mode admin + setup wizard. */
public final class ServicePointDto {

    private ServicePointDto() {}

    public record CreateRequest(
            String name,
            String code,
            String servicePointType,
            Long facilityUnitId,
            String queueId,
            String workflowArchetype,
            Map<String, Object> metadata
    ) {}

    /** Partial-update request for a service point; null fields are left unchanged. */
    public record UpdateRequest(
            String name,
            String code,
            String servicePointType,
            Long facilityUnitId,
            String queueId,
            String workflowArchetype,
            String status,
            Boolean active,
            Map<String, Object> metadata
    ) {}

    public record Response(
            String id,
            Long facilityId,
            Long facilityUnitId,
            String name,
            String code,
            String servicePointType,
            String queueId,
            String workflowArchetype,
            String status,
            boolean active
    ) {}
}
