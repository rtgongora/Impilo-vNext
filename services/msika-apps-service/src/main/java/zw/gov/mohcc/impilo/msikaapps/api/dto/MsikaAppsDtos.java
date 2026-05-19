package zw.gov.mohcc.impilo.msikaapps.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for the Msika Apps capability marketplace.
 * Keep aligned with contracts/openapi/msika-apps.openapi.yaml and the
 * TypeScript manifest contracts under contracts/marketplace-manifests.ts.
 */
public final class MsikaAppsDtos {
    private MsikaAppsDtos() {}

    // ── Publishers ───────────────────────────────────────────────────────────
    public record PublisherResponse(
            String id, String name, String legalName,
            String websiteUrl, String supportEmail,
            String status, OffsetDateTime verifiedAt, OffsetDateTime createdAt
    ) {}

    // ── Marketplace Items ────────────────────────────────────────────────────
    public record MarketplaceItemResponse(
            String id, String itemCode, String name, String description,
            String type, String category,
            String publisherId, String publisherName,
            String version, String compatibilityVersion,
            List<String> supportedEnvironments,
            String visibility,
            String iconRef, String documentationUrl, String manifestRef,
            String securityClassification, String dataProtectionClassification,
            String clinicalSafetyClassification,
            String approvalStatus, String certificationStatus, String regulatoryStatus,
            String pricingModel, String licensingModel,
            List<String> requiredScopes, List<String> requiredEvents, List<String> requiredApis,
            List<String> requiredDataCategories, List<String> requiredPermissions,
            List<String> facilityTypeWhitelist, List<String> rolesAllowed,
            List<String> consentRequirements,
            String healthStatus, OffsetDateTime lastUpdated,
            OffsetDateTime deprecationDate,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── Activation Requests ──────────────────────────────────────────────────
    public record ActivationRequestRequest(
            @NotBlank String itemId,
            String facilityId, String workspaceId, String programmeId,
            @NotBlank String purposeOfUse,
            @NotBlank String justification,
            String requestedConfigurationJson
    ) {}

    public record ActivationDecisionRequest(
            @NotBlank String decision,            // APPROVED | REJECTED | CHANGES_REQUESTED
            String reason
    ) {}

    public record ActivationRequestResponse(
            String id, String itemId,
            String requesterActorId, String requesterTenantId,
            String requesterFacilityId, String requesterWorkspaceId, String requesterProgrammeId,
            String purposeOfUse, String justification,
            String status, OffsetDateTime decidedAt, String decisionReason,
            String correlationId,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── Installations ────────────────────────────────────────────────────────
    public record InstallationConfigureRequest(
            List<String> facilityIds, List<String> workspaceIds,
            List<String> programmeIds, List<String> rolesEnabled,
            String configurationJson,
            String integrationContractId, String externalAppId
    ) {}

    public record InstallationSuspendRequest(@NotBlank String reason) {}

    public record InstallationResponse(
            String id, String itemId, String itemVersion,
            String tenantId,
            List<String> facilityIds, List<String> workspaceIds, List<String> programmeIds,
            List<String> rolesEnabled,
            String configurationJson,
            String integrationContractId, String externalAppId,
            String lifecycle, String installedBy,
            OffsetDateTime installedAt, OffsetDateTime configuredAt, OffsetDateTime activatedAt,
            OffsetDateTime suspendedAt, String suspendedReason,
            OffsetDateTime lastHealthCheckAt, String healthStatus,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── Launcher / role-aware ────────────────────────────────────────────────
    public record LauncherAppResponse(
            String id, String itemCode, String name, String description,
            String type, String category, String iconRef,
            String state,             // INSTALLED | AVAILABLE | REQUEST_ACCESS | PENDING_APPROVAL | NOT_AVAILABLE_AT_FACILITY | REQUIRES_CONFIGURATION | TEMPORARILY_UNAVAILABLE | SUSPENDED | DEPRECATED
            String reason,
            String launchUrl,
            List<String> rolesAllowed,
            String installationId
    ) {}

    public record AuditEventResponse(
            String id, String itemId, String installationId, String activationRequestId,
            String externalAppId, String eventType,
            String actorId, String actorType,
            String tenantId, String facilityId,
            String reason, String correlationId, OffsetDateTime occurredAt
    ) {}
}
