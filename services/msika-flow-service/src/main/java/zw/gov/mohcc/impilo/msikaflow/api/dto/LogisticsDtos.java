package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class LogisticsDtos {

    private LogisticsDtos() {}

    public record CreateDeliveryPlanRequest(
            String fulfillmentId,
            String mode,
            Object origin,
            Object destination,
            Object constraintsSummary,
            Object metadata
    ) {}

    public record UpdatePlanRequest(
            String status,
            String selectedProviderRef,
            Object estimatedWindow,
            Object constraintsSummary,
            Object metadata
    ) {}

    public record UpdateLegRequest(
            String status,
            String assignedProviderId,
            String assignedAssetId,
            OffsetDateTime estimatedDeparture,
            OffsetDateTime estimatedArrival,
            OffsetDateTime actualDeparture,
            OffsetDateTime actualArrival,
            Object metadata
    ) {}

    public record DeliveryLegView(
            String legId,
            int sequenceNumber,
            String legType,
            String mode,
            String status,
            Object origin,
            Object destination,
            String assignedProviderId,
            String assignedAssetId,
            OffsetDateTime estimatedDeparture,
            OffsetDateTime estimatedArrival,
            OffsetDateTime actualDeparture,
            OffsetDateTime actualArrival,
            Object metadata
    ) {}

    public record DeliveryPlanView(
            String planId,
            String fulfillmentId,
            String mode,
            String status,
            Object origin,
            Object destination,
            String selectedProviderRef,
            Object estimatedWindow,
            Object constraintsSummary,
            Object metadata,
            List<DeliveryLegView> legs,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CreateProviderRequest(
            String providerType,
            String name,
            Object supportedModes,
            Object serviceAreas,
            Object capabilities,
            Boolean active
    ) {}

    public record ProviderView(
            String providerId,
            String providerType,
            String name,
            Object supportedModes,
            Object serviceAreas,
            Object capabilities,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CreateAssetRequest(
            String providerId,
            String assetType,
            String assetCode,
            String status,
            Object capabilitySummary,
            Boolean telemetrySupported,
            Boolean active
    ) {}

    public record AssetView(
            String assetId,
            String providerId,
            String assetType,
            String assetCode,
            String status,
            Object capabilitySummary,
            boolean telemetrySupported,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CreateMissionRequest(
            String legId,
            String missionType,
            String commandRef,
            String telemetryRef,
            OffsetDateTime launchAt,
            Object metadata
    ) {}

    public record MissionView(
            String missionId,
            String legId,
            String missionType,
            String missionStatus,
            String commandRef,
            String telemetryRef,
            OffsetDateTime launchAt,
            OffsetDateTime completedAt,
            String failureReason,
            Object metadata,
            OffsetDateTime createdAt
    ) {}

    public record RecordHandoffRequest(
            String fulfillmentId,
            String legId,
            String handoffType,
            Object fromParty,
            Object toParty,
            Object location,
            OffsetDateTime occurredAt,
            String integrityOutcome,
            String notes,
            Object metadata
    ) {}

    public record HandoffView(
            String handoffId,
            String fulfillmentId,
            String legId,
            String handoffType,
            Object fromParty,
            Object toParty,
            Object location,
            OffsetDateTime occurredAt,
            String integrityOutcome,
            String notes,
            Object metadata
    ) {}

    public record CreateCustodyRequest(
            String fulfillmentId,
            String packageRef,
            String custodianType,
            String custodianRef,
            OffsetDateTime fromAt,
            OffsetDateTime toAt,
            Object conditionSummary,
            String sealStatus,
            String temperatureStatus,
            Object metadata
    ) {}

    public record CustodyView(
            String custodyId,
            String fulfillmentId,
            String packageRef,
            String custodianType,
            String custodianRef,
            OffsetDateTime fromAt,
            OffsetDateTime toAt,
            Object conditionSummary,
            String sealStatus,
            String temperatureStatus,
            Object metadata
    ) {}

    public record RaiseExceptionRequest(
            String planId,
            String legId,
            String exceptionType,
            String severity,
            Object metadata
    ) {}

    public record DeliveryExceptionView(
            String exceptionId,
            String planId,
            String legId,
            String exceptionType,
            String severity,
            String status,
            OffsetDateTime raisedAt,
            String resolutionSummary,
            OffsetDateTime resolvedAt,
            Object metadata
    ) {}

    public record RecordProofRequest(
            String planId,
            String handoffId,
            String proofType,
            String proofReference,
            Object metadata
    ) {}

    public record ProofView(
            String proofId,
            String planId,
            String handoffId,
            String proofType,
            String proofReference,
            String verificationOutcome,
            OffsetDateTime verifiedAt,
            Object metadata,
            OffsetDateTime createdAt
    ) {}
}

