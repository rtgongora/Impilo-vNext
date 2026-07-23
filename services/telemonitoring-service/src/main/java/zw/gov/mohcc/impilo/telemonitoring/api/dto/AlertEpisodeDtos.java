package zw.gov.mohcc.impilo.telemonitoring.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** OF-B26 alert-episode API contracts. */
public final class AlertEpisodeDtos {

    private AlertEpisodeDtos() {
    }

    public record AcknowledgeRequest(String acknowledgedBy) {
    }

    public record LadderStepRequest(
            @Min(1) @Max(15) int rung,
            String actor,
            String note) {
    }

    public record AssumptionOfCareRequest(
            @NotBlank String note,
            String actor) {
    }

    public record ResolveRequest(
            String resolvedBy,
            String assessment,
            String action,
            String outcome) {
    }

    public record EpisodeResponse(
            UUID episodeId,
            UUID planId,
            String patientCpid,
            String deviceRef,
            String triggerClass,
            String metricCode,
            String initialSeverity,
            String severity,
            String status,
            boolean severityCeilingApplied,
            @JsonRawValue String contributingReadings,
            @JsonRawValue String ladderHistory,
            Integer currentRung,
            int breachCount,
            OffsetDateTime responseDeadlineAt,
            String acknowledgedBy,
            OffsetDateTime acknowledgedAt,
            OffsetDateTime autoEscalatedAt,
            Boolean chwTaskDispatched,
            String reviewReferralRef,
            String emsIncidentRef,
            Boolean emsDispatched,
            String assumptionOfCareNote,
            String assumptionOfCareBy,
            String resolvedBy,
            String assessment,
            String actionTaken,
            String outcome,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt) {

        public static EpisodeResponse from(AlertEpisodeEntity e) {
            return new EpisodeResponse(
                    e.getId(), e.getPlanId(), e.getPatientCpid(), e.getDeviceRef(),
                    e.getTriggerClass().name(), e.getMetricCode(),
                    e.getInitialSeverity().name(), e.getSeverity().name(), e.getStatus().name(),
                    e.isSeverityCeilingApplied(),
                    e.getContributingReadings(), e.getLadderHistory(),
                    e.getCurrentRung(), e.getBreachCount(), e.getResponseDeadlineAt(),
                    e.getAcknowledgedBy(), e.getAcknowledgedAt(), e.getAutoEscalatedAt(),
                    e.getChwTaskDispatched(), e.getReviewReferralRef(),
                    e.getEmsIncidentRef(), e.getEmsDispatched(),
                    e.getAssumptionOfCareNote(), e.getAssumptionOfCareBy(),
                    e.getResolvedBy(), e.getAssessment(), e.getActionTaken(), e.getOutcome(),
                    e.getResolvedAt(), e.getCreatedAt());
        }
    }
}
