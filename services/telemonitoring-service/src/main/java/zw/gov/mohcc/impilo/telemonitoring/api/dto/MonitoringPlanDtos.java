package zw.gov.mohcc.impilo.telemonitoring.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Request/response DTOs for the monitoring-plan API. */
public final class MonitoringPlanDtos {

    private MonitoringPlanDtos() {
    }

    public record CreatePlanRequest(
            @NotBlank String patientCpid,
            @NotBlank String programmeCode,
            String orosOrderId,
            String clinicalIndication,
            String monitoringSetting,
            String reviewCadence,
            String careTeamJson,
            String initialThresholdsJson,
            String suggestedBy,
            String suggestedSystemVersion,
            String consentReference,
            String consentStatus) {
    }

    public record LifecycleRequest(String reason, String actor) {
    }

    public record ApproveRequest(String approvedBy) {
    }

    /** MVUMO consent POINTER sync — reference + journey state only, never consent content. */
    public record ConsentPointerRequest(
            String consentReference,
            @NotBlank String consentStatus,
            String actor) {
    }

    /** Records a completed clinical review — re-arms the review-cadence timer. */
    public record RecordReviewRequest(String reviewedBy) {
    }

    public record AmendThresholdsRequest(
            @NotBlank String parametersJson,
            @NotBlank String reason,
            String amendedBy,
            Integer expectedCurrentVersion) {
    }

    public record PlanResponse(
            UUID id,
            UUID tenantId,
            String patientCpid,
            String programmeCode,
            String status,
            String orosOrderId,
            String clinicalIndication,
            String monitoringSetting,
            String suggestedBy,
            String suggestedSystemVersion,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String reviewCadence,
            OffsetDateTime reviewDueAt,
            String careTeamJson,
            String lifecycleReason,
            String consentReference,
            String consentStatus,
            OffsetDateTime consentUpdatedAt,
            OffsetDateTime lastReviewAt,
            OffsetDateTime reviewDueNotifiedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static PlanResponse from(MonitoringPlanEntity plan) {
            return new PlanResponse(
                    plan.getId(), plan.getTenantId(), plan.getPatientCpid(), plan.getProgrammeCode(),
                    plan.getStatus().name(), plan.getOrosOrderId(), plan.getClinicalIndication(),
                    plan.getMonitoringSetting(), plan.getSuggestedBy(), plan.getSuggestedSystemVersion(),
                    plan.getApprovedBy(), plan.getApprovedAt(), plan.getStartAt(), plan.getEndAt(),
                    plan.getReviewCadence(), plan.getReviewDueAt(), plan.getCareTeam(),
                    plan.getLifecycleReason(),
                    plan.getConsentReference(),
                    plan.getConsentStatus() != null ? plan.getConsentStatus().name() : null,
                    plan.getConsentUpdatedAt(), plan.getLastReviewAt(), plan.getReviewDueNotifiedAt(),
                    plan.getCreatedAt(), plan.getUpdatedAt());
        }
    }

    public record ThresholdProfileResponse(
            UUID id,
            UUID planId,
            int version,
            UUID supersedes,
            String parametersJson,
            String reason,
            String createdBy,
            String approvedBy,
            OffsetDateTime createdAt) {

        public static ThresholdProfileResponse from(ThresholdProfileEntity profile) {
            return new ThresholdProfileResponse(
                    profile.getId(), profile.getPlanId(), profile.getVersion(), profile.getSupersedes(),
                    profile.getParameters(), profile.getReason(), profile.getCreatedBy(),
                    profile.getApprovedBy(), profile.getCreatedAt());
        }
    }

    public record ProgrammeResponse(
            UUID id,
            String code,
            String name,
            String description,
            String conditionCode,
            String defaultThresholdsJson,
            boolean active,
            int version,
            String metadataJson,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            boolean retired,
            OffsetDateTime retiredAt,
            String retiredBy,
            String retiredReason,
            String updatedBy) {

        public static ProgrammeResponse from(MonitoringProgrammeEntity programme) {
            return new ProgrammeResponse(
                    programme.getId(), programme.getCode(), programme.getName(), programme.getDescription(),
                    programme.getConditionCode(), programme.getDefaultThresholds(), programme.isActive(),
                    programme.getVersion(), programme.getMetadata(),
                    programme.getEffectiveFrom(), programme.getEffectiveTo(),
                    programme.isRetired(), programme.getRetiredAt(), programme.getRetiredBy(),
                    programme.getRetiredReason(), programme.getUpdatedBy());
        }
    }

    // ── Programme governance (OF-B21) ──

    public record CreateProgrammeRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String conditionCode,
            String defaultThresholdsJson,
            String metadataJson,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            String actor) {
    }

    public record UpdateProgrammeRequest(
            String name,
            String description,
            String conditionCode,
            String defaultThresholdsJson,
            String metadataJson,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            Boolean active,
            String actor) {
    }

    /** Retire never deletes: reason-bound deactivation with the row retained forever. */
    public record RetireProgrammeRequest(
            @NotBlank String reason,
            String actor) {
    }
}
