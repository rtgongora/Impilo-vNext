package zw.gov.mohcc.impilo.air.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ModelDtos {

    private ModelDtos() {}

    public record CreateModelRequest(
            @NotBlank String name,
            String description,
            @NotBlank String modelType,
            String owner) {}

    public record UpdateModelRequest(String name, String description, String modelType, String owner) {}

    public record ApproveModelRequest(String approvedBy) {}

    public record WithdrawModelRequest(String reason) {}

    public record ModelResponse(
            UUID modelId,
            UUID tenantId,
            String name,
            String description,
            String modelType,
            String owner,
            String status,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime withdrawnAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record CreateVersionRequest(
            @NotBlank String version,
            String artifactRef,
            String configJson,
            String approvedUseCases,
            String prohibitedUseCases,
            BigDecimal driftThreshold,
            String status) {}

    public record VersionResponse(
            UUID versionId,
            UUID modelId,
            String version,
            String artifactRef,
            String configJson,
            String approvedUseCases,
            String prohibitedUseCases,
            BigDecimal driftThreshold,
            String status,
            OffsetDateTime createdAt) {}

    public record CreateInferenceRequest(
            @NotNull UUID modelId,
            String modelVersion,
            @NotBlank String inferenceClass,
            String workflowRef,
            String subjectType,
            String subjectId,
            String outputSummary,
            BigDecimal score,
            BigDecimal confidence,
            Boolean humanReviewRequired,
            Boolean humanAccepted,
            String overrideReason) {}

    public record InferenceResponse(
            UUID recordId,
            UUID tenantId,
            UUID modelId,
            String modelVersion,
            String inferenceClass,
            String workflowRef,
            String subjectType,
            String subjectId,
            String outputSummary,
            BigDecimal score,
            BigDecimal confidence,
            Boolean humanReviewRequired,
            Boolean humanAccepted,
            String overrideReason,
            OffsetDateTime createdAt) {}

    public record CreateDriftRequest(
            @NotNull UUID modelId,
            String modelVersion,
            String driftMetric,
            BigDecimal driftValue,
            BigDecimal threshold,
            String severity) {}

    public record DriftResponse(
            UUID eventId,
            UUID tenantId,
            UUID modelId,
            String modelVersion,
            String driftMetric,
            BigDecimal driftValue,
            BigDecimal threshold,
            String severity,
            OffsetDateTime detectedAt) {}
}
