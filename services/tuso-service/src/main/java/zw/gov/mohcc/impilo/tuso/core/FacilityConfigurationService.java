package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ChecklistItem;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ConfigEvent;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.ConfigurationSummary;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.DownstreamReadiness;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.QueueDefinitionView;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityConfigurationDto.SetupChecklist;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityConfigVersionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityConfigVersionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composes existing TUSO facility entities into the read-models the Facility Configuration Console
 * consumes: configuration summary, setup/readiness checklist, derived queue definitions and downstream
 * readiness. TUSO is the source of truth for facility configuration.
 *
 * <p>Honest by construction: the configuration state and checklist reflect what actually exists in TUSO.
 * Missing sections are reported as MISSING/PENDING — never faked to COMPLETE. Live downstream (PCT)
 * materialisation status is not fabricated here; it is surfaced through the PCT queue materialisation
 * viewer.</p>
 */
@Service
public class FacilityConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(FacilityConfigurationService.class);

    /** Outbox subject-type used to tag facility-scoped configuration audit events. */
    public static final String SUBJECT_FACILITY = "FACILITY";

    /**
     * Dedicated aggregate type for PCT queue reconcile triggers. The dual-emit publisher derives the
     * v1.1 Kafka topic from the aggregate type, so this maps to {@code impilo.tuso.facility_queue_config}
     * — a purpose-scoped channel PCT subscribes to (it is not flooded with unrelated facility events).
     */
    public static final String QUEUE_RECONCILE_AGGREGATE = "FACILITY_QUEUE_CONFIG";

    /** Event type carried on the reconcile trigger. */
    public static final String QUEUE_RECONCILE_EVENT = "FacilityQueueConfigurationChanged";

    /**
     * Build a PCT-consumable queue reconcile-trigger outbox event for a queue-relevant facility
     * configuration change (service point / workspace / published config). The event is a trigger only:
     * it carries just enough to identify the facility (UUID) and tenant, plus a {@code changeType} tag —
     * it deliberately does NOT carry queue definitions. PCT reconciles the whole facility from TUSO's
     * current queue definitions when it receives this event, which keeps materialisation idempotent and
     * robust to missed/duplicated/replayed events. Queue definitions remain TUSO-owned; PCT only
     * materialises them.
     *
     * <p>The {@code facilityId} payload field carries the facility <em>UUID</em> (the identifier PCT
     * uses to reconcile), matching {@code PctEventConsumer}. Returns {@code null} when the facility UUID
     * or tenant is unknown, so callers never emit an unreconcilable trigger.</p>
     */
    public static EventOutboxEntity queueReconcileTrigger(UUID facilityUuid, UUID tenantId, String changeType) {
        if (facilityUuid == null || tenantId == null) {
            return null;
        }
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(QUEUE_RECONCILE_AGGREGATE);
        event.setAggregateId(facilityUuid.toString());
        event.setEventType(QUEUE_RECONCILE_EVENT);
        event.setTenantId(tenantId.toString());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("facilityId", facilityUuid.toString());
        payload.put("facilityUuid", facilityUuid.toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("changeType", changeType);
        event.setPayload(payload);
        return event;
    }

    private final FacilityRepository facilityRepository;
    private final ServicePointRepository servicePointRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FacilityUnitRepository facilityUnitRepository;
    private final FacilityReadinessRepository readinessRepository;
    private final FacilityContactRepository contactRepository;
    private final FacilityConfigVersionRepository configVersionRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilityConfigurationService(FacilityRepository facilityRepository,
                                        ServicePointRepository servicePointRepository,
                                        WorkspaceRepository workspaceRepository,
                                        FacilityUnitRepository facilityUnitRepository,
                                        FacilityReadinessRepository readinessRepository,
                                        FacilityContactRepository contactRepository,
                                        FacilityConfigVersionRepository configVersionRepository,
                                        EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.servicePointRepository = servicePointRepository;
        this.workspaceRepository = workspaceRepository;
        this.facilityUnitRepository = facilityUnitRepository;
        this.readinessRepository = readinessRepository;
        this.contactRepository = contactRepository;
        this.configVersionRepository = configVersionRepository;
        this.outboxRepository = outboxRepository;
    }

    // ── Reads ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConfigurationSummary getConfigurationSummary(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = requireFacility(ctx, facilityId);

        long activeServicePoints = servicePointRepository.countByFacilityIdAndActiveTrue(facilityId);
        long activeWorkspaces = workspaceRepository.findByFacilityIdAndActiveTrue(facilityId).size();
        long departments = facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId).size();
        long activeQueueDefs = activeServicePoints; // one queue definition per active service point

        List<String> missing = missingRequiredSections(facility, activeServicePoints, activeWorkspaces);
        boolean setupComplete = missing.isEmpty();
        FacilityConfigurationState state = computeState(
                facility, activeServicePoints, activeWorkspaces, departments, setupComplete);

        FacilityConfigVersionEntity latest =
                configVersionRepository.findLatestActive(facilityId).orElse(null);

        return new ConfigurationSummary(
                facility.getId(),
                facility.getFacilityUuid() != null ? facility.getFacilityUuid().toString() : null,
                facility.getName(),
                facility.getFacilityCode(),
                facility.getFacilityType(),
                facility.getProvince(),
                facility.getDistrict(),
                facility.getRegulatoryStatus() != null ? facility.getRegulatoryStatus().name() : null,
                facility.getOperationalStatus(),
                facility.getStatus(),
                state.name(),
                activeServicePoints,
                activeWorkspaces,
                departments,
                activeQueueDefs,
                setupComplete,
                missing,
                sourceProvenance(facility),
                facility.getVersion(),
                facility.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public SetupChecklist getSetupChecklist(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = requireFacility(ctx, facilityId);

        long activeServicePoints = servicePointRepository.countByFacilityIdAndActiveTrue(facilityId);
        long activeWorkspaces = workspaceRepository.findByFacilityIdAndActiveTrue(facilityId).size();
        long departments = facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId).size();
        FacilityReadinessEntity readiness = readinessRepository.findByFacilityId(facilityId).orElse(null);
        List<FacilityContactEntity> contacts = contactRepository.findByFacilityId(facilityId);

        List<String> missing = missingRequiredSections(facility, activeServicePoints, activeWorkspaces);
        boolean setupComplete = missing.isEmpty();
        FacilityConfigurationState state = computeState(
                facility, activeServicePoints, activeWorkspaces, departments, setupComplete);

        List<ChecklistItem> items = new ArrayList<>();
        // Identity — always present if the facility exists.
        items.add(new ChecklistItem("identity_imported", "Facility identity imported",
                "COMPLETE", true, "Facility record exists in TUSO"));
        items.add(present("facility_code", "Facility code present", true,
                notBlank(facility.getFacilityCode()), facility.getFacilityCode()));
        items.add(present("coordinates", "Geographic coordinates present", false,
                facility.getLatitude() != null && facility.getLongitude() != null,
                facility.getLatitude() != null ? "lat/long set" : null));
        items.add(present("facility_type", "Facility type present", false,
                notBlank(facility.getFacilityType()), facility.getFacilityType()));
        items.add(present("ownership", "Ownership present", false,
                notBlank(facility.getOwnership()), facility.getOwnership()));
        items.add(present("operational_status", "Operational status present", false,
                notBlank(facility.getOperationalStatus()), facility.getOperationalStatus()));
        // Practitioner-in-charge — honest: PENDING unless a PIC contact exists.
        boolean hasPic = contacts.stream().anyMatch(FacilityConfigurationService::isPractitionerInCharge);
        items.add(new ChecklistItem("practitioner_in_charge", "Practitioner-in-charge (PIC) captured",
                hasPic ? "COMPLETE" : "PENDING", false,
                hasPic ? "PIC contact on file" : "PIC not yet captured"));
        // Regulatory compliance — owned by the facility regulatory registry; not asserted here.
        items.add(new ChecklistItem("regulatory_compliance", "Regulatory compliance",
                "PENDING", false, "Managed in the facility regulatory registry"));
        items.add(present("service_points", "Service points configured", true,
                activeServicePoints > 0, activeServicePoints + " active"));
        items.add(present("workspaces", "Workspaces configured", true,
                activeWorkspaces > 0, activeWorkspaces + " active"));
        // Queues are derived from service points and materialised downstream (PCT), not authored here.
        items.add(new ChecklistItem("queues_materialised", "Queues derived + materialised",
                activeServicePoints > 0 ? "PENDING" : "MISSING", false,
                activeServicePoints > 0
                        ? activeServicePoints + " queue definition(s) derived; materialisation confirmed in PCT"
                        : "No service points to derive queues from"));
        // Workforce / stock / digital readiness — pending unless assessed.
        items.add(new ChecklistItem("workforce", "Workforce linked",
                "PENDING", false, "Staffing linkage pending"));
        items.add(new ChecklistItem("stock_readiness", "Stock readiness",
                "PENDING", false, "Stock/store readiness pending"));
        boolean digitalReady = readiness != null && readiness.getAssessedAt() != null;
        items.add(new ChecklistItem("digital_readiness", "Digital readiness assessed",
                digitalReady ? "COMPLETE" : "PENDING", false,
                digitalReady ? "Assessed " + readiness.getAssessedAt() : "Connectivity/power/device assessment pending"));

        return new SetupChecklist(facility.getId(), state.name(), setupComplete, items);
    }

    @Transactional(readOnly = true)
    public List<QueueDefinitionView> getQueueDefinitions(TrustContext ctx, Long facilityId) {
        requireFacility(ctx, facilityId);
        // Derived strictly from active service points — retired service points do not produce active
        // queue definitions.
        return servicePointRepository.findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(facilityId).stream()
                .map(FacilityConfigurationService::toQueueDefinition)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownstreamReadiness getDownstreamReadiness(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = requireFacility(ctx, facilityId);
        long activeServicePoints = servicePointRepository.countByFacilityIdAndActiveTrue(facilityId);
        return new DownstreamReadiness(
                facility.getId(),
                facility.getFacilityUuid() != null ? facility.getFacilityUuid().toString() : null,
                activeServicePoints,
                activeServicePoints,
                facility.getUpdatedAt(),
                "TUSO owns these queue definitions. Live materialisation status is reported by PCT "
                        + "via the queue materialisation viewer; use reconcile to re-materialise.");
    }

    @Transactional(readOnly = true)
    public List<ConfigEvent> getConfigurationHistory(TrustContext ctx, Long facilityId) {
        requireFacility(ctx, facilityId);
        return outboxRepository
                .findTop50BySubjectTypeAndSubjectIdOrderByCreatedAtDesc(SUBJECT_FACILITY, String.valueOf(facilityId))
                .stream()
                .map(e -> new ConfigEvent(
                        e.getEventType(), e.getAggregateType(), e.getAggregateId(),
                        e.getCreatedAt(), e.getPublishedAt()))
                .toList();
    }

    // ── Writes ─────────────────────────────────────────────────────────

    /**
     * Emit a facility-level configuration-update event so downstream (PCT) can reconcile the derived
     * queue definitions. Reuses the existing TUSO outbox; does not invent a parallel event system.
     */
    @Transactional
    public ConfigurationSummary publishConfigurationUpdate(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = requireFacility(ctx, facilityId);
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_CONFIGURATION");
        event.setAggregateId(String.valueOf(facilityId));
        event.setEventType("FacilityQueueConfigurationChanged");
        event.setSubjectType(SUBJECT_FACILITY);
        event.setSubjectId(String.valueOf(facilityId));
        if (facility.getTenantId() != null) {
            event.setTenantId(facility.getTenantId().toString());
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("tenant", facility.getTenantId() != null ? facility.getTenantId().toString() : null);
        payload.put("facilityId", facilityId);
        payload.put("facilityUuid", facility.getFacilityUuid() != null ? facility.getFacilityUuid().toString() : null);
        payload.put("changeType", "CONFIGURATION_PUBLISHED");
        payload.put("timestamp", Instant.now().toString());
        payload.put("version", facility.getVersion());
        event.setPayload(payload);
        outboxRepository.save(event);
        // Reconcile trigger for PCT (dedicated queue-config channel). The event above is the
        // facility-scoped audit/history record; this one drives downstream materialisation.
        EventOutboxEntity reconcile = queueReconcileTrigger(
                facility.getFacilityUuid(), facility.getTenantId(), "CONFIGURATION_PUBLISHED");
        if (reconcile != null) {
            outboxRepository.save(reconcile);
        }
        log.info("Published facility configuration update for facility {} (actor={})", facilityId, actor(ctx));
        return getConfigurationSummary(ctx, facilityId);
    }

    // ── Derivation helpers ─────────────────────────────────────────────

    FacilityConfigurationState computeState(FacilityEntity facility, long activeServicePoints,
                                            long activeWorkspaces, long departments, boolean setupComplete) {
        boolean imported = facility.getRegulatoryStatus() == FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION;
        if (imported && !setupComplete) {
            return FacilityConfigurationState.IMPORTED_PENDING_CONFIGURATION;
        }
        if (setupComplete) {
            return FacilityConfigurationState.CONFIGURED;
        }
        if (activeServicePoints == 0 && activeWorkspaces == 0 && departments == 0) {
            return FacilityConfigurationState.NOT_STARTED;
        }
        return FacilityConfigurationState.PARTIALLY_CONFIGURED;
    }

    List<String> missingRequiredSections(FacilityEntity facility, long activeServicePoints, long activeWorkspaces) {
        List<String> missing = new ArrayList<>();
        if (!notBlank(facility.getFacilityCode())) {
            missing.add("facility_code");
        }
        if (activeServicePoints == 0) {
            missing.add("service_points");
        }
        if (activeWorkspaces == 0) {
            missing.add("workspaces");
        }
        return missing;
    }

    private static QueueDefinitionView toQueueDefinition(ServicePointEntity sp) {
        boolean active = sp.isActive() && "ACTIVE".equalsIgnoreCase(sp.getStatus());
        String type = sp.getServicePointType() != null ? sp.getServicePointType() : "FIFO";
        boolean priority = "TRIAGE".equalsIgnoreCase(type) || "EMERGENCY".equalsIgnoreCase(type);
        return new QueueDefinitionView(
                sp.getId().toString(),
                sp.getName(),
                type,
                sp.getServicePointType(),
                null,
                priority,
                true,
                true,
                active);
    }

    private static boolean isPractitionerInCharge(FacilityContactEntity c) {
        String role = c.getRole() != null ? c.getRole() : "";
        String type = c.getContactType() != null ? c.getContactType() : "";
        return role.toUpperCase().contains("IN_CHARGE") || role.toUpperCase().contains("PIC")
                || type.toUpperCase().contains("IN_CHARGE") || type.toUpperCase().contains("PIC");
    }

    private static ChecklistItem present(String key, String label, boolean required,
                                         boolean complete, String detail) {
        return new ChecklistItem(key, label, complete ? "COMPLETE" : "MISSING", required,
                complete ? detail : "Not set");
    }

    private static String sourceProvenance(FacilityEntity facility) {
        if (facility.getRegulatoryStatus() == FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION) {
            return "IMPORTED_FROM_MASTER_LIST";
        }
        return facility.getGofrId() != null ? "GOFR:" + facility.getGofrId() : "MANUAL";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private FacilityEntity requireFacility(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (ctx != null && facility.getTenantId() != null && !facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }
        return facility;
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
