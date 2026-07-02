package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Read-model DTOs for the TUSO Facility Configuration Console (service points, workspaces, derived queue
 * definitions, setup checklist, downstream readiness, configuration history). TUSO is the source of truth
 * for facility configuration; these are composed from existing TUSO entities.
 */
public final class FacilityConfigurationDto {

    private FacilityConfigurationDto() {}

    /** Overview of a facility's operational configuration and completeness. */
    public record ConfigurationSummary(
            Long facilityId,
            String facilityUuid,
            String name,
            String facilityCode,
            String facilityType,
            String province,
            String district,
            String regulatoryStatus,
            String operationalStatus,
            String status,
            /** Derived configuration lifecycle state (see FacilityConfigurationState). */
            String configurationState,
            long activeServicePointCount,
            long activeWorkspaceCount,
            long departmentCount,
            long activeQueueDefinitionCount,
            boolean setupComplete,
            List<String> missingSections,
            String sourceProvenance,
            Integer configVersion,
            Instant lastConfigChangeAt
    ) {}

    /** A single readiness/completeness checklist row. */
    public record ChecklistItem(
            String key,
            String label,
            /** COMPLETE | MISSING | PENDING | NOT_APPLICABLE — never faked to COMPLETE. */
            String status,
            boolean required,
            String detail
    ) {}

    /** Setup / readiness checklist for a facility. */
    public record SetupChecklist(
            Long facilityId,
            String configurationState,
            boolean setupComplete,
            List<ChecklistItem> items
    ) {}

    /**
     * A queue definition derived from a service point. TUSO owns the definition; PCT materialises it for
     * care operations. Never authored on this surface.
     */
    public record QueueDefinitionView(
            String sourceRef,
            String displayName,
            String queueType,
            String servicePointType,
            String workspaceId,
            boolean priorityFlag,
            boolean walkInSupported,
            boolean appointmentSupported,
            boolean active
    ) {}

    /**
     * TUSO's view of downstream materialisation readiness. The authoritative live materialisation status
     * lives in PCT and is surfaced via the queue materialisation viewer; this reports what TUSO knows
     * (derived queue definition counts + last configuration change) without fabricating PCT state.
     */
    public record DownstreamReadiness(
            Long facilityId,
            String facilityUuid,
            long activeQueueDefinitionCount,
            long activeServicePointCount,
            Instant lastConfigChangeAt,
            String note
    ) {}

    /** A configuration-change audit event derived from the TUSO outbox. */
    public record ConfigEvent(
            String eventType,
            String aggregateType,
            String aggregateId,
            Instant occurredAt,
            Instant publishedAt
    ) {}
}
