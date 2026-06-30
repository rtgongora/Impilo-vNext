package zw.gov.mohcc.impilo.assetregistry.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTOs for the equipment-operations REST surface (WS#5). Grouped in one file to
 * keep the operational-lifecycle contract cohesive; each is an immutable record validated
 * in the controller (boundary refs are strings — Vashandi/Tuso/Rito/inventory own the truth).
 */
public final class EquipmentOpsDtos {

    private EquipmentOpsDtos() {}

    public record RegisterEquipmentRequest(
            String facilityId, String name, String equipmentType, String serialNumber, String tag,
            String manufacturer, String model, String criticality, String assetClass, Boolean regulated,
            String custodianRef, String clinicalDeviceType, String deviceId) {}

    public record UpdateMetadataRequest(String custodianRef, String locationDescription, String criticality) {}

    public record ChangeStatusRequest(String status, String note) {}

    public record TransferRequest(String toFacility, String toLocation, String toCustodianRef, String reason,
                                  Boolean requiresApproval) {}

    public record MaintenanceRequest(String taskType, String title, String description, String priority,
                                     LocalDate dueDate, String technicianRef, String sparePartRequestRef,
                                     UUID faultReportId) {}

    public record MaintenanceUpdateRequest(String status, String technicianRef, String serviceReport,
                                           Integer downtimeMinutes) {}

    public record MaintenanceCompleteRequest(String serviceReport, Boolean returnToService) {}

    public record CalibrationRequest(String outcome, LocalDate performedOn, LocalDate nextDue,
                                     String certificateRef, String notes) {}

    public record FaultRequest(String severity, String summary, String detail, Boolean safetyConcern) {}

    public record RitoLinkRequest(String ritoCaseRef) {}

    public record AlertRequest(String deviceId, String alertType, String metricType, Double observedValue,
                               Double thresholdValue, String severity, String detail) {}

    public record ReadinessRequirementDto(String equipmentType, Integer minCount, Boolean mustBeCritical, String note) {}

    public record ReadinessProfileRequest(String facilityRef, String servicePoint, String name,
                                          List<ReadinessRequirementDto> requirements) {}

    public record KitRequest(String name, String deploymentRef, String locationRef, String custodianRef,
                             List<UUID> equipmentIds) {}

    public record KitReturnItem(UUID equipmentId, String condition, String note) {}

    public record KitReturnRequest(List<KitReturnItem> items) {}

    public record AuditRequest(String facilityRef, String name) {}

    public record AuditItemRequest(Boolean confirmed, String exceptionCode, String note) {}
}
