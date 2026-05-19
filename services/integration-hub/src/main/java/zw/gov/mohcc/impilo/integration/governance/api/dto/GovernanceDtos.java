package zw.gov.mohcc.impilo.integration.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bundled request/response DTOs for the integration governance plane.
 * Kept in one file because each surface is small and grouping aids review.
 */
public final class GovernanceDtos {

    private GovernanceDtos() {}

    // ── External Applications ────────────────────────────────────────────────

    public record ContactPointDto(String name, String email, String phone, String role) {}

    public record ExternalApplicationRequest(
            @NotBlank String appCode,
            @NotBlank String name,
            String description,
            @NotBlank String publisherId,
            @NotBlank String publisherName,
            ContactPointDto technicalContact,
            ContactPointDto dataProtectionContact,
            @NotBlank String environment,
            @NotBlank String integrationCategory,
            String riskClassification,
            @NotBlank String authenticationMethod,
            List<String> allowedTenants,
            List<String> allowedFacilities,
            Integer rateLimitRpm,
            Integer rateLimitBurst
    ) {}

    public record ExternalApplicationResponse(
            String id, String appCode, String name, String description,
            String publisherId, String publisherName,
            ContactPointDto technicalContact, ContactPointDto dataProtectionContact,
            String status, String environment, String integrationCategory, String riskClassification,
            String authenticationMethod, String oauthClientId, String mtlsCertificateFingerprint,
            List<String> ipAllowlist, List<String> allowedTenants, List<String> allowedFacilities,
            Integer rateLimitRpm, Integer rateLimitBurst,
            OffsetDateTime credentialExpiresAt, OffsetDateTime registeredAt,
            OffsetDateTime approvedAt, String approvedBy,
            OffsetDateTime suspendedAt, String suspendedReason
    ) {}

    public record SuspendRequest(@NotBlank String reason) {}

    // ── Integration Contracts ────────────────────────────────────────────────

    public record IntegrationContractRequest(
            @NotBlank String externalAppId,
            @NotBlank String contractCode,
            @NotBlank String version,
            @NotBlank String integrationCategory,
            @NotBlank String environment,
            @NotNull OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveUntil,
            List<String> allowedApis,
            List<String> allowedEvents,
            List<String> allowedWebhookCallbacks,
            List<String> scopes,
            List<String> purposeOfUse,
            String consentBasis,
            @NotBlank String signatureMethod,
            Integer rateLimitRpm,
            Integer rateLimitBurst,
            String errorHandlingPolicy,
            Integer dataRetentionDays,
            Integer incidentReportingHours,
            String status,
            String signedByName,
            String signedByEmail,
            String documentRef
    ) {}

    public record IntegrationContractResponse(
            String id, String externalAppId, String contractCode, String version,
            String integrationCategory, String environment,
            OffsetDateTime effectiveFrom, OffsetDateTime effectiveUntil,
            List<String> allowedApis, List<String> allowedEvents, List<String> allowedWebhookCallbacks,
            List<String> scopes, List<String> purposeOfUse, String consentBasis,
            String signatureMethod, Integer rateLimitRpm, Integer rateLimitBurst,
            String errorHandlingPolicy, Integer dataRetentionDays, Integer incidentReportingHours,
            String status, String documentRef,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── S2S Contracts ────────────────────────────────────────────────────────

    public record ServiceToServiceContractRequest(
            @NotBlank String callerServiceId,
            @NotBlank String calleeServiceId,
            @NotBlank String contractVersion,
            List<String> allowedOperations,
            List<String> allowedScopes,
            List<String> allowedEventTopics,
            List<String> allowedRequestSources,
            Integer rateLimitRpm,
            Integer rateLimitBurst,
            @NotBlank String ownerTeam,
            ContactPointDto supportContact
    ) {}

    public record ServiceToServiceContractResponse(
            String id, String callerServiceId, String calleeServiceId, String contractVersion,
            List<String> allowedOperations, List<String> allowedScopes, List<String> allowedEventTopics,
            List<String> allowedRequestSources,
            Integer rateLimitRpm, Integer rateLimitBurst,
            String ownerTeam, ContactPointDto supportContact,
            OffsetDateTime lastVerifiedAt, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── Webhook Subscriptions ────────────────────────────────────────────────

    public record WebhookSubscriptionFilter(
            List<String> facilityIds,
            List<String> programmeIds
    ) {}

    public record WebhookSubscriptionRetryPolicy(
            Integer maxRetries, Integer initialDelayMs, Integer maxDelayMs, Integer deadLetterAfterRetries
    ) {}

    public record WebhookSubscriptionRequest(
            @NotBlank String externalAppId,
            @NotBlank String integrationContractId,
            List<String> eventTopics,
            @NotBlank String deliveryUrl,
            @NotBlank String signatureMethod,
            WebhookSubscriptionFilter filter,
            WebhookSubscriptionRetryPolicy retryPolicy
    ) {}

    public record WebhookSubscriptionResponse(
            String id, String externalAppId, String integrationContractId,
            List<String> eventTopics, String deliveryUrl, String signatureMethod, boolean active,
            WebhookSubscriptionFilter filter, WebhookSubscriptionRetryPolicy retryPolicy,
            OffsetDateTime lastDeliveryAt, String lastDeliveryStatus, int consecutiveFailures,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    // ── Event Catalogue ──────────────────────────────────────────────────────

    public record EventCatalogueEntryResponse(
            String id, String topic, String description, String classification,
            String sensitivityTier, String ownerServiceId,
            String schemaRef, String schemaVersion,
            boolean partnerPublishable, boolean containsPii, boolean containsPhi,
            boolean deprecated, OffsetDateTime introducedAt, OffsetDateTime updatedAt
    ) {}
}
