package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.api.dto.LogisticsDtos;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LogisticsService {

    private final FulfillmentOrderRepository fulfillmentOrderRepository;
    private final DeliveryPlanRepository planRepository;
    private final DeliveryLegRepository legRepository;
    private final DeliveryExceptionRepository exceptionRepository;
    private final ProofRepository proofRepository;
    private final LogisticsProviderRepository providerRepository;
    private final DeliveryAssetRepository assetRepository;
    private final DeliveryMissionRepository missionRepository;
    private final HandoffEventRepository handoffRepository;
    private final CustodyRecordRepository custodyRepository;
    private final ObjectMapper objectMapper;

    public LogisticsService(FulfillmentOrderRepository fulfillmentOrderRepository,
                            DeliveryPlanRepository planRepository,
                            DeliveryLegRepository legRepository,
                            DeliveryExceptionRepository exceptionRepository,
                            ProofRepository proofRepository,
                            LogisticsProviderRepository providerRepository,
                            DeliveryAssetRepository assetRepository,
                            DeliveryMissionRepository missionRepository,
                            HandoffEventRepository handoffRepository,
                            CustodyRecordRepository custodyRepository,
                            ObjectMapper objectMapper) {
        this.fulfillmentOrderRepository = fulfillmentOrderRepository;
        this.planRepository = planRepository;
        this.legRepository = legRepository;
        this.exceptionRepository = exceptionRepository;
        this.proofRepository = proofRepository;
        this.providerRepository = providerRepository;
        this.assetRepository = assetRepository;
        this.missionRepository = missionRepository;
        this.handoffRepository = handoffRepository;
        this.custodyRepository = custodyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LogisticsDtos.DeliveryPlanView createPlan(LogisticsDtos.CreateDeliveryPlanRequest req) {
        FulfillmentOrderEntity f = fulfillmentOrderRepository.findById(req.fulfillmentId())
                .orElseThrow(() -> new IllegalArgumentException("Fulfillment not found: " + req.fulfillmentId()));

        DeliveryMode mode = DeliveryMode.valueOf(req.mode());

        DeliveryPlanEntity plan = new DeliveryPlanEntity();
        plan.setPlanId(UlidGenerator.generate());
        plan.setFulfillmentId(f.getFulfillmentId());
        plan.setMode(mode);
        plan.setOriginJson(JsonSupport.toJsonSafe(objectMapper, req.origin(), "{}"));
        plan.setDestinationJson(JsonSupport.toJsonSafe(objectMapper, req.destination(), "{}"));
        plan.setConstraintsSummaryJson(JsonSupport.toJsonSafe(objectMapper, req.constraintsSummary(), "{}"));
        plan.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        plan = planRepository.save(plan);

        // 1st leg: direct by default; multi-leg can be added later.
        DeliveryLegEntity leg = new DeliveryLegEntity();
        leg.setLegId(UlidGenerator.generate());
        leg.setPlanId(plan.getPlanId());
        leg.setSequenceNumber(1);
        leg.setLegType("DIRECT");
        leg.setMode(mode == DeliveryMode.HYBRID ? DeliveryMode.HUMAN_DELIVERY : mode);
        leg.setOriginJson(plan.getOriginJson());
        leg.setDestinationJson(plan.getDestinationJson());
        leg.setEstimatedDeparture(OffsetDateTime.now().plusMinutes(15));
        leg.setEstimatedArrival(OffsetDateTime.now().plusHours(2));
        legRepository.save(leg);

        return toView(plan);
    }

    public LogisticsDtos.DeliveryPlanView getPlan(String planId) {
        DeliveryPlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        return toView(plan);
    }

    public List<LogisticsDtos.DeliveryPlanView> listForFulfillment(String fulfillmentId) {
        return planRepository.findByFulfillmentIdOrderByCreatedAtAsc(fulfillmentId).stream().map(this::toView).toList();
    }

    @Transactional
    public LogisticsDtos.DeliveryPlanView updatePlan(String planId, LogisticsDtos.UpdatePlanRequest req) {
        DeliveryPlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (req.status() != null) {
            plan.setStatus(DeliveryStatus.valueOf(req.status()));
        }
        if (req.selectedProviderRef() != null) {
            plan.setSelectedProviderRef(req.selectedProviderRef());
        }
        if (req.estimatedWindow() != null) {
            plan.setEstimatedWindowJson(JsonSupport.toJsonSafe(objectMapper, req.estimatedWindow(), "{}"));
        }
        if (req.constraintsSummary() != null) {
            plan.setConstraintsSummaryJson(JsonSupport.toJsonSafe(objectMapper, req.constraintsSummary(), "{}"));
        }
        if (req.metadata() != null) {
            plan.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        }
        planRepository.save(plan);
        return toView(plan);
    }

    @Transactional
    public LogisticsDtos.DeliveryLegView updateLeg(String legId, LogisticsDtos.UpdateLegRequest req) {
        DeliveryLegEntity leg = legRepository.findById(legId)
                .orElseThrow(() -> new IllegalArgumentException("Leg not found: " + legId));

        if (req.status() != null) {
            leg.setStatus(DeliveryStatus.valueOf(req.status()));
        }
        if (req.assignedProviderId() != null) {
            leg.setAssignedProviderId(req.assignedProviderId());
        }
        if (req.assignedAssetId() != null) {
            leg.setAssignedAssetId(req.assignedAssetId());
        }
        if (req.estimatedDeparture() != null) leg.setEstimatedDeparture(req.estimatedDeparture());
        if (req.estimatedArrival() != null) leg.setEstimatedArrival(req.estimatedArrival());
        if (req.actualDeparture() != null) leg.setActualDeparture(req.actualDeparture());
        if (req.actualArrival() != null) leg.setActualArrival(req.actualArrival());
        if (req.metadata() != null) leg.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        legRepository.save(leg);

        return new LogisticsDtos.DeliveryLegView(
                leg.getLegId(),
                leg.getSequenceNumber(),
                leg.getLegType(),
                leg.getMode().name(),
                leg.getStatus().name(),
                JsonSupport.parseJsonSafe(objectMapper, leg.getOriginJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, leg.getDestinationJson(), Map.of()),
                leg.getAssignedProviderId(),
                leg.getAssignedAssetId(),
                leg.getEstimatedDeparture(),
                leg.getEstimatedArrival(),
                leg.getActualDeparture(),
                leg.getActualArrival(),
                JsonSupport.parseJsonSafe(objectMapper, leg.getMetadataJson(), Map.of())
        );
    }

    // ── Provider / asset registry (tenant-scoped) ──────────────────────────

    @Transactional
    public LogisticsDtos.ProviderView createProvider(UUID tenantId, LogisticsDtos.CreateProviderRequest req) {
        LogisticsProviderEntity p = new LogisticsProviderEntity();
        p.setProviderId(UlidGenerator.generate());
        p.setTenantId(tenantId);
        p.setProviderType(LogisticsProviderType.valueOf(req.providerType()));
        p.setName(req.name());
        p.setSupportedModesJson(JsonSupport.toJsonSafe(objectMapper, req.supportedModes(), "[]"));
        p.setServiceAreasJson(JsonSupport.toJsonSafe(objectMapper, req.serviceAreas(), "[]"));
        p.setCapabilitiesJson(JsonSupport.toJsonSafe(objectMapper, req.capabilities(), "{}"));
        if (req.active() != null) p.setActive(req.active());
        p = providerRepository.save(p);
        return toView(p);
    }

    public List<LogisticsDtos.ProviderView> listProviders(UUID tenantId, boolean activeOnly) {
        return providerRepository.findByTenantIdAndActiveOrderByUpdatedAtDesc(tenantId, activeOnly).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public LogisticsDtos.AssetView createAsset(LogisticsDtos.CreateAssetRequest req) {
        providerRepository.findById(req.providerId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + req.providerId()));

        DeliveryAssetEntity a = new DeliveryAssetEntity();
        a.setAssetId(UlidGenerator.generate());
        a.setProviderId(req.providerId());
        a.setAssetType(AssetType.valueOf(req.assetType()));
        a.setAssetCode(req.assetCode());
        if (req.status() != null) a.setStatus(AssetStatus.valueOf(req.status()));
        a.setCapabilitySummaryJson(JsonSupport.toJsonSafe(objectMapper, req.capabilitySummary(), "{}"));
        if (req.telemetrySupported() != null) a.setTelemetrySupported(req.telemetrySupported());
        if (req.active() != null) a.setActive(req.active());
        a = assetRepository.save(a);
        return toView(a);
    }

    public List<LogisticsDtos.AssetView> listAssets(String providerId, boolean activeOnly) {
        return assetRepository.findByProviderIdAndActiveOrderByUpdatedAtDesc(providerId, activeOnly).stream()
                .map(this::toView)
                .toList();
    }

    // ── Missions / handoffs / custody ──────────────────────────────────────

    /**
     * Dispatch lifecycle is owned by Nhume (SoR); these mission-lifecycle methods
     * remain for operator-visibility CRUD only (ops console mission log, not a
     * competing dispatch engine).
     */
    @Transactional
    public LogisticsDtos.MissionView createMission(LogisticsDtos.CreateMissionRequest req) {
        legRepository.findById(req.legId())
                .orElseThrow(() -> new IllegalArgumentException("Leg not found: " + req.legId()));

        DeliveryMissionEntity m = new DeliveryMissionEntity();
        m.setMissionId(UlidGenerator.generate());
        m.setLegId(req.legId());
        m.setMissionType(MissionType.valueOf(req.missionType()));
        m.setCommandRef(req.commandRef());
        m.setTelemetryRef(req.telemetryRef());
        m.setLaunchAt(req.launchAt());
        m.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        m = missionRepository.save(m);
        return toView(m);
    }

    /** Dispatch lifecycle is owned by Nhume (SoR); this remains for operator-visibility CRUD only. */
    public List<LogisticsDtos.MissionView> listMissions(String legId) {
        return missionRepository.findByLegIdOrderByCreatedAtDesc(legId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public LogisticsDtos.HandoffView recordHandoff(LogisticsDtos.RecordHandoffRequest req) {
        HandoffEventEntity h = new HandoffEventEntity();
        h.setHandoffId(UlidGenerator.generate());
        h.setFulfillmentId(req.fulfillmentId());
        h.setLegId(req.legId());
        h.setHandoffType(req.handoffType());
        h.setFromPartyJson(JsonSupport.toJsonSafe(objectMapper, req.fromParty(), "{}"));
        h.setToPartyJson(JsonSupport.toJsonSafe(objectMapper, req.toParty(), "{}"));
        h.setLocationJson(JsonSupport.toJsonSafe(objectMapper, req.location(), "{}"));
        h.setOccurredAt(req.occurredAt());
        h.setIntegrityOutcome(req.integrityOutcome());
        h.setNotes(req.notes());
        h.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        h = handoffRepository.save(h);
        return toView(h);
    }

    public List<LogisticsDtos.HandoffView> listHandoffs(String fulfillmentId, String legId) {
        if (legId != null) {
            return handoffRepository.findByLegIdOrderByOccurredAtDesc(legId).stream()
                    .map(this::toView)
                    .toList();
        }
        if (fulfillmentId != null) {
            return handoffRepository.findByFulfillmentIdOrderByOccurredAtDesc(fulfillmentId).stream()
                    .map(this::toView)
                    .toList();
        }
        return List.of();
    }

    @Transactional
    public LogisticsDtos.CustodyView createCustody(LogisticsDtos.CreateCustodyRequest req) {
        CustodyRecordEntity c = new CustodyRecordEntity();
        c.setCustodyId(UlidGenerator.generate());
        c.setFulfillmentId(req.fulfillmentId());
        c.setPackageRef(req.packageRef());
        c.setCustodianType(req.custodianType());
        c.setCustodianRef(req.custodianRef());
        c.setFromAt(req.fromAt());
        c.setToAt(req.toAt());
        c.setConditionSummaryJson(JsonSupport.toJsonSafe(objectMapper, req.conditionSummary(), "{}"));
        c.setSealStatus(req.sealStatus());
        c.setTemperatureStatus(req.temperatureStatus());
        c.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        c = custodyRepository.save(c);
        return toView(c);
    }

    public List<LogisticsDtos.CustodyView> listCustody(String fulfillmentId) {
        return custodyRepository.findByFulfillmentIdOrderByFromAtDesc(fulfillmentId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public LogisticsDtos.DeliveryExceptionView raiseException(LogisticsDtos.RaiseExceptionRequest req) {
        DeliveryExceptionEntity ex = new DeliveryExceptionEntity();
        ex.setExceptionId(UlidGenerator.generate());
        ex.setPlanId(req.planId());
        ex.setLegId(req.legId());
        ex.setExceptionType(req.exceptionType());
        if (req.severity() != null) ex.setSeverity(ExceptionSeverity.valueOf(req.severity()));
        ex.setStatus(ExceptionStatus.OPEN);
        ex.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        ex = exceptionRepository.save(ex);
        return toView(ex);
    }

    public List<LogisticsDtos.DeliveryExceptionView> listExceptions(String planId, String status) {
        if (planId != null) {
            return exceptionRepository.findByPlanIdOrderByRaisedAtDesc(planId).stream().map(this::toView).toList();
        }
        if (status != null) {
            return exceptionRepository.findTop100ByStatusOrderByRaisedAtDesc(ExceptionStatus.valueOf(status)).stream().map(this::toView).toList();
        }
        return exceptionRepository.findTop100ByStatusOrderByRaisedAtDesc(ExceptionStatus.OPEN).stream().map(this::toView).toList();
    }

    @Transactional
    public LogisticsDtos.ProofView recordProof(LogisticsDtos.RecordProofRequest req) {
        ProofEntity p = new ProofEntity();
        p.setProofId(UlidGenerator.generate());
        p.setPlanId(req.planId());
        p.setHandoffId(req.handoffId());
        p.setProofType(ProofType.valueOf(req.proofType()));
        p.setProofReference(req.proofReference());
        p.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        p = proofRepository.save(p);
        return toView(p);
    }

    public List<LogisticsDtos.ProofView> listProofs(String planId, String handoffId) {
        if (handoffId != null) {
            return proofRepository.findByHandoffIdOrderByCreatedAtDesc(handoffId).stream().map(this::toView).toList();
        }
        if (planId != null) {
            return proofRepository.findByPlanIdOrderByCreatedAtDesc(planId).stream().map(this::toView).toList();
        }
        return List.of();
    }

    private LogisticsDtos.DeliveryPlanView toView(DeliveryPlanEntity plan) {
        List<DeliveryLegEntity> legs = legRepository.findByPlanIdOrderBySequenceNumberAsc(plan.getPlanId());
        List<LogisticsDtos.DeliveryLegView> legViews = legs.stream().map(l -> new LogisticsDtos.DeliveryLegView(
                l.getLegId(),
                l.getSequenceNumber(),
                l.getLegType(),
                l.getMode().name(),
                l.getStatus().name(),
                JsonSupport.parseJsonSafe(objectMapper, l.getOriginJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, l.getDestinationJson(), Map.of()),
                l.getAssignedProviderId(),
                l.getAssignedAssetId(),
                l.getEstimatedDeparture(),
                l.getEstimatedArrival(),
                l.getActualDeparture(),
                l.getActualArrival(),
                JsonSupport.parseJsonSafe(objectMapper, l.getMetadataJson(), Map.of())
        )).toList();

        return new LogisticsDtos.DeliveryPlanView(
                plan.getPlanId(),
                plan.getFulfillmentId(),
                plan.getMode().name(),
                plan.getStatus().name(),
                JsonSupport.parseJsonSafe(objectMapper, plan.getOriginJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, plan.getDestinationJson(), Map.of()),
                plan.getSelectedProviderRef(),
                JsonSupport.parseJsonSafe(objectMapper, plan.getEstimatedWindowJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, plan.getConstraintsSummaryJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, plan.getMetadataJson(), Map.of()),
                legViews,
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private LogisticsDtos.DeliveryExceptionView toView(DeliveryExceptionEntity ex) {
        return new LogisticsDtos.DeliveryExceptionView(
                ex.getExceptionId(),
                ex.getPlanId(),
                ex.getLegId(),
                ex.getExceptionType(),
                ex.getSeverity().name(),
                ex.getStatus().name(),
                ex.getRaisedAt(),
                ex.getResolutionSummary(),
                ex.getResolvedAt(),
                JsonSupport.parseJsonSafe(objectMapper, ex.getMetadataJson(), Map.of())
        );
    }

    private LogisticsDtos.ProofView toView(ProofEntity p) {
        return new LogisticsDtos.ProofView(
                p.getProofId(),
                p.getPlanId(),
                p.getHandoffId(),
                p.getProofType().name(),
                p.getProofReference(),
                p.getVerificationOutcome(),
                p.getVerifiedAt(),
                JsonSupport.parseJsonSafe(objectMapper, p.getMetadataJson(), Map.of()),
                p.getCreatedAt()
        );
    }

    private LogisticsDtos.ProviderView toView(LogisticsProviderEntity p) {
        return new LogisticsDtos.ProviderView(
                p.getProviderId(),
                p.getProviderType().name(),
                p.getName(),
                JsonSupport.parseJsonSafe(objectMapper, p.getSupportedModesJson(), List.of()),
                JsonSupport.parseJsonSafe(objectMapper, p.getServiceAreasJson(), List.of()),
                JsonSupport.parseJsonSafe(objectMapper, p.getCapabilitiesJson(), Map.of()),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private LogisticsDtos.AssetView toView(DeliveryAssetEntity a) {
        return new LogisticsDtos.AssetView(
                a.getAssetId(),
                a.getProviderId(),
                a.getAssetType().name(),
                a.getAssetCode(),
                a.getStatus().name(),
                JsonSupport.parseJsonSafe(objectMapper, a.getCapabilitySummaryJson(), Map.of()),
                a.isTelemetrySupported(),
                a.isActive(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }

    private LogisticsDtos.MissionView toView(DeliveryMissionEntity m) {
        return new LogisticsDtos.MissionView(
                m.getMissionId(),
                m.getLegId(),
                m.getMissionType().name(),
                m.getMissionStatus().name(),
                m.getCommandRef(),
                m.getTelemetryRef(),
                m.getLaunchAt(),
                m.getCompletedAt(),
                m.getFailureReason(),
                JsonSupport.parseJsonSafe(objectMapper, m.getMetadataJson(), Map.of()),
                m.getCreatedAt()
        );
    }

    private LogisticsDtos.HandoffView toView(HandoffEventEntity h) {
        return new LogisticsDtos.HandoffView(
                h.getHandoffId(),
                h.getFulfillmentId(),
                h.getLegId(),
                h.getHandoffType(),
                JsonSupport.parseJsonSafe(objectMapper, h.getFromPartyJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, h.getToPartyJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, h.getLocationJson(), Map.of()),
                h.getOccurredAt(),
                h.getIntegrityOutcome(),
                h.getNotes(),
                JsonSupport.parseJsonSafe(objectMapper, h.getMetadataJson(), Map.of())
        );
    }

    private LogisticsDtos.CustodyView toView(CustodyRecordEntity c) {
        return new LogisticsDtos.CustodyView(
                c.getCustodyId(),
                c.getFulfillmentId(),
                c.getPackageRef(),
                c.getCustodianType(),
                c.getCustodianRef(),
                c.getFromAt(),
                c.getToAt(),
                JsonSupport.parseJsonSafe(objectMapper, c.getConditionSummaryJson(), Map.of()),
                c.getSealStatus(),
                c.getTemperatureStatus(),
                JsonSupport.parseJsonSafe(objectMapper, c.getMetadataJson(), Map.of())
        );
    }
}

