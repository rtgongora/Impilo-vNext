package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Facility department read/write DTOs (canonical department identity, TUSO SoR). */
public final class FacilityDepartmentDto {

    private FacilityDepartmentDto() {}

    public record CreateRequest(
            String departmentCode,
            String officialName,
            String displayName,
            String departmentType,
            UUID parentDepartmentId,
            LocalDate openedAt,
            UUID headPersonHealthId,
            String locationRef,
            Map<String, Object> contact,
            Map<String, Object> escalation,
            Map<String, Object> metadata
    ) {}

    /** Partial-update request; null fields are left unchanged. */
    public record UpdateRequest(
            String officialName,
            String displayName,
            String departmentType,
            UUID parentDepartmentId,
            String operatingStatus,
            UUID headPersonHealthId,
            String locationRef,
            Map<String, Object> contact,
            Map<String, Object> escalation,
            Map<String, Object> metadata
    ) {}

    public record Response(
            String id,
            Long facilityId,
            String departmentCode,
            String officialName,
            String displayName,
            String departmentType,
            String parentDepartmentId,
            String operatingStatus,
            LocalDate openedAt,
            LocalDate closedAt,
            String headPersonHealthId,
            String locationRef,
            Map<String, Object> contact,
            Map<String, Object> escalation,
            String provenance,
            int version,
            boolean active
    ) {}
}
