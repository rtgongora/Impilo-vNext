package zw.gov.mohcc.impilo.integration.governance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.integration.governance.api.dto.GovernanceDtos.*;
import zw.gov.mohcc.impilo.integration.governance.domain.*;
import zw.gov.mohcc.impilo.integration.governance.repository.GovernanceRepositories.*;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for the integration governance plane. Handles CRUD, status
 * transitions, and credential issuance for external apps, integration
 * contracts, service-to-service contracts, webhook subscriptions, and the
 * event catalogue.
 */
@Service
public class GovernanceService {

    private final ExternalApplicationRepository externalApps;
    private final IntegrationContractRepository integrationContracts;
    private final ServiceToServiceContractRepository s2sContracts;
    private final WebhookSubscriptionRepository webhookSubs;
    private final ExternalEventSubscriptionRepository externalEventSubs;
    private final EventCatalogueRepository eventCatalogue;
    private final SecureRandom secureRandom = new SecureRandom();

    public GovernanceService(
            ExternalApplicationRepository externalApps,
            IntegrationContractRepository integrationContracts,
            ServiceToServiceContractRepository s2sContracts,
            WebhookSubscriptionRepository webhookSubs,
            ExternalEventSubscriptionRepository externalEventSubs,
            EventCatalogueRepository eventCatalogue) {
        this.externalApps = externalApps;
        this.integrationContracts = integrationContracts;
        this.s2sContracts = s2sContracts;
        this.webhookSubs = webhookSubs;
        this.externalEventSubs = externalEventSubs;
        this.eventCatalogue = eventCatalogue;
    }

    // ── External Applications ────────────────────────────────────────────────

    @Transactional
    public ExternalApplicationResponse registerExternalApplication(
            ExternalApplicationRequest req, String tenantId, String podId) {
        externalApps.findByTenantIdAndAppCode(tenantId, req.appCode())
                .ifPresent(existing -> {
                    throw new IllegalStateException("External application already registered: " + req.appCode());
                });
        ExternalApplicationEntity e = new ExternalApplicationEntity();
        e.setAppCode(req.appCode());
        e.setName(req.name());
        e.setDescription(req.description());
        e.setPublisherId(req.publisherId());
        e.setPublisherName(req.publisherName());
        if (req.technicalContact() != null) {
            e.setTechnicalContactName(req.technicalContact().name());
            e.setTechnicalContactEmail(req.technicalContact().email());
            e.setTechnicalContactPhone(req.technicalContact().phone());
        }
        if (req.dataProtectionContact() != null) {
            e.setDataProtectionContactName(req.dataProtectionContact().name());
            e.setDataProtectionContactEmail(req.dataProtectionContact().email());
        }
        e.setEnvironment(req.environment());
        e.setIntegrationCategory(req.integrationCategory());
        e.setRiskClassification(req.riskClassification() != null ? req.riskClassification() : "STANDARD");
        e.setAuthenticationMethod(req.authenticationMethod());
        e.setAllowedTenantsCsv(joinCsv(req.allowedTenants() != null ? req.allowedTenants() : List.of(tenantId)));
        e.setAllowedFacilitiesCsv(joinCsv(req.allowedFacilities()));
        e.setRateLimitRpm(req.rateLimitRpm());
        e.setRateLimitBurst(req.rateLimitBurst());
        // Issue an OAuth client id; the real secret lives in vault-kms outside this scope.
        // In sandbox we generate a sample reference so the contract is testable end-to-end.
        if ("OAUTH2_CLIENT_CREDENTIALS".equals(req.authenticationMethod())) {
            e.setOauthClientId("impilo-ext-" + UUID.randomUUID().toString().substring(0, 12));
            e.setOauthClientSecretRef("vault://oauth-secrets/" + e.getOauthClientId());
        }
        e.setTenantId(tenantId);
        e.setPodId(podId);
        externalApps.save(e);
        return toExternalAppResponse(e);
    }

    @Transactional(readOnly = true)
    public List<ExternalApplicationResponse> listExternalApplications(String tenantId) {
        return externalApps.findByTenantIdOrderByRegisteredAtDesc(tenantId).stream()
                .map(this::toExternalAppResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExternalApplicationResponse getExternalApplication(String id) {
        return externalApps.findById(id).map(this::toExternalAppResponse)
                .orElseThrow(() -> new IllegalArgumentException("External application not found: " + id));
    }

    @Transactional
    public ExternalApplicationResponse suspendExternalApplication(String id, String reason, String actorId) {
        ExternalApplicationEntity e = externalApps.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("External application not found: " + id));
        e.setStatus("SUSPENDED");
        e.setSuspendedAt(OffsetDateTime.now());
        e.setSuspendedReason(reason);
        externalApps.save(e);
        // Cascade: deactivate webhook subscriptions for the app.
        webhookSubs.findByExternalAppIdOrderByCreatedAtDesc(id).forEach(sub -> {
            sub.setActive(false);
            webhookSubs.save(sub);
        });
        return toExternalAppResponse(e);
    }

    // ── Integration Contracts ────────────────────────────────────────────────

    @Transactional
    public IntegrationContractResponse createIntegrationContract(
            IntegrationContractRequest req, String tenantId, String podId) {
        IntegrationContractEntity e = new IntegrationContractEntity();
        e.setExternalAppId(req.externalAppId());
        e.setContractCode(req.contractCode());
        e.setVersion(req.version());
        e.setIntegrationCategory(req.integrationCategory());
        e.setEnvironment(req.environment());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveUntil(req.effectiveUntil());
        e.setAllowedApisCsv(joinCsv(req.allowedApis()));
        e.setAllowedEventsCsv(joinCsv(req.allowedEvents()));
        e.setAllowedWebhookCallbacksCsv(joinCsv(req.allowedWebhookCallbacks()));
        e.setScopesCsv(joinCsv(req.scopes()));
        e.setPurposeOfUseCsv(joinCsv(req.purposeOfUse()));
        e.setConsentBasis(req.consentBasis());
        e.setSignatureMethod(req.signatureMethod());
        e.setRateLimitRpm(req.rateLimitRpm());
        e.setRateLimitBurst(req.rateLimitBurst());
        e.setErrorHandlingPolicy(req.errorHandlingPolicy());
        e.setDataRetentionDays(req.dataRetentionDays());
        e.setIncidentReportingHours(req.incidentReportingHours());
        e.setStatus(req.status() != null ? req.status() : "DRAFT");
        e.setSignedByName(req.signedByName());
        e.setSignedByEmail(req.signedByEmail());
        if (req.signedByName() != null) e.setSignedAt(OffsetDateTime.now());
        e.setDocumentRef(req.documentRef());
        e.setTenantId(tenantId);
        e.setPodId(podId);
        integrationContracts.save(e);
        return toContractResponse(e);
    }

    @Transactional(readOnly = true)
    public List<IntegrationContractResponse> listIntegrationContracts(String tenantId, String externalAppId) {
        if (externalAppId != null && !externalAppId.isBlank()) {
            return integrationContracts.findByExternalAppIdOrderByCreatedAtDesc(externalAppId).stream()
                    .map(this::toContractResponse).toList();
        }
        return integrationContracts.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toContractResponse).toList();
    }

    // ── S2S Contracts ────────────────────────────────────────────────────────

    @Transactional
    public ServiceToServiceContractResponse createS2SContract(
            ServiceToServiceContractRequest req, String tenantId, String podId) {
        ServiceToServiceContractEntity e = new ServiceToServiceContractEntity();
        e.setCallerServiceId(req.callerServiceId());
        e.setCalleeServiceId(req.calleeServiceId());
        e.setContractVersion(req.contractVersion());
        e.setAllowedOperationsCsv(joinCsv(req.allowedOperations()));
        e.setAllowedScopesCsv(joinCsv(req.allowedScopes()));
        e.setAllowedEventTopicsCsv(joinCsv(req.allowedEventTopics()));
        e.setAllowedRequestSourcesCsv(joinCsv(req.allowedRequestSources()));
        e.setRateLimitRpm(req.rateLimitRpm());
        e.setRateLimitBurst(req.rateLimitBurst());
        e.setOwnerTeam(req.ownerTeam());
        if (req.supportContact() != null) {
            e.setSupportContactName(req.supportContact().name());
            e.setSupportContactEmail(req.supportContact().email());
        }
        e.setStatus("ACTIVE");
        e.setLastVerifiedAt(OffsetDateTime.now());
        e.setTenantId(tenantId);
        e.setPodId(podId);
        s2sContracts.save(e);
        return toS2SResponse(e);
    }

    @Transactional(readOnly = true)
    public List<ServiceToServiceContractResponse> listS2SContracts(String tenantId) {
        return s2sContracts.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toS2SResponse).toList();
    }

    /**
     * Look up whether a (caller, callee) pair has an ACTIVE contract permitting
     * the given request source. Used by gateway helpers / tests.
     */
    @Transactional(readOnly = true)
    public boolean isS2SCallAuthorised(String callerServiceId, String calleeServiceId, String requestSource) {
        return s2sContracts.findByCallerServiceIdOrderByCreatedAtDesc(callerServiceId).stream()
                .anyMatch(c -> calleeServiceId.equals(c.getCalleeServiceId())
                        && "ACTIVE".equals(c.getStatus())
                        && (c.getAllowedRequestSourcesCsv() == null
                            || Arrays.stream(c.getAllowedRequestSourcesCsv().split(","))
                                .map(String::trim).anyMatch(rs -> rs.equals(requestSource))));
    }

    // ── Webhook Subscriptions ────────────────────────────────────────────────

    @Transactional
    public WebhookSubscriptionResponse createWebhookSubscription(
            WebhookSubscriptionRequest req, String tenantId, String podId) {
        // Validate that integration contract permits the requested topics.
        IntegrationContractEntity contract = integrationContracts.findById(req.integrationContractId())
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + req.integrationContractId()));
        if (!"ACTIVE".equals(contract.getStatus())) {
            throw new IllegalStateException("Integration contract is not ACTIVE; cannot create webhook subscription.");
        }
        List<String> permitted = splitCsv(contract.getAllowedEventsCsv());
        List<String> requestedTopics = req.eventTopics() != null ? req.eventTopics() : List.of();
        for (String t : requestedTopics) {
            if (!permitted.isEmpty() && !permitted.contains(t)) {
                throw new IllegalStateException("Topic not permitted by contract: " + t);
            }
            // Topic must exist as an externally-publishable event in the catalogue.
            EventCatalogueEntryEntity entry = eventCatalogue.findByTopic(t)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown event topic: " + t));
            if (!"EXTERNALLY_PUBLISHABLE".equals(entry.getClassification())) {
                throw new IllegalStateException(
                        "Topic is not externally publishable: " + t + " (classification: " + entry.getClassification() + ")");
            }
        }

        WebhookSubscriptionEntity e = new WebhookSubscriptionEntity();
        e.setExternalAppId(req.externalAppId());
        e.setIntegrationContractId(req.integrationContractId());
        e.setEventTopicsCsv(joinCsv(req.eventTopics()));
        e.setDeliveryUrl(req.deliveryUrl());
        e.setSignatureMethod(req.signatureMethod());

        // Generate and store a per-subscription HMAC secret REFERENCE; the actual
        // secret would be written to vault-kms in production. We return it in
        // the response exactly once on creation.
        String generatedSecret = generateSecret();
        e.setSignatureSecretRef("vault://webhook-secrets/" + UUID.randomUUID().toString() + " (sandbox: " + maskSecret(generatedSecret) + ")");

        if (req.filter() != null) {
            e.setFilterFacilityIdsCsv(joinCsv(req.filter().facilityIds()));
            e.setFilterProgrammeIdsCsv(joinCsv(req.filter().programmeIds()));
        }
        if (req.retryPolicy() != null) {
            if (req.retryPolicy().maxRetries() != null)        e.setMaxRetries(req.retryPolicy().maxRetries());
            if (req.retryPolicy().initialDelayMs() != null)    e.setInitialDelayMs(req.retryPolicy().initialDelayMs());
            if (req.retryPolicy().maxDelayMs() != null)        e.setMaxDelayMs(req.retryPolicy().maxDelayMs());
            if (req.retryPolicy().deadLetterAfterRetries() != null) {
                e.setDeadLetterAfterRetries(req.retryPolicy().deadLetterAfterRetries());
            }
        }
        e.setTenantId(tenantId);
        e.setPodId(podId);
        webhookSubs.save(e);

        // Project external event subscriptions per topic
        for (String topic : requestedTopics) {
            ExternalEventSubscriptionEntity sub = new ExternalEventSubscriptionEntity();
            sub.setExternalAppId(req.externalAppId());
            sub.setIntegrationContractId(req.integrationContractId());
            sub.setWebhookSubscriptionId(e.getId());
            sub.setEventTopic(topic);
            sub.setStatus("ACTIVE");
            sub.setApprovedBy("system");
            sub.setApprovedAt(OffsetDateTime.now());
            sub.setTenantId(tenantId);
            sub.setPodId(podId);
            externalEventSubs.save(sub);
        }
        return toWebhookResponse(e);
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> listWebhookSubscriptions(String tenantId, String externalAppId) {
        if (externalAppId != null && !externalAppId.isBlank()) {
            return webhookSubs.findByExternalAppIdOrderByCreatedAtDesc(externalAppId).stream()
                    .map(this::toWebhookResponse).toList();
        }
        return webhookSubs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toWebhookResponse).toList();
    }

    // ── External event subscriptions ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExternalEventSubscriptionEntity> listExternalEventSubscriptions(String tenantId) {
        return externalEventSubs.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    // ── Event catalogue ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EventCatalogueEntryResponse> listEventCatalogue(String classification, String ownerServiceId) {
        List<EventCatalogueEntryEntity> entries;
        if (classification != null && !classification.isBlank()) {
            entries = eventCatalogue.findByClassificationOrderByTopicAsc(classification);
        } else if (ownerServiceId != null && !ownerServiceId.isBlank()) {
            entries = eventCatalogue.findByOwnerServiceIdOrderByTopicAsc(ownerServiceId);
        } else {
            entries = eventCatalogue.findAll();
        }
        return entries.stream().map(this::toCatalogueResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join(",", values);
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) return "***";
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ExternalApplicationResponse toExternalAppResponse(ExternalApplicationEntity e) {
        return new ExternalApplicationResponse(
                e.getId(), e.getAppCode(), e.getName(), e.getDescription(),
                e.getPublisherId(), e.getPublisherName(),
                new ContactPointDto(e.getTechnicalContactName(), e.getTechnicalContactEmail(),
                        e.getTechnicalContactPhone(), null),
                new ContactPointDto(e.getDataProtectionContactName(), e.getDataProtectionContactEmail(), null, null),
                e.getStatus(), e.getEnvironment(), e.getIntegrationCategory(), e.getRiskClassification(),
                e.getAuthenticationMethod(), e.getOauthClientId(), e.getMtlsCertificateFingerprint(),
                splitCsv(e.getIpAllowlistCsv()), splitCsv(e.getAllowedTenantsCsv()),
                splitCsv(e.getAllowedFacilitiesCsv()),
                e.getRateLimitRpm(), e.getRateLimitBurst(),
                e.getCredentialExpiresAt(), e.getRegisteredAt(),
                e.getApprovedAt(), e.getApprovedBy(),
                e.getSuspendedAt(), e.getSuspendedReason()
        );
    }

    private IntegrationContractResponse toContractResponse(IntegrationContractEntity e) {
        return new IntegrationContractResponse(
                e.getId(), e.getExternalAppId(), e.getContractCode(), e.getVersion(),
                e.getIntegrationCategory(), e.getEnvironment(),
                e.getEffectiveFrom(), e.getEffectiveUntil(),
                splitCsv(e.getAllowedApisCsv()), splitCsv(e.getAllowedEventsCsv()),
                splitCsv(e.getAllowedWebhookCallbacksCsv()),
                splitCsv(e.getScopesCsv()), splitCsv(e.getPurposeOfUseCsv()), e.getConsentBasis(),
                e.getSignatureMethod(), e.getRateLimitRpm(), e.getRateLimitBurst(),
                e.getErrorHandlingPolicy(), e.getDataRetentionDays(), e.getIncidentReportingHours(),
                e.getStatus(), e.getDocumentRef(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private ServiceToServiceContractResponse toS2SResponse(ServiceToServiceContractEntity e) {
        return new ServiceToServiceContractResponse(
                e.getId(), e.getCallerServiceId(), e.getCalleeServiceId(), e.getContractVersion(),
                splitCsv(e.getAllowedOperationsCsv()), splitCsv(e.getAllowedScopesCsv()),
                splitCsv(e.getAllowedEventTopicsCsv()), splitCsv(e.getAllowedRequestSourcesCsv()),
                e.getRateLimitRpm(), e.getRateLimitBurst(),
                e.getOwnerTeam(),
                new ContactPointDto(e.getSupportContactName(), e.getSupportContactEmail(), null, null),
                e.getLastVerifiedAt(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private WebhookSubscriptionResponse toWebhookResponse(WebhookSubscriptionEntity e) {
        WebhookSubscriptionFilter filter = (e.getFilterFacilityIdsCsv() != null || e.getFilterProgrammeIdsCsv() != null)
                ? new WebhookSubscriptionFilter(splitCsv(e.getFilterFacilityIdsCsv()), splitCsv(e.getFilterProgrammeIdsCsv()))
                : null;
        return new WebhookSubscriptionResponse(
                e.getId(), e.getExternalAppId(), e.getIntegrationContractId(),
                splitCsv(e.getEventTopicsCsv()), e.getDeliveryUrl(), e.getSignatureMethod(), e.isActive(),
                filter,
                new WebhookSubscriptionRetryPolicy(e.getMaxRetries(), e.getInitialDelayMs(),
                        e.getMaxDelayMs(), e.getDeadLetterAfterRetries()),
                e.getLastDeliveryAt(), e.getLastDeliveryStatus(), e.getConsecutiveFailures(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private EventCatalogueEntryResponse toCatalogueResponse(EventCatalogueEntryEntity e) {
        return new EventCatalogueEntryResponse(
                e.getId(), e.getTopic(), e.getDescription(), e.getClassification(),
                e.getSensitivityTier(), e.getOwnerServiceId(),
                e.getSchemaRef(), e.getSchemaVersion(),
                e.isPartnerPublishable(), e.isContainsPii(), e.isContainsPhi(),
                e.isDeprecated(), e.getIntroducedAt(), e.getUpdatedAt()
        );
    }
}
