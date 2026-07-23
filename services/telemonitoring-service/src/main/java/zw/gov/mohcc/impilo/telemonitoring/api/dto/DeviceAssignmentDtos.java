package zw.gov.mohcc.impilo.telemonitoring.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.telemonitoring.core.DeviceAssignmentService.ResolvedAssignment;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Request/response shapes for the OF-B24 device-assignment surface. */
public final class DeviceAssignmentDtos {

    private DeviceAssignmentDtos() {
    }

    public record AssignRequest(
            @NotNull UUID planId,
            @NotBlank String deviceRef,
            String assetRef,
            String assignmentType,
            String assignedBy,
            String notes) {
    }

    public record ActivateRequest(
            boolean trainingConfirmed,
            String actor,
            String notes) {
    }

    public record LifecycleRequest(
            @NotBlank String reason,
            String actor) {
    }

    public record AssignmentResponse(
            UUID assignmentId,
            UUID tenantId,
            UUID planId,
            String patientCpid,
            String deviceRef,
            String assetRef,
            String status,
            String assignmentType,
            String assignedBy,
            OffsetDateTime assignedAt,
            OffsetDateTime activatedAt,
            String activatedBy,
            boolean trainingConfirmed,
            OffsetDateTime returnedAt,
            String lifecycleReason,
            String notes) {

        public static AssignmentResponse from(DeviceAssignmentEntity e) {
            return new AssignmentResponse(
                    e.getId(),
                    e.getTenantId(),
                    e.getPlanId(),
                    e.getPatientCpid(),
                    e.getDeviceRef(),
                    e.getAssetRef(),
                    e.getStatus().name(),
                    e.getAssignmentType(),
                    e.getAssignedBy(),
                    e.getAssignedAt(),
                    e.getActivatedAt(),
                    e.getActivatedBy(),
                    e.isTrainingConfirmed(),
                    e.getReturnedAt(),
                    e.getLifecycleReason(),
                    e.getNotes());
        }
    }

    /** The assignment-gate shape (OF-B25 consumer contract). */
    public record ResolvedAssignmentResponse(
            UUID assignmentId,
            UUID tenantId,
            UUID planId,
            String patientCpid,
            String deviceRef,
            String assetRef,
            String status,
            String calibrationPosture,
            String calibrationPostureReason,
            OffsetDateTime activatedAt) {

        public static ResolvedAssignmentResponse from(ResolvedAssignment r) {
            return new ResolvedAssignmentResponse(
                    r.assignmentId(),
                    r.tenantId(),
                    r.planId(),
                    r.patientCpid(),
                    r.deviceRef(),
                    r.assetRef(),
                    r.status().name(),
                    r.calibrationPosture().name(),
                    r.calibrationPostureReason(),
                    r.activatedAt());
        }
    }
}
