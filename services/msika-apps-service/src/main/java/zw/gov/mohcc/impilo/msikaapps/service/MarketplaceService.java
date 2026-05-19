package zw.gov.mohcc.impilo.msikaapps.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaapps.api.dto.MsikaAppsDtos.*;
import zw.gov.mohcc.impilo.msikaapps.domain.*;
import zw.gov.mohcc.impilo.msikaapps.repository.MsikaAppsRepositories.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * MarketplaceService — orchestrates the capability marketplace lifecycle:
 * discover → request → approve → install/activate → configure → monitor → suspend.
 *
 * Distinct from MSIKA (commerce product/service registry) and MSIKA Flow
 * (commerce orchestration). See HEALTH_OS_EXTENSIBILITY_DOCTRINE.md.
 */
@Service
public class MarketplaceService {

    private final MarketplaceItemRepository items;
    private final PublisherRepository publishers;
    private final ActivationRequestRepository activationRequests;
    private final InstallationRepository installations;
    private final AuditEventRepository auditEvents;

    public MarketplaceService(MarketplaceItemRepository items,
                              PublisherRepository publishers,
                              ActivationRequestRepository activationRequests,
                              InstallationRepository installations,
                              AuditEventRepository auditEvents) {
        this.items = items;
        this.publishers = publishers;
        this.activationRequests = activationRequests;
        this.installations = installations;
        this.auditEvents = auditEvents;
    }

    // ── Catalogue ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> searchCatalogue(String type, String category, String publisherId) {
        Map<String, PublisherEntity> pubIndex = indexPublishers();
        return items.search(blankToNull(type), blankToNull(category), blankToNull(publisherId)).stream()
                .map(i -> toItemResponse(i, pubIndex))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceItemResponse getItem(String id) {
        MarketplaceItemEntity item = items.findById(id)
                .or(() -> items.findByItemCode(id))
                .orElseThrow(() -> new IllegalArgumentException("Marketplace item not found: " + id));
        return toItemResponse(item, indexPublishers());
    }

    @Transactional(readOnly = true)
    public List<PublisherResponse> listPublishers() {
        return publishers.findAllByOrderByNameAsc().stream().map(this::toPublisherResponse).toList();
    }

    // ── Activation ───────────────────────────────────────────────────────────

    @Transactional
    public ActivationRequestResponse createActivationRequest(
            ActivationRequestRequest req, String actorId, String tenantId, String podId, String correlationId) {
        MarketplaceItemEntity item = items.findById(req.itemId())
                .orElseThrow(() -> new IllegalArgumentException("Marketplace item not found: " + req.itemId()));
        if (!"APPROVED".equals(item.getApprovalStatus())) {
            throw new IllegalStateException("Item is not APPROVED; cannot request activation: " + item.getItemCode());
        }
        ActivationRequestEntity e = new ActivationRequestEntity();
        e.setItemId(item.getId());
        e.setRequesterActorId(actorId);
        e.setRequesterTenantId(tenantId);
        e.setRequesterFacilityId(req.facilityId());
        e.setRequesterWorkspaceId(req.workspaceId());
        e.setRequesterProgrammeId(req.programmeId());
        e.setPurposeOfUse(req.purposeOfUse());
        e.setJustification(req.justification());
        e.setRequestedConfigurationJson(req.requestedConfigurationJson());
        e.setStatus("REQUESTED");
        e.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        e.setPodId(podId);
        activationRequests.save(e);

        recordAudit("ACTIVATION_REQUESTED", item.getId(), null, e.getId(), null,
                actorId, "HUMAN", tenantId, req.facilityId(), req.justification(), e.getCorrelationId());
        return toActivationResponse(e);
    }

    @Transactional
    public ActivationRequestResponse decideActivationRequest(
            String requestId, ActivationDecisionRequest decision, String actorId, String tenantId) {
        ActivationRequestEntity req = activationRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Activation request not found: " + requestId));
        if (!"REQUESTED".equals(req.getStatus()) && !"IN_REVIEW".equals(req.getStatus())) {
            throw new IllegalStateException("Activation request is not awaiting decision: " + req.getStatus());
        }
        String d = decision.decision();
        if (!List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED").contains(d)) {
            throw new IllegalArgumentException("Invalid decision: " + d);
        }
        req.setStatus(d);
        req.setDecidedAt(OffsetDateTime.now());
        req.setDecisionReason(decision.reason());
        activationRequests.save(req);
        recordAudit("ACTIVATION_" + d, req.getItemId(), null, req.getId(), null,
                actorId, "HUMAN", tenantId, req.getRequesterFacilityId(), decision.reason(), req.getCorrelationId());

        // On approval, materialise an installation in INSTALLED lifecycle.
        // Activation requires explicit configure → activate.
        if ("APPROVED".equals(d)) {
            ensureInstallationForApprovedRequest(req, actorId);
        }
        return toActivationResponse(req);
    }

    private void ensureInstallationForApprovedRequest(ActivationRequestEntity req, String actorId) {
        MarketplaceItemEntity item = items.findById(req.getItemId())
                .orElseThrow(() -> new IllegalStateException("Item missing for request: " + req.getId()));
        List<InstallationEntity> existing = installations.findByItemIdAndTenantIdOrderByInstalledAtDesc(
                item.getId(), req.getRequesterTenantId());
        if (!existing.isEmpty()) return;

        InstallationEntity inst = new InstallationEntity();
        inst.setItemId(item.getId());
        inst.setItemVersion(item.getVersion());
        inst.setTenantId(req.getRequesterTenantId());
        inst.setFacilityIdsCsv(req.getRequesterFacilityId());
        inst.setLifecycle("INSTALLED");
        inst.setInstalledBy(actorId);
        inst.setInstalledAt(OffsetDateTime.now());
        inst.setHealthStatus("HEALTHY");
        inst.setPodId(req.getPodId());
        installations.save(inst);
        recordAudit("INSTALLATION_CREATED", item.getId(), inst.getId(), req.getId(), null,
                actorId, "SYSTEM", req.getRequesterTenantId(), req.getRequesterFacilityId(),
                "Auto-installed after approval", req.getCorrelationId());
    }

    @Transactional(readOnly = true)
    public List<ActivationRequestResponse> listActivationRequests(String tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return activationRequests.findByStatusOrderByCreatedAtAsc(status).stream()
                    .filter(r -> r.getRequesterTenantId().equals(tenantId))
                    .map(this::toActivationResponse).toList();
        }
        return activationRequests.findByRequesterTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toActivationResponse).toList();
    }

    // ── Installation lifecycle ──────────────────────────────────────────────

    @Transactional
    public InstallationResponse configureInstallation(
            String installationId, InstallationConfigureRequest req, String actorId, String tenantId) {
        InstallationEntity inst = requireInstallation(installationId, tenantId);
        if (req.facilityIds()  != null) inst.setFacilityIdsCsv(joinCsv(req.facilityIds()));
        if (req.workspaceIds() != null) inst.setWorkspaceIdsCsv(joinCsv(req.workspaceIds()));
        if (req.programmeIds() != null) inst.setProgrammeIdsCsv(joinCsv(req.programmeIds()));
        if (req.rolesEnabled() != null) inst.setRolesEnabledCsv(joinCsv(req.rolesEnabled()));
        if (req.configurationJson()      != null) inst.setConfigurationJson(req.configurationJson());
        if (req.integrationContractId()  != null) inst.setIntegrationContractId(req.integrationContractId());
        if (req.externalAppId()          != null) inst.setExternalAppId(req.externalAppId());
        inst.setConfiguredAt(OffsetDateTime.now());
        installations.save(inst);
        recordAudit("INSTALLATION_CONFIGURED", inst.getItemId(), inst.getId(), null, inst.getExternalAppId(),
                actorId, "HUMAN", tenantId, null, null, null);
        return toInstallationResponse(inst);
    }

    @Transactional
    public InstallationResponse activateInstallation(String installationId, String actorId, String tenantId) {
        InstallationEntity inst = requireInstallation(installationId, tenantId);
        if (!"INSTALLED".equals(inst.getLifecycle()) && !"SUSPENDED".equals(inst.getLifecycle())) {
            throw new IllegalStateException("Installation cannot be activated from lifecycle " + inst.getLifecycle());
        }
        inst.setLifecycle("ACTIVE");
        inst.setActivatedAt(OffsetDateTime.now());
        inst.setSuspendedAt(null);
        inst.setSuspendedReason(null);
        installations.save(inst);
        recordAudit("INSTALLATION_ACTIVATED", inst.getItemId(), inst.getId(), null, inst.getExternalAppId(),
                actorId, "HUMAN", tenantId, null, null, null);
        return toInstallationResponse(inst);
    }

    @Transactional
    public InstallationResponse suspendInstallation(String installationId, InstallationSuspendRequest req, String actorId, String tenantId) {
        InstallationEntity inst = requireInstallation(installationId, tenantId);
        inst.setLifecycle("SUSPENDED");
        inst.setSuspendedAt(OffsetDateTime.now());
        inst.setSuspendedReason(req.reason());
        installations.save(inst);
        recordAudit("INSTALLATION_SUSPENDED", inst.getItemId(), inst.getId(), null, inst.getExternalAppId(),
                actorId, "HUMAN", tenantId, null, req.reason(), null);
        return toInstallationResponse(inst);
    }

    @Transactional(readOnly = true)
    public List<InstallationResponse> listInstallations(String tenantId, String lifecycle) {
        List<InstallationEntity> list = (lifecycle != null && !lifecycle.isBlank())
                ? installations.findByTenantIdAndLifecycleOrderByInstalledAtDesc(tenantId, lifecycle)
                : installations.findByTenantIdOrderByInstalledAtDesc(tenantId);
        return list.stream().map(this::toInstallationResponse).toList();
    }

    @Transactional(readOnly = true)
    public InstallationResponse getInstallation(String id, String tenantId) {
        return toInstallationResponse(requireInstallation(id, tenantId));
    }

    // ── Launcher view (role-aware) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LauncherAppResponse> getLauncher(String tenantId, String facilityId, List<String> roles) {
        Map<String, PublisherEntity> pubIndex = indexPublishers();
        Map<String, InstallationEntity> latestInstallByItem = new HashMap<>();
        installations.findByTenantIdOrderByInstalledAtDesc(tenantId)
                .forEach(i -> latestInstallByItem.putIfAbsent(i.getItemId(), i));

        // Apps + AI skills are the main launcher candidates. Connectors,
        // adapters, content/workflow packs are configured by admins and surface
        // inside their host apps rather than as standalone launcher tiles.
        List<String> launcherTypes = List.of("APP", "AI_SKILL");
        return items.findAll().stream()
                .filter(item -> launcherTypes.contains(item.getType()))
                .filter(item -> !isHidden(item))
                .map(item -> buildLauncherTile(item, latestInstallByItem.get(item.getId()), facilityId, roles, pubIndex))
                .filter(Objects::nonNull)
                .toList();
    }

    private LauncherAppResponse buildLauncherTile(
            MarketplaceItemEntity item, InstallationEntity inst,
            String facilityId, List<String> roles,
            Map<String, PublisherEntity> pubIndex) {
        List<String> rolesAllowed = splitCsv(item.getRolesAllowedCsv());
        boolean roleMatch = rolesAllowed.isEmpty() || rolesAllowed.contains("ALL")
                || (roles != null && roles.stream().anyMatch(rolesAllowed::contains));
        if (!roleMatch) {
            return null;
        }
        String state;
        String reason = null;
        String installationId = inst != null ? inst.getId() : null;
        if (inst == null) {
            state = "REQUEST_ACCESS";
            reason = "This capability is available but not yet activated for your facility.";
        } else if ("ACTIVE".equals(inst.getLifecycle())) {
            state = "INSTALLED";
            if (facilityId != null && inst.getFacilityIdsCsv() != null
                    && !splitCsv(inst.getFacilityIdsCsv()).isEmpty()
                    && !splitCsv(inst.getFacilityIdsCsv()).contains(facilityId)) {
                state = "NOT_AVAILABLE_AT_FACILITY";
                reason = "This installation is not enabled for the selected facility.";
            }
        } else if ("INSTALLED".equals(inst.getLifecycle())) {
            state = "REQUIRES_CONFIGURATION";
            reason = "Installed but not yet activated. An administrator must configure and activate.";
        } else if ("SUSPENDED".equals(inst.getLifecycle())) {
            state = "SUSPENDED";
            reason = inst.getSuspendedReason() != null ? inst.getSuspendedReason() : "Installation suspended.";
        } else {
            state = "TEMPORARILY_UNAVAILABLE";
            reason = "Lifecycle: " + inst.getLifecycle();
        }
        if (item.getDeprecationDate() != null && item.getDeprecationDate().isBefore(OffsetDateTime.now())) {
            state = "DEPRECATED";
            reason = "This capability has been deprecated.";
        }
        return new LauncherAppResponse(
                item.getId(), item.getItemCode(), item.getName(), item.getDescription(),
                item.getType(), item.getCategory(), item.getIconRef(),
                state, reason,
                launchUrlFor(item), rolesAllowed, installationId
        );
    }

    private boolean isHidden(MarketplaceItemEntity item) {
        return "SUSPENDED".equals(item.getApprovalStatus())
                || "DEPRECATED".equals(item.getApprovalStatus());
    }

    private String launchUrlFor(MarketplaceItemEntity item) {
        // The launcher returns a relative URL hint; the host shell maps it to a
        // route. For AI skills, surfacing happens inside Nompilo, so no URL.
        if ("AI_SKILL".equals(item.getType())) return null;
        return "/launcher/launch/" + item.getItemCode();
    }

    // ── Audit ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuditEventResponse> listAuditEvents(String tenantId, String itemId) {
        List<AuditEventEntity> list = (itemId != null && !itemId.isBlank())
                ? auditEvents.findByItemIdOrderByOccurredAtDesc(itemId)
                : auditEvents.findByTenantIdOrderByOccurredAtDesc(tenantId);
        return list.stream().map(this::toAuditResponse).toList();
    }

    private void recordAudit(String eventType, String itemId, String installationId, String activationRequestId,
                             String externalAppId, String actorId, String actorType, String tenantId,
                             String facilityId, String reason, String correlationId) {
        AuditEventEntity ev = new AuditEventEntity();
        ev.setEventType(eventType);
        ev.setItemId(itemId);
        ev.setInstallationId(installationId);
        ev.setActivationRequestId(activationRequestId);
        ev.setExternalAppId(externalAppId);
        ev.setActorId(actorId);
        ev.setActorType(actorType);
        ev.setTenantId(tenantId);
        ev.setFacilityId(facilityId);
        ev.setReason(reason);
        ev.setCorrelationId(correlationId);
        auditEvents.save(ev);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private InstallationEntity requireInstallation(String id, String tenantId) {
        InstallationEntity inst = installations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Installation not found: " + id));
        if (!inst.getTenantId().equals(tenantId)) {
            throw new SecurityException("Installation belongs to a different tenant.");
        }
        return inst;
    }

    private Map<String, PublisherEntity> indexPublishers() {
        Map<String, PublisherEntity> out = new HashMap<>();
        publishers.findAll().forEach(p -> out.put(p.getId(), p));
        return out;
    }

    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join(",", values);
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private PublisherResponse toPublisherResponse(PublisherEntity p) {
        return new PublisherResponse(p.getId(), p.getName(), p.getLegalName(),
                p.getWebsiteUrl(), p.getSupportEmail(),
                p.getStatus(), p.getVerifiedAt(), p.getCreatedAt());
    }

    private MarketplaceItemResponse toItemResponse(MarketplaceItemEntity m, Map<String, PublisherEntity> pubIndex) {
        PublisherEntity pub = pubIndex.get(m.getPublisherId());
        return new MarketplaceItemResponse(
                m.getId(), m.getItemCode(), m.getName(), m.getDescription(),
                m.getType(), m.getCategory(),
                m.getPublisherId(), pub != null ? pub.getName() : m.getPublisherId(),
                m.getVersion(), m.getCompatibilityVersion(),
                splitCsv(m.getSupportedEnvironments()),
                m.getVisibility(),
                m.getIconRef(), m.getDocumentationUrl(), m.getManifestRef(),
                m.getSecurityClassification(), m.getDataProtectionClassification(),
                m.getClinicalSafetyClassification(),
                m.getApprovalStatus(), m.getCertificationStatus(), m.getRegulatoryStatus(),
                m.getPricingModel(), m.getLicensingModel(),
                splitCsv(m.getRequiredScopesCsv()), splitCsv(m.getRequiredEventsCsv()),
                splitCsv(m.getRequiredApisCsv()), splitCsv(m.getRequiredDataCategoriesCsv()),
                splitCsv(m.getRequiredPermissionsCsv()),
                splitCsv(m.getFacilityTypeWhitelistCsv()), splitCsv(m.getRolesAllowedCsv()),
                splitCsv(m.getConsentRequirementsCsv()),
                m.getHealthStatus(), m.getLastUpdated(), m.getDeprecationDate(),
                m.getCreatedAt(), m.getUpdatedAt()
        );
    }

    private ActivationRequestResponse toActivationResponse(ActivationRequestEntity e) {
        return new ActivationRequestResponse(
                e.getId(), e.getItemId(),
                e.getRequesterActorId(), e.getRequesterTenantId(),
                e.getRequesterFacilityId(), e.getRequesterWorkspaceId(), e.getRequesterProgrammeId(),
                e.getPurposeOfUse(), e.getJustification(),
                e.getStatus(), e.getDecidedAt(), e.getDecisionReason(),
                e.getCorrelationId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private InstallationResponse toInstallationResponse(InstallationEntity i) {
        return new InstallationResponse(
                i.getId(), i.getItemId(), i.getItemVersion(),
                i.getTenantId(),
                splitCsv(i.getFacilityIdsCsv()), splitCsv(i.getWorkspaceIdsCsv()),
                splitCsv(i.getProgrammeIdsCsv()), splitCsv(i.getRolesEnabledCsv()),
                i.getConfigurationJson(), i.getIntegrationContractId(), i.getExternalAppId(),
                i.getLifecycle(), i.getInstalledBy(),
                i.getInstalledAt(), i.getConfiguredAt(), i.getActivatedAt(),
                i.getSuspendedAt(), i.getSuspendedReason(),
                i.getLastHealthCheckAt(), i.getHealthStatus(),
                i.getCreatedAt(), i.getUpdatedAt()
        );
    }

    private AuditEventResponse toAuditResponse(AuditEventEntity a) {
        return new AuditEventResponse(
                a.getId(), a.getItemId(), a.getInstallationId(), a.getActivationRequestId(),
                a.getExternalAppId(), a.getEventType(),
                a.getActorId(), a.getActorType(),
                a.getTenantId(), a.getFacilityId(),
                a.getReason(), a.getCorrelationId(), a.getOccurredAt()
        );
    }
}
